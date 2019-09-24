package org.janelia.saalfeldlab.paintera.meshes;

import java.util.HashMap;
import java.util.Map;

public class BlockTree<K, N extends BlockTreeNode<K>>
{
	public final Map<K, N> nodes = new HashMap<>();
}
