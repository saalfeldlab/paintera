package org.janelia.saalfeldlab.paintera.meshes;

import bdv.viewer.Source;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import java.util.Arrays;

public class RendererBlockSizes
{
	private RendererBlockSizes() {}

	public static int[][] getRendererBlockSizes(final int rendererBlockSize, final Source<?> source)
	{
		final double[][] scales = new double[source.getNumMipmapLevels()][];
		Arrays.setAll(scales, i -> DataSource.getScale(source, 0, i));
		return getRendererBlockSizes(rendererBlockSize, scales);
	}

	public static int[][] getRendererBlockSizes(final int rendererBlockSize, final double[][] sourceScales)
	{
		if (rendererBlockSize <= 0)
			return null;

		final int[][] rendererFullBlockSizes = new int[sourceScales.length][];
		for (int i = 0; i < rendererFullBlockSizes.length; ++i)
		{
			rendererFullBlockSizes[i] = new int[sourceScales[i].length];
			final double minScale = Arrays.stream(sourceScales[i]).min().getAsDouble();
			for (int d = 0; d < rendererFullBlockSizes[i].length; ++d)
			{
				final double scaleRatio = sourceScales[i][d] / minScale;
				final double bestBlockSize = rendererBlockSize / scaleRatio;
				final int adjustedBlockSize;
				if (i > 0) {
					final int closestMultipleFactor = Math.max(1, (int) Math.round(bestBlockSize / rendererFullBlockSizes[i - 1][d]));
					adjustedBlockSize = rendererFullBlockSizes[i - 1][d] * closestMultipleFactor;
				} else {
					adjustedBlockSize = (int) Math.round(bestBlockSize);
				}
				// clamp the block size, but do not limit the block size in Z to allow for closer to isotropic blocks
				final int clampedBlockSize = Math.max(
						d == 2 ? 1 : Viewer3DConfig.RENDERER_BLOCK_SIZE_MIN_VALUE, Math.min(
								Viewer3DConfig.RENDERER_BLOCK_SIZE_MAX_VALUE,
								adjustedBlockSize
						)
				);
				rendererFullBlockSizes[i][d] = clampedBlockSize;
			}
		}

		return rendererFullBlockSizes;
	}
}
