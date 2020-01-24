package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.scene.Group

interface PainteraMeshManager<Key> {

    // TODO should this be a group or a ObservableList<Node>?
    val meshesGroup: Group

    fun refreshMeshes()

}
