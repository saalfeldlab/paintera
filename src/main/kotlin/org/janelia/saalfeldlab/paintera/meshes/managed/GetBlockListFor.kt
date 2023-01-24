package org.janelia.saalfeldlab.paintera.meshes.managed

import net.imglib2.Interval

fun interface GetBlockListFor<Key> {
	fun getBlocksFor(level: Int, key: Key): Array<Interval>
}
