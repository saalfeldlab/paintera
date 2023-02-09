package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormatService
import org.scijava.AbstractGateway
import org.scijava.Context
import org.scijava.Gateway
import org.scijava.plugin.Plugin
import org.scijava.script.ScriptService
import org.scijava.service.Service

@Plugin(type = Gateway::class)
class PainteraGateway(context: Context) : AbstractGateway(Constants.NAME, context) {

	constructor() : this(Context(*DEFAULT_SERVICES))

	fun triangleMeshFormat() = this[TriangleMeshFormatService::class.java]

	val triangleMeshFormat: TriangleMeshFormatService
		get() = triangleMeshFormat()

	companion object {
		private val DEFAULT_SERVICES = arrayOf<Class<out Service>>(
			TriangleMeshFormatService::class.java,
			ScriptService::class.java
		)
	}

}
