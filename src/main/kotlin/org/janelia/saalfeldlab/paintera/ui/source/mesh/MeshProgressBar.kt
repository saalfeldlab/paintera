package org.janelia.saalfeldlab.paintera.ui.source.mesh

import javafx.animation.AnimationTimer
import javafx.css.PseudoClass
import javafx.scene.control.Tooltip
import javafx.util.Subscription
import org.controlsfx.control.StatusBar
import org.janelia.saalfeldlab.paintera.meshes.MeshProgressState

class MeshProgressBar(private val updateIntervalMsec: Long = UPDATE_INTERVAL_MSEC) : StatusBar() {
	private val statusToolTip = Tooltip()
	private var meshProgress: MeshProgressState? = null
	private var progressBarUpdater: AnimationTimer? = null

	init {
		tooltip = statusToolTip
		setCssProperties()
	}

	private fun setCssProperties() {
		styleClass += "mesh-status-bar"
		val complete = PseudoClass.getPseudoClass("complete")
		progressProperty().subscribe { progress ->
			pseudoClassStateChanged(complete, !(progress.toDouble() < 1.0))
		}
	}

	fun bindTo(meshProgress: MeshProgressState) {
		unbind()
		this.meshProgress = meshProgress

		progressBarUpdater = createAnimationTimer(meshProgress)
	}

	private fun createAnimationTimer(meshProgress: MeshProgressState): AnimationTimer {

		return object : AnimationTimer() {
			private var subscription: Subscription? = null
			var lastUpdate: Long = -1L
			var handleUpdate: Boolean = false

			init {
				start()
				subscription = meshProgress.totalNumTasksProperty.subscribe { it ->
					val numTasks = it.toInt()
					handleUpdate = numTasks > 0 && meshProgress.numCompletedTasks < numTasks
				}
			}

			override fun stop() {
				super.stop()
				subscription?.unsubscribe()
				subscription = null
			}

			override fun handle(now: Long) {

				if (handleUpdate && now - lastUpdate > updateIntervalMsec) {
					lastUpdate = now
					val numTotalTasks = meshProgress.numTotalTasks
					val numCompletedTasks = meshProgress.numCompletedTasks

					progress = numCompletedTasks.toDouble() / numTotalTasks
					statusToolTip.text = "$numCompletedTasks/$numTotalTasks"
				}
			}
		}
	}

	fun unbind() {
		progressBarUpdater?.stop()
		meshProgress = null
		progress = 1e-7
	}

	companion object {
		const val UPDATE_INTERVAL_MSEC: Long = 100
	}
}