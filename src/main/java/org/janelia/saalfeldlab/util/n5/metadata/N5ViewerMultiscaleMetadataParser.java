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
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N5ViewerMultiscaleMetadataParser
		extends MultiscaleMetadata<N5SingleScaleMetadata>
		implements N5Metadata {

  private final String basePath;

  public N5ViewerMultiscaleMetadataParser() {

	super();
	this.basePath = null;
  }

  public N5ViewerMultiscaleMetadataParser(N5SingleScaleMetadata[] childrenMetadata, String basePath) {

	super(childrenMetadata);
	this.basePath = basePath;
  }

  /**
   * Called by the {@link org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer}
   * while discovering the N5 tree and filling the metadata for datasets or groups.
   *
   * @param node the node
   * @return the metadata
   */
  public static Optional<N5ViewerMultiscaleMetadataParser> parseMetadataGroup(final N5Reader reader, final N5TreeNode node) {

	final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
	String[] units = null;
	for (final N5TreeNode childNode : node.childrenList()) {
	  if (scaleLevelPredicate.test(childNode.getNodeName()) &&
			  childNode.isDataset() &&
			  childNode.getMetadata() instanceof N5SingleScaleMetadata) {
		scaleLevelNodes.put(childNode.getNodeName(), childNode);
		if (units == null)
		  units = ((N5SingleScaleMetadata)childNode.getMetadata()).units();
	  }
	}

	if (scaleLevelNodes.isEmpty())
	  return Optional.empty();

	final N5SingleScaleMetadata[] childMetadata = scaleLevelNodes.values().stream().map(N5TreeNode::getMetadata).toArray(N5SingleScaleMetadata[]::new);
	return Optional.of(new N5ViewerMultiscaleMetadataParser(childMetadata, node.getPath()));
  }

  @Override public String getPath() {

	return this.basePath;
  }
}
