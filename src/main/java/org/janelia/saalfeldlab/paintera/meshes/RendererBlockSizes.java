package org.janelia.saalfeldlab.paintera.meshes;

import bdv.viewer.Source;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import java.util.Arrays;

public class RendererBlockSizes {

	private RendererBlockSizes() {

	}

	public static CellGrid[] getRendererGrids(final DataSource<?, ?> source, final int rendererBlockSize) {

		final int[][] rendererBlockSizes = getRendererBlockSizes(source, rendererBlockSize);
		return rendererBlockSizes != null ? getRendererGrids(source, rendererBlockSizes) : null;
	}

	public static CellGrid[] getRendererGrids(final DataSource<?, ?> source, final int[][] rendererBlockSizes) {

		final long[][] imgDimensions = Arrays.stream(source.getGrids()).map(CellGrid::getImgDimensions).toArray(long[][]::new);
		return getRendererGrids(imgDimensions, rendererBlockSizes);
	}

	public static CellGrid[] getRendererGrids(final long[][] imgDimensions, final int[][] rendererBlockSizes) {

		final CellGrid[] rendererGrids = new CellGrid[rendererBlockSizes.length];
		for (int i = 0; i < rendererGrids.length; ++i) {
			rendererGrids[i] = new CellGrid(imgDimensions[i], rendererBlockSizes[i]);
		}
		return rendererGrids;
	}

	public static int[][] getRendererBlockSizes(final Source<?> source, final int rendererBlockSize) {

		final double[][] scales = new double[source.getNumMipmapLevels()][];
		Arrays.setAll(scales, i -> DataSource.getScale(source, 0, i));
		return getRendererBlockSizes(scales, rendererBlockSize);
	}

	public static int[][] getRendererBlockSizes(final double[][] sourceScales, final int rendererBlockSize) {

		if (rendererBlockSize <= 0)
			return null;

		final int[][] rendererFullBlockSizes = new int[sourceScales.length][];
		for (int i = 0; i < rendererFullBlockSizes.length; ++i) {
			rendererFullBlockSizes[i] = new int[sourceScales[i].length];
			final double minScale = Arrays.stream(sourceScales[i]).min().getAsDouble();
			for (int d = 0; d < rendererFullBlockSizes[i].length; ++d) {
				final double scaleRatio = sourceScales[i][d] / minScale;
				final double bestBlockSize = rendererBlockSize / scaleRatio;
				final int adjustedBlockSize;
				if (i > 0) {
					final int closestMultipleFactor = Math.max(1, (int)Math.round(bestBlockSize / rendererFullBlockSizes[i - 1][d]));
					adjustedBlockSize = rendererFullBlockSizes[i - 1][d] * closestMultipleFactor;
				} else {
					adjustedBlockSize = (int)Math.round(bestBlockSize);
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
