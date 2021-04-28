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
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.io.IOException;
import java.util.Optional;

import static org.janelia.saalfeldlab.util.n5.N5Helpers.MULTI_SCALE_KEY;

public class N5RawMultiScaleMetadata extends MultiscaleMetadata<N5SingleScaleMetadata> implements N5Metadata, PhysicalMetadata {

  public final String basePath;

  public N5RawMultiScaleMetadata(N5SingleScaleMetadata[] childrenMetadata) {

	super(childrenMetadata);
	this.basePath = null;
  }

  protected N5RawMultiScaleMetadata() {

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
  public static Optional<N5RawMultiScaleMetadata> parseMetadataGroup(final N5Reader reader, final N5TreeNode node) {

    if (node.getMetadata() instanceof N5DatasetMetadata)
      return Optional.empty(); // we're a dataset, so not a multiscale group

	/* check by attribute */
	try {
	  boolean isMultiscale = Optional.ofNullable(reader.getAttribute(node.getPath(), MULTI_SCALE_KEY, Boolean.class)).orElse(false);
	  if (isMultiscale) {
		return Optional.of(new N5RawMultiScaleMetadata());
	  }
	} catch (IOException ignore) {
	}

	var isMultiScale = true;
	for (final N5TreeNode childNode : node.childrenList()) {
	  isMultiScale = scaleLevelPredicate.test(childNode.getNodeName());
	  if (!isMultiScale) {
	    return Optional.empty();
	  }
	}

	return Optional.of(new N5RawMultiScaleMetadata());
  }

  @Override
  public AffineGet physicalTransform() {
	// spatial transforms are specified by the individual scales
	return null;
  }

}
