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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.janelia.saalfeldlab.n5.AbstractGsonReader;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GsonAttributesParser;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface N5GsonMetadataParser<T extends N5Metadata> extends N5MetadataParser<T> {

  /**
   * Called by the {@link org.janelia.saalfeldlab.util.n5.ij.N5DatasetDiscoverer}
   * while discovering the N5 tree and filling the metadata for datasets or
   * groups.
   * <p>
   * The metadata parsing is done in the bottom-up fashion, so the children of
   * the given {@code node} are parsed before parents and should already
   * contain valid metadata (if any) when parents are parsed.
   *
   * @param <R>     the type of parser
   * @param parser  the parser
   * @param dataset the dataset
   * @param map     map of json elements
   * @return the metadata
   * @throws Exception parsing exception
   */
  default <R extends AbstractGsonReader> Optional<T> parseMetadataGson(final R parser, final String dataset, final HashMap<String, JsonElement> map) {

	final HashMap<String, Object> objMap = new HashMap<String, Object>();
	final HashMap<String, Class<?>> typeMap = keysToTypes();
	objMap.put("dataset", dataset);

	try {
	  objMap.put("attributes", parseDatasetAttributesJson(map));
	} catch (final Exception e1) {
	}

	for (final String k : typeMap.keySet()) {
	  objMap.put(k, parser.getGson().fromJson(map.get(k), typeMap.get(k)));
	}
	return parseMetadata(objMap);
  }

  default Optional<T> parseMetadataGson(final String dataset, final HashMap<String, JsonElement> map) {

	final Gson gson = new GsonBuilder().create();
	final HashMap<String, Object> objMap = new HashMap<String, Object>();
	final HashMap<String, Class<?>> typeMap = keysToTypes();
	objMap.put("dataset", dataset);

	for (final String k : typeMap.keySet()) {
	  objMap.put(k, gson.fromJson(map.get(k), typeMap.get(k)));
	}
	return parseMetadata(objMap);
  }

  static DatasetAttributes parseDatasetAttributes(final HashMap<String, Object> map) {

	try {
	  final int[] blockSize = (int[])map.get("blockSize");
	  final long[] dimensions = (long[])map.get("dimensions");
	  final DataType dataType = (DataType)map.get("dataType");

	  // fix compression eventually
	  // final Compression compression = ( Compression ) map.get( "compression" );
	  return new DatasetAttributes(dimensions, blockSize, dataType, null);
	} catch (final Exception e) {
	}
	return null;
  }

  static DatasetAttributes parseDatasetAttributesJson(
		  final HashMap<String, JsonElement> map) {

	final Gson gson = new GsonBuilder().create();
	try {
	  final int[] blockSize = GsonAttributesParser.parseAttribute(map, "blockSize", int[].class, gson);
	  final long[] dimensions = GsonAttributesParser.parseAttribute(map, "dimensions", long[].class, gson);
	  final DataType dataType = DataType.fromString(GsonAttributesParser.parseAttribute(map, "dataType", String.class, gson));

	  // fix compression eventually...
	  // final Compression compression = GsonAttributesParser.parseAttribute( map, "compression", Compression.class, gson);

	  if (dimensions != null && dataType != null && dataType != null)
		return new DatasetAttributes(dimensions, blockSize, dataType, null);
	} catch (final IOException e) {
	}
	return null;
  }

  default <R extends AbstractGsonReader> Optional<T> parseMetadataGson(final R parser, final String dataset) {

	try {
	  HashMap<String, JsonElement> attributes = parser.getAttributes(dataset);
	  return parseMetadataGson(parser, dataset, attributes);
	} catch (Exception e) {
	  return Optional.empty();
	}
  }

  default <R extends AbstractGsonReader> Map<String, Object> parseMetadataGson(
		  final R n5, final String dataset, final Map<String, Class<?>> keys) {

	HashMap<String, JsonElement> map;
	try {
	  map = n5.getAttributes(dataset);
	} catch (final IOException e1) {
	  // empty map, or could be null?
	  return new HashMap<>();
	}

	final Gson gson = new GsonBuilder().create();
	final HashMap<String, Object> objMap = new HashMap<String, Object>();
	final HashMap<String, Class<?>> typeMap = keysToTypes();
	objMap.put("dataset", dataset);

	for (final String k : typeMap.keySet()) {
	  objMap.put(k, gson.fromJson(map.get(k), typeMap.get(k)));
	}

	return objMap;
  }

  default Map<String, Object> parseMetadata(
		  final N5Reader n5, final String dataset, final Map<String, Class<?>> keys) {

	final HashMap<String, Object> map = new HashMap<>();
	for (final String k : keys.keySet()) {
	  if (!map.containsKey(k))
		return null;

	  try {
		map.put(k, n5.getAttribute(dataset, k, keys.get(k)));
	  } catch (final IOException e) {
		return null;
	  }
	}

	return map;
  }

  @Override
  default Optional<T> parseMetadata(final N5Reader n5, final String dataset) {

	if (n5 instanceof AbstractGsonReader) {
	  return parseMetadataGson((AbstractGsonReader)n5, dataset);
	} else {
	  final Map<String, Object> keys = N5MetadataParser.parseMetadataStatic(n5, dataset, keysToTypes());
	  return parseMetadata(keys);
	}
  }

  @Override
  default Optional<T> parseMetadata(final N5Reader n5, final N5TreeNode node) {

	if (n5 instanceof AbstractGsonReader) {
	  return parseMetadataGson((AbstractGsonReader)n5, node.getPath());
	} else {
	  final Map<String, Object> keys = N5MetadataParser.parseMetadataStatic(n5, node.getPath(), keysToTypes());
	  return parseMetadata(keys);
	}
  }

}
