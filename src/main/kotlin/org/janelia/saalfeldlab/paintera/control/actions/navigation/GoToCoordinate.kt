package org.janelia.saalfeldlab.paintera.control.actions.navigation

import bdv.viewer.Source
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Duration
import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.onAction
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts


object GoToCoordinate : MenuAction("_Go to Coordinate...") {

	init {
		verifyPermission(NavigationActionType.Pan)
		onAction(::GoToCoordinateState) { showDialog(it) }
	}

	private fun GoToCoordinateState.showDialog(event: Event?) {
		initializeWithCurrentCoordinates()
		PainteraAlerts.confirmation("Go", "Cancel", true).apply {
			isResizable = true
			Paintera.registerStylesheets(dialogPane)
			title = name?.replace("_", "")
			headerText = "Go To Source Coordinate"

			dialogPane.content = GoToCoordinateUI(this@showDialog)
		}.showAndWait().takeIf { it.nullable == ButtonType.OK }?.run {
			goToCoordinates(xProperty.value, yProperty.value, zProperty.value)
		}
	}

	private fun GoToCoordinateState.goToCoordinates(x: Double, y: Double, z: Double) {
		goToCoordinates(source, viewer, translationController, x, y, z)
	}
}

internal fun goToCoordinates(
	source: Source<*>,
	viewer: ViewerPanelFX,
	translationController: TranslationController,
	x: Double, y: Double, z: Double
) {

	val sourceToGlobalTransform = AffineTransform3D().also { source.getSourceTransform(viewer.state.timepoint, 0, it) }
	val currentSourceCoordinate = RealPoint(3).also {
		viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, it)
	}

	val sourceDeltaX = x - currentSourceCoordinate.getDoublePosition(0)
	val sourceDeltaY = y - currentSourceCoordinate.getDoublePosition(1)
	val sourceDeltaZ = z - currentSourceCoordinate.getDoublePosition(2)

	val viewerCenterInSource = RealPoint(3)
	viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, viewerCenterInSource)

	val newViewerCenter = RealPoint(3)
	viewer.sourceToDisplayCoordinates(
		viewerCenterInSource.getDoublePosition(0) + sourceDeltaX,
		viewerCenterInSource.getDoublePosition(1) + sourceDeltaY,
		viewerCenterInSource.getDoublePosition(2) + sourceDeltaZ,
		sourceToGlobalTransform,
		newViewerCenter
	)


	val deltaX = viewer.width / 2.0 - newViewerCenter.getDoublePosition(0)
	val deltaY = viewer.height / 2.0 - newViewerCenter.getDoublePosition(1)
	val deltaZ = 0 - newViewerCenter.getDoublePosition(2)

	translationController.translate(deltaX, deltaY, deltaZ, Duration(300.0))


}

fun main() {
	InvokeOnJavaFXApplicationThread {

		val state = object : GoToCoordinateUIState {
			override val xProperty = SimpleDoubleProperty()
			override val yProperty = SimpleDoubleProperty()
			override val zProperty = SimpleDoubleProperty()
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

