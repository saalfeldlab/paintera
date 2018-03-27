package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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
import bdv.img.AccessBoxRandomAccessibleOnGet;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseEvent;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.tmp.BiConsumerRealRandomAccessible;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.LinAlgHelpers;
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

	private final ExecutorService paintQueue = Executors.newFixedThreadPool( 1 );

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

		final RandomAccessibleInterval< UnsignedByteType > labels = this.canvas.get();
		if ( labels == null )
			return;

		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform.copy();
		final AffineTransform3D labelToGlobalTransform = this.labelToGlobalTransform.copy();

		final AffineTransform3D viewerTransformWithoutTranslation = new AffineTransform3D();
		viewer.getState().getViewerTransform( viewerTransformWithoutTranslation );
		viewerTransformWithoutTranslation.setTranslation( 0, 0, 0 );
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		labelToGlobalTransform.setTranslation( 0, 0, 0 );

		final AffineTransform3D relevantTransform = labelToGlobalTransformWithoutTranslation
				.copy()
				.inverse()
				.concatenate( viewerTransformWithoutTranslation.inverse() );

		final double[] ones = { 1.0, 1.0, 1.0 };
		labelToGlobalTransformWithoutTranslation.apply( ones, ones );
		viewerTransformWithoutTranslation.apply( ones, ones );

		// TODO is division by 2 save?
		final double range = LinAlgHelpers.length( ones ) / 2;

		final double radius = this.brushOverlay.viewerRadiusProperty().get();

		final RandomAccessible< UnsignedByteType > labelSource = Views.extendValue( labels, new UnsignedByteType( 1 ) );
		final AccessBoxRandomAccessibleOnGet< UnsignedByteType > trackingLabelSource = new AccessBoxRandomAccessibleOnGet<>( labelSource );

		LOG.debug( "r={} center=({}, )", radius, viewerX, viewerY );

		final Supplier< BiConsumer< RealLocalizable, BitType > > function = () -> {
			final double radiusSquared = radius * radius;
			return ( loc, t ) -> {
				final double z = loc.getDoublePosition( 2 );
				boolean isValid = false;
				if ( z <= range && z >= -range )
				{
					final double x = loc.getDoublePosition( 0 );
					final double y = loc.getDoublePosition( 1 );
					final double dx = x - viewerX;
					final double dy = y - viewerY;
					if ( dx * dx + dy * dy < radiusSquared )
						isValid = true;
				}
				t.set( isValid );
			};
		};
		final AffineRandomAccessible< BitType, AffineGet > containsCheck =
				RealViews.affine( new BiConsumerRealRandomAccessible<>( 3, function, () -> new BitType( true ) ), labelToViewerTransform.inverse() );

		final RealPoint seedReal = new RealPoint( viewerX, viewerY, 0 );
		labelToViewerTransform.applyInverse( seedReal, seedReal );
		final Point seed = new Point( IntStream.range( 0, 3 ).mapToDouble( seedReal::getDoublePosition ).mapToLong( Math::round ).toArray() );
		FloodFill.fill( containsCheck, trackingLabelSource, seed, new UnsignedByteType( 1 ), new DiamondShape( 1 ), ( comp, ref ) -> comp.getA().get() && comp.getB().get() == 0 );


		final long[] min = trackingLabelSource.getMin().clone();
		final long[] max = trackingLabelSource.getMax().clone();
//		LOG.warn( "Touched min={} and max={} while painting", min, max );
		if ( this.interval.get() != null )
		{
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.min( this.interval.get().min( d ), min[ d ] );
				max[ d ] = Math.max( this.interval.get().max( d ), max[ d ] );
			}
		}
		LOG.debug( "Combined min={} and max={} after painting", min, max );
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
			paintQueue.submit( () -> paint( event, consume ) );
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

			paintQueue.submit( () -> {

				for ( int i = 0; i < l; ++i )
				{
					paint( p1[ 0 ], p1[ 1 ] );
					LinAlgHelpers.add( p1, d, p1 );
				}
				paint( x, y );
				startX = x;
				startY = y;
				repaintRequest.run();
			} );
		}

		@Override
		public void endDrag( final MouseEvent event )
		{
			paintQueue.submit( () -> applyMask() );
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
