package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import org.janelia.saalfeldlab.fx.extensions.getValue
import org.janelia.saalfeldlab.fx.extensions.setValue
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig

class MeshManagerModel {

    val meshesEnabledProperty = SimpleBooleanProperty(this, "mesh-manager-model", true)
    val showBlockBoundariesProperty = SimpleBooleanProperty(false)
    val blockSizeProperty = SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE)
    val numElementsPerFrameProperty = SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE)
    val frameDelayMsecProperty = SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE)
    val sceneUpdateDelayMsecProperty = SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE)


    var isMeshesEnabled: Boolean by meshesEnabledProperty
    var isShowBlockBounadries: Boolean by showBlockBoundariesProperty
    var blockSize: Int by blockSizeProperty
    var numElementsPerFrame: Int by numElementsPerFrameProperty
    var frameDelayMsec: Long by frameDelayMsecProperty
    var sceneUpdateDelayMsec: Long by sceneUpdateDelayMsecProperty


}
