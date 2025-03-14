package org.janelia.saalfeldlab.paintera.control.actions

import javafx.event.Event
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.Action.Companion.onAction
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.full.primaryConstructor

//TODO Caleb: Move to Saalfx
interface ActionState<A> where A : ActionState<A> {

	fun <E : Event> Action<E>.verifyState()

	fun copyVerified(): A

	companion object {
		inline operator fun <reified A : ActionState<A>> invoke(): () -> A {
			return { A::class.primaryConstructor!!.call() }
		}
	}
}


fun <E : Event, A : ActionState<A>, T> Action<E>.verify(property: KMutableProperty0<T>, description: String, stateProvider: () -> T?) {

	verify(description) {
		stateProvider()?.let { property.set(it) } != null
	}
}

/**
 * Verify an [ActionState] as provided by [createState] and provide a verified [ActionState]
 * to [withActionState] to run during [Action.onAction].
 *
 * The instance provided for [withActionState] will be NEW each time [Action.onAction] is called.
 * */
fun <E : Event, A : ActionState<A>> Action<E>.onAction(createState: () -> A, withActionState: A.(E?) -> Unit) {
	val verifiedState = createState().apply { verifyState() }
	onAction {
		withActionState(verifiedState.copyVerified(), it)
	}
}

@JvmSynthetic
@JvmName("onActionState")
inline fun <reified A : ActionState<A>> Action<Event>.onAction(noinline withActionState: A.(Event?) -> Unit) {
	onAction<Event, A>(ActionState<A>(), withActionState )
}

@JvmSynthetic
@JvmName("onActionStateWithEvent")
inline fun <E : Event, reified A : ActionState<A>> Action<E>.onAction(noinline withActionState: A.(E?) -> Unit) {
	onAction<E, A>(ActionState<A>(), withActionState )
}

