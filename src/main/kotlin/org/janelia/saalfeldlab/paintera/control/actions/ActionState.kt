package org.janelia.saalfeldlab.paintera.control.actions

import javafx.event.Event
import org.janelia.saalfeldlab.fx.actions.Action
import kotlin.reflect.KMutableProperty0

//TODO Caleb: Move to Saalfx
abstract class ActionState {
	abstract fun <E : Event> Action<E>.verifyState()
}

fun <E : Event> Action<E>.verifyState(state: ActionState) = state.run { verifyState() }

fun <E : Event, T> Action<E>.verify(property: KMutableProperty0<T>, description: String, stateProvider: () -> T?) {
	verify(description) { stateProvider()?.also(property::set) != null }
}

fun <E: Event, A : ActionState> Action<E>.onAction(state: A, withActionState : A.(E?) -> Unit) {
	verifyState(state)
	onAction {
		state.withActionState(it)
	}
}