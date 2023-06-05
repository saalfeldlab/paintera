package org.janelia.saalfeldlab.paintera.meshes;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class BlockTree<K, N extends BlockTreeNode<K>> {

	public final Map<K, N> nodes = new HashMap<>();

	/**
	 * Returns the node for the given key, or {@code null} if the node does not exist.
	 *
	 * @param key
	 * @return
	 */
	public N getNode(final K key) {

		return nodes.get(key);
	}

	/**
	 * Returns the parent node for the node with the given key, or {@code null} if the node does not have a parent.
	 *
	 * @param key
	 * @return
	 */
	public N getParentNode(final K key) {

		final N node = nodes.get(key);
		if (node == null || node.parentKey == null)
			return null;
		return nodes.get(node.parentKey);
	}

	/**
	 * Returns the children nodes for the node with the given key, or an empty set if the node does not have any children.
	 *
	 * @param key
	 * @return
	 */
	public Set<N> getChildrenNodes(final K key) {

		final Set<N> childrenNodes = new HashSet<>();
		final N node = nodes.get(key);
		if (node != null)
			node.children.forEach(childKey -> childrenNodes.add(nodes.get(childKey)));
		return childrenNodes;
	}

	/**
	 * Returns a set of root nodes in the tree.
	 *
	 * @return
	 */
	public Set<K> getRootKeys() {

		return nodes.keySet().stream().filter(this::isRoot).collect(Collectors.toSet());
	}

	/**
	 * Returns a set of leaf nodes in the tree.
	 *
	 * @return
	 */
	public Set<K> getLeafKeys() {

		return nodes.keySet().stream().filter(this::isLeaf).collect(Collectors.toSet());
	}

	/**
	 * Checks if the node with the given key is a root node (that does not have a parent node).
	 *
	 * @param key
	 * @return
	 */
	public boolean isRoot(final K key) {

		final N node = nodes.get(key);
		return node != null && node.parentKey == null;
	}

	/**
	 * Checks if the node with the given key is a leaf node (that does not have any children).
	 *
	 * @param key
	 * @return
	 */
	public boolean isLeaf(final K key) {

		final N node = nodes.get(key);
		return node != null && node.children.isEmpty();
	}

	/**
	 * Iterates the subtree in the breadth-first order starting from the node with the given key.
	 *
	 * @param key
	 * @param action should return {@code true} if needed to continue the search in the subtree of the current node
	 */
	public void traverseSubtree(final K key, final BiPredicate<K, N> action) {

		traverseSubtree(key, action, false);
	}

	/**
	 * Iterates the subtree in the breadth-first order starting from the children of the node with the given key.
	 *
	 * @param key
	 * @param action should return {@code true} if needed to continue the search in the subtree of the current node
	 */
	public void traverseSubtreeSkipRoot(final K key, final BiPredicate<K, N> action) {

		traverseSubtree(key, action, true);
	}

	private void traverseSubtree(final K key, final BiPredicate<K, N> action, final boolean skipRoot) {

		final Queue<K> childrenQueue = new ArrayDeque<>(skipRoot ? nodes.get(key).children : Collections.singleton(key));
		while (!childrenQueue.isEmpty()) {
			final K childKey = childrenQueue.poll();
			final N childNode = nodes.get(childKey);
			if (action.test(childKey, childNode))
				childrenQueue.addAll(childNode.children);
		}
	}

	/**
	 * Iterates through the ancestors of the node with the given key starting from this node.
	 *
	 * @param key
	 * @param action
	 */
	public void traverseAncestors(final K key, final BiConsumer<K, N> action) {

		K currentKey = key;
		while (currentKey != null) {
			final N node = nodes.get(currentKey);
			action.accept(currentKey, node);
			currentKey = node.parentKey;
		}
	}
}
