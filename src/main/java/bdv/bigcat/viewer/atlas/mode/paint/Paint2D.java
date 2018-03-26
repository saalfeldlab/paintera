package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.mask.MaskInUse;
import bdv.bigcat.viewer.atlas.data.mask.MaskInfo;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.state.GlobalTransformManager;
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
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;

public class Paint2D
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ViewerPanelFX viewer;

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

	public Paint2D(
			final ViewerPanelFX viewer,
			final SourceInfo sourceInfo,
			final GlobalTransformManager manager,
			final Runnable repaintRequest )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.brushOverlay = new BrushOverlay( this.viewer, manager );
		this.brushOverlay.physicalRadiusProperty().bind( brushRadius );
		this.repaintRequest = repaintRequest;
	}

	private void setCoordinates( final double x, final double y, final AffineTransform3D labelTransform )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		final RealPoint copy = new RealPoint( labelLocation );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
		this.labelToViewerTransform.applyInverse( copy, copy );
	}

	private < T extends RealLocalizable & RealPositionable > void setCoordinates(
			final double x,
			final double y,
			final AffineTransform3D labelTransform,
			final T labelLocation )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		final RealPoint copy = new RealPoint( labelLocation );

		viewer.displayToGlobalCoordinates( labelLocation );

		labelTransform.applyInverse( labelLocation, labelLocation );
		this.labelToViewerTransform.applyInverse( copy, copy );
//		LOG.warn( "WAS DA LOS ? {} {}", copy, labelLocation );
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
		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		final int level = state.getBestMipMapLevel( screenScaleTransform, currentSource );
		maskedSource.getSourceTransform( 0, level, labelToGlobalTransform );
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

//	private void paint( final double x, final double y )
//	{
//
//		setCoordinates( x, y, labelToGlobalTransform );
//		paint( this.labelLocation, x, y );
//
//	}

	private void paint( final double viewerX, final double viewerY )
	{

		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform.copy();

		final RandomAccessibleInterval< UnsignedByteType > labels = this.canvas.get();
		if ( labels == null )
			return;

		final RandomAccessible< UnsignedByteType > labelSource = Views.extendZero( labels );

		final RealRandomAccessible< UnsignedByteType > interpolatedLabels = Views.interpolate( labelSource, new NearestNeighborInterpolatorFactory<>() );
		final RealRandomAccess< UnsignedByteType > labelSourceAccess = interpolatedLabels.realRandomAccess();
		final AffineTransform3D tfFront = labelToViewerTransform.copy().preConcatenate( new Translation3D( 0, 0, 1.0 / 2.0 ) );
		final AffineTransform3D tfBack = labelToViewerTransform.copy().preConcatenate( new Translation3D( 0, 0, -1.0 / 2.0 ) );
		final MixedTransformView< UnsignedByteType > labelsFront = Views.hyperSlice( RealViews.affine( interpolatedLabels, tfFront ), 2, 0l );
		final MixedTransformView< UnsignedByteType > labelsBack = Views.hyperSlice( RealViews.affine( interpolatedLabels, tfBack ), 2, 0l );

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		this.viewer.getState().getViewerTransform( viewerTransform );
//		LOG.warn( "ltv={} ltg={} gtv={}", labelToViewerTransform, labelToGlobalTransform, viewerTransform );

		final double radius = this.brushOverlay.viewerRadiusProperty().get();
		final long longRadius = ( long ) Math.ceil( radius );
		final long longX = ( long ) viewerX;
		final long longY = ( long ) viewerY;

		final HyperSphereShape sphere = new HyperSphereShape( longRadius );
		final RandomAccess< Neighborhood< UnsignedByteType > > accessFront = sphere.neighborhoodsRandomAccessible( labelsFront ).randomAccess();
		final RandomAccess< Neighborhood< UnsignedByteType > > accessBack = sphere.neighborhoodsRandomAccessible( labelsBack ).randomAccess();
		accessFront.setPosition( longX, 0 );
		accessFront.setPosition( longY, 1 );
		accessBack.setPosition( accessFront );
		final Cursor< UnsignedByteType > front = accessFront.get().cursor();
		final Cursor< UnsignedByteType > back = accessBack.get().cursor();
		// This is necessary because
		// sphere.neighborhoodsRandomAccessible.randomAccess() sets the position
		// of the source randomAccess
//		accessTrackingSource.randomAccess();
		final long[] min = new long[] { Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE };
		final long[] max = new long[] { Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE };
		for ( ; front.hasNext(); )
		{
//			LOG.warn( "BEFORE {} {}", accessTrackingSource.getMin(), accessTrackingSource.getMax() );
			front.next().set( 1 );
			back.next().set( 1 );
			labelSourceAccess.setPosition( front.getDoublePosition( 0 ), 0 );
			labelSourceAccess.setPosition( front.getDoublePosition( 1 ), 1 );
			labelSourceAccess.setPosition( 0, 2 );
			tfFront.applyInverse( labelSourceAccess, labelSourceAccess );
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.min( ( long ) Math.floor( labelSourceAccess.getDoublePosition( d ) ), min[ d ] );
				max[ d ] = Math.max( ( long ) Math.ceil( labelSourceAccess.getDoublePosition( d ) ), min[ d ] );
			}
			labelSourceAccess.get().set( 1 );
			labelSourceAccess.setPosition( back.getDoublePosition( 0 ), 0 );
			labelSourceAccess.setPosition( back.getDoublePosition( 1 ), 1 );
			labelSourceAccess.setPosition( 0, 2 );
			tfBack.applyInverse( labelSourceAccess, labelSourceAccess );
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.min( ( long ) Math.floor( labelSourceAccess.getDoublePosition( d ) ), min[ d ] );
				max[ d ] = Math.max( ( long ) Math.ceil( labelSourceAccess.getDoublePosition( d ) ), min[ d ] );
			}
			labelSourceAccess.get().set( 1 );
//			LOG.warn( "AFTER  {} {}", accessTrackingSource.getMin(), accessTrackingSource.getMax() );
		}

//		LOG.warn( "longRadius={} numDimensions={}", longRadius, accessFront.numDimensions() );

//		final long[] min = accessTrackingSource.getMin().clone();
//		final long[] max = accessTrackingSource.getMax().clone();
//		LOG.warn( "Touched min={} and max={} while painting", min, max );
		if ( this.interval.get() != null )
		{
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.min( this.interval.get().min( d ), min[ d ] );
				max[ d ] = Math.max( this.interval.get().max( d ), max[ d ] );
			}
		}
//		LOG.warn( "Combined min={} and max={} after painting", min, max );
		final double[] minReal = new double[] { min[ 0 ], min[ 1 ], min[ 2 ] };
		final double[] maxReal = new double[] { max[ 0 ], max[ 1 ], max[ 2 ] };
		this.labelToGlobalTransform.apply( minReal, minReal );
		this.labelToGlobalTransform.apply( maxReal, maxReal );
//		LOG.warn( "MIN REAL: {}", minReal );
//		LOG.warn( "MIN REAL: {}", maxReal );

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

			final double[] p1 = new double[] { startX, startY };

//			LOG.warn( "Drag: paint at screen=({},{}) / start=({},{})", x, y, startX, startY );

			final double[] d = new double[] { x, y };

			LinAlgHelpers.subtract( d, p1, d );

			final double l = LinAlgHelpers.length( d );
			LinAlgHelpers.normalize( d );

			for ( int i = 0; i < l; ++i )
			{
				paint( p1[ 0 ], p1[ 1 ] );
				LinAlgHelpers.add( p1, d, p1 );
			}
			paint( x, y );
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
