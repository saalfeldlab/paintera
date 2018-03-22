package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.BiConsumer;
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
import bdv.img.AccessBoxRandomAccessible;
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
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.tmp.BiConsumerRealRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
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
		LOG.warn( "WAS DA LOS ? {} {}", copy, labelLocation );
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

	private void paint( final double x, final double y )
	{

		setCoordinates( x, y, labelToGlobalTransform );
		paint( this.labelLocation, x, y );

	}

	private void paint( final RealLocalizable coords, final double viewerX, final double viewerY )
	{
		System.out.println( "PAINTING 2D!" );
		// TODO currently, this paints in 3D. Maybe we should paint 2D only so
		// we see every pixel that we paint into.
		final RandomAccessibleInterval< UnsignedByteType > labels = this.canvas.get();
		LOG.debug( "Painting on canvas {} at {}", labels, coords );
		if ( labels == null )
			return;
		final AffineTransform3D labelToGlobalTransform = this.labelToGlobalTransform.copy();
		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform.copy();
		final RandomAccessible< UnsignedByteType > labelSource = Views.extendZero( labels );

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		this.viewer.getState().getViewerTransform( viewerTransform );
		LOG.warn( "ltv={} ltg={} gtv={}", labelToViewerTransform, labelToGlobalTransform, viewerTransform );

//		final RealRandomAccessible< UnsignedByteType > interpolatedLabels =
//				Views.interpolate( Views.extendZero( labels ), new NearestNeighborInterpolatorFactory<>() );
//		final RandomAccessible< UnsignedByteType > transformedLabels =
//				Views.raster( RealViews.transformReal( interpolatedLabels, labelToViewerTransform ) );

		final double radius = this.brushOverlay.viewerRadiusProperty().get();
//		final long radius = ( long ) Math.ceil( doubleRadius );
//		final HyperSphereShape sphere = new HyperSphereShape( radius );
//		final MixedTransformView< UnsignedByteType > hs1 = Views.hyperSlice( transformedLabels, 2, 0l );
//		final RealPoint screenCoordinates = new RealPoint( coords.numDimensions() );
//		labelToViewerTransform.apply( coords, screenCoordinates );
//		final RandomAccess< Neighborhood< UnsignedByteType > > access = sphere.neighborhoodsRandomAccessible( hs1 ).randomAccess();
//		access.setPosition( Math.round( screenCoordinates.getDoublePosition( 0 ) ), 0 );
//		access.setPosition( Math.round( screenCoordinates.getDoublePosition( 1 ) ), 1 );

//		final ArrayImg< ByteType, ByteArray > brushMask = ArrayImgs.bytes( 2 * radius + 1, 2 * radius + 1 );
//		final RandomAccess< Neighborhood< ByteType > > sphereOnMaskAccess = sphere.neighborhoodsRandomAccessible( brushMask ).randomAccess();
//		sphereOnMaskAccess.setPosition( radius, 0 );
//		sphereOnMaskAccess.setPosition( radius, 1 );
//		sphereOnMaskAccess.get().forEach( m -> m.set( ( byte ) 1 ) );
//		LOG.debug( "Got brush mask for radius={}: {}", radius, brushMask.update( null ).getCurrentStorageArray() );
//		final RandomAccessibleInterval< ByteType > cylinder = Views.stack( brushMask, brushMask );
//		final AffineTransform3D viewerToLabelTransform = labelToViewerTransform.inverse().copy();
//		final RealRandomAccessible< ByteType > interpolatedCylinder = Views.interpolate( Views.extendZero( cylinder ), new NLinearInterpolatorFactory<>() );
//		final AffineRandomAccessible< ByteType, AffineGet > brushMaskInSourceSpace = RealViews.affine(
//				interpolatedCylinder,
//				viewerToLabelTransform
//						.concatenate( new Translation3D( viewerX - radius, viewerY - radius, -0.5 ) )
//						.preConcatenate( new Translation3D(
//								coords.getDoublePosition( 0 ),
//								coords.getDoublePosition( 1 ),
//								coords.getDoublePosition( 2 ) ) ) );

		LOG.warn( "Painting 2D with radius={} (physical radius={})", radius, this.brushOverlay.physicalRadiusProperty().get() );
		final Supplier< BiConsumer< RealLocalizable, UnsignedByteType > > function = () -> {
			final RealPoint p = new RealPoint( 3 );
			final double radiusSquared = radius * radius;
			final double sqrt2 = Math.sqrt( 2 );
			final double oneOverSqrt2 = 1 / sqrt2;
			return ( pos, mask ) -> {
				labelToViewerTransform.apply( pos, p );
				final double x = p.getDoublePosition( 0 );
				final double y = p.getDoublePosition( 1 );
				final double z = p.getDoublePosition( 2 );
//				if ( z >= -0.5 && z <= 0.5 )
				if ( z >= -oneOverSqrt2 && z <= oneOverSqrt2 )
				{
					final double dX = x - viewerX;
					final double dY = y - viewerY;
					if ( dX * dX + dY * dY <= radiusSquared )
					{
						mask.set( 1 );
						return;
					}
				}
				mask.set( 0 );
			};
		};
		final BiConsumerRealRandomAccessible< UnsignedByteType > brushMask = new BiConsumerRealRandomAccessible<>( 3, function, UnsignedByteType::new );

		final long[] pos = new long[] {
				Math.round( coords.getDoublePosition( 0 ) ),
				Math.round( coords.getDoublePosition( 1 ) ),
				Math.round( coords.getDoublePosition( 2 ) ) };
//		LOG.warn( "viewer to label={} seed={} x={} y={}", viewerToLabelTransform, pos, viewerX, viewerY );

		final AccessBoxRandomAccessible< UnsignedByteType > accessTrackingLabelSource = new AccessBoxRandomAccessible<>( labelSource );
		FloodFill.fill(
				Views.raster( brushMask ),
				accessTrackingLabelSource,
				Point.wrap( pos ),
				new UnsignedByteType( 1 ),
				new DiamondShape( 1 ),
				( p1, p2 ) -> p1.getA().get() == 1 && p1.getB().get() == 0 );

		final Interval interval = accessTrackingLabelSource.createAccessInterval();
		final long[] min = Intervals.minAsLongArray( interval );
		final long[] max = Intervals.maxAsLongArray( interval );
		LOG.warn( "Touched min={} and max={} while painting", min, max );
		if ( this.interval.get() != null )
		{
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.min( this.interval.get().min( d ), min[ d ] );
				max[ d ] = Math.max( this.interval.get().max( d ), max[ d ] );
			}
		}
		LOG.warn( "Combined min={} and max={} after painting", min, max );

