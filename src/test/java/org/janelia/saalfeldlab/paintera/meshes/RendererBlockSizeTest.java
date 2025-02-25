package org.janelia.saalfeldlab.paintera.meshes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class RendererBlockSizeTest {

	@Test
	public void testIsotropicSmallValues() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{1, 1, 1}, {2, 2, 2}, {4, 4, 4}},
				8
		);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[0]);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[1]);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[2]);
	}

	@Test
	public void testIsotropic() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{1, 1, 1}, {2, 2, 2}, {4, 4, 4}},
				16
		);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[0]);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[1]);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[2]);
	}

	@Test
	public void testNonIsotropicSmallValues() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}},
				8
		);
		assertArrayEquals(new int[]{8, 8, 1}, blockSizes[0]);
		assertArrayEquals(new int[]{8, 8, 2}, blockSizes[1]);
		assertArrayEquals(new int[]{8, 8, 4}, blockSizes[2]);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[3]);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[4]);
		assertArrayEquals(new int[]{8, 8, 8}, blockSizes[5]);
	}

	@Test
	public void testNonIsotropic() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}},
				16
		);
		assertArrayEquals(new int[]{16, 16, 2}, blockSizes[0]);
		assertArrayEquals(new int[]{16, 16, 4}, blockSizes[1]);
		assertArrayEquals(new int[]{16, 16, 8}, blockSizes[2]);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[3]);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[4]);
		assertArrayEquals(new int[]{16, 16, 16}, blockSizes[5]);
	}

	@Test
	public void testLargeValues() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{4, 4, 40}, {8, 8, 40}, {16, 16, 40}, {32, 32, 40}, {64, 64, 80}, {128, 128, 80}},
				64
		);
		assertArrayEquals(new int[]{64, 64, 6}, blockSizes[0]);
		assertArrayEquals(new int[]{64, 64, 12}, blockSizes[1]);
		assertArrayEquals(new int[]{64, 64, 24}, blockSizes[2]);
		assertArrayEquals(new int[]{64, 64, 48}, blockSizes[3]);
		assertArrayEquals(new int[]{64, 64, 48}, blockSizes[4]);
		assertArrayEquals(new int[]{64, 64, 48}, blockSizes[5]);
	}

	@Test
	public void testLargeValuesExtremeResolution() {

		final int[][] blockSizes = RendererBlockSizes.getRendererBlockSizes(
				new double[][]{{4, 4, 160}, {8, 8, 160}, {16, 16, 160}, {32, 32, 160}, {64, 64, 160}, {128, 128, 160}, {256, 256, 160}},
				64
		);
		assertArrayEquals(new int[]{64, 64, 2}, blockSizes[0]);
		assertArrayEquals(new int[]{64, 64, 4}, blockSizes[1]);
		assertArrayEquals(new int[]{64, 64, 8}, blockSizes[2]);
		assertArrayEquals(new int[]{64, 64, 16}, blockSizes[3]);
		assertArrayEquals(new int[]{64, 64, 32}, blockSizes[4]);
		assertArrayEquals(new int[]{64, 64, 64}, blockSizes[5]);
		assertArrayEquals(new int[]{64, 64, 64}, blockSizes[6]);
	}
}
