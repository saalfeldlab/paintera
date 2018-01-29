package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.util.volatiles.SharedQueue;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public interface LabelDataSource< D, T > extends DataSource< D, T >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

	/**
	 * Create a primitive single scale level label source with an assignment
	 * from a {@link RandomAccessibleInterval}.
	 *
	 * @param name
	 * @param data
	 * @param sourceTransform
	 * @param sharedQueue
	 * @param priority
	 * @param assignment
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createLabelSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final AffineGet sourceTransform,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return new LabelDataSourceFromDelegates< T, V >(
				DataSource.createDataSource( name, data, sourceTransform, sharedQueue, priority ),
				assignment );
	}

	/**
	 * Create a primitive single scale level label source with an assignment
	 * from a {@link RandomAccessibleInterval}.
	 *
	 * @param name
	 * @param data
	 * @param resolution
	 * @param offset
	 * @param sharedQueue
	 * @param priority
	 * @param assignment
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createLabelSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return new LabelDataSourceFromDelegates< T, V >(
				DataSource.createDataSource( name, data, resolution, offset, sharedQueue, priority ),
				assignment );
	}

	/**
	 * Create a primitive single scale level label source with an assignment
	 * from a {@link RandomAccessibleInterval}.
	 *
	 * @param name
	 * @param data
	 * @param resolution
	 * @param sharedQueue
	 * @param priority
	 * @param assignment
	 * @return
	 * @throws IOException
	 */
	public static < T extends NativeType< T > & NumericType< T >, V extends NumericType< V > > LabelDataSource< T, V > createLabelSource(
			final String name,
			final RandomAccessibleInterval< T > data,
			final double[] resolution,
			final SharedQueue sharedQueue,
			final int priority,
			final FragmentSegmentAssignmentState< ? > assignment ) throws IOException
	{
		return createLabelSource( name, data, resolution, new double[ resolution.length ], sharedQueue, priority, assignment );
	}
}
