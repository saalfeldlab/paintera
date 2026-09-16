package org.janelia.saalfeldlab.paintera.control.modes

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyEvent.KEY_PRESSED
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.paintera.RawSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.IntensityThreshold
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

open class RawSourceMode : AbstractToolMode() {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()

	override val allowedActions = AllowedActions.NAVIGATION

	private val minMaxIntensityThreshold = painteraActionSet("Min/Max Intensity Threshold") {
		verifyAll(KEY_PRESSED, "Invalid Source State") { (activeSourceStateProperty.get() as? SourceState<*, RealType<*>>) != null }
		KEY_PRESSED(RawSourceStateKeys.RESET_MIN_MAX_INTENSITY_THRESHOLD) {
			createToolNode = { apply { styleClass += "intensity-reset-min-max" } }
			onAction {
				(activeSourceStateProperty.get() as? SourceState<*, RealType<*>>)?.let {
					IntensityThreshold.resetIntensityMinMax(it)
				}
			}
		}
		KEY_PRESSED(RawSourceStateKeys.AUTO_MIN_MAX_INTENSITY_THRESHOLD) {
			createToolNode = { apply { styleClass += "intensity-auto-min-max" } }
			onAction {
				val viewer = paintera.baseView.run {
					mostRecentFocusHolder.value ?: orthogonalViews().topLeft
				}.viewer()
				(activeSourceStateProperty.get() as? SourceState<*, RealType<*>>)?.let {
					IntensityThreshold.autoIntensityMinMax(it, viewer)
				}
			}
		}
	}

	override val activeViewerActions: List<ActionSet> = listOf(minMaxIntensityThreshold)
}
