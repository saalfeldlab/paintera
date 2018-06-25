package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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
import bdv.util.Affine3DHelpers;
import bdv.viewer.Source;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseEvent;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
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

	private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

	private final SimpleObjectProperty< MaskedSource< ?, ? > > maskedSource = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< RandomAccessibleInterval< UnsignedByteType > > canvas = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< Interval > interval = new SimpleObjectProperty<>();

	private final Runnable repaintRequest;

	private final ExecutorService paintQueue;

	private int fillLabel;

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
		this.fillLabel = 1;
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

		final AffineTransform3D labelToViewerTransform = this.labelToViewerTransform;
		final AffineTransform3D globalToViewerTransform = this.globalToViewerTransform;
		final AffineTransform3D labelToGlobalTransform = this.labelToGlobalTransform;
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		final AffineTransform3D viewerTransformWithoutTranslation = globalToViewerTransform.copy();
		labelToGlobalTransformWithoutTranslation.setTranslation( 0.0, 0.0, 0.0 );
		viewerTransformWithoutTranslation.setTranslation( 0.0, 0.0, 0.0 );

		// get maximum extent of pixels along z
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
		final double zRange = 0.5 * ( Math.abs( unitX[ 2 ] ) + Math.abs( unitY[ 2 ] ) + Math.abs( unitZ[ 2 ] ) );
		LOG.debug( "range is {}", zRange );

		final double radius = this.brushRadius.get();
		final double viewerRadius = Affine3DHelpers.extractScale( globalToViewerTransform, 0 ) * radius;
		final double[] fillMin = { viewerX - viewerRadius, viewerY - viewerRadius, -zRange };
		final double[] fillMax = { viewerX + viewerRadius, viewerY + viewerRadius, +zRange };
		labelToViewerTransform.applyInverse( fillMin, fillMin );
		labelToViewerTransform.applyInverse( fillMax, fillMax );
		final double[] transformedFillMin = new double[ 3 ];
		final double[] transformedFillMax = new double[ 3 ];
		Arrays.setAll( transformedFillMin, d -> ( long ) Math.floor( Math.min( fillMin[ d ], fillMax[ d ] ) ) );
		Arrays.setAll( transformedFillMax, d -> ( long ) Math.ceil( Math.max( fillMin[ d ], fillMax[ d ] ) ) );

		final Interval trackedInterval = Intervals.smallestContainingInterval( new FinalRealInterval( transformedFillMin, transformedFillMax ) );
		final RandomAccess< UnsignedByteType > access = Views.extendValue( labels, new UnsignedByteType( fillLabel ) ).randomAccess( trackedInterval );
		final RealPoint seed = new RealPoint( viewerX, viewerY, 0.0 );

		specialPurposeFloodfill(
				access,
				seed,
				labelToViewerTransform,
				new double[] { viewerX, viewerY },
				viewerRadius * viewerRadius,
				-zRange,
				+zRange,
				fillLabel );

		this.interval.set( Intervals.union( trackedInterval, Optional.ofNullable( this.interval.get() ).orElse( trackedInterval ) ) );
		this.fillLabel = fillLabel == 1 ? 2 : 1;

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

	private static void specialPurposeFloodfill(
			final RandomAccess< ? extends IntegerType< ? > > localAccess,
			final RealLocalizable seedWorld,
			final AffineTransform3D localToWorld,
			final double[] centerInWorldCoordinates,
			final double radiusWorldSquaredIn,
			final double zMinExclusive,
			final double zMaxExclusive,
			final long fillLabel )
	{
		final double[] dx = { 1.0, 0.0, 0.0 };
		final double[] dy = { 0.0, 1.0, 0.0 };
		final double[] dz = { 0.0, 0.0, 1.0 };
		final double[] pos = IntStream.range( 0, 3 ).mapToDouble( seedWorld::getDoublePosition ).toArray();
		final AffineTransform3D transformNoTranslation = localToWorld.copy();
		transformNoTranslation.setTranslation( 0.0, 0.0, 0.0 );
		transformNoTranslation.apply( dx, dx );
		transformNoTranslation.apply( dy, dy );
		transformNoTranslation.apply( dz, dz );

		final RealPoint seedLocal = new RealPoint( seedWorld );
		localToWorld.applyInverse( seedLocal, seedLocal );

		final double dxx = dx[ 0 ];
		final double dxy = dx[ 1 ];
		final double dxz = dx[ 2 ];

		final double dyx = dy[ 0 ];
		final double dyy = dy[ 1 ];
		final double dyz = dy[ 2 ];

		final double dzx = dz[ 0 ];
		final double dzy = dz[ 1 ];
		final double dzz = dz[ 2 ];

		final TLongArrayList sourceCoordinates = new TLongArrayList();
		final TDoubleArrayList worldCoordinates = new TDoubleArrayList();

		final double cx = centerInWorldCoordinates[ 0 ];
		final double cy = centerInWorldCoordinates[ 1 ];

		for ( int d = 0; d < 3; ++d )
		{
			sourceCoordinates.add( Math.round( seedLocal.getDoublePosition( d ) ) );
			worldCoordinates.add( pos[ d ] );
		}

		for ( int offset = 0; offset < sourceCoordinates.size(); offset += 3 )
		{
			final int o0 = offset + 0;
			final int o1 = offset + 1;
			final int o2 = offset + 2;
			final long lx = sourceCoordinates.get( o0 );
			final long ly = sourceCoordinates.get( o1 );
			final long lz = sourceCoordinates.get( o2 );
			localAccess.setPosition( lx, 0 );
			localAccess.setPosition( ly, 1 );
			localAccess.setPosition( lz, 2 );

			final IntegerType< ? > val = localAccess.get();

			if ( val.getIntegerLong() == fillLabel )
			{
				continue;
			}
			val.setInteger( fillLabel );

			final double x = worldCoordinates.get( o0 );
			final double y = worldCoordinates.get( o1 );
			final double z = worldCoordinates.get( o2 );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx + 1, ly, lz,
					x + dxx, y + dxy, z + dxz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx - 1, ly, lz,
					x - dxx, y - dxy, z - dxz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx, ly + 1, lz,
					x + dyx, y + dyy, z + dyz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx, ly - 1, lz,
					x - dyx, y - dyy, z - dyz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx, ly, lz + 1,
					x + dzx, y + dzy, z + dzz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

			addIfInside(
					sourceCoordinates,
					worldCoordinates,
					lx, ly, lz - 1,
					x - dzx, y - dzy, z - dzz,
					radiusWorldSquaredIn,
					cx, cy,
					zMinExclusive, zMaxExclusive );

		}

	}

	private static final void addIfInside(
			final TLongArrayList labelCoordinates,
			final TDoubleArrayList worldCoordinates,
			final long lx,
			final long ly,
			final long lz,
			final double wx,
			final double wy,
			final double wz,
			final double rSquared,
			final double cx,
			final double cy,
			final double zMinExclusive,
			final double zMaxExclusive )
	{
		if ( wz > zMinExclusive && wz < zMaxExclusive )
		{
			final double dx = wx - cx;
			final double dy = wy - cy;
			if ( dx * dx + dy * dy < rSquared )
			{
				labelCoordinates.add( lx );
				labelCoordinates.add( ly );
				labelCoordinates.add( lz );

				worldCoordinates.add( wx );
				worldCoordinates.add( wy );
				worldCoordinates.add( wz );
			}
		}
	}

}
