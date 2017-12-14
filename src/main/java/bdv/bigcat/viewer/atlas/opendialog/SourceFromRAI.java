package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSourceFromDelegates;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSourceWithTime;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
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
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public interface SourceFromRAI extends BackendDialog
{

	public < T extends NativeType< T >, V extends Volatile< T > > Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< V > > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException;

	public boolean isLabelType() throws Exception;

	public boolean isLabelMultisetType() throws Exception;

	public boolean isIntegerType() throws Exception;

	public Iterator< ? extends FragmentSegmentAssignmentState< ? > > assignments();

	@Override
	public default < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > Collection< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< V > > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		return getCached( dataAndVolatile.getA(), dataAndVolatile.getB(), name, resolution, offset, axisOrder, sharedQueue, priority );
	}

	@Override
	public default Collection< ? extends LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws Exception
	{
		if ( isLabelType() )
			if ( isIntegerType() )
				return getIntegerTypeSource( name, resolution, offset, axisOrder, sharedQueue, priority, assignments() );
			else if ( isLabelMultisetType() )
				return new ArrayList<>();
		return new ArrayList<>();
	}

	public static < T extends NumericType< T >, V extends NumericType< V > > Collection< DataSource< T, V > > getCached(
			final RandomAccessibleInterval< T > rai,
			final RandomAccessibleInterval< V > vrai,
			final String nameOrPattern,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return getCached(
				rai,
				vrai,
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				interpolation -> interpolation.equals( Interpolation.NLINEAR ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				nameOrPattern,
				resolution,
				offset,
				axisOrder,
				sharedQueue,
				priority );
	}

	public static < T extends Type< T >, V extends Type< V > > Collection< DataSource< T, V > > getCached(
			final RandomAccessibleInterval< T > rai,
			final RandomAccessibleInterval< V > vrai,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String nameOrPattern,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final long[] dimensions = Intervals.dimensionsAsLongArray( rai );
		final int[] componentMapping = axisOrder.spatialOnly().inversePermutation();
		final AffineTransform3D sourceTransform = permutedSourceTransform( resolution, offset, componentMapping );
		final AffineTransform3D[] transforms = new AffineTransform3D[] { sourceTransform };

		if ( axisOrder.hasChannels() )
		{
			final int channelAxis = axisOrder.channelAxis();
			final long numChannels = dimensions[ channelAxis ];

			final ArrayList< DataSource< T, V > > sources = new ArrayList<>();
			for ( long channel = 0; channel < numChannels; ++channel )
				sources.add( getAsSource( Views.hyperSlice( rai, channelAxis, channel ), Views.hyperSlice( vrai, channelAxis, channel ), axisOrder, transforms, interpolation, vinterpolation, String.format( nameOrPattern, channel ) ) );
			return sources;
		}
		else
			return Arrays.asList( getAsSource( rai, vrai, axisOrder, transforms, interpolation, vinterpolation, nameOrPattern ) );
	}

	public default < T extends IntegerType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > > Collection< ? extends LabelDataSource< T, V > > getIntegerTypeSource(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority,
			final Iterator< ? extends FragmentSegmentAssignmentState< ? > > assignment ) throws IOException
	{
		final Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< V > > dataAndVolatile = getDataAndVolatile( sharedQueue, priority );
		final Collection< DataSource< T, V > > sources = SourceFromRAI.getCached(
				dataAndVolatile.getA(),
				dataAndVolatile.getB(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				axisOrder.hasChannels() ? name + " (%d)" : name, resolution, offset, axisOrder, sharedQueue, priority );
		final ArrayList< LabelDataSource< T, V > > delegated = new ArrayList<>();
		for ( final DataSource< T, V > source : sources )
			delegated.add( new LabelDataSourceFromDelegates<>( source, assignment.next() ) );
		return delegated;
	}

	public static < T extends Type< T >, V extends Type< V > > DataSource< T, V > getAsSource(
			final RandomAccessibleInterval< T > rai,
			final RandomAccessibleInterval< V > vrai,
			final AxisOrder axisOrder,
			final AffineTransform3D[] transforms,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > interpolation,
			final Function< Interpolation, InterpolatorFactory< V, RandomAccessible< V > > > vinterpolation,
			final String name )
	{
		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< T >[] rais = new RandomAccessibleInterval[] { rai };
		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< V >[] vrais = new RandomAccessibleInterval[] { vrai };
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
		for ( int i = 0, contentOffset = 0; i < offset.length; ++i, contentOffset += 4 )
		{
			matrixContent[ contentOffset + componentMapping[ i ] ] = resolution[ i ];
			matrixContent[ contentOffset + 3 ] = offset[ i ];
		}
		rawTransform.set( matrixContent );
		return rawTransform;
	}

}
