package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Metadata Parser for a Paintera Data Multiscale Dataset. Namely, transforms are dependant on group metadata
 */
public class N5PainteraDataMultiScaleMetadata extends SpatialMultiscaleMetadata<N5SpatialDatasetMetadata> {

	public N5PainteraDataMultiScaleMetadata(final String basePath, final N5SpatialDatasetMetadata[] childrenMetadata) {

		super(basePath, childrenMetadata);
	}
	@Override public String unit() {

		return "pixel";
	}

	public static class PainteraDataMultiScaleParser implements N5MetadataParser<N5PainteraDataMultiScaleMetadata> {

		@Override public Optional<N5PainteraDataMultiScaleMetadata> parseMetadata(N5Reader n5, N5TreeNode node) {

			if (!node.getNodeName().equals("data")) {
				return Optional.empty();
			}

			final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
			for (final N5TreeNode childNode : node.childrenList()) {
				if (N5PainteraDataMultiScaleGroup.SCALE_LEVEL_PREDICATE.test(childNode.getNodeName()) && childNode.isDataset()
						&& childNode.getMetadata() instanceof N5SpatialDatasetMetadata) {
					scaleLevelNodes.put(childNode.getNodeName(), childNode);
				}
			}

			if (scaleLevelNodes.isEmpty())
				return Optional.empty();

			final N5SingleScaleMetadata[] childMetadata = scaleLevelNodes.values().stream().map(N5TreeNode::getMetadata).toArray(N5SingleScaleMetadata[]::new);
			if (!sortScaleMetadata(childMetadata)) {
				return Optional.empty();
			}

			final Function<String, double[]> getAttrOrNull = (key) -> {
				try {
					return n5.getAttribute(node.getPath(), key, double[].class);
				} catch (Exception e) {
					return null;
				}
			};

			final double[] resolution = Optional.ofNullable(getAttrOrNull.apply("resolution")).orElseGet(() -> new double[]{1.0, 1.0, 1.0});
			final double[] offset = Optional.ofNullable(getAttrOrNull.apply("offset")).orElseGet(() -> new double[]{0.0, 0.0, 0.0});

			if (resolution.length != 3 || offset.length != 3) {
				return Optional.empty();
			}

			final var transform = new AffineTransform3D();
			transform.set(
					resolution[0], 0.0, 0.0, offset[0],
					0.0, resolution[1], 0.0, offset[1],
					0.0, 0.0, resolution[2], offset[2]
			);
			/* generate new children metadata with resolved spatialTransforms */
			final N5SingleScaleMetadata[] resolvedChildrenMetadata = new N5SingleScaleMetadata[childMetadata.length];
			for (int i = 0; i < childMetadata.length; i++) {
				final N5SingleScaleMetadata child = childMetadata[i];
				final AffineTransform3D spatialTransform = new AffineTransform3D();
				final Scale3D downsample = new Scale3D(child.getDownsamplingFactors());
				spatialTransform.set(transform);
				spatialTransform.concatenate(downsample);
				final double[] pixelResolution = new double[]{
						spatialTransform.get(0,0),
						spatialTransform.get(1,1),
						spatialTransform.get(2,2),
				};
				final double[] pixelOffset = new double[]{
						spatialTransform.get(0,3),
						spatialTransform.get(1,3),
						spatialTransform.get(2,3),
				};

				resolvedChildrenMetadata[i] = new N5SingleScaleMetadata(
						child.getPath(),
						spatialTransform,
						child.getDownsamplingFactors(),
						pixelResolution,
						pixelOffset,
						child.unit(),
						child.getAttributes(),
						child.minIntensity(),
						child.maxIntensity(),
						child.isLabelMultiset()
				);
			}

			return Optional.of(new N5PainteraDataMultiScaleMetadata(node.getPath(), resolvedChildrenMetadata));
		}
	}
}
