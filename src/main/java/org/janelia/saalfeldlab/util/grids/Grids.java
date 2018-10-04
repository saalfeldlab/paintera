package org.janelia.saalfeldlab.util.grids;

import net.imglib2.realtransform.ScaleAndTranslation;

public class Grids {

	public static void scaleBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final double[] relativeScale
			)
	{
		final ScaleAndTranslation tf = new ScaleAndTranslation(relativeScale, new double[relativeScale.length]);
		mapBoundingBox(min, max, mappedMin, mappedMax, tf);
	}

	public static void mapBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			ScaleAndTranslation tf)
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
