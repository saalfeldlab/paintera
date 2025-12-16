package org.janelia.saalfeldlab.paintera.ui.source.mesh

import javafx.beans.binding.DoubleExpression
import javafx.css.PseudoClass
import javafx.scene.control.ProgressIndicator
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.ui.AnimatedProgressBar

private typealias ReverseBehavior = AnimatedProgressBar.Companion.ReverseBehavior

class MeshProgressBar(reverseBehavior: ReverseBehavior = ReverseBehavior.SKIP) : AnimatedProgressBar(reverseBehavior) {
	private var subscription: Subscription? = null

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

	fun bindTo(meshProgress: DoubleExpression) {
		subscription?.unsubscribe()

		/* Don't animate the initialization, just set it to the current value */
		progressProperty().set(meshProgress.get())

		progressTargetProperty.value = meshProgress.value
		progressTargetProperty.bind(meshProgress)
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
		progress = ProgressIndicator.INDETERMINATE_PROGRESS
	}
}