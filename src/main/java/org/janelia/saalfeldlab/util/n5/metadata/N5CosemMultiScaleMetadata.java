/**
 * Copyright (c) 2018--2020, Saalfeld lab
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineGet;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.math.ArrayMath;
import org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N5CosemMultiScaleMetadata extends MultiscaleMetadata<N5CosemMetadata> implements N5Metadata, PhysicalMetadata, PainteraMultiscaleGroup<N5CosemMetadata> {

  public final String basePath;

  public N5CosemMultiScaleMetadata(N5CosemMetadata[] childrenMetadata, String basePath) {

	super(childrenMetadata);
	this.basePath = basePath;
  }

  protected N5CosemMultiScaleMetadata() {

	super();
	basePath = null;
  }

  @Override
  public String getPath() {

	return basePath;
  }

  /**
   * Called by the {@link N5DatasetDiscoverer}
   * while discovering the N5 tree and filling the metadata for datasets or groups.
   *
   * @param node the node
   * @return the metadata
   */
  public static Optional<N5CosemMultiScaleMetadata> parseMetadataGroup(final N5Reader reader, final N5TreeNode node) {

	final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
	String[] units = null;
	/* get s0 so we can calculate downsamplingFactors for Paintera */
	final var s0NodeOpt = node.childrenList().stream()
			.filter(x -> x.getMetadata() instanceof N5CosemMetadata)
			.filter(x -> x.getNodeName().equals("s0"))
			.findFirst();
	if (s0NodeOpt.isEmpty()) {
	  return Optional.empty();
	}
	final var s0Metadata = (N5CosemMetadata)s0NodeOpt.get().getMetadata();
	final double[] zeroScale = s0Metadata.getCosemTransform().scale;

	for (final N5TreeNode childNode : node.childrenList()) {
	  if (scaleLevelPredicate.test(childNode.getNodeName()) && childNode.isDataset() && childNode.getMetadata() instanceof N5CosemMetadata) {
		scaleLevelNodes.put(childNode.getNodeName(), childNode);
		if (units == null) {
		  units = ((N5CosemMetadata)childNode.getMetadata()).units();
		}
		final var childMetadata = (N5CosemMetadata)childNode.getMetadata();
		final double[] curScale = childMetadata.getCosemTransform().scale;
		final var downsamplingFactor = ArrayMath.divide3(curScale, zeroScale);
		childMetadata.setDownsamplingFactors(downsamplingFactor);
	  }
	}

	if (scaleLevelNodes.isEmpty())
	  return Optional.empty();

	final N5CosemMetadata[] childMetadata = scaleLevelNodes.values().stream().map(N5TreeNode::getMetadata).toArray(N5CosemMetadata[]::new);
	if (!sortScaleMetadata(childMetadata)) {
	  return Optional.empty();
	}
	return Optional.of(new N5CosemMultiScaleMetadata(childMetadata, node.getPath()));
  }

  /**
   * Sort the array according to scale level; If not all metadata are scale sets, no sorting is done.
   * All metadata names as returned by {@code N5Metadata::getName()} should be of the form sN.
   *
   * @param metadataToBeSorted array of the unsorted scale metadata to be sorted
   * @param <T>                the type of the metadata
   */
  public static <T extends N5DatasetMetadata> boolean sortScaleMetadata(T[] metadataToBeSorted) {

	final var allAreScaleSets = Arrays.stream(metadataToBeSorted).allMatch(x -> x.getName().matches("^s\\d+$"));
	if (!allAreScaleSets)
	  return false;

	Arrays.sort(metadataToBeSorted, Comparator.comparingInt(s -> Integer.parseInt(s.getName().replaceAll("[^\\d]", ""))));
	return true;
  }

  @Override public boolean isLabel() {

	return childrenMetadata[0].getAttributes().getDataType() == DataType.UINT64;
  }

  @Override
  public AffineGet physicalTransform() {
	// spatial transforms are specified by the individual scales
	return null;
  }

  @Override public double[] getDownsamplingFactors(int scaleIdx) {

	return getChildrenMetadata()[scaleIdx].getDownsamplingFactors();
  }
}
