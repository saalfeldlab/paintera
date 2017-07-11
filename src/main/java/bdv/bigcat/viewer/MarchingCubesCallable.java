package bdv.bigcat.viewer;

import java.util.concurrent.Callable;

import bdv.labels.labelset.LabelMultisetType;
import net.imglib2.RandomAccessibleInterval;

public class MarchingCubesCallable implements Callable< Mesh2 >
{
	/** volume data */
	RandomAccessibleInterval< LabelMultisetType > volume;

	/** volume dimension */
	int[] volDim;

	/** offset to positioning the vertices in global coordinates */
	int[] offset;

	/** marching cube voxel dimension */
	float[] voxDim;

	/**
	 * defines if the mesh must be create for the exact isolevel (true) or for
	 * all above isolevels (false)
	 */
	boolean isExact;

	/** the isolevel */
	int isolevel;

	/**
	 * indicates if it is to use the implementation directly with RAI (true) or
	 * if we must convert for an array first (false)
	 */
	boolean usingRAI;

	public MarchingCubesCallable( RandomAccessibleInterval< LabelMultisetType > input, int[] volDim, int[] offset, float[] voxDim, boolean isExact, int level, boolean usingRAI )
	{
		this.volume = input;
		this.volDim = volDim;
		this.offset = offset;
		this.voxDim = voxDim;
		this.isExact = isExact;
		this.isolevel = level;
		this.usingRAI = usingRAI;
	}

	@Override
	public Mesh2 call() throws Exception
	{
		MarchingCubesRAI mc_rai = new MarchingCubesRAI();
		Mesh2 m = mc_rai.generateSurface( volume, volDim, offset, voxDim, isExact, isolevel, usingRAI );

		return m;
	}
}
