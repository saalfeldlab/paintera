package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.scene.Group
import net.imglib2.Interval
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey

interface PainteraMeshManager<Key> {

    val settings: MeshSettings

    val meshesGroup: Group

    fun refreshMeshes()
    fun createMeshFor(key: Key)
    fun removeMeshFor(key: Key)
    fun removeAllMeshes()

    interface GetBlockListFor<Key> {
        fun getBlocksFor(level: Int, key: Key): Array<Interval>?
    }

    interface GetMeshFor<Key> {
        fun getMeshFor(key: ShapeKey<Key>): PainteraTriangleMesh?
    }

}

//final InterruptibleFunction<T, Interval[]>[] blockListCache,
//final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
