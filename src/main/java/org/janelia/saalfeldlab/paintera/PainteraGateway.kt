package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenu
import org.scijava.AbstractGateway
import org.scijava.Context
import org.scijava.Gateway
import org.scijava.plugin.Plugin
import org.scijava.service.Service

@Plugin(type = Gateway::class)
class PainteraGateway(context: Context) : AbstractGateway(Paintera.NAME, context) {

	constructor() : this(Context(*DEFAULT_SERVICES))

	fun openDialogMenu() = this[OpenDialogMenu::class.java]

	companion object {
		private val DEFAULT_SERVICES = arrayOf<Class<out Service>>(
				OpenDialogMenu::class.java
		)
	}

}
