package org.janelia.saalfeldlab.util.grids;

import net.imglib2.realtransform.ScaleAndTranslation;
import org.junit.Assert;
import org.junit.Test;

public class GridsTest {

	@Test
	public void testMapBoundingBox2D()
	{
		final double[] min = {-1.0, -1.0};
		final double[] max = {+1.0, +1.0};
		final double[] mappedMin = new double[2];
		final double[] mappedMax = new double[2];

		Grids.mapBoundingBox(min, max, mappedMin, mappedMax, new ScaleAndTranslation(new double[] {1.0, 1.0}, new double[] {0.5, 1.0}));
		Assert.assertArrayEquals(new double[] {-0.5, +0.0}, mappedMin, 0.0);
		Assert.assertArrayEquals(new double[] {+1.5, +2.0}, mappedMax, 0.0);

		Grids.scaleBoundingBox(min, max, mappedMin, mappedMax, new double[] {2.0, 3.0});
		Assert.assertArrayEquals(new double[] {-2.0, -3.0}, mappedMin, 0.0);
		Assert.assertArrayEquals(new double[] {+2.0, +3.0}, mappedMax, 0.0);
	}

}
