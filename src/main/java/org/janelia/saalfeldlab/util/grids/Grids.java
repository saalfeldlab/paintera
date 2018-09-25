package org.janelia.saalfeldlab.util.grids;

import net.imglib2.realtransform.RealTransform;

public class Grids {

	public static void mapBoundingBox(
			double[] min,
			double[] max,
			double[] mappedMin,
			double[] mappedMax,
			RealTransform tf)
	{
		assert min.length == tf.numSourceDimensions();
		assert max.length == tf.numSourceDimensions();
		assert mappedMin.length == tf.numTargetDimensions();
		assert mappedMax.length == tf.numTargetDimensions();

		// TODO how to deal with rotations?
		tf.apply(min, mappedMin);
		tf.apply(max, mappedMax);
		for (int d = 0; d < mappedMin.length; ++d)
		{
			double tmp = mappedMax[d];
			if (mappedMin[d] > tmp)
			{
				mappedMax[d] = mappedMin[d];
				mappedMin[d] = tmp;
			}
		}
	}

}
