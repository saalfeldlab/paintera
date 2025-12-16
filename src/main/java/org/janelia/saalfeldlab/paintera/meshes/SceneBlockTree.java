package org.janelia.saalfeldlab.paintera.meshes;

import bdv.util.Affine3DHelpers;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustumCulling;
import org.janelia.saalfeldlab.util.grids.Grids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;

public class SceneBlockTree {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final double[] levelOfDetailMaxPixels;

	static {
		levelOfDetailMaxPixels = new double[MeshSettings.Defaults.Values.getMaxLevelOfDetail() - MeshSettings.Defaults.Values.getMinLevelOfDetail() + 1];
		Arrays.setAll(levelOfDetailMaxPixels, i -> Math.pow(2, levelOfDetailMaxPixels.length - 1 - i));
	}

	private SceneBlockTree() {

	}


	public static BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> createSceneBlockTree(
			final DataSource<?, ?> source,
			final ViewFrustum viewFrustum,
			final AffineTransform3D eyeToWorldTransform,
			final int levelOfDetail,
			final int coarsestScaleLevel,
			final int finestScaleLevel,
			final CellGrid[] rendererGrids,
			final BooleanSupplier wasInterrupted) {

		final int numScaleLevels = source.getNumMipmapLevels();

		final double maxPixelsInProjectedVoxel = levelOfDetailMaxPixels[
				Math.max(0, Math.min(levelOfDetail - MeshSettings.Defaults.Values.getMinLevelOfDetail(), levelOfDetailMaxPixels.length - 1))
				];
		LOG.debug("levelOfDetail={}, maxPixelsInProjectedVoxel={}", levelOfDetail, maxPixelsInProjectedVoxel);

		final ViewFrustumCulling[] viewFrustumCullingInSourceSpace = new ViewFrustumCulling[numScaleLevels];
		final double[] minMipmapPixelSize = new double[numScaleLevels];
		for (int i = 0; i < viewFrustumCullingInSourceSpace.length; ++i) {
			final AffineTransform3D sourceToWorldTransform = new AffineTransform3D();
			source.getSourceTransform(0, i, sourceToWorldTransform);

			final AffineTransform3D cameraToSourceTransform = new AffineTransform3D();
			cameraToSourceTransform.preConcatenate(eyeToWorldTransform).preConcatenate(sourceToWorldTransform.inverse());

			viewFrustumCullingInSourceSpace[i] = new ViewFrustumCulling(viewFrustum, cameraToSourceTransform);

			final double[] extractedScale = new double[3];
			Arrays.setAll(extractedScale, d -> Affine3DHelpers.extractScale(cameraToSourceTransform.inverse(), d));
			minMipmapPixelSize[i] = Arrays.stream(extractedScale).min().getAsDouble();
		}

		final double[][] sourceScales = new double[numScaleLevels][];
		Arrays.setAll(sourceScales, i -> DataSource.getScale(source, 0, i));

		final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> blockTree = new BlockTree<>();
		final LinkedHashMap<BlockTreeFlatKey, BlockTreeFlatKey> blockAndParentQueue = new LinkedHashMap<>();

		// start with all blocks at the lowest resolution
		final int lowestResolutionScaleLevel = numScaleLevels - 1;
		final CellGrid rendererGridAtLowestResolution = rendererGrids[lowestResolutionScaleLevel];
		final long numBlocksAtLowestResolution = Intervals.numElements(rendererGridAtLowestResolution.getGridDimensions());
		LongStream.range(0, numBlocksAtLowestResolution)
				.forEach(blockIndex -> blockAndParentQueue.put(new BlockTreeFlatKey(lowestResolutionScaleLevel, blockIndex), null));

		while (!blockAndParentQueue.isEmpty() && !wasInterrupted.getAsBoolean()) {
			final Iterator<Map.Entry<BlockTreeFlatKey, BlockTreeFlatKey>> it = blockAndParentQueue.entrySet().iterator();
			final Map.Entry<BlockTreeFlatKey, BlockTreeFlatKey> entry = it.next();
			it.remove();

			final BlockTreeFlatKey key = entry.getKey();
			final BlockTreeFlatKey parentKey = entry.getValue();

			final int scaleLevel = key.scaleLevel;
			final Interval blockInterval = Grids.getCellInterval(rendererGrids[scaleLevel], key.blockIndex);

			if (viewFrustumCullingInSourceSpace[scaleLevel].intersects(blockInterval)) {
				final double distanceFromCamera = viewFrustumCullingInSourceSpace[scaleLevel].distanceFromCamera(blockInterval);
				final double screenSizeToViewPlaneRatio = viewFrustum.screenSizeToViewPlaneRatio(distanceFromCamera);
				final double screenPixelSize = screenSizeToViewPlaneRatio * minMipmapPixelSize[scaleLevel];
				LOG.trace("scaleIndex={}, screenSizeToViewPlaneRatio={}, screenPixelSize={}", scaleLevel, screenSizeToViewPlaneRatio, screenPixelSize);

				final BlockTreeNode<BlockTreeFlatKey> treeNode = new BlockTreeNode<>(parentKey, new HashSet<>(), distanceFromCamera);
				blockTree.nodes.put(key, treeNode);
				if (parentKey != null)
					blockTree.nodes.get(parentKey).children.add(key);

				// check if needed to subdivide the block
				if (scaleLevel > coarsestScaleLevel || (scaleLevel > finestScaleLevel && screenPixelSize > maxPixelsInProjectedVoxel)) {
					final int nextScaleLevel = scaleLevel - 1;
					final CellGrid rendererNextLevelGrid = rendererGrids[nextScaleLevel];

					final double[] relativeScales = new double[3];
					Arrays.setAll(relativeScales, d -> sourceScales[scaleLevel][d] / sourceScales[nextScaleLevel][d]);

					final double[] nextScaleLevelBlockMin = new double[3], nextScaleLevelBlockMax = new double[3];
					for (int d = 0; d < 3; ++d) {
						nextScaleLevelBlockMin[d] = blockInterval.min(d) * relativeScales[d];
						nextScaleLevelBlockMax[d] = (blockInterval.max(d) + 1) * relativeScales[d] - 1;
					}
					final Interval nextLevelBlockInterval = Intervals.smallestContainingInterval(
							new FinalRealInterval(nextScaleLevelBlockMin, nextScaleLevelBlockMax));

					// find out what blocks at higher resolution intersect with this block
					final long[] intersectingNextLevelBlockIndices = Grids.getIntersectingBlocks(nextLevelBlockInterval, rendererNextLevelGrid);
					for (final long intersectingNextLevelBlockIndex : intersectingNextLevelBlockIndices) {
						final BlockTreeFlatKey childKey = new BlockTreeFlatKey(nextScaleLevel, intersectingNextLevelBlockIndex);
						blockAndParentQueue.put(childKey, key);
					}
				}
			}
		}

		return !wasInterrupted.getAsBoolean() ? blockTree : null;
	}
}
