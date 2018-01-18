package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.mask.MaskInUse;
import bdv.bigcat.viewer.atlas.data.mask.MaskInfo;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.img.AccessBoxRandomAccessible;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class FloodFill
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final Runnable requestRepaint;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	public FloodFill( final ViewerPanelFX viewer, final SourceInfo sourceInfo, final Runnable requestRepaint )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.requestRepaint = requestRepaint;
		viewer.addTransformListener( t -> viewerTransform.set( t ) );
	}

	public void fillAt( final double x, final double y, final Supplier< Long > fillSupplier )
	{
		if ( sourceInfo.currentSourceProperty().get() == null )
		{
			LOG.warn( "No current source selected -- will not fill" );
			return;
		}
		final Long fill = fillSupplier.get();
		if ( fill == null )
		{
			LOG.warn( "Received invalid label {} -- will not fill.", fill );
			return;
		}
		fillAt( x, y, fill );
	}

	public void fillAt( final double x, final double y, final long fill )
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

		LOG.debug( "Filling source {} with label {} at {}", source, fill, p );
		final Scene scene = viewer.getScene();
		final Cursor previousCursor = scene.getCursor();
		try
		{
			fill(
					( MaskedSource ) source,
					time,
					level,
					fill,
					p,
					new RunAll( requestRepaint, () -> scene.setCursor( Cursor.WAIT ) ),
					new RunAll( requestRepaint, () -> scene.setCursor( previousCursor ) ) );
		}
		catch ( final MaskInUse e )
		{
			LOG.warn( e.getMessage() );
			return;
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

	private static < T extends RealType< T > > void fill(
			final MaskedSource< T, ? > source,
			final int time,
			final int level,
			final long fill,
			final Localizable seed,
			final Runnable doWhileFilling,
			final Runnable doWhenDone ) throws MaskInUse
	{
		final MaskInfo< UnsignedLongType > maskInfo = new MaskInfo<>( time, level, new UnsignedLongType( fill ) );
		final RandomAccessibleInterval< UnsignedByteType > mask = source.generateMask( maskInfo );
		final AccessBoxRandomAccessible< UnsignedByteType > accessTracker = new AccessBoxRandomAccessible<>( Views.extendValue( mask, new UnsignedByteType( 1 ) ) );
		final Thread t = new Thread( () -> {
			net.imglib2.algorithm.fill.FloodFill.fill(
					source.getDataSource( time, level ),
					accessTracker,
					seed,
					new UnsignedByteType( 1 ),
					new DiamondShape( 1 ),
					makeFilter() );
			final Interval interval = accessTracker.createAccessInterval();
			LOG.debug( "Applying mask for interval {} {}", Arrays.toString( Intervals.minAsLongArray( interval ) ), Arrays.toString( Intervals.maxAsLongArray( interval ) ) );
		} );
		t.start();
		new Thread( () -> {
			while ( t.isAlive() && !Thread.interrupted() )
			{
				try
				{
					Thread.sleep( 100 );
				}
				catch ( final InterruptedException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOG.debug( "Updating current view!" );
				doWhileFilling.run();
			}
			doWhenDone.run();
			if ( !Thread.interrupted() )
				source.applyMask( mask, accessTracker.createAccessInterval() );
		} ).start();
	}

	private static < T extends Type< T > > Filter< Pair< T, UnsignedByteType >, Pair< T, UnsignedByteType > > makeFilter()
	{
		final UnsignedByteType zero = new UnsignedByteType( 0 );
		// first element in pair is current pixel, second element is reference
		return ( p1, p2 ) -> p1.getB().valueEquals( zero ) && p1.getA().valueEquals( p2.getA() );
	}

	public static class RunAll implements Runnable
	{

		private final List< Runnable > runnables;

		public RunAll( final Runnable... runnables )
		{
			this( Arrays.asList( runnables ) );
		}

		public RunAll( final Collection< Runnable > runnables )
		{
			super();
			this.runnables = new ArrayList<>( runnables );
		}

		@Override
		public void run()
		{
			this.runnables.forEach( Runnable::run );
		}

	}

}
