package org.janelia.saalfeldlab.paintera.meshes;

import org.junit.Assert;
import org.junit.Test;

public class RendererBlockSizeTest
{
	@Test
	public void testIsotropicSmallValues()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(16, new double[][] {
				{1, 1, 1}, {2, 2, 2}, {4, 4, 4}
			});
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[2]);
	}

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
	public void testNonIsotropicSmallValues()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(16, new double[][] {
				{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}
			});
		Assert.assertArrayEquals(new int[] {16, 16, 8}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {16, 16, 8}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {16, 16, 8}, blockSizes[2]);
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[3]);
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[4]);
		Assert.assertArrayEquals(new int[] {16, 16, 16}, blockSizes[5]);
	}

	@Test
	public void testNonIsotropic()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(64, new double[][] {
				{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}
			});
		Assert.assertArrayEquals(new int[] {64, 64, 8}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {64, 64, 16}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {64, 64, 32}, blockSizes[2]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[3]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[4]);
		Assert.assertArrayEquals(new int[] {64, 64, 64}, blockSizes[5]);
	}

	@Test
	public void testLargeValues()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(256, new double[][] {
				{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}
			});
		Assert.assertArrayEquals(new int[] {256, 256, 26}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {256, 256, 52}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {256, 256, 104}, blockSizes[2]);
		Assert.assertArrayEquals(new int[] {256, 256, 208}, blockSizes[3]);
		Assert.assertArrayEquals(new int[] {256, 256, 208}, blockSizes[4]);
		Assert.assertArrayEquals(new int[] {256, 256, 208}, blockSizes[5]);
	}

	@Test
	public void testLargeValuesExtremeResolution()
	{
		final int[][] blockSizes = MeshGeneratorJobManager.getRendererFullBlockSizes(256, new double[][] {
				{4, 4, 160}, {8, 8, 160}, {16, 16, 160}, {32, 32, 160}, {64, 64, 160}, {128, 128, 160}, {256, 256, 160}
			});
		Assert.assertArrayEquals(new int[] {256, 256, 8}, blockSizes[0]);
		Assert.assertArrayEquals(new int[] {256, 256, 16}, blockSizes[1]);
		Assert.assertArrayEquals(new int[] {256, 256, 32}, blockSizes[2]);
		Assert.assertArrayEquals(new int[] {256, 256, 64}, blockSizes[3]);
		Assert.assertArrayEquals(new int[] {256, 256, 128}, blockSizes[4]);
		Assert.assertArrayEquals(new int[] {256, 256, 256}, blockSizes[5]);
		Assert.assertArrayEquals(new int[] {256, 256, 256}, blockSizes[6]);
	}
}
