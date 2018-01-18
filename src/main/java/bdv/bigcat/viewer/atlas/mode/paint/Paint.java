package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.mask.MaskInUse;
import bdv.bigcat.viewer.atlas.data.mask.MaskInfo;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.mode.paint.neighborhood.HyperEllipsoidNeighborhood;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.util.Affine3DHelpers;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseEvent;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

public class Paint
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ViewerPanelFX viewer;

	private final ViewerNode.ViewerAxis viewerAxis;

	private final int viewerAxisDimension;

	private final SourceInfo sourceInfo;

	private final BrushOverlay brushOverlay;

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty( 5.0 );

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty( 1.0 );

	private final AffineTransform3D labelToViewerTransform = new AffineTransform3D();

	private final AffineTransform3D labelToGlobalTransform = new AffineTransform3D();

	private final SimpleObjectProperty< MaskedSource< ?, ? > > maskedSource = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< RandomAccessibleInterval< UnsignedByteType > > canvas = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Interval > interval = new SimpleObjectProperty<>();

	private final RealPoint labelLocation = new RealPoint( 3 );

	private final Runnable repaintRequest;

	public Paint(
			final ViewerPanelFX viewer,
			final ViewerAxis viewerAxis,
			final SourceInfo sourceInfo,
			final GlobalTransformManager manager,
			final Runnable repaintRequest )
	{
		super();
		this.viewer = viewer;
		this.viewerAxis = viewerAxis;
		this.viewerAxisDimension = viewerAxisToDimensionIndex( this.viewerAxis );
		this.sourceInfo = sourceInfo;
		this.brushOverlay = new BrushOverlay( this.viewer, manager );
		this.brushOverlay.radiusProperty().set( brushRadius.get() );
		this.brushOverlay.radiusProperty().bind( brushRadius );
		this.repaintRequest = repaintRequest;
	}

	private void setCoordinates( final double x, final double y, final AffineTransform3D labelTransform )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
	}

	public void hideBrushOverlay()
	{
		setBrushOverlayVisible( false );
	}

	public void showBrushOverlay()
	{
		setBrushOverlayVisible( true );
	}

	public void setBrushOverlayVisible( final boolean visible )
	{
		this.brushOverlay.setVisible( visible );
		viewer.getDisplay().drawOverlays();
	}

	public void changeBrushRadius( final double sign )
	{
		if ( sign > 0 )
			decreaseBrushRadius();
		else if ( sign < 0 )
			increaseBrushRadius();
	}

	public void decreaseBrushRadius()
	{
		setBrushRadius( brushRadius.get() - brushRadiusIncrement.get() );
	}

	public void increaseBrushRadius()
	{
		setBrushRadius( brushRadius.get() + brushRadiusIncrement.get() );
	}

	public void setBrushRadius( final double radius )
	{
		if ( radius > 0 && radius < Math.min( viewer.getWidth(), viewer.getHeight() ) )
			this.brushRadius.set( radius );
	}

	public MouseDragFX paintLabel( final String name, final Supplier< Long > id, @SuppressWarnings( "unchecked" ) final Predicate< MouseEvent >... eventFilter )
	{
		return new PaintDrag( name, eventFilter, true, this, id );
	}

	public void paint( final MouseEvent event )
	{
		paint( event, true );
	}

	private void paint( final MouseEvent event, final boolean consume )
	{
		paint( event.getX(), event.getY() );
		if ( consume )
			event.consume();
		repaintRequest.run();
//		applyMask();

	}

	private void prepareForPainting( final Long id ) throws MaskInUse
	{
		final ViewerState state = viewer.getState();
		final Source< ? > viewerSource = sourceInfo.currentSourceProperty().get();
		final int currentSource = sourceInfo.currentSourceIndexInVisibleSources().get();
		this.canvas.set( null );
		this.maskedSource.set( null );
		this.interval.set( null );

		LOG.debug( "Prepare for painting with source {}", viewerSource );

		if ( viewerSource == null || !( viewerSource instanceof DataSource< ?, ? > ) || !sourceInfo.getState( viewerSource ).visibleProperty().get() )
			return;

		final DataSource< ?, ? > source = ( DataSource< ?, ? > ) viewerSource;
		final MaskedSource< ?, ? > maskedSource = sourceInfo.getState( source ).maskedSourceProperty().get();

		if ( maskedSource == null )
			return;

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		state.getViewerTransform( viewerTransform );
		final int level = state.getBestMipMapLevel( viewerTransform, currentSource );
		maskedSource.getSourceTransform( state.getCurrentGroup(), level, labelToGlobalTransform );
		this.labelToViewerTransform.set( viewerTransform.copy().concatenate( labelToGlobalTransform ) );

		if ( id == null )
		{
			LOG.debug( "Do not a valid id to paint: {} -- will not paint.", id );
			return;
		}

		final UnsignedLongType value = new UnsignedLongType( id );

		final MaskInfo< UnsignedLongType > mask = new MaskInfo<>( 0, level, value );
		final RandomAccessibleInterval< UnsignedByteType > canvas = maskedSource.generateMask( mask );
		// canvasSource.getDataSource( state.getCurrentTimepoint(), level );
		LOG.debug( "Setting canvas to {}", canvas );
		this.canvas.set( canvas );
		this.maskedSource.set( maskedSource );
	}

	private void paint( final double x, final double y )
	{

		setCoordinates( x, y, labelToGlobalTransform );
		paint( this.labelLocation );

	}

	private void paint( final RealLocalizable coords )
	{
		// TODO currently, this paints in 3D. Maybe we should paint 2D only so
		// we see every pixel that we paint into.
		final RandomAccessibleInterval< UnsignedByteType > labels = this.canvas.get();
		LOG.debug( "Painting on canvas {} at {}", labels, coords );
		if ( labels == null )
			return;
		final AffineTransform3D labelTransform = this.labelToViewerTransform;
		final RandomAccessible< UnsignedByteType > labelSource = Views.extendZero( labels );

		final double brushRadius = this.brushRadius.get();

		final double[] scales = new double[] {
				Affine3DHelpers.extractScale( labelTransform, 0 ),
				Affine3DHelpers.extractScale( labelTransform, 1 ),
				Affine3DHelpers.extractScale( labelTransform, 2 )
		};
		final long[] pos = new long[] {
				Math.round( coords.getDoublePosition( 0 ) ),
				Math.round( coords.getDoublePosition( 1 ) ),
				Math.round( coords.getDoublePosition( 2 ) ) };

		final long[] radii = new long[] {
				Math.round( brushRadius / scales[ 0 ] ),
				Math.round( brushRadius / scales[ 1 ] ),
				Math.round( brushRadius / scales[ 2 ] ) };

		// TODO this check is necessary because we would paint into the border
		// pixel if we painted outside the defined interval as we use
		// Views.extendBorder to extend the image. As a side effect, we do not
		// paint at all when the defined interval and brush overlap in part but
		// the brush center is outside the interval.
//		if ( !Intervals.contains( labelSource, new Point( pos ) ) )
//			return;

		LOG.debug( "Painting with radius={} position={} brush-radius={} active-viewer-axis={}",
				Arrays.toString( radii ),
				Arrays.toString( pos ),
				brushRadius,
				viewerAxisDimension );

		// TODO use Views.extendValue once it accepts arbitrary types (instead
		// of Views..extendBorder)
		final RandomAccess< UnsignedByteType > ra = labelSource.randomAccess();
		final Neighborhood< UnsignedByteType > ellipse =
				HyperEllipsoidNeighborhood.< UnsignedByteType >factory().create( pos, radii, ra );

		final Cursor< UnsignedByteType > cursor = ellipse.cursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().set( 1 );
		}

		final long[] min = this.interval.get() != null ? Intervals.minAsLongArray( this.interval.get() ) : new long[] { Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE };
		final long[] max = this.interval.get() != null ? Intervals.maxAsLongArray( this.interval.get() ) : new long[] { Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE };

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = Math.min( pos[ d ] - radii[ d ], min[ d ] );
			max[ d ] = Math.max( pos[ d ] + radii[ d ], max[ d ] );
		}

		this.interval.set( new FinalInterval( min, max ) );

	}

	private static int viewerAxisToDimensionIndex( final ViewerNode.ViewerAxis axis )
	{
		switch ( axis )
		{
		case X:
			return 0;
		case Y:
			return 1;
		case Z:
			return 2;
		default:
			return -1;
		}
	}

	private class PaintDrag extends MouseDragFX
	{

		private final Supplier< Long > id;

		public PaintDrag(
				final String name,
				final Predicate< MouseEvent >[] eventFilter,
				final boolean consume,
				final Object transformLock,
				final Supplier< Long > id )
		{
			super( name, eventFilter, consume, transformLock );
			this.id = id;
		}

		@Override
		public void initDrag( final MouseEvent event )
		{
			try
			{
				prepareForPainting( id.get() );
			}
			catch ( final MaskInUse e )
			{
				LOG.info( "{} -- will not paint.", e.getMessage() );
				return;
			}
			paint( event, consume );
		}

		@Override
		public void drag( final MouseEvent event )
		{
			// TODO we assume that no changes to current source or viewer/global
			// transform can happen during this drag.
			final double x = event.getX();
			final double y = event.getY();
			setCoordinates( startX, startY, labelToGlobalTransform );
			LOG.debug( "Drag: paint at screen=({},{}) / world={}", x, y, labelLocation );
			final double[] p1 = new double[ 3 ];
			final RealPoint rp1 = RealPoint.wrap( p1 );
			labelLocation.localize( p1 );

			setCoordinates( x, y, labelToGlobalTransform );
			final double[] d = new double[ 3 ];
			labelLocation.localize( d );

			LinAlgHelpers.subtract( d, p1, d );

			final double l = LinAlgHelpers.length( d );
			LinAlgHelpers.normalize( d );

			for ( int i = 1; i < l; ++i )
			{
				LinAlgHelpers.add( p1, d, p1 );
				paint( rp1 );
			}
			paint( labelLocation );
			startX = x;
			startY = y;
			repaintRequest.run();
		}

		@Override
		public void endDrag( final MouseEvent event )
		{
			applyMask();
		}

	}

	public DoubleProperty brushRadiusProperty()
	{
		return this.brushRadius;
	}

	public DoubleProperty brushRadiusIncrementProperty()
	{
		return this.brushRadiusIncrement;
	}

	private void applyMask()
	{
		Optional.ofNullable( maskedSource.get() ).ifPresent( ms -> ms.applyMask( canvas.get(), interval.get() ) );
	}

}
