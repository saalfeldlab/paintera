package bdv.bigcat.viewer;

import java.util.concurrent.Callable;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;

public class MarchingCubesCallable implements Callable< SimpleMesh >
{
	/** volume data */
	RandomAccessibleInterval< LabelMultisetType > volume;

	/** volume dimension */
	int[] volDim;

	/** offset to positioning the vertices in global coordinates */
	int[] offset;

	/** marching cube voxel dimension */
	int[] cubeSize;

	/**
	 * defines if the criterion that will be used to generate the mesh
	 */
	MarchingCubes.ForegroundCriterion criterion;

	/** the value to match the criterion */
	int foregroundValue;

	/**
	 * indicates if it is to use the implementation directly with RAI (false) or
	 * if we must copy the data for an array first (true)
	 */
	boolean copyToArray;

	public MarchingCubesCallable( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, int[] cubeSize, MarchingCubes.ForegroundCriterion criterion, int level, boolean usingRAI )
	{
		this.volume = input;
		this.volDim = volDim;
		this.offset = offset;
		this.cubeSize = cubeSize;
		this.criterion = criterion;
		this.foregroundValue = level;
		this.copyToArray = usingRAI;
	}

	@Override
	public SimpleMesh call() throws Exception
	{
		MarchingCubes mc_rai = new MarchingCubes();
		SimpleMesh m = mc_rai.generateMesh( volume, volDim, offset, cubeSize, criterion, foregroundValue, copyToArray );

		return m;
	}
}
