package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

data class BlockTreeParametersKey(
    val levelOfDetail: Int,
    val coarsestScaleLevel: Int,
    val finestScaleLevel: Int)
