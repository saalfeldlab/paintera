package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSourceFromDelegates;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSourceWithTime;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.net.imglib2.util.Triple;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public interface SourceFromRAI extends BackendDialog
{

	public static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public < T extends NativeType< T >, V extends Volatile< T > > Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException;

	public boolean isLabelType() throws Exception;

	public boolean isLabelMultisetType() throws Exception;

	public boolean isIntegerType() throws Exception;

	public Iterator< ? extends FragmentSegmentAssignmentState< ? > > assignments();

	@Override
	public default < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > Collection< DataSource< T, V > > getRaw(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		return getCached( dataAndVolatile.getA(), dataAndVolatile.getB(), dataAndVolatile.getC(), name, sharedQueue, priority );
	}

	@Override
	public default Collection< ? extends LabelDataSource< ?, ? > > getLabels(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws Exception
	{
		if ( isLabelType() )
			if ( isIntegerType() )
				return getIntegerTypeSource( name, sharedQueue, priority, assignments() );
			else if ( isLabelMultisetType() )
				return new ArrayList<>();
		return new ArrayList<>();
	}

	public default < T extends NumericType< T >, V extends NumericType< V > > Collection< DataSource< T, V > > getCached(
			final RandomAccessibleInterval< T >[] rai,
			final RandomAccessibleInterval< V >[] vrai,
			final AffineTransform3D[] transforms,
			final String nameOrPattern,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return getCached(
				rai,
				vrai,
				transforms,
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				nameOrPattern,
				sharedQueue,
				priority );
	}

	public default < T extends Type< T >, V extends Type< V > > Collection< DataSource< T, V > > getCached(
			final RandomAccessibleInterval< T >[] rai,
			final RandomAccessibleInterval< V >[] vrai,
			final AffineTransform3D[] transforms,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String nameOrPattern,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final long[] dimensionsAtHighestRes = Intervals.dimensionsAsLongArray( rai[ 0 ] );
		LOG.info( "Using source transforms {} for {} sources", Arrays.toString( transforms ), rai.length );

		final AxisOrder axisOrder = this.axisOrder().get();

		if ( axisOrder.hasChannels() )
		{
			final int channelAxis = axisOrder.channelAxis();
			final long numChannels = dimensionsAtHighestRes[ channelAxis ];

			final ArrayList< DataSource< T, V > > sources = new ArrayList<>();
			for ( long channel = 0; channel < numChannels; ++channel )
			{
				final long fChannel = channel;
				@SuppressWarnings( "unchecked" )
				final RandomAccessibleInterval< T >[] hs = Arrays.stream( rai ).map( r -> Views.hyperSlice( r, channelAxis, fChannel ) ).toArray( RandomAccessibleInterval[]::new );
				@SuppressWarnings( "unchecked" )
				final RandomAccessibleInterval< V >[] vhs = Arrays.stream( vrai ).map( r -> Views.hyperSlice( r, channelAxis, fChannel ) ).toArray( RandomAccessibleInterval[]::new );
				sources.add( getAsSource( hs, vhs, axisOrder, transforms, interpolation, vinterpolation, String.format( nameOrPattern, channel ) ) );
			}
			return sources;
		}
		else
			return Arrays.asList( getAsSource( rai, vrai, axisOrder, transforms, interpolation, vinterpolation, nameOrPattern ) );
	}

	public default < T extends IntegerType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > > Collection< ? extends LabelDataSource< T, V > > getIntegerTypeSource(
			final String name,
			final SharedQueue sharedQueue,
			final int priority,
			final Iterator< ? extends FragmentSegmentAssignmentState< ? > > assignment ) throws IOException
	{

		final AxisOrder axisOrder = this.axisOrder().get();

		final Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		final Collection< DataSource< T, V > > sources = getCached(
				dataAndVolatile.getA(),
				dataAndVolatile.getB(),
				dataAndVolatile.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				axisOrder.hasChannels() ? name + " (%d)" : name,
				sharedQueue,
				priority );
		final ArrayList< LabelDataSource< T, V > > delegated = new ArrayList<>();
		for ( final DataSource< T, V > source : sources )
			delegated.add( new LabelDataSourceFromDelegates<>( source, assignment.next() ) );
		return delegated;
	}

	public static < T extends Type< T >, V extends Type< V > > DataSource< T, V > getAsSource(
			final RandomAccessibleInterval< T >[] rais,
			final RandomAccessibleInterval< V >[] vrais,
			final AxisOrder axisOrder,
			final AffineTransform3D[] transforms,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String name )
	{

		assert rais.length == vrais.length;
		assert rais.length == transforms.length;

		if ( axisOrder.hasTime() )
		{
			final int timeAxis = axisOrder.withoutChannel().timeAxis();
			return RandomAccessibleIntervalDataSourceWithTime.< T, V >fromRandomAccessibleInterval( rais, vrais, transforms, timeAxis, interpolation, vinterpolation, name );
		}
		else
			return new RandomAccessibleIntervalDataSource<>( rais, vrais, transforms, interpolation, vinterpolation, name );
	}

	public static AffineTransform3D permutedSourceTransform( final double[] resolution, final double[] offset, final int[] componentMapping )
	{
		final AffineTransform3D rawTransform = new AffineTransform3D();
		final double[] matrixContent = new double[ 12 ];
		LOG.warn( "component mapping={}", Arrays.toString( componentMapping ) );
		for ( int i = 0, contentOffset = 0; i < offset.length; ++i, contentOffset += 4 )
		{
			matrixContent[ 4 * componentMapping[ i ] + i ] = resolution[ i ];
			matrixContent[ contentOffset + 3 ] = offset[ i ];
		}
		rawTransform.set( matrixContent );
		LOG.warn( "permuted transform={}", rawTransform );
		return rawTransform;
	}

	public default double[] resolution()
	{
//		final double[] resolution = new double[ 3 ];
//		final int[] permutation = axisOrder().get().spatialOnly().permutation();
//		resolution[ permutation[ 0 ] ] = resolutionX().get();
//		resolution[ permutation[ 1 ] ] = resolutionY().get();
//		resolution[ permutation[ 2 ] ] = resolutionZ().get();
//		return resolution;
		return new double[] { resolutionX().get(), resolutionY().get(), resolutionZ().get() };
	}

	public default double[] offset()
	{
//		final double[] offset = new double[ 3 ];
//		final int[] permutation = axisOrder().get().spatialOnly().permutation();
//		offset[ permutation[ 0 ] ] = offsetX().get();
//		offset[ permutation[ 1 ] ] = offsetY().get();
//		offset[ permutation[ 2 ] ] = offsetZ().get();
//		return offset;
		return new double[] { offsetX().get(), offsetY().get(), offsetZ().get() };
	}

}
