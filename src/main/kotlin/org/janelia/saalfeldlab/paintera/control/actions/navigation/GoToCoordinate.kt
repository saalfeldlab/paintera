package org.janelia.saalfeldlab.paintera.control.actions.navigation

import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import kotlin.jvm.optionals.getOrNull


object GoToCoordinate : MenuAction("_Go to Coordinate...") {

	init {
		verifyPermission(NavigationActionType.Pan)
		onActionWithState<GoToSourceCoordinateState> {
			initializeCurrentCoordinates()
			when (getDialog("Go To Source Coordinate").showAndWait().getOrNull()) {
				ButtonType.OK -> {
					translateToCoordinate(xProperty?.property?.value, yProperty?.property?.value, zProperty?.property?.value)
					updateSlicePositions()
				}
			}
		}
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {

		val state = object : GoToCoordinateModel {
			override val xProperty = DoublePositionProperty("X", 0.0)
			override val yProperty = DoublePositionProperty("Y", 0.0)
			override val zProperty = DoublePositionProperty("Z", 0.0)

			override val positionProperties: List<PositionProperty<*>> = listOf(
				xProperty,
				yProperty,
				zProperty,
				LongPositionProperty("Long", 0L)
			)

		}
		val root = VBox()
		root.apply {
			children += Button("Reload").apply {
				onAction = EventHandler {
					root.children.removeIf { it is GoToCoordinateUI }
					root.children.add(GoToCoordinateUI(state))
				}
			}
			children += GoToCoordinateUI(state)
		}

		val scene = Scene(root)
		val stage = Stage()
		stage.scene = scene
		stage.show()
	}
}

