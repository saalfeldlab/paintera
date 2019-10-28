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

	public boolean isValid()
	{
		for (final Map.Entry<K, N> entry : nodes.entrySet())
		{
			final K key = entry.getKey();
			final N node = entry.getValue();

			if (node.parentKey != null)
			{
				// validate parent
				if (!nodes.containsKey(node.parentKey) || !nodes.get(node.parentKey).children.contains(key))
					return false;
			}

			// validate children
			for (final K childKey : node.children)
				if (!nodes.containsKey(childKey) || !nodes.get(childKey).parentKey.equals(key))
					return false;
		}

		return true;
	}
}
