package org.janelia.saalfeldlab.paintera.state

import javafx.beans.binding.ObjectBinding
import net.imglib2.Volatile
import net.imglib2.type.logic.BoolType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor

interface IntersectableSourceState<D, T, K : MeshCacheKey> : SourceState<D, T> {

    fun getIntersectableMask(): DataSource<BoolType, Volatile<BoolType>>

    fun getMeshCacheKeyBinding(): ObjectBinding<K>

    fun getGetBlockListFor(): GetBlockListFor<K>

}
