package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormatService
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenu
import org.scijava.AbstractGateway
import org.scijava.Context
import org.scijava.Gateway
import org.scijava.plugin.Plugin
import org.scijava.script.ScriptService
import org.scijava.service.Service

@Plugin(type = Gateway::class)
class PainteraGateway(context: Context) : AbstractGateway(Paintera2.Constants.NAME, context) {

	constructor() : this(Context(*DEFAULT_SERVICES))

	fun openDialogMenu() = this[OpenDialogMenu::class.java]

	val openDialogMenu: OpenDialogMenu
		get() = openDialogMenu()

	fun triangleMeshFormat() = this[TriangleMeshFormatService::class.java]

	val triangleMeshFormat: TriangleMeshFormatService
		get() = triangleMeshFormat()

	companion object {
		private val DEFAULT_SERVICES = arrayOf<Class<out Service>>(
				OpenDialogMenu::class.java,
				TriangleMeshFormatService::class.java,
				ScriptService::class.java
		)
	}

}
