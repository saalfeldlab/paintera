package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import bdv.AbstractViewerSetupImgLoader;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
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
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleApplication2 extends Application
{

	public ExampleApplication2()
	{
		super();
	}

	public static HashMap< Long, Atlas > activeViewers = new HashMap<>();

	public static AtomicLong index = new AtomicLong( 0 );

	public static void main( final String[] args ) throws Exception
	{

		final String rawFile = "/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B.augmented.0.hdf";
		final String rawDataset = "volumes/raw";
		final String labelsFile = rawFile;
		final String labelsDataset = "/volumes/labels/neuron_ids";

		final double[] resolution = { 4, 4, 40 };
		final double[] offset = { 424, 424, 560 };
		final int[] cellSize = { 145, 53, 5 };

		final HDF5UnsignedByteSpec rawSource = new HDF5UnsignedByteSpec( rawFile, rawDataset, cellSize, resolution, 0, 255 );

		final Atlas viewer = makeViewer();

		viewer.addSource( rawSource );

//		final HDF5LabelMultisetSourceSpecDeprecated labelSpec = new HDF5LabelMultisetSourceSpecDeprecated( labelsFile, labelsDataset, cellSize );
//		viewer.addSource( labelSpec );

		final HDF5LabelMultisetSourceSpec labelSpec2 = new HDF5LabelMultisetSourceSpec( labelsFile, labelsDataset, cellSize );
		viewer.addSource( labelSpec2 );

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

		final Atlas viewer = new Atlas();
		viewer.start( primaryStage );
		System.out.println( getParameters() );
		activeViewers.put( Long.parseLong( getParameters().getRaw().get( 0 ) ), viewer );
	}

	public static Atlas makeViewer() throws InterruptedException
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
			interpolatorFactories = new InterpolatorFactory[] {
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
