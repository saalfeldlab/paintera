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

public class N5PainteraLabelMultiScaleGroup extends N5GenericMultiScaleMetadata<PainteraSourceMetadata> {

  public final String basePath;
  private final PainteraMultiscaleGroup<? extends PainteraSourceMetadata> dataGroup;
  private final PainteraMultiscaleGroup<? extends PainteraSourceMetadata> uniqueLabelsGroup;
  private final PainteraMultiscaleGroup<? extends PainteraSourceMetadata> fragmentSegmentAssignmentGroup;
  private final Long maxId;
  private final boolean isLabelMultisetType;

  public N5PainteraLabelMultiScaleGroup(
		  PainteraSourceMetadata[] childrenMetadata,
		  String basePath,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> dataGroup,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> uniqueLabelsGroup,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> fragmentSegmentAssignmentGroup
  ) {

	this(childrenMetadata, basePath, dataGroup, uniqueLabelsGroup, fragmentSegmentAssignmentGroup, null, false);
  }

  public N5PainteraLabelMultiScaleGroup(
		  PainteraSourceMetadata[] childrenMetadata,
		  String basePath,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> dataGroup,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> uniqueLabelsGroup,
		  PainteraMultiscaleGroup<? extends PainteraSourceMetadata> fragmentSegmentAssignmentGroup,
		  Long maxId,
		  Boolean isLabelMultisetType
  ) {

	super(childrenMetadata, basePath);
	this.basePath = basePath;
	this.dataGroup = dataGroup;
	this.uniqueLabelsGroup = uniqueLabelsGroup;
	this.fragmentSegmentAssignmentGroup = fragmentSegmentAssignmentGroup;
	this.maxId = maxId;
	this.isLabelMultisetType = isLabelMultisetType;
  }

  @Override public boolean isLabel() {

	return true;
  }

  @Override public boolean isLabelMultisetType() {

	return isLabelMultisetType;
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

	/*{"painteraData":{"type":"label"},"maxId":67}*/
	String painteraDataType = null;
	Long maxId = null;
	try {
	  final var painteraData = reader.getAttribute(node.getPath(), "painteraData", JsonObject.class);
	  if (painteraData == null) {
	    return Optional.empty();
	  } else {
		painteraDataType = Optional.ofNullable(painteraData.get("type")).map(JsonElement::getAsString).orElse(null);
		maxId = Optional.ofNullable(reader.getAttribute(node.getPath(), "maxId", Long.class)).orElse(null);
	  }
	} catch (IOException e) {
	  return Optional.empty();
	}

	if (!painteraDataType.equals("label")) {
	  return Optional.empty();
	}
	boolean allChildrenArePainteraCompliant = node.childrenList().stream()
			.filter(x -> !x.getPath().endsWith("label-to-block-mapping"))
			.map(N5TreeNode::getMetadata)
			.allMatch(PainteraMultiscaleGroup.class::isInstance);
	if (!allChildrenArePainteraCompliant) {
	  return Optional.empty();
	}
	boolean containsData = false;
	PainteraMultiscaleGroup<? extends PainteraSourceMetadata> dataGroup = null;
	PainteraMultiscaleGroup<? extends PainteraSourceMetadata> uniqueLabelsGroup = null;
	PainteraMultiscaleGroup<? extends PainteraSourceMetadata> fragmentSegmentAssignmentGroup = null;
	for (final var child : node.childrenList()) {
	  N5Metadata metadata = child.getMetadata();
	  if (metadata instanceof PainteraMultiscaleGroup) {
		final var painteraMultiMetadata = (PainteraMultiscaleGroup<? extends PainteraSourceMetadata>)metadata;
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
			  dataGroup.getChildrenMetadata(), node.getPath(),
			  dataGroup, uniqueLabelsGroup, fragmentSegmentAssignmentGroup,
			  maxId, dataGroup.isLabelMultisetType()));
	}
	return Optional.empty();
  }

  @Override public String getPath() {

	return basePath;
  }

  public PainteraMultiscaleGroup<? extends PainteraSourceMetadata> getDataGroupMetadata() {

	return dataGroup;
  }

  public PainteraMultiscaleGroup<? extends PainteraSourceMetadata> getUniqueLabelsGroupMetadata() {

	return uniqueLabelsGroup;
  }

  public PainteraMultiscaleGroup<? extends PainteraSourceMetadata> getFragmentSegmentAssignmentGroupMetadata() {

	return fragmentSegmentAssignmentGroup;
  }
}
