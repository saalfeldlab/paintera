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

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.util.HashMap;
import java.util.Map;

public class N5MultiScaleMetadata
		extends MultiscaleMetadata<N5SingleScaleMetadata>
		implements N5Metadata, N5GroupParser<N5MultiScaleMetadata> {

  public final String basePath;

  public N5MultiScaleMetadata(final String basePath, final String[] paths,
		  final AffineTransform3D[] transforms,
		  final String[] units) {

	super(paths, transforms, units);
	this.basePath = basePath;
  }

  protected N5MultiScaleMetadata() {

	super();
	basePath = null;
  }

  public N5GroupParser<N5MultiScaleMetadata> getParser() {

	return new N5MultiScaleMetadata();
  }

  @Override
  public String getPath() {

	return basePath;
  }

  public N5MultiScaleMetadata(N5SingleScaleMetadata[] childrenMetadata) {

	super(childrenMetadata);
	this.basePath = null;
  }

  /**
   * Called by the {@link N5DatasetDiscoverer}
   * while discovering the N5 tree and filling the metadata for datasets or groups.
   *
   * @param node the node
   * @return the metadata
   */
  @Override
  public N5MultiScaleMetadata parseMetadataGroup(final N5TreeNode node) {

	final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
	String[] units = null;
	for (final N5TreeNode childNode : node.childrenList()) {
	  if (scaleLevelPredicate.test(childNode.getNodeName()) && childNode.isDataset() && childNode.getMetadata() instanceof N5SingleScaleMetadata) {
		scaleLevelNodes.put(childNode.getNodeName(), childNode);
		if (units == null)
		  units = ((N5SingleScaleMetadata)childNode.getMetadata()).units();
	  }
	}

	if (scaleLevelNodes.isEmpty())
	  return null;

	final N5SingleScaleMetadata[] childMetadata = scaleLevelNodes.values().stream().map(N5TreeNode::getMetadata).toArray(N5SingleScaleMetadata[]::new);
	return new N5MultiScaleMetadata(childMetadata);
  }
}
