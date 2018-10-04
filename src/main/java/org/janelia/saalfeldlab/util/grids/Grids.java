package org.janelia.saalfeldlab.util.grids;

import net.imglib2.realtransform.ScaleAndTranslation;

public class Grids {

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to {@code relativeScale}.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param relativeScale relativeScale from source to target
	 */
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

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to scaling to world space.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param sourceToWorld transform from source to world coordinates
	 * @param targetToWorld transform from target to world coordinates
	 */
	public static void scaleBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final double[] sourceToWorld,
			final double[] targetToWorld
	)
	{
		mapBoundingBox(
				min,
				max,
				mappedMin,
				mappedMax,
				new ScaleAndTranslation(sourceToWorld, new double[sourceToWorld.length]),
				new ScaleAndTranslation(targetToWorld, new double[targetToWorld.length]));
	}

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to {@code tf}.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param tf transform from source to target
	 */
	public static void mapBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final ScaleAndTranslation tf)
	{
		assert min.length == tf.numSourceDimensions();
		assert max.length == tf.numSourceDimensions();
		assert mappedMin.length == tf.numTargetDimensions();
		assert mappedMax.length == tf.numTargetDimensions();

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

	/**
	 * Map bounding box defined by {@code min} and {@code max} according to transforms to world coordinates.
	 * @param min source top-left
	 * @param max source bottom-right
	 * @param mappedMin target top-left
	 * @param mappedMax target bottom-right
	 * @param sourceToWorld transform from source to world coordinates
	 * @param targetToWorld transform from target to world coordinates
	 */
	public static void mapBoundingBox(
			final double[] min,
			final double[] max,
			final double[] mappedMin,
			final double[] mappedMax,
			final ScaleAndTranslation sourceToWorld,
			final ScaleAndTranslation targetToWorld)
	{
		assert sourceToWorld.numSourceDimensions() == targetToWorld.numSourceDimensions();
		assert sourceToWorld.numTargetDimensions() == targetToWorld.numTargetDimensions();
		assert min.length == sourceToWorld.numSourceDimensions();
		assert max.length == sourceToWorld.numSourceDimensions();
		assert mappedMin.length == targetToWorld.numTargetDimensions();
		assert mappedMax.length == targetToWorld.numTargetDimensions();
		mapBoundingBox(min, max, mappedMin, mappedMax, sourceToWorld.preConcatenate(targetToWorld.inverse()));
	}

}
