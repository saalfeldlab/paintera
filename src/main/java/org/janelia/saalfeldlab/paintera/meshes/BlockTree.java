package org.janelia.saalfeldlab.paintera.meshes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockTree<K, N extends BlockTreeNode<K>>
{
	public final Map<K, N> nodes = new HashMap<>();

	public Set<K> getLeafKeys()
	{
		final Set<K> leafKeys = new HashSet<>(nodes.keySet());
		nodes.values().forEach(node -> leafKeys.remove(node.parentKey));
		return leafKeys;
	}
}
