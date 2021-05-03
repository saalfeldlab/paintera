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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.io.IOException;
import java.util.Optional;

public class N5PainteraRawMultiScaleGroup extends N5GenericMultiScaleMetadata {

  public final String basePath;

  public N5PainteraRawMultiScaleGroup(N5GenericSingleScaleMetadata[] childrenMetadata, String basePath) {

	super(childrenMetadata, basePath);
	this.basePath = basePath;
  }

  /**
   * Called by the {@link N5DatasetDiscoverer}
   * while discovering the N5 tree and filling the metadata for datasets or groups.
   *
   * @param node the node
   * @return the metadata
   */
  public static Optional<N5GenericMultiScaleMetadata> parseMetadataGroup(final N5Reader reader, final N5TreeNode node) {

	if (node.getMetadata() instanceof N5DatasetMetadata)
	  return Optional.empty(); // we're a dataset, so not a multiscale group

	/* We'll short-circuit here if any of the children don't conform to the scaleLevelPredicate */
	for (final N5TreeNode childNode : node.childrenList()) {
	  var isMultiScale = scaleLevelPredicate.test(childNode.getNodeName());
	  if (!isMultiScale) {
		return Optional.empty();
	  }
	}

	String painteraDataType = null;
	try {
	  final var painteraData = reader.getAttribute(node.getPath(), "painteraData", JsonObject.class);
	  if (painteraData == null) {
		return Optional.empty();
	  } else {
		painteraDataType = Optional.ofNullable(painteraData.get("type")).map(JsonElement::getAsString).orElse(null);
	  }
	} catch (IOException e) {
	  return Optional.empty();
	}

	if (painteraDataType.equals("raw")) {
	  boolean allChildrenArePainteraCompliant = node.childrenList().stream().map(N5TreeNode::getMetadata).allMatch(N5GenericSingleScaleMetadata.class::isInstance);
	  if (allChildrenArePainteraCompliant) {
		N5GenericSingleScaleMetadata[] childrenMetadata = node.childrenList().stream()
				.map(N5TreeNode::getMetadata)
				.map(N5GenericSingleScaleMetadata.class::cast)
				.toArray(N5GenericSingleScaleMetadata[]::new);
		N5PainteraRawMultiScaleGroup value = new N5PainteraRawMultiScaleGroup(childrenMetadata, node.getPath());
		return Optional.of(value);
	  }
	  return Optional.empty();
	}
	return Optional.empty();
  }

  @Override public String getPath() {

	return basePath;
  }
}
