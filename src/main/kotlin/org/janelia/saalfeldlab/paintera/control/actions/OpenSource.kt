package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.event.EventHandler
import javafx.scene.input.KeyEvent.KEY_PRESSED
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.control.actions.OpenSourceModel.Companion.getDialog
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import kotlin.jvm.optionals.getOrNull

private val LOG = KotlinLogging.logger {}

object OpenSource : MenuAction("_Open Source...") {

	init {
		verifyPermission(MenuActionType.AddSource)
		onActionWithState<OpenSourceActionState> {
			try {
				val dialog = getDialog()

				dialog.onCloseRequest = EventHandler {
					if (isBusyProperty.get()) {
						cancelParsing()
						isBusyProperty.set(false)
						it.consume()
					}
				}
				dialog.showAndWait().getOrNull() ?: run {
					cancelParsing()
					return@onActionWithState
				}
				addSource()
			} catch (e: Exception) {
				LOG.warn(e) {
					"Unable to add source from ${containerState?.uri} and dataset ${activeNode?.path}"
				}
				Exceptions.exceptionAlert(Constants.NAME, "Unable to add source", e).apply {
					initAppDialog()
					paintera.baseView.node.scene?.window?.also { initOwner(it) }
					show()
				}
			}
		}
	}

	fun actionSet() = painteraActionSet("Open dataset", MenuActionType.AddSource) {
		KEY_PRESSED(PainteraBaseKeys.namedCombinationsCopy(), PainteraBaseKeys.OPEN_SOURCE) {
			onAction { OpenSource() }
		}
	}
}
