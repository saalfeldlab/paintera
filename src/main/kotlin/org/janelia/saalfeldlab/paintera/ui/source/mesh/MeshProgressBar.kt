package org.janelia.saalfeldlab.paintera.ui.source.mesh

import javafx.css.PseudoClass
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar
import org.janelia.saalfeldlab.paintera.meshes.MeshProgressState

class MeshProgressBar : AnimatedProgressBar() {
	private var meshProgress: MeshProgressState? = null
	private var subscription : Subscription? = null

	init {
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
		/* don't rebind if already bound */
		if (meshProgress == this.meshProgress)
			return

		subscription?.unsubscribe()
		this.meshProgress = meshProgress
		val progressBinding = meshProgress.progressBinding

		/* Don't animate the initialization, just set it to the current value */
		progressProperty().set(progressBinding.get())

		progressTargetProperty.bind(progressBinding)
		subscription = Subscription {
			subscription = null
			unbind()
		}

	}

	fun unbind() {
		if (subscription == null)
			return

		subscription = null
		stop()
		progressTargetProperty.unbind()
		meshProgress = null
		progress = Double.MIN_VALUE
	}
}