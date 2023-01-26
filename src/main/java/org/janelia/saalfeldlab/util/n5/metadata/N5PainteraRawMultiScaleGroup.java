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
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;

import java.io.IOException;
import java.util.Optional;

public class N5PainteraRawMultiScaleGroup extends N5PainteraDataMultiScaleGroup {

	public N5PainteraRawMultiScaleGroup(final String basePath, N5PainteraDataMultiScaleMetadata dataGroup) {

		super(basePath, dataGroup);
	}

	@Override
	public N5PainteraDataMultiScaleMetadata getDataGroupMetadata() {

		return dataGroup;
	}

	public static class PainteraRawMultiScaleParser implements N5MetadataParser<N5PainteraRawMultiScaleGroup> {

		/**
		 * Called by the {@link N5DatasetDiscoverer}
		 * while discovering the N5 tree and filling the metadata for datasets or groups.
		 *
		 * @param node the node
		 * @return the metadata
		 */
		@Override public Optional<N5PainteraRawMultiScaleGroup> parseMetadata(N5Reader n5, N5TreeNode node) {

			if (node.getMetadata() instanceof N5DatasetMetadata)
				return Optional.empty(); // we're a dataset, so not a multiscale group

			String painteraDataType = null;
			try {
				final var painteraData = n5.getAttribute(node.getPath(), "painteraData", JsonObject.class);
				if (painteraData == null) {
					return Optional.empty();
				} else {
					painteraDataType = Optional.ofNullable(painteraData.get("type")).map(JsonElement::getAsString).orElse(null);
				}
			} catch (IOException e) {
				return Optional.empty();
			}

			if (!"raw".equals(painteraDataType)) {
				return Optional.empty();
			}
			N5PainteraDataMultiScaleMetadata dataGroup = null;
			for (final var child : node.childrenList()) {
				N5Metadata metadata = child.getMetadata();
				if (metadata instanceof MultiscaleMetadata) {
					/* Children should either be data group, or N5MultiscaleMetadata */
					if (metadata instanceof N5PainteraDataMultiScaleMetadata) {
						dataGroup = (N5PainteraDataMultiScaleMetadata)metadata;
					}
				}
			}
			return Optional.ofNullable(dataGroup).map(dg -> new N5PainteraRawMultiScaleGroup(node.getPath(), dg));
		}
	}

}
