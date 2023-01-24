package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Set;

public class BlockTreeNode<K> {

	public final K parentKey;
	public final Set<K> children;
	public double distanceFromCamera;

	public BlockTreeNode(final K parentKey, final Set<K> children, final double distanceFromCamera) {

		this.parentKey = parentKey;
		this.children = children;
		this.distanceFromCamera = distanceFromCamera;
	}

	@Override
	public String toString() {

		return String.format("[parentExists=%b, numChildren=%d, distanceFromCamera=%.5f]", parentKey != null, children.size(), distanceFromCamera);
	}
}
