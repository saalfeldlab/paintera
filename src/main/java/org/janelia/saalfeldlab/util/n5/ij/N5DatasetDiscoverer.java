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
package org.janelia.saalfeldlab.util.n5.ij;

import com.google.gson.JsonElement;
import org.janelia.saalfeldlab.n5.AbstractGsonReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.util.n5.metadata.N5GsonMetadataParser;
import org.janelia.saalfeldlab.util.n5.metadata.N5Metadata;
import se.sawano.java.text.AlphanumericComparator;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class N5DatasetDiscoverer {

  @SuppressWarnings("rawtypes")
  private final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers;
  private final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers;

  private final Comparator<? super String> comparator;

  private final Predicate<N5TreeNode> filter;

  private final ExecutorService executor;

  private N5TreeNode root;

  private final HashMap<String, N5Metadata> metadataMap;

  private String groupSeparator;

  private N5Reader n5;

  /**
   * Creates an N5 discoverer with alphanumeric sorting order of groups/datasets (such as, s9 goes before s10).
   *
   * @param executor        the executor
   * @param groupParsers    group parsers
   * @param metadataParsers metadata parsers
   */
  @SuppressWarnings("rawtypes")
  public N5DatasetDiscoverer(final ExecutorService executor,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers) {

	this(executor,
			Optional.of(new AlphanumericComparator(Collator.getInstance())),
			null,
			groupParsers,
			metadataParsers);
  }

  /**
   * Creates an N5 discoverer with alphanumeric sorting order of groups/datasets (such as, s9 goes before s10).
   *
   * @param groupParsers    group parsers
   * @param metadataParsers metadata parsers
   */
  @SuppressWarnings("rawtypes")
  public N5DatasetDiscoverer(
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers) {

	this(Executors.newSingleThreadExecutor(),
			Optional.of(new AlphanumericComparator(Collator.getInstance())),
			null,
			groupParsers,
			metadataParsers);
  }

  @SuppressWarnings("rawtypes")
  public N5DatasetDiscoverer(
		  final ExecutorService executor,
		  final Predicate<N5TreeNode> filter,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers) {

	this(executor,
			Optional.of(new AlphanumericComparator(Collator.getInstance())),
			filter,
			groupParsers,
			metadataParsers);
  }

  @SuppressWarnings("rawtypes")
  public N5DatasetDiscoverer(
		  final ExecutorService executor,
		  final Optional<Comparator<? super String>> comparator,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers) {

	this(executor, comparator, null, groupParsers, metadataParsers);
  }

  /**
   * Creates an N5 discoverer.
   * <p>
   * If the optional parameter {@code comparator} is specified, the groups and datasets
   * will be listed in the order determined by this comparator.
   *
   * @param executor        the executor
   * @param comparator      optional string comparator
   * @param filter          the dataset filter
   * @param groupParsers    group parsers
   * @param metadataParsers metadata parsers
   */
  @SuppressWarnings("rawtypes")
  public N5DatasetDiscoverer(
		  final ExecutorService executor,
		  final Optional<Comparator<? super String>> comparator,
		  final Predicate<N5TreeNode> filter,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers) {

	this.executor = executor;
	this.comparator = comparator.orElseGet(null);
	this.filter = filter;
	this.groupParsers = groupParsers;
	this.metadataParsers = metadataParsers;

	metadataMap = new HashMap<>();
  }

  /**
   * A method usable as a {@link Predicate} to a {@link N5Reader#deepList} call,
   * that returns only datasets with metadata parsable with one of this object's
   * metadata parsers.
   * <p>
   * Adds the parsed metadata to this object's metadataMap to avoid parsing wtice
   *
   * @param path the dataset path
   */
  public void metadataParserRecursive(final N5TreeNode node) {
	/* depth first, check if we have children */
	List<N5TreeNode> children = node.childrenList();
	if (!children.isEmpty()) {
	  for (final var child : children) {
		metadataParserRecursive(child);
	  }
	}

	try {
	  N5DatasetDiscoverer.parseMetadata(n5, node, metadataParsers, groupParsers);
	} catch (IOException e) {
	}
	N5Metadata metadata = node.getMetadata();
	metadataMap.put(node.getPath(), metadata);
  }

  /**
   * Recursively discovers and parses metadata for datasets that are children
   * of the given base path using {@link N5Reader#deepList}. Returns an {@link N5TreeNode}
   * that can be displayed as a JTree.
   *
   * @param n5   the n5 reader
   * @param base the base path
   * @return the n5 tree node
   * @throws IOException the io exception
   */
  public N5TreeNode discoverRecursive(final N5Reader n5, final String base) throws IOException {

	metadataMap.clear();
	this.n5 = n5;
	groupSeparator = n5.getGroupSeparator();

	root = new N5TreeNode(base);
	String[] datasetPaths;
	try {

	  final String groupSeparator = n5.getGroupSeparator();
	  final String normalPathName = base.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
	  final ArrayList<String> results = new ArrayList<>();
	  final LinkedBlockingQueue<Future<String>> datasetFutures = new LinkedBlockingQueue<>();
	  localDeepListHelper(n5, normalPathName, executor, datasetFutures);

	  datasetFutures.poll().get(); // skip self
	  while (!datasetFutures.isEmpty() && !executor.isShutdown()) {
		/* FIXME; if we are shutdown, should we return what we have, or nothing?*/
		final String result = datasetFutures.poll().get();
		if (result != null)
		  results.add(result.substring(normalPathName.length() + groupSeparator.length()));
	  }
	  datasetPaths = results.stream().toArray(String[]::new);
	  buildNodes(root, datasetPaths);
	  sortAndTrimRecursive(root);
	  this.metadataParserRecursive(root);
	} catch (Exception e) {
	  e.printStackTrace();
	}

	//	parseGroupsRecursive(root, groupParsers);

	return root;
  }

  static void localDeepListHelper(
		  final N5Reader n5,
		  final String path,
		  final ExecutorService executor,
		  final LinkedBlockingQueue<Future<String>> datasetFutures) {

	final String groupSeparator = n5.getGroupSeparator();

	Future<String> future = executor.submit(() -> {
	  try {
		String[] children = n5.list(path);
		for (final String child : children) {
		  final String fullChildPath = path + groupSeparator + child;
		  localDeepListHelper(n5, fullChildPath, executor, datasetFutures);
		}
	  } catch (final IOException ignored) {
	  }
	  System.out.println("found: " + path);
	  return path;
	});

	datasetFutures.add(future);
  }

  /**
   * Generates a tree based on {@link N5Reader#deepList}, modifying the passed {@link N5TreeNode}.
   *
   * @param rootNode    root node
   * @param parsedPaths the result of deepList
   */
  private void buildNodes(final N5TreeNode rootNode, final String[] parsedPaths) {

	final HashMap<String, N5TreeNode> pathToNode = new HashMap<>();

	String normalBase = normalPathName(rootNode.getPath(), groupSeparator);
	pathToNode.put(normalBase, rootNode);

	for (final String datasetPath : parsedPaths) {
	  final String fullPath = normalBase + groupSeparator + datasetPath;
	  final N5TreeNode node = new N5TreeNode(fullPath);
	  node.setMetadata(metadataMap.get(fullPath));
	  pathToNode.put(node.getPath(), node);
	  add(normalBase, node, pathToNode, groupSeparator);
	}
  }

  /**
   * Add the node to its parent, creating parents recursively if needed.
   *
   * @param basePath       the path
   * @param node           the n5 node
   * @param pathToNodeMap  map from paths to nodes
   * @param groupSeparator the separator character
   */
  private static void add(final String basePath,
		  final N5TreeNode node,
		  final HashMap<String, N5TreeNode> pathToNodeMap,
		  final String groupSeparator) {

	final String fullPath = node.getPath();
	/* if we are the basePath, skip */
	if (fullPath.equals(basePath))
	  return;

	final String parentPath = fullPath.substring(0, fullPath.lastIndexOf(groupSeparator));
	final var parentNode = Optional.ofNullable(pathToNodeMap.get(parentPath)).orElseGet(() -> {
	  /* create the parent*/
	  final var newParentNode = new N5TreeNode(parentPath);
	  /* add the parent to the map*/
	  pathToNodeMap.put(parentPath, newParentNode);
	  // if the parent's parent is not in the map, handle that.
	  add(basePath, newParentNode, pathToNodeMap, groupSeparator);
	  return newParentNode;
	});

	// add the node as a child
	parentNode.add(node);
  }

  private static String normalPathName(final String fullPath, String groupSeparator) {

	return fullPath.replaceAll("(^" + groupSeparator + "*)|(" + groupSeparator + "*$)", "");
  }

  public N5TreeNode parse(final N5Reader n5, final String dataset) throws IOException {

	final N5TreeNode node = new N5TreeNode(dataset);
	parseMetadata(n5, node, metadataParsers, null);
	return node;
  }

  public void parseGroupsRecursive(final N5TreeNode node, BiFunction<N5Reader, N5TreeNode, ? extends N5Metadata>[] groupParsers) {

	parseGroupsRecursive(node, this.groupParsers);
  }

  public void sortAndTrimRecursive(final N5TreeNode node) {

	trim(node);
	if (comparator != null)
	  sort(node, comparator);

	for (final N5TreeNode c : node.childrenList())
	  sortAndTrimRecursive(c);
  }

  public void filterRecursive(final N5TreeNode node) {

	if (filter == null)
	  return;

	if (!filter.test(node))
	  node.setMetadata(null);

	for (final N5TreeNode c : node.childrenList())
	  filterRecursive(c);
  }

  public static void parseMetadata(final N5Reader n5, final N5TreeNode node,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers) throws IOException {

	HashMap<String, JsonElement> jsonMap = null;
	if (n5 instanceof AbstractGsonReader) {
	  jsonMap = ((AbstractGsonReader)n5).getAttributes(node.getPath());
	  if (jsonMap == null) {
		return;
	  }
	}

	// Go through all parsers to populate metadata
	for (final var parser : metadataParsers) {
	  try {
		Optional<? extends N5Metadata> parsedMeta;
		if (jsonMap != null && parser instanceof N5GsonMetadataParser) {
		  parsedMeta = ((N5GsonMetadataParser<?>)parser).parseMetadataGson(node.getPath(), jsonMap);
		} else
		  parsedMeta = parser.apply(n5, node);

		parsedMeta.ifPresent(node::setMetadata);
		if (parsedMeta.isPresent())
		  break;
	  } catch (final Exception ignored) {
	  }
	}

	if ((node.getMetadata() == null) && groupParsers != null) {
	  // this is not a dataset but may be a group (e.g. multiscale pyramid)
	  // try to parse groups
	  for (final var gp : groupParsers) {
		final Optional<? extends N5Metadata> groupMeta = gp.apply(n5, node);
		groupMeta.ifPresent(node::setMetadata);
		if (groupMeta.isPresent())
		  break;
	  }
	}
  }

  public static void parseMetadataRecursive(final N5Reader n5, final N5TreeNode node,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> metadataParsers,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers) throws IOException {
	// Recursively parse metadata for children nodes
	for (final N5TreeNode childNode : node.childrenList())
	  parseMetadataRecursive(n5, childNode, metadataParsers, groupParsers);

	// this parses groups as well
	parseMetadata(n5, node, metadataParsers, groupParsers);
  }

  public void parseGroupsRecursive(
		  final N5TreeNode node,
		  final List<BiFunction<N5Reader, N5TreeNode, Optional<? extends N5Metadata>>> groupParsers) {

	if (groupParsers == null)
	  return;

	if (node.getMetadata() == null) {
	  // this is not a dataset but may be a group (e.g. multiscale pyramid)
	  // try to parse groups
	  for (final var gp : groupParsers) {

		final var groupMeta = gp.apply(n5, node);
		groupMeta.ifPresent(node::setMetadata);
		if (groupMeta.isPresent()) {
		  break;
		}
	  }
	}

	for (final N5TreeNode c : node.childrenList())
	  parseGroupsRecursive(c, groupParsers);
  }

  /**
   * Removes branches of the N5 container tree that do not contain any nodes that can be opened
   * (nodes with metadata).
   *
   * @param node the node
   * @return {@code true} if the branch contains a node that can be opened, {@code false} otherwise
   */
  private static boolean trim(final N5TreeNode node) {

	//FIXME if this is kept in n5-ij, make sure the concurrent modification is resolved
	final List<N5TreeNode> children = List.copyOf(node.childrenList());
	if (children.isEmpty() && !node.isDataset()) {
	  return true;
	}
	if (node.getMetadata() == null) {
	  return false;
	}

	var trim = true;
	for (N5TreeNode child : children) {
	  if (trim(child)) {
		node.remove(child);
	  } else {
		trim = false;
	  }
	}
	return trim;
  }

  private static void sort(final N5TreeNode node, final Comparator<? super String> comparator) {

	final List<N5TreeNode> children = node.childrenList();
	children.sort(Comparator.comparing(N5TreeNode::toString, comparator));

	for (final N5TreeNode childNode : children)
	  sort(childNode, comparator);
  }
}
