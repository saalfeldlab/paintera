package org.janelia.saalfeldlab.paintera.util.debug

import com.sun.javafx.perf.PerformanceTracker
import javafx.animation.AnimationTimer
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.paintera.ui.hvGrow
import org.janelia.saalfeldlab.paintera.ui.vGrow
import kotlin.math.pow

internal class DebugNodes : HBox() {

	private val vbox: VBox = VBox().hGrow {
		isFillWidth = true
	}

	init {
		isVisible = Paintera.debugMode
		vGrow(Priority.NEVER)
		isFillHeight = true
		children += vbox

		DebugNode.entries.forEach { debugNode ->
			vbox.children += debugNode.debugNode()
		}
	}
}

internal enum class DebugNode(val debugNode: () -> Node) {
	PrimarySceneFps({ FpsTimers() });
}

internal class FpsTimers() : VBox() {

	init {
		Window.getWindows().subscribe {
			children.clear()
			children += Window.getWindows().mapNotNull { it.scene }.map { FpsTimer(it) }
		}
	}
}

internal class FpsTimer(scene: Scene) : HBox() {

	init {

		val averageFps = Label()
		val instantFps = Label()

		val tracker = PerformanceTracker.getSceneTracker(scene)
		val timer: AnimationTimer = object : AnimationTimer() {

			val nanosPerSecond = 10.0.pow(9.0)
			val averageWindow = 3 * nanosPerSecond
			var prevAvgTimeStamp = 0L

			override fun handle(now: Long) {
				val nanoDiff = now - prevAvgTimeStamp
				if (nanoDiff > averageWindow) {
					averageFps.text = "Average frame rate: ${tracker.averageFPS} fps"
					tracker.resetAverageFPS()
					prevAvgTimeStamp = now
				}
				instantFps.text = "Instantaneous frame rate: ${tracker.instantFPS} fps"
			}
		}

		visibleProperty().subscribe { it ->
			if (it) {
				timer.start()
			} else {
				timer.stop()
			}
		}
		hvGrow {
			children += averageFps.hGrow {
				maxWidth = Double.MAX_VALUE
			}
			children += instantFps.hGrow {
				alignment = Pos.CENTER_RIGHT
				maxWidth = Double.MAX_VALUE
			}
		}
	}
}