//		final RealPoint labelCoordinates = new RealPoint( coords.numDimensions() );
//		final long[] min = new long[ labelCoordinates.numDimensions() ];
//		final long[] max = new long[ labelCoordinates.numDimensions() ];
//		for ( final Cursor< UnsignedByteType > cursor = access.get().cursor(); cursor.hasNext(); )
//		{
//			cursor.next().set( 1 );
//			labelCoordinates.setPosition( cursor.getDoublePosition( 0 ), 0 );
//			labelCoordinates.setPosition( cursor.getDoublePosition( 1 ), 1 );
//			labelCoordinates.setPosition( 0d, 2 );
//			labelToViewerTransform.inverse().apply( labelCoordinates, labelCoordinates );
//
//			min[ 0 ] = Math.min( ( long ) Math.floor( labelCoordinates.getDoublePosition( 0 ) ), min[ 0 ] );
//			min[ 1 ] = Math.min( ( long ) Math.floor( labelCoordinates.getDoublePosition( 1 ) ), min[ 1 ] );
//			min[ 2 ] = Math.min( ( long ) Math.floor( labelCoordinates.getDoublePosition( 2 ) ), min[ 2 ] );
//
//			max[ 0 ] = Math.max( ( long ) Math.ceil( labelCoordinates.getDoublePosition( 0 ) ), max[ 0 ] );
//			max[ 1 ] = Math.max( ( long ) Math.ceil( labelCoordinates.getDoublePosition( 1 ) ), max[ 1 ] );
//			max[ 2 ] = Math.max( ( long ) Math.ceil( labelCoordinates.getDoublePosition( 2 ) ), max[ 2 ] );
//		}
//
//		Optional.ofNullable( this.interval.get() ).ifPresent( interval -> {
//			Arrays.setAll( min, d -> Math.min( interval.min( d ), min[ d ] ) );
//			Arrays.setAll( max, d -> Math.max( interval.max( d ), max[ d ] ) );
//		} );

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

			for ( int i = 0; i < l; ++i )
			{
				paint( rp1, startX + ( x - startX ) * 1.0 * i / l, startY + ( y - startY ) * 1.0 * i / l );
				LinAlgHelpers.add( p1, d, p1 );
			}
			paint( labelLocation, x, y );
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
