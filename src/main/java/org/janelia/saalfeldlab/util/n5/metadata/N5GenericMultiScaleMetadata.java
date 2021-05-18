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

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.janelia.saalfeldlab.util.n5.N5Helpers.IS_LABEL_MULTISET_KEY;
import static org.janelia.saalfeldlab.util.n5.N5Helpers.MULTI_SCALE_KEY;
import static org.janelia.saalfeldlab.util.n5.N5Helpers.RESOLUTION_KEY;

public class N5GenericMultiScaleMetadata<T extends N5DatasetMetadata & PainteraSourceMetadata> extends MultiscaleMetadata<T> implements PainteraMultiscaleGroup<T> {

  public final String basePath;
  private final Boolean isLabelMultiset;
  private final double[] resolution;

  public N5GenericMultiScaleMetadata(T[] childrenMetadata, String basePath, Boolean isLabelMultiSet, double[] resolution) {

	super(childrenMetadata);
	this.basePath = basePath;
	this.isLabelMultiset = isLabelMultiSet;
	this.resolution = resolution;
  }

  public N5GenericMultiScaleMetadata(T[] childrenMetadata, String basePath, Boolean isLabelMultiset) {

	this(childrenMetadata, basePath, isLabelMultiset, new double[]{1.0, 1.0, 1.0});
  }

  public N5GenericMultiScaleMetadata(T[] childrenMetadata, String basePath) {

	this(childrenMetadata, basePath, false);
  }

  protected N5GenericMultiScaleMetadata(String basePath) {

	super();
	this.basePath = basePath;
	this.isLabelMultiset = false;
	this.resolution = new double[]{1.0, 1.0, 1.0};
  }

  @Override public double[] getResolution() {

	return resolution;
  }

  @Override public boolean isLabelMultisetType() {

	return isLabelMultiset;
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
  public static Optional<N5GenericMultiScaleMetadata<?>> parseMetadataGroup(final N5Reader reader, final N5TreeNode node) {

	if (node.getMetadata() instanceof N5DatasetMetadata)
	  return Optional.empty(); // we're a dataset, so not a multiscale group

	/* All children should be SingleScale */
	final List<N5Metadata> childrenMetadata = node.childrenList().stream()
			.map(N5TreeNode::getMetadata).collect(Collectors.toList());

	if (!childrenMetadata.stream().allMatch(N5GenericSingleScaleMetadata.class::isInstance)) {
	  return Optional.empty();
	}

	final N5GenericSingleScaleMetadata[] childrenMetadataGenericSS = childrenMetadata.stream()
			.map(N5GenericSingleScaleMetadata.class::cast)
			.toArray(N5GenericSingleScaleMetadata[]::new);

	/* check by attribute */
	boolean isMultiscale;
	boolean isLabelMultiset = false;
	double[] resolution = new double[]{1.0, 1.0, 1.0};
	try {
	  isMultiscale = Optional.ofNullable(reader.getAttribute(node.getPath(), MULTI_SCALE_KEY, Boolean.class)).orElse(false);
	  resolution = Optional.ofNullable(reader.getAttribute(node.getPath(), RESOLUTION_KEY, double[].class)).orElse(resolution);
	  /* This first checks if we explicitly say we are label multiset at the group level.
	   *  	But if it doesn't, we still check the first child to see if they say anything. */
	  isLabelMultiset = Optional.ofNullable(reader.getAttribute(node.getPath(), IS_LABEL_MULTISET_KEY, Boolean.class))
			  .orElseGet(() -> {
				if (isMultiscale) {
				  return childrenMetadataGenericSS[0].isLabelMultiset();
				} else
				  return false;
			  });
	  if (isMultiscale) {
		/* check the first child for label multiset if */
		return Optional.of(new N5GenericMultiScaleMetadata<>(childrenMetadataGenericSS, node.getPath(), isLabelMultiset, resolution));
	  }
	} catch (IOException ignore) {
	  return Optional.empty();
	}

	/* We'll short-circuit here if any of the children don't conform to the scaleLevelPredicate */
	for (final N5TreeNode childNode : node.childrenList()) {
	  var isMultiScale = scaleLevelPredicate.test(childNode.getNodeName());
	  if (!isMultiScale) {
		return Optional.empty();
	  }
	}

	/* Otherwise, if we get here, nothing went wrong, assume we are multiscale*/
	return Optional.of(new N5GenericMultiScaleMetadata<>(childrenMetadataGenericSS, node.getPath(), isLabelMultiset, resolution));
  }
}
