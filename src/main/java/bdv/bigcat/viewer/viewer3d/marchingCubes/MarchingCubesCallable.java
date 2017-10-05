package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.concurrent.Callable;

import bdv.bigcat.viewer.viewer3d.util.SimpleMesh;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;

/**
 * Class that calls the method to generate the mesh. This class is necessary to
 * use the future threads.
 *
 * @author vleite
 *
 * @param <T>
 */
public class MarchingCubesCallable< T > implements Callable< SimpleMesh >
{
	/** volume data */
	RandomAccessible< T > volume;

	private final Interval interval;

	/** marching cube voxel dimension */
	private final int[] cubeSize;

	/**
	 * defines if the criterion that will be used to generate the mesh
	 */
	/** the value to match the criterion */
	private final int foregroundValue;

	/**
	 * indicates if it is to use the implementation directly with RAI (false) or
	 * if we must copy the data for an array first (true)
	 */
	boolean copyToArray;

	/**
	 * Constructor
	 *
	 * @param input
	 *            the data that will be used to generate the mesh
	 * @param volDim
	 *            dimension of the dataset (chunk)
	 * @param offset
	 *            offset of the dataset (chunk)
	 * @param cubeSize
	 *            the size of the cube that will "march" through the data
	 * @param criterion
	 *            the criteria used to activate voxels
	 * @param foregroundValue
	 *            the value that will be used to generate the mesh
	 * @param copyToArray
	 *            boolean that indicates if the data must be copied to an array
	 *            before generate the mesh
	 */
	public MarchingCubesCallable( final RandomAccessible< T > input, final Interval interval, final int[] cubeSize, final int foregroundValue, final boolean copyToArray )
	{
		this.volume = input;
		this.interval = interval;
		this.cubeSize = cubeSize;
		this.foregroundValue = foregroundValue;
		this.copyToArray = copyToArray;
	}

	@Override
	public SimpleMesh call() throws Exception
	{
		final MarchingCubes< T > mc_rai = new MarchingCubes<>( volume, interval, cubeSize, foregroundValue );// new
		// MarchingCubes<>();
		final SimpleMesh m = mc_rai.generateMesh( copyToArray );

		return m;
	}
}
