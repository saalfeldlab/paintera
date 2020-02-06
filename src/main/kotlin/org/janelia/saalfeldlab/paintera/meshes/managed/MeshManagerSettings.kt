package org.janelia.saalfeldlab.paintera.meshes.managed

import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleLongProperty
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig

class MeshManagerSettings {

    private val _meshesEnabled: BooleanProperty = SimpleBooleanProperty(true)
    var isMeshesEnabled: Boolean
        get() = _meshesEnabled.get()
        set(meshesEnabled) = _meshesEnabled.set(meshesEnabled)
    fun meshesEnabledProperty() = _meshesEnabled

    private val _showBlockBoundaries: BooleanProperty = SimpleBooleanProperty(false)
    var isShowBlockBounadries: Boolean
        get() = _showBlockBoundaries.get()
        set(showBlockBoundaries) = _showBlockBoundaries.set(showBlockBoundaries)
    fun showBlockBoundariesProperty() = _showBlockBoundaries

    private val _blockSize: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE)
    var blockSize: Int
        get() = _blockSize.value
        set(blockSize) = _blockSize.set(blockSize)
    fun blockSizeProperty() = _blockSize

    private val _numElementsPerFrame: IntegerProperty = SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE)
    var numElementsPerFrame: Int
        get() = _numElementsPerFrame.get()
        set(numElementsPerFrame) = _numElementsPerFrame.set(numElementsPerFrame)
    fun numElementsPerFrameProperty() = _numElementsPerFrame

    private val _frameDelayMsec: LongProperty = SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE)
    var frameDelayMsec: Long
        get() = _frameDelayMsec.get()
        set(delayMsec) = _frameDelayMsec.set(delayMsec)
    fun frameDelayMsecProperty() = _frameDelayMsec

    private val _sceneUpdateDelayMsec: LongProperty = SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE)
    var sceneUpdateDelayMsec: Long
        get() = _sceneUpdateDelayMsec.get()
        set(delayMsec) = _sceneUpdateDelayMsec.set(delayMsec)
    fun sceneUpdateDelayMsecProperty() = _sceneUpdateDelayMsec
}
