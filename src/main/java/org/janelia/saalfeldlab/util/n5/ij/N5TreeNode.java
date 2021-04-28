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

import org.janelia.saalfeldlab.util.n5.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.util.n5.metadata.N5GroupParser;
import org.janelia.saalfeldlab.util.n5.metadata.N5Metadata;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class N5TreeNode // extends DefaultMutableTreeNode
{

  private final String path;

  private N5Metadata metadata;

  private final ArrayList<N5TreeNode> children;

  public N5TreeNode(final String path) {

	this.path = path;
	children = new ArrayList<>();
  }

  public static Stream<N5TreeNode> flattenN5Tree(N5TreeNode root) {

	return Stream.concat(
			Stream.of(root),
			root.childrenList().stream().flatMap(N5TreeNode::flattenN5Tree));
  }

  public String getNodeName() {

	return Paths.get(removeLeadingSlash(path)).getFileName().toString();
  }

  public void add(final N5TreeNode child) {

	children.add(child);
  }

  public void remove(final N5TreeNode child) {

	children.remove(child);
  }

  public void removeAllChildren() {

	children.clear();
  }

  public List<N5TreeNode> childrenList() {

	return children;
  }

  public boolean isDataset() {

	return Optional.ofNullable(getMetadata()).map(N5DatasetMetadata.class::isInstance).orElse(false);
  }

  public void setMetadata(final N5Metadata metadata) {

	this.metadata = metadata;
  }

  public N5Metadata getMetadata() {

	return metadata;
  }

  public String getPath() {

	return path;
  }

  @Override
  public String toString() {

	final String nodeName = getNodeName();
	return nodeName.isEmpty() ? "/" : nodeName;
  }

  public String printRecursive() {

	return printRecursiveHelper(this, "");
  }

  private static String printRecursiveHelper(N5TreeNode node, String prefix) {

	StringBuffer out = new StringBuffer();
	out.append(prefix + node.path + "\n");
	for (N5TreeNode c : node.childrenList()) {
	  System.out.println(c.path);
	  out.append(printRecursiveHelper(c, prefix + " "));
	}

	return out.toString();
  }

  /**
   * Removes the leading slash from a given path and returns the corrected path.
   * It ensures correctness on both Unix and Windows, otherwise {@code pathName} is treated
   * as UNC path on Windows, and {@code Paths.get(pathName, ...)} fails with {@code InvalidPathException}.
   *
   * @param pathName the path
   * @return the corrected path
   */
  protected static String removeLeadingSlash(final String pathName) {

	return pathName.startsWith("/") || pathName.startsWith("\\") ? pathName.substring(1) : pathName;
  }

  public List<N5TreeNode> groups() {

	return groups(N5GroupParser.class);
  }

  public <T extends N5GroupParser<?>> List<N5TreeNode> groups(Class<T> groupParser) {

	return flattenN5Tree(this).filter(node -> groupParser.isInstance(node.getMetadata())).collect(Collectors.toList());
  }
}
