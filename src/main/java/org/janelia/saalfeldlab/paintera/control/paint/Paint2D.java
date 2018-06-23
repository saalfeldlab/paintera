package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import bdv.util.Affine3DHelpers;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseEvent;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
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

	private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

	private final SimpleObjectProperty< MaskedSource< ?, ? > > maskedSource = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< RandomAccessibleInterval< UnsignedByteType > > canvas = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Interval > interval = new SimpleObjectProperty<>();

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
		this.globalToViewerTransform.set( viewerTransform );

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
		paint( viewerX, viewerY, 0, 0 );
	}

	private void paint( final double viewerX, final double viewerY, final double shiftX, final double shiftY )
	{

//		LOG.warn( "At {} {}", viewerX, viewerY );

		final RandomAccessibleInterval< UnsignedByteType > labels = this.canvas.get();
		if ( labels == null ) { return; }

		final long tBeforePaint0 = System.currentTimeMillis();
		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform;
		final AffineTransform3D globalToViewerTransform = this.globalToViewerTransform;

		final double range = 0.5;

		// radius is in global coordinates, scale from viewer coordinates to
		// global coordinates
		final double scale = Affine3DHelpers.extractScale( globalToViewerTransform, 0 );
		final long xScale = Math.round( viewerX / scale );
		final long yScale = Math.round( viewerY / scale );

		final Point p = new Point( xScale, yScale );

		final AffineTransform3D tf = labelToViewerTransform.copy();
		tf.preConcatenate( new Scale3D( 1.0 / scale, 1.0 / scale, 1.0 / scale ) );

		final AffineTransform3D tfFront = tf.copy().preConcatenate( new Translation3D( 0, 0, -range ) );
		final AffineTransform3D tfBack = tf.copy().preConcatenate( new Translation3D( 0, 0, range ) );

		final RandomAccessible< UnsignedByteType > labelsExtended = Views.extendValue( labels, new UnsignedByteType( 1 ) );
		final RealRandomAccessible< UnsignedByteType > interpolated = Views.interpolate( labelsExtended, new NearestNeighborInterpolatorFactory<>() );
		final MixedTransformView< UnsignedByteType > front = Views.hyperSlice( RealViews.affine( interpolated, tfFront ), 2, 0l );
		final MixedTransformView< UnsignedByteType > back = Views.hyperSlice( RealViews.affine( interpolated, tfBack ), 2, 0l );

		final double dr = this.brushRadius.get();
		final long r = Math.round( dr );
		final long tBeforePaint1 = System.currentTimeMillis();
		final long t0 = System.currentTimeMillis();
		new HyperSphere<>( front, p, r ).forEach( UnsignedByteType::setOne );
		final long t1 = System.currentTimeMillis();
		new HyperSphere<>( back, p, r ).forEach( UnsignedByteType::setOne );
		final long t2 = System.currentTimeMillis();

		final long tAfterPaint0 = System.currentTimeMillis();

		// add painted interval
		final double[] topLeft = { Math.floor( xScale - dr ), Math.floor( yScale - dr ), 0 };
		final double[] bottomRight = { Math.ceil( xScale + dr ), Math.ceil( yScale + dr ), 0 };

		tf.applyInverse( topLeft, topLeft );
		tf.applyInverse( bottomRight, bottomRight );

		final long[] tl = new long[ 3 ];
		final long[] br = new long[ 3 ];
		Arrays.setAll( tl, d -> ( long ) Math.floor( Math.min( topLeft[ d ], bottomRight[ d ] ) ) );
		Arrays.setAll( br, d -> ( long ) Math.ceil( Math.max( topLeft[ d ], bottomRight[ d ] ) ) );

		final FinalInterval trackedInterval = new FinalInterval( tl, br );
		this.interval.set( Intervals.union( trackedInterval, Optional.ofNullable( this.interval.get() ).orElse( trackedInterval ) ) );

		final long tAfterPaint1 = System.currentTimeMillis();

		LOG.trace(
				"before paint {}ms paint1 {}ms paint2 {}ms after paint{}",
				tBeforePaint1 - tBeforePaint0,
				t1 - t0,
				t2 - t1,
				tAfterPaint1 - tAfterPaint0 );
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

			if ( x != startX || y != startY )
			{
				final double[] p1 = new double[] { startX, startY };

//			LOG.warn( "Drag: paint at screen=({},{}) / start=({},{})", x, y, startX, startY );

				final double[] d = new double[] { x, y };

				LinAlgHelpers.subtract( d, p1, d );

				final double l = LinAlgHelpers.length( d );
				LinAlgHelpers.normalize( d );

				final double radius = brushOverlay.viewerRadiusProperty().get();
				final double shiftX = d[ 0 ] * radius;
				final double shiftY = d[ 1 ] * radius;

				LOG.debug( "Number of paintings triggered {}", l + 1 );
				paintQueue.submit( () -> {

					final long t0 = System.currentTimeMillis();
					for ( int i = 0; i < l; ++i )
					{
						paint( p1[ 0 ], p1[ 1 ], shiftX, shiftY );
						LinAlgHelpers.add( p1, d, p1 );
					}
//					paint( x, y, shiftX, shiftY );
					final long t1 = System.currentTimeMillis();
					LOG.debug( "Painting {} times with radius {} took a total of {}ms", l + 1, brushRadius.get(), t1 - t0 );
					repaintRequest.run();
				} );
			}
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
