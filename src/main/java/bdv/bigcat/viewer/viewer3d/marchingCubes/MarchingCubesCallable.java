package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.concurrent.Callable;

import bdv.bigcat.viewer.viewer3d.util.SimpleMesh;
import net.imglib2.RandomAccessibleInterval;

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
	RandomAccessibleInterval< T > volume;

	/** volume dimension */
	private final int[] volDim;

	/** offset to positioning the vertices in global coordinates */
	private final long[] offset;

	/** marching cube voxel dimension */
	private final int[] cubeSize;

	/**
	 * defines if the criterion that will be used to generate the mesh
	 */
	private final MarchingCubes.ForegroundCriterion criterion;

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
	public MarchingCubesCallable( final RandomAccessibleInterval< T > input, final int[] volDim, final long[] offset, final int[] cubeSize, final MarchingCubes.ForegroundCriterion criterion, final int foregroundValue, final boolean copyToArray )
	{
		this.volume = input;
		this.volDim = volDim;
		this.offset = offset;
		this.cubeSize = cubeSize;
		this.criterion = criterion;
		this.foregroundValue = foregroundValue;
		this.copyToArray = copyToArray;
	}

	@Override
	public SimpleMesh call() throws Exception
	{
		final MarchingCubes< T > mc_rai = new MarchingCubes<>( volume, cubeSize, foregroundValue, offset );// new
		// MarchingCubes<>();
		final SimpleMesh m = mc_rai.generateMesh( copyToArray );

		return m;
	}
}
