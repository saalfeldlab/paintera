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
import org.janelia.saalfeldlab.n5.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;

import java.io.IOException;
import java.util.Optional;

public class N5PainteraLabelMultiScaleGroup extends N5PainteraDataMultiScaleGroup {

  private final MultiscaleMetadata<? extends N5SingleScaleMetadata> uniqueLabelsGroup;
  private final MultiscaleMetadata<? extends N5SingleScaleMetadata> fragmentSegmentAssignmentGroup;
  private final Long maxId;
  private final boolean isLabelMultisetType;

  public N5PainteraLabelMultiScaleGroup(
		  String basePath,
		  N5MultiScaleMetadata dataGroup,
		  MultiscaleMetadata<? extends N5SingleScaleMetadata> uniqueLabelsGroup,
		  MultiscaleMetadata<? extends N5SingleScaleMetadata> fragmentSegmentAssignmentGroup
  ) {

	this(basePath, dataGroup, uniqueLabelsGroup, fragmentSegmentAssignmentGroup, null, false);
  }

  public N5PainteraLabelMultiScaleGroup(
		  String basePath,
		  N5MultiScaleMetadata dataGroup,
		  MultiscaleMetadata<? extends N5SingleScaleMetadata> uniqueLabelsGroup,
		  MultiscaleMetadata<? extends N5SingleScaleMetadata> fragmentSegmentAssignmentGroup,
		  Long maxId,
		  Boolean isLabelMultisetType
  ) {

	super(basePath, dataGroup);
	this.uniqueLabelsGroup = uniqueLabelsGroup;
	this.fragmentSegmentAssignmentGroup = fragmentSegmentAssignmentGroup;
	this.maxId = maxId;
	this.isLabelMultisetType = isLabelMultisetType;
  }

  public boolean isLabel() {

	return true;
  }

  public boolean isLabelMultisetType() {

	return isLabelMultisetType;
  }

  public static class PainteraLabelMultiScaleParser implements N5MetadataParser<N5PainteraLabelMultiScaleGroup> {

	/**
	 * Called by the {@link N5DatasetDiscoverer}
	 * while discovering the N5 tree and filling the metadata for datasets or groups.
	 *
	 * @param node the node
	 * @return the metadata
	 */
	@Override public Optional<N5PainteraLabelMultiScaleGroup> parseMetadata(N5Reader n5, N5TreeNode node) {

	  if (node.getMetadata() instanceof N5DatasetMetadata)
		return Optional.empty(); // we're a dataset, so not a multiscale group

	  String painteraDataType;
	  Long maxId;
	  try {
		final var painteraData = n5.getAttribute(node.getPath(), "painteraData", JsonObject.class);
		if (painteraData == null) {
		  return Optional.empty();
		} else {
		  painteraDataType = Optional.ofNullable(painteraData.get("type")).map(JsonElement::getAsString).orElse(null);
		  maxId = Optional.ofNullable(n5.getAttribute(node.getPath(), "maxId", Long.class)).orElse(null);
		}
	  } catch (IOException e) {
		return Optional.empty();
	  }

	  if (!"label".equals(painteraDataType)) {
		return Optional.empty();
	  }
	  boolean allChildrenArePainteraCompliant = node.childrenList().stream()
			  .filter(x -> !x.getPath().endsWith("label-to-block-mapping"))
			  .map(N5TreeNode::getMetadata)
			  .allMatch(N5MultiScaleMetadata.class::isInstance);
	  if (!allChildrenArePainteraCompliant) {
		return Optional.empty();
	  }
	  boolean containsData = false;
	  N5MultiScaleMetadata dataGroup = null;
	  MultiscaleMetadata<? extends N5SingleScaleMetadata> uniqueLabelsGroup = null;
	  MultiscaleMetadata<? extends N5SingleScaleMetadata> fragmentSegmentAssignmentGroup = null;
	  for (final var child : node.childrenList()) {
		N5Metadata metadata = child.getMetadata();
		if (metadata instanceof N5MultiScaleMetadata) {
		  final N5MultiScaleMetadata painteraMultiMetadata = (N5MultiScaleMetadata)metadata;
		  switch (child.getNodeName()) {
		  case "data":
			containsData = true;
			dataGroup = painteraMultiMetadata;
			continue;
		  case "unique-labels":
			uniqueLabelsGroup = painteraMultiMetadata;
			continue;
		  case "label-to-block-mapping":
			continue;
		  case "fragment-segment-assignment":
			fragmentSegmentAssignmentGroup = painteraMultiMetadata;
			continue;
		  default:
			return Optional.empty();
		  }
		}
	  }
	  if (containsData) {
		return Optional.of(new N5PainteraLabelMultiScaleGroup(
				node.getPath(),
				dataGroup, uniqueLabelsGroup, fragmentSegmentAssignmentGroup,
				maxId, dataGroup.getChildrenMetadata()[0].isLabelMultiset()));
	  }
	  return Optional.empty();
	}
  }

  public MultiscaleMetadata<? extends N5SingleScaleMetadata> getUniqueLabelsGroupMetadata() {

	return uniqueLabelsGroup;
  }

  public MultiscaleMetadata<? extends N5SingleScaleMetadata> getFragmentSegmentAssignmentGroupMetadata() {

	return fragmentSegmentAssignmentGroup;
  }

  public Long getMaxId() {

	return maxId;
  }
}
