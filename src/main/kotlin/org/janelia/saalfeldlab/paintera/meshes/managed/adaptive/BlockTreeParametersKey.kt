package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import org.janelia.saalfeldlab.paintera.meshes.MeshSettings

data class BlockTreeParametersKey(
	val levelOfDetail: Int,
	val coarsestScaleLevel: Int,
	val finestScaleLevel: Int
) {

	constructor(settings: MeshSettings) : this(
		settings.levelOfDetail,
		settings.coarsestScaleLevel,
		settings.finestScaleLevel
	)
}
