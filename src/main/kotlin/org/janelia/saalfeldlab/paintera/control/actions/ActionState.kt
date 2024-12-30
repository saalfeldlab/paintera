package org.janelia.saalfeldlab.paintera.control.actions

import javafx.event.Event
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabelState
import kotlin.jvm.java
import kotlin.reflect.KMutableProperty0

//TODO Caleb: Move to Saalfx
interface ActionState {

	fun <E : Event> Action<E>.verifyState()

	/**
	 * Create a new instance of this [ActionState] with the verified fields copied from this instance.
	 * Should be called only AFTER [verifyState] has been called.
	 *
	 * @return A copy of verified fields of [ReplaceLabelState], after verification
	 */
	fun copyVerified(): ActionState

	companion object {
		inline operator fun <reified A : ActionState> invoke() : A {
			return A::class.java.getDeclaredConstructor().newInstance()
		}
	}
}

fun <E : Event> Action<E>.verifyState(state: ActionState) = state.run { verifyState() }

fun <E : Event, T> Action<E>.verify(property: KMutableProperty0<T>, description: String, stateProvider: () -> T?) {
	verify(description) { stateProvider()?.also(property::set) != null }
}
/**
 * Verify an [ActionState] as provided by [createState] and provide a verified [ActionState]
 * to [withActionState] to run during [Action.onAction].
 *
 * The instance provided for [withActionState] will be NEW each time [Action.onAction] is called.
 * */
fun <E: Event, A : ActionState> Action<E>.onAction(createState: () -> A, withActionState : A.(E?) -> Unit) {
	val verifiedState = createState()
	verifyState(verifiedState)
	onAction {
		(verifiedState.copyVerified() as A).withActionState(it)
	}
}