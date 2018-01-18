package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.mask.MaskInUse;
import bdv.bigcat.viewer.atlas.data.mask.MaskInfo;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.img.AccessBoxRandomAccessible;
import bdv.labels.labelset.Label;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class RestrictPainting
{

	// TODO restrict to fragment only or to segment?
	// currently fragment only

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int CLEANUP_THRESHOLD = ( int ) 1e5;

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	public RestrictPainting( final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.requestRepaint = requestRepaint;
	}

	public void restrictTo( final double x, final double y )
	{
		final Source< ? > currentSource = sourceInfo.currentSourceProperty().get();
		final ViewerState viewerState = viewer.getState();
		if ( currentSource == null )
		{
			LOG.warn( "No current source selected -- will not fill" );
			return;
		}

		final AtlasSourceState< ?, ? > state = sourceInfo.getState( currentSource );
		if ( !state.visibleProperty().get() )
		{
			LOG.warn( "Selected source is not visible -- will not fill" );
			return;
		}

		if ( !( currentSource instanceof MaskedSource< ?, ? > ) )
		{
			LOG.warn( "Selected source is not painting-enabled -- will not fill" );
			return;
		}

		final Function< ?, ? > maskGenerator = state.maskGeneratorProperty().get();
		if ( maskGenerator == null )
		{
			LOG.warn( "Cannot generate boolean mask for this source -- will not fill" );
			return;
		}

		final MaskedSource< ?, ? > source = ( MaskedSource< ?, ? > ) currentSource;

		final Type< ? > t = source.getDataType();

		if ( !( t instanceof RealType< ? > ) )
		{
			LOG.warn( "Data type is not integer type -- will not fill" );
			return;
		}

		final int level = viewerState.getBestMipMapLevel( viewerTransform, sourceInfo.currentSourceIndexInVisibleSources().get() );
		final AffineTransform3D labelTransform = new AffineTransform3D();
		final int time = viewerState.getCurrentTimepoint();
		source.getSourceTransform( time, level, labelTransform );

		final RealPoint rp = setCoordinates( x, y, viewer, labelTransform );
		final Point p = new Point( rp.numDimensions() );
		for ( int d = 0; d < p.numDimensions(); ++d )
			p.setPosition( Math.round( rp.getDoublePosition( d ) ), d );

		try
		{
			restrictTo( ( MaskedSource ) source, time, level, p, requestRepaint );
		}
		catch ( final MaskInUse e )
		{
			LOG.warn( "Mask already in use -- will not paint: {}", e.getMessage() );
		}

	}

	private static RealPoint setCoordinates(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform )
	{
		return setCoordinates( x, y, new RealPoint( labelTransform.numDimensions() ), viewer, labelTransform );
	}

	private static < P extends RealLocalizable & RealPositionable > P setCoordinates(
			final double x,
			final double y,
			final P location,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform )
	{
		location.setPosition( x, 0 );
		location.setPosition( y, 1 );
		location.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( location );
		labelTransform.applyInverse( location, location );

		return location;
	}

	private static < T extends RealType< T > > void restrictTo(
			final MaskedSource< T, ? > source,
			final int time,
			final int level,
			final Localizable seed,
			final Runnable requestRepaint ) throws MaskInUse
	{
		final RandomAccessibleInterval< UnsignedLongType > canvas = source.getReadOnlyDataCanvas( time, level );
		final RandomAccessibleInterval< T > background = source.getReadOnlyDataBackground( time, level );
		final MaskInfo< UnsignedLongType > maskInfo = new MaskInfo<>( time, level, new UnsignedLongType( Label.TRANSPARENT ) );
		final RandomAccessibleInterval< UnsignedByteType > mask = source.generateMask( maskInfo );
		final AccessBoxRandomAccessible< UnsignedByteType > accessTracker = new AccessBoxRandomAccessible<>( Views.extendValue( mask, new UnsignedByteType( 1 ) ) );

		final RandomAccess< UnsignedLongType > canvasAccess = canvas.randomAccess();
		canvasAccess.setPosition( seed );
		final UnsignedLongType paintedLabel = canvasAccess.get();
		final RandomAccess< T > backgroundAccess = background.randomAccess();
		backgroundAccess.setPosition( seed );
		final T backgroundSeed = backgroundAccess.get();

		final RandomAccessible< Pair< T, UnsignedLongType > > paired = Views.pair( Views.extendBorder( background ), Views.extendValue( canvas, new UnsignedLongType( Label.INVALID ) ) );

		restrictTo( paired, accessTracker, seed, new DiamondShape( 1 ), bg -> bg.equals( backgroundSeed ), cv -> cv.equals( paintedLabel ) );

		requestRepaint.run();

		source.applyMask( mask, accessTracker.createAccessInterval() );

	}

	private static < T, U > void restrictTo(
			final RandomAccessible< Pair< T, U > > source,
			final RandomAccessible< UnsignedByteType > mask,
			final Localizable seed,
			final Shape shape,
			final Predicate< T > backgroundFilter,
			final Predicate< U > canvasFilter )
	{
		final int n = source.numDimensions();

		final RandomAccessible< Pair< Pair< T, U >, UnsignedByteType > > paired = Views.pair( source, mask );

		final TLongList[] coordinates = new TLongList[ n ];
		for ( int d = 0; d < n; ++d )
		{
			coordinates[ d ] = new TLongArrayList();
			coordinates[ d ].add( seed.getLongPosition( d ) );
		}

		final RandomAccessible< Neighborhood< Pair< Pair< T, U >, UnsignedByteType > > > neighborhood = shape.neighborhoodsRandomAccessible( paired );
		final RandomAccess< Neighborhood< Pair< Pair< T, U >, UnsignedByteType > > > neighborhoodAccess = neighborhood.randomAccess();

		final RandomAccess< UnsignedByteType > targetAccess = mask.randomAccess();
		targetAccess.setPosition( seed );
		targetAccess.get().set( 1 );

		final UnsignedByteType zero = new UnsignedByteType( 0 );
		final UnsignedByteType one = new UnsignedByteType( 1 );
		final UnsignedByteType two = new UnsignedByteType( 2 );

		for ( int i = 0; i < coordinates[ 0 ].size(); ++i )
		{
			for ( int d = 0; d < n; ++d )
				neighborhoodAccess.setPosition( coordinates[ d ].get( i ), d );

			final Cursor< Pair< Pair< T, U >, UnsignedByteType > > neighborhoodCursor = neighborhoodAccess.get().cursor();

			while ( neighborhoodCursor.hasNext() )
			{
				final Pair< Pair< T, U >, UnsignedByteType > p = neighborhoodCursor.next();
				final UnsignedByteType m = p.getB();
				final Pair< T, U > backgroundAndCanvas = p.getA();
				if ( m.valueEquals( zero ) && canvasFilter.test( backgroundAndCanvas.getB() ) )
				{
					// If background is same as at seed, mark mask with two
					// (==not active), else with one (==active).
					m.set( backgroundFilter.test( backgroundAndCanvas.getA() ) ? two : one );
					for ( int d = 0; d < n; ++d )
						coordinates[ d ].add( neighborhoodCursor.getLongPosition( d ) );
				}

			}

			if ( i > CLEANUP_THRESHOLD )
			{
				for ( int d = 0; d < coordinates.length; ++d )
				{
					final TLongList c = coordinates[ d ];
					coordinates[ d ] = c.subList( i, c.size() );
				}
				i = 0;
			}

		}
	}

}
