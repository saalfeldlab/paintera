package org.janelia.saalfeldlab.paintera.state.label

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.util.Pair
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentStateWithActionTracker
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

data class FragmentSegmentAssignmentActions(val events: List<Pair<AssignmentAction, Boolean>>) {

	constructor(vararg events: Pair<AssignmentAction, Boolean>) : this(listOf(*events))

	constructor(fragmentSegmentAssignment: FragmentSegmentAssignmentStateWithActionTracker) : this(
		fragmentSegmentAssignment.events().map { Pair(it.key, it.value.value) })

	fun feedInto(fragmentSegmentAssignment: FragmentSegmentAssignmentStateWithActionTracker) = fragmentSegmentAssignment.applyWithEnabledFlag(events)

	@Plugin(type = PainteraSerialization.PainteraAdapter::class)
	class Adapter : PainteraSerialization.PainteraAdapter<FragmentSegmentAssignmentActions> {
		override fun serialize(
			src: FragmentSegmentAssignmentActions,
			typeOfSrc: Type,
			context: JsonSerializationContext
		) = JsonObject().also { it.add(ACTIONS, src.events.toJson(context)) }

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext
		) = json
			.toEventsWithEnabledStatus(context)
			?.let { FragmentSegmentAssignmentActions(it) }
			?: FragmentSegmentAssignmentActions()

		override fun getTargetClass() = FragmentSegmentAssignmentActions::class.java


		companion object {
			private const val ACTIONS = "actions"
			private const val TYPE = "type"
			private const val DATA = "data"
			private const val IS_DISABLED = "isDisabled"

			private fun List<Pair<AssignmentAction, Boolean>>.toJson(context: JsonSerializationContext) = context
				.serialize(map { it.toJson(context) }.toTypedArray())

			private fun Pair<AssignmentAction, Boolean>.toJson(context: JsonSerializationContext) = JsonObject()
				.also { it.add(TYPE, context.serialize(key.type)) }
				.also { it.add(DATA, context.serialize(key)) }
				.also { m -> value.not().takeIf { it }?.let { m.addProperty(IS_DISABLED, it) } }

			private fun JsonElement.toEventsWithEnabledStatus(context: JsonDeserializationContext) = with(GsonExtensions) {
				getJsonArray(ACTIONS)?.mapNotNull { it.toEventWithEnabledStatus(context) }
			}

			private fun JsonElement.toEventWithEnabledStatus(context: JsonDeserializationContext) = this
				.takeIf { isJsonObject }
				?.let { it.asJsonObject }
				?.let { it.toEventWithEnabledStatus(context) }

			private fun JsonObject.toEventWithEnabledStatus(context: JsonDeserializationContext) = this
				.takeIf { it.has(TYPE) && it.has(DATA) }
				?.let {
					val type = context.deserialize<AssignmentAction.Type>(it[TYPE], AssignmentAction.Type::class.java)
					val action = context.deserialize<AssignmentAction>(it[DATA], type.classForType)
					with(GsonExtensions) { Pair(action, it.getBooleanProperty(IS_DISABLED)?.not() ?: true) }
				}
		}

	}

}
