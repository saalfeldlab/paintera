package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.janelia.saalfeldlab.fx.event.MouseDragFX;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
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

	private final SimpleIntegerProperty maskFill = new SimpleIntegerProperty( 1 );

	private final Runnable repaintRequest;

	private final ExecutorService paintQueue;

	public Paint2D(
			final ViewerPanelFX viewer,
			final SourceInfo sourceInfo,
			final GlobalTransformManager manager,
			final Runnable repaintRequest,
			final ExecutorService paintQueue )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.brushOverlay = new BrushOverlay( this.viewer, manager );
		this.brushOverlay.physicalRadiusProperty().bind( brushRadius );
		this.repaintRequest = repaintRequest;
		this.paintQueue = paintQueue;
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
		{
			decreaseBrushRadius();
		}
		else if ( sign < 0 )
		{
			increaseBrushRadius();
		}
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
		{
			this.brushRadius.set( radius );
		}
	}

	public MouseDragFX dragPaintLabel( final String name, final Supplier< Long > id, final Predicate< MouseEvent > eventFilter )
	{
		return new PaintDrag( name, eventFilter, true, this, id );
	}

	public void prepareAndPaint( final MouseEvent event, final Long id ) throws MaskInUse
	{
		prepareForPainting( id );
		paintQueue.submit( () -> paint( event ) );
		paintQueue.submit( () -> applyMask() );
	}

	public void prepareAndPaintUnchecked( final MouseEvent event, final Long id )
	{
		if ( id == null )
		{
			LOG.debug( "Id is nullpointer: {}", id );
			return;
		}
		try
		{
			prepareAndPaint( event, id );
		}
		catch ( final MaskInUse e )
		{
			throw new RuntimeException( e );
		}
	}

	public void paint( final MouseEvent event )
	{
		paint( event, true );
	}

	private void paint( final MouseEvent event, final boolean consume )
	{
		paint( event.getX(), event.getY() );
		if ( consume )
		{
			event.consume();
		}
		repaintRequest.run();
//		applyMask();

	}

	private void prepareForPainting( final Long id ) throws MaskInUse
	{
		if ( id == null )
		{
			LOG.debug( "Do not a valid id to paint: {} -- will not paint.", id );
			return;
		}
		final ViewerState state = viewer.getState();
		final Source< ? > viewerSource = sourceInfo.currentSourceProperty().get();
		final int currentSource = sourceInfo.currentSourceIndexInVisibleSources().get();
		this.canvas.set( null );
		this.maskedSource.set( null );
		this.interval.set( null );

		LOG.debug( "Prepare for painting with source {}", viewerSource );

		if ( viewerSource == null || !( viewerSource instanceof DataSource< ?, ? > ) || !sourceInfo.getState( viewerSource ).isVisibleProperty().get() ) { return; }

		final SourceState< ?, ? > currentSourceState = sourceInfo.getState( viewerSource );
		final DataSource< ?, ? > source = currentSourceState.getDataSource();

		if ( !( source instanceof MaskedSource< ?, ? > ) ) { return; }

		final MaskedSource< ?, ? > maskedSource = ( MaskedSource< ?, ? > ) source;

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		state.getViewerTransform( viewerTransform );
		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		final int level = state.getBestMipMapLevel( screenScaleTransform, currentSource );
		maskedSource.getSourceTransform( 0, level, labelToGlobalTransform );
		this.labelToViewerTransform.set( viewerTransform.copy().concatenate( labelToGlobalTransform ) );

		final UnsignedLongType value = new UnsignedLongType( id );

		final MaskInfo< UnsignedLongType > mask = new MaskInfo<>( 0, level, value );
		final RandomAccessibleInterval< UnsignedByteType > canvas = maskedSource.generateMask( mask );
		// canvasSource.getDataSource( state.getCurrentTimepoint(), level );
		LOG.debug( "Setting canvas to {}", canvas );
		this.canvas.set( canvas );
		this.maskedSource.set( maskedSource );
		this.maskFill.set( 1 );
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
		if ( labels == null ) { return; }

		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform.copy();
		final AffineTransform3D labelToGlobalTransform = this.labelToGlobalTransform.copy();

		final AffineTransform3D viewerTransformWithoutTranslation = new AffineTransform3D();
		viewer.getState().getViewerTransform( viewerTransformWithoutTranslation );
		viewerTransformWithoutTranslation.setTranslation( 0, 0, 0 );
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		labelToGlobalTransformWithoutTranslation.setTranslation( 0, 0, 0 );

		final double[] unitX = { 1.0, 0.0, 0.0 };
		final double[] unitY = { 0.0, 1.0, 0.0 };
		final double[] unitZ = { 0.0, 0.0, 1.0 };
		labelToGlobalTransformWithoutTranslation.apply( unitX, unitX );
		labelToGlobalTransformWithoutTranslation.apply( unitY, unitY );
		labelToGlobalTransformWithoutTranslation.apply( unitZ, unitZ );
		viewerTransformWithoutTranslation.apply( unitX, unitX );
		viewerTransformWithoutTranslation.apply( unitY, unitY );
		viewerTransformWithoutTranslation.apply( unitZ, unitZ );
		LOG.debug( "Transformed unit vectors x={} y={} z={}", unitX, unitY, unitZ );
		final double range = 0.5 * ( Math.abs( unitX[ 2 ] ) + Math.abs( unitY[ 2 ] ) + Math.abs( unitZ[ 2 ] ) );
		LOG.debug( "range is {}", range );

		final double radius = this.brushOverlay.viewerRadiusProperty().get();

		final RandomAccessible< UnsignedByteType > labelSource = Views.extendValue( labels, new UnsignedByteType( 1 ) );
		final AccessBoxRandomAccessibleOnGet< UnsignedByteType > trackingLabelSource = new AccessBoxRandomAccessibleOnGet<>( labelSource );

		LOG.debug( "r={} center=({}, )", radius, viewerX, viewerY );

		final Supplier< BiConsumer< RealLocalizable, BitType > > function = () -> {
			final double radiusSquared = radius * radius;
			final double upperBound = +range;
			final double lowerBound = -range;
			return ( loc, t ) -> {
				final double z = loc.getDoublePosition( 2 );
				boolean isValid = false;
				if ( z < upperBound && z > lowerBound )
				{
					final double x = loc.getDoublePosition( 0 );
					final double y = loc.getDoublePosition( 1 );
					final double dx = x - viewerX;
					final double dy = y - viewerY;
					if ( dx * dx + dy * dy < radiusSquared )
					{
						isValid = true;
					}
				}
				t.set( isValid );
			};
		};
		final AffineRandomAccessible< BitType, AffineGet > containsCheck =
				RealViews.affine( new FunctionRealRandomAccessible<>( 3, function, () -> new BitType( true ) ), labelToViewerTransform.inverse() );

		final RealPoint seedReal = new RealPoint( viewerX, viewerY, 0 );
		labelToViewerTransform.applyInverse( seedReal, seedReal );
		final Point seed = new Point( IntStream.range( 0, 3 ).mapToDouble( seedReal::getDoublePosition ).mapToLong( Math::round ).toArray() );
		final int maskFill = this.maskFill.get();
		LOG.trace( "Filling with threshold {}", maskFill );

		FloodFill.fill(
				containsCheck,
				trackingLabelSource,
				seed,
				new UnsignedByteType( maskFill ),
				new DiamondShape( 1 ),
				( BiPredicate< BitType, UnsignedByteType > ) ( mask, canvas ) -> mask.get() && canvas.get() != maskFill );

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
		this.maskFill.set( maskFill % 2 + 1 );

	}

	private class PaintDrag extends MouseDragFX
	{

		private final Supplier< Long > id;

		public PaintDrag(
				final String name,
				final Predicate< MouseEvent > eventFilter,
				final boolean consume,
				final Object transformLock,
				final Supplier< Long > id )
		{
			super( name, eventFilter, consume, transformLock, false );
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
				repaintRequest.run();
			} );
			startX = x;
			startY = y;
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
