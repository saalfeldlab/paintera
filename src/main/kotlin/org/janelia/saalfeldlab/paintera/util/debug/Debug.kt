package org.janelia.saalfeldlab.paintera.util.debug

import javafx.beans.property.SimpleBooleanProperty
import javafx.util.Subscription
import org.janelia.saalfeldlab.paintera.Paintera

internal object DebugModeProperty : SimpleBooleanProperty(System.getenv("PAINTERA_DEBUG")?.equals("1") ?: false) {
	private var debugModeSubscription = Subscription.EMPTY

	fun trackDebugSubscription(subscription: Subscription) {
		debugModeSubscription = debugModeSubscription.and(subscription)
	}

	init {
		subscribe { it ->
			runCatching {
				if (!it) {
					debugModeSubscription.unsubscribe()
					debugModeSubscription = Subscription.EMPTY
				} else referenceDebugClasses()
			}.getOrElse { it.printStackTrace() }
		}
	}

	private fun referenceDebugClasses() {
		Paintera.whenPaintable {
			runCatching { DebugMenu }.getOrElse { it.printStackTrace() }
			runCatching { DebugWindow }.getOrElse { it.printStackTrace() }
		}
	}

	override fun subscribe(invalidationSubscriber: Runnable): Subscription {
		return runCatching {
			super.subscribe { it ->
				runCatching {
					invalidationSubscriber.run()
				}.getOrElse { it.printStackTrace() }
			}.also { trackDebugSubscription(it) }
		}.getOrElse {
			it.printStackTrace()
			Subscription.EMPTY
		}
	}
}