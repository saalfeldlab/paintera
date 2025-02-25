package org.janelia.saalfeldlab.fx.actions

import javafx.event.Event
import javafx.event.EventTarget
import org.janelia.saalfeldlab.control.mcu.MCUControlPanel
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.paintera.control.actions.ActionType
import org.janelia.saalfeldlab.paintera.paintera
import java.util.function.Consumer


fun ActionSet.verifyPermission(vararg actionType: ActionType) {
	if (actionType.isEmpty()) return
	actionType.forEach { permission ->
		verifyAll(Event.ANY, " No Permission for $permission") { paintera.baseView.allowedActionsProperty().hasPermission(permission) }
	}
}

fun Action<*>.verifyPermission(vararg actionType: ActionType) {
	if (actionType.isEmpty()) return
	actionType.forEach { permission ->
		verify( "No Permission for $permission") { paintera.baseView.allowedActionsProperty().hasPermission(permission) }
	}
}

fun ActionSet.verifyPainteraNotDisabled() {
	verifyAll(Event.ANY, "Paintera is Disabled") {  !paintera.baseView.isDisabledProperty.get() }
}

fun Action<*>.verifyPainteraNotDisabled() {
	verify("Paintera is Disabled") { !paintera.baseView.isDisabledProperty.get() }
}

@JvmSynthetic
fun painteraActionSet(namedKey: NamedKeyBinding, actionType: ActionType? = null, ignoreDisable: Boolean = false, apply: (ActionSet.() -> Unit)?): ActionSet {
	return painteraActionSet(namedKey.keyBindingName, actionType, ignoreDisable, apply)
}

@JvmSynthetic
fun painteraActionSet(name: String, actionType: ActionType? = null, ignoreDisable: Boolean = false, apply: (ActionSet.() -> Unit)?): ActionSet {
	return ActionSet(name, { paintera.keyTracker }).apply {
		actionType?.let { (verifyPermission(it)) }
		if (!ignoreDisable) {
			verifyPainteraNotDisabled()
		}
		apply?.let { it() }
	}
}

@JvmOverloads
fun painteraActionSet(name: String, actionType: ActionType? = null, ignoreDisable: Boolean = false, apply: Consumer<ActionSet>?): ActionSet {
	return painteraActionSet(name, actionType, ignoreDisable = ignoreDisable) {
		apply?.accept(this)
	}
}

@JvmSynthetic
fun painteraDragActionSet(
	name: String,
	actionType: ActionType? = null,
	ignoreDisable: Boolean = false,
	filter: Boolean = true,
	consumeMouseClicked: Boolean = false,
	apply: (DragActionSet.() -> Unit)?
): DragActionSet {
	return DragActionSet(name, { paintera.keyTracker }, filter, consumeMouseClicked).apply {
		actionType?.let { (verifyPermission(it)) }
		if (!ignoreDisable) {
			verifyPainteraNotDisabled()
		}
		apply?.invoke(this)
	}
}

@JvmSynthetic
fun painteraMidiActionSet(
	name: String,
	device: MCUControlPanel,
	target: EventTarget,
	actionType: ActionType? = null,
	ignoreDisable: Boolean = false,
	apply: (MidiActionSet.() -> Unit)?
): MidiActionSet {
	return MidiActionSet(name, device, target, { paintera.keyTracker }) {
		actionType?.let { (verifyPermission(it)) }
		if (!ignoreDisable) {
			verifyPainteraNotDisabled()
		}
		apply?.let { it() }
	}
}
