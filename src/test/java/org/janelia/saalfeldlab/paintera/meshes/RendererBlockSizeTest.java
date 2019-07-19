package org.janelia.saalfeldlab.paintera.meshes;

import org.junit.Assert;
import org.junit.Test;

public class RendererBlockSizeTest
{
	@Test
	public void testIsotropic()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(64, new double[][] {
				{1, 1, 1}, {2, 2, 2}, {4, 4, 4}
			});
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[2]);
	}

	@Test
	public void testNonIsotropic()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(64, new double[][] {
				{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}
			});
		Assert.assertArrayEquals(new int[] {64, 64, 16}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {64, 64, 16}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {64, 64, 32}, blockSizes[2]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[3]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[4]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[5]);
	}
}
