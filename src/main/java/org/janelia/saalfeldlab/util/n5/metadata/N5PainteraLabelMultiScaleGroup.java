/*
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

  private final N5MultiScaleMetadata uniqueLabelsGroup;
  private final N5SingleScaleMetadata fragmentSegmentAssignmentGroup;
  private final N5MultiScaleMetadata labelToBlockLookupGroup;

  private final Long maxId;
  private final boolean isLabelMultisetType;

  public N5PainteraLabelMultiScaleGroup(
		  String basePath,
		  N5PainteraDataMultiScaleMetadata dataGroup,
		  N5MultiScaleMetadata uniqueLabelsGroup,
		  N5SingleScaleMetadata fragmentSegmentAssignmentGroup,
		  N5MultiScaleMetadata labelToBlockLookupGroup
  ) {

	this(basePath, dataGroup, uniqueLabelsGroup, fragmentSegmentAssignmentGroup, labelToBlockLookupGroup, null, false);
  }

  public N5PainteraLabelMultiScaleGroup(
		  String basePath,
		  N5PainteraDataMultiScaleMetadata dataGroup,
		  N5MultiScaleMetadata uniqueLabelsGroup,
		  N5SingleScaleMetadata fragmentSegmentAssignmentGroup,
		  N5MultiScaleMetadata labelToBlockLookupGroup,
		  Long maxId,
		  Boolean isLabelMultisetType
  ) {

	super(basePath, dataGroup);
	this.uniqueLabelsGroup = uniqueLabelsGroup;
	this.fragmentSegmentAssignmentGroup = fragmentSegmentAssignmentGroup;
	this.labelToBlockLookupGroup = labelToBlockLookupGroup;
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
	  N5PainteraDataMultiScaleMetadata dataGroup = null;
	  //TODO Caleb; Determine which of these, if any, are required/optional
	  N5MultiScaleMetadata uniqueLabelsGroup = null;
	  N5SingleScaleMetadata fragmentSegmentAssignment = null;
	  N5MultiScaleMetadata labelToBlockLookupGroup = null;
	  for (final var child : node.childrenList()) {
		N5Metadata metadata = child.getMetadata();
		if (metadata instanceof N5SingleScaleMetadata && child.getNodeName().equals("fragment-segment-assignment")) {
		  fragmentSegmentAssignment = (N5SingleScaleMetadata)metadata;
		} else if (metadata instanceof N5PainteraDataMultiScaleMetadata) {
		  dataGroup = (N5PainteraDataMultiScaleMetadata)metadata;
		} else if (metadata instanceof N5MultiScaleMetadata) {
		  final N5MultiScaleMetadata painteraMultiMetadata = (N5MultiScaleMetadata)metadata;
		  switch (child.getNodeName()) {
		  case "unique-labels":
			uniqueLabelsGroup = painteraMultiMetadata;
			continue;
		  case "label-to-block-mapping":
			labelToBlockLookupGroup = painteraMultiMetadata;
		  }
		}
	  }
	  final var finalUniqueLabelsGroup = uniqueLabelsGroup;
	  final var finalFragmentSegmentAssignmentGroup = fragmentSegmentAssignment;
	  final var finalLabelToBlockLookupGroup = labelToBlockLookupGroup;

	  return Optional.ofNullable(dataGroup).map(dg -> new N5PainteraLabelMultiScaleGroup(
			  node.getPath(),
			  dg,
			  finalUniqueLabelsGroup, finalFragmentSegmentAssignmentGroup, finalLabelToBlockLookupGroup,
			  maxId, dg.getChildrenMetadata()[0].isLabelMultiset()
	  ));
	}
  }

  public MultiscaleMetadata<? extends N5SingleScaleMetadata> getUniqueLabelsGroupMetadata() {

	return uniqueLabelsGroup;
  }

  public N5SingleScaleMetadata getFragmentSegmentAssignmentMetadata() {

	return fragmentSegmentAssignmentGroup;
  }

  public MultiscaleMetadata<? extends N5SingleScaleMetadata> getLabelToBlockLookupGroupMetadata() {

	return labelToBlockLookupGroup;
  }

  public Long getMaxId() {

	return maxId;
  }
}
