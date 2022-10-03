package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Metadata Parser for a Paintera Data Multiscale Dataset. Namely, transforms are dependant on group metadata
 */
public class N5PainteraDataMultiScaleMetadata extends N5MultiScaleMetadata {

  private AffineTransform3D groupTransform;

  private final AffineTransform3D[] dataChildrenTransforms = new AffineTransform3D[childrenMetadata.length];

  public N5PainteraDataMultiScaleMetadata(final String basePath, final N5SingleScaleMetadata[] childrenMetadata, final AffineTransform3D groupTransform) {

	super(basePath, childrenMetadata);
	this.groupTransform = groupTransform;
	for (int i = 0; i < childrenMetadata.length; i++) {
	  final var metadata = childrenMetadata[i];
	  final var childTransform = metadata.spatialTransform3d();
	  dataChildrenTransforms[i] = groupTransform.copy().concatenate(childTransform);
	}
  }

  @Override public AffineTransform3D[] spatialTransforms3d() {

	return Arrays.stream(dataChildrenTransforms).map(AffineTransform3D::copy).toArray(AffineTransform3D[]::new);
  }

  @Override public AffineGet spatialTransform() {

	return groupTransform.copy();
  }

  @Override public AffineTransform3D spatialTransform3d() {

	return groupTransform.copy();
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
		if (N5PainteraDataMultiScaleGroup.SCALE_LEVEL_PREDICATE.test(childNode.getNodeName()) && childNode.isDataset() && childNode.getMetadata() instanceof N5SingleScaleMetadata) {
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
	  return Optional.of(new N5PainteraDataMultiScaleMetadata(node.getPath(), childMetadata, transform));
	}
  }
}
