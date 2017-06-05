package bdv.bigcat.viewer;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import javafx.application.Application;
import javafx.stage.Stage;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.display.ScaledARGBConverter.ARGB;
import net.imglib2.display.ScaledARGBConverter.VolatileARGB;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleApplication2 extends Application
{

	public ExampleApplication2()
	{
		super();
	}

	public static HashMap< Long, OrthoView > activeViewers = new HashMap<>();

	public static AtomicLong index = new AtomicLong( 0 );

	public static void main( final String[] args ) throws InterruptedException, IOException
	{


		final String rawFile = "/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B.augmented.0.hdf";
		final String rawDataset = "volumes/raw";

		final double[] resolution = { 4, 4, 40 };
		final double[] offset = { 424, 424, 560 };
		final int[] cellSize = { 145, 53, 5 };

		final VolatileGlobalCellCache cache = new VolatileGlobalCellCache( 1, 20 );

		final IHDF5Writer reader = HDF5Factory.open( rawFile );
		final H5UnsignedByteSetupImageLoader rawLoader = new H5UnsignedByteSetupImageLoader( reader, rawDataset, 0, cellSize, resolution, cache );

		final RandomAccessibleInterval< VolatileUnsignedByteType > img = rawLoader.getVolatileImage( 0, 0 );
		Converters.convert( img, new VolatileRealARGBConverter<>( 0, 255 ), new VolatileARGBType() );

		final ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
		final VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

		final ARGBConvertedSource< UnsignedByteType > rawSource = new ARGBConvertedSource<>( 1, rawLoader, new VolatileRealARGBConverter<>( 0, 255 ) );
		final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter<>( rawSource, vconverter );
		final SourceAndConverter< ARGBType > soc = new SourceAndConverter<>( rawSource.nonVolatile(), converter, vsoc );

		final OrthoView viewer = makeViewer();

//		viewer.addSource( soc );
//
//		viewer.speedFactor( 40 );

//		viewer.addSource( source );

	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		final OrthoView viewer = new OrthoView();
		viewer.start( primaryStage );
		System.out.println( getParameters() );
		activeViewers.put( Long.parseLong( getParameters().getRaw().get( 0 ) ), viewer );
	}

	public static OrthoView makeViewer() throws InterruptedException
	{

		synchronized ( index )
		{
			final long idx = index.get();
			final Thread t = new Thread( () -> Application.launch( ExampleApplication2.class, Long.toString( idx ) ) );
			t.start();
			while ( !activeViewers.containsKey( idx ) )
				Thread.sleep( 10 );
			return activeViewers.get( idx );
		}
	}

	public static class ARGBConvertedSource< T > implements Source< VolatileARGBType >
	{
		final private AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader;

		private final int setupId;

		private final Converter< Volatile< T >, VolatileARGBType > converter;

		final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[]{
					new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
					new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
			};
		}

		public ARGBConvertedSource(
				final int setupId,
				final AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader,
						final Converter< Volatile< T >, VolatileARGBType > converter )
		{
			this.setupId = setupId;
			this.loader = loader;
			this.converter = converter;
		}

		final public AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > getLoader()
		{
			return loader;
		}

		@Override
		public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
		{
			return Converters.convert(
					loader.getVolatileImage( t, level ),
					converter,
					new VolatileARGBType() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		/**
		 * TODO Store this in a field
		 */
		@Override
		public int getNumMipmapLevels()
		{
			return loader.getMipmapResolutions().length;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{

			final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
					Views.extendValue( getSource( t, level ), new VolatileARGBType( 0 ) );
			switch ( method )
			{
			case NLINEAR:
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default:
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}

		@Override
		public VolatileARGBType getType()
		{
			return new VolatileARGBType();
		}

		@Override
		public String getName()
		{
			return "1 2 3";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		// TODO: make ARGBType version of this source
		public Source nonVolatile()
		{
			return this;
		}
	}


}