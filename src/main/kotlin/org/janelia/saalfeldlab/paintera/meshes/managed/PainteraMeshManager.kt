package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.scene.Group
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings

interface PainteraMeshManager<Key> {

    val settings: MeshSettings

    // TODO should this be a group or a ObservableList<Node>?
    val meshesGroup: Group

    fun refreshMeshes()

}
