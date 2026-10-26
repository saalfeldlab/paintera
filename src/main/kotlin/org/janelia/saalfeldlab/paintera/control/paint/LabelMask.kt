package org.janelia.saalfeldlab.paintera.control.paint

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.control.modes.ModeSetting
import org.janelia.saalfeldlab.paintera.control.paint.LabelMaskConfig.SegmentMaskMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks.SegmentMask.Blacklist
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks.SegmentMask.Whitelist
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.ui.IdListSection
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

/** Binding layer between [LabelMaskConfig], [LabelMaskUI], and the actual LabelMask logic  */
class LabelMaskModel(
    config: LabelMaskConfig,
    assignment: FragmentSegmentAssignmentState,
    val currentSelection: () -> LongArray,
) {

    val paintOverLockedLabelsProperty = SimpleBooleanProperty().apply { bindBidirectional(config.paintOverLockedLabelsProperty) }

    val segmentMaskModeProperty = SimpleObjectProperty<SegmentMaskMode?>().apply { bindBidirectional(config.segmentMaskModeProperty) }

    val ids = config.ids

    private val segments = SelectedSegments(ids, assignment)

    /** Segment mask for [ids]; null if no filter is set or the list is empty */
    fun segmentMask(): AcceptLabelMasks.SegmentMask? {
        if (ids.isEmpty)
            return null
        return when (segmentMaskModeProperty.get()) {
            SegmentMaskMode.Whitelist -> Whitelist(segments)
            SegmentMaskMode.Blacklist -> Blacklist(segments)
            null -> null
        }
    }
}

class LabelMaskConfig {

	enum class SegmentMaskMode(val label: String) {
		Whitelist("Whitelist"),
		Blacklist("Blacklist")
	}

	val paintOverLockedLabelsProperty = SimpleBooleanProperty(false)

	/** Whitelist or blacklist of [ids], or null if no label label mask */
	val segmentMaskModeProperty = SimpleObjectProperty<SegmentMaskMode?>(null)

	val ids = SelectedIds()

	fun copyFrom(other: LabelMaskConfig) {
		paintOverLockedLabelsProperty.set(other.paintOverLockedLabelsProperty.get())
		segmentMaskModeProperty.set(other.segmentMaskModeProperty.get())
		ids.activate(*other.ids.activeIdsCopyAsArray)
	}

	fun isDefault() = !paintOverLockedLabelsProperty.get() && segmentMaskModeProperty.get() == null && ids.isEmpty
}

class LabelMaskUI(model: ObservableValue<LabelMaskModel?>) : ModeSetting {

	override val title = "Label Mask"

	private val paintOverLocked = CheckBox("Paint over Locked Labels")
	private val segmentMask = IdListSection()
	private var boundPaintOverLocked: BooleanProperty? = null

	override val node = VBox(8.0, paintOverLocked, Label("Segment Filter"), segmentMask).apply { isDisable = true }

	init {
		model.subscribe { activeModel -> bind(activeModel) }
	}

	private fun bind(model: LabelMaskModel?) {
		boundPaintOverLocked?.let { paintOverLocked.selectedProperty().unbindBidirectional(it) }
		boundPaintOverLocked = model?.paintOverLockedLabelsProperty?.also { paintOverLocked.selectedProperty().bindBidirectional(it) }
		segmentMask.bind(model?.segmentMaskModeProperty, model?.ids, model?.currentSelection ?: { longArrayOf() })
		node.isDisable = model == null
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class LabelMaskConfigSerializer : PainteraSerialization.PainteraAdapter<LabelMaskConfig> {

    override fun serialize(src: LabelMaskConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        if (src.isDefault())
            return JsonNull.INSTANCE
        return JsonObject().apply {
            if (src.paintOverLockedLabelsProperty.get())
                addProperty(PAINT_OVER_LOCKED_LABELS_KEY, true)
            src.segmentMaskModeProperty.get()?.let { addProperty(SEGMENT_MASK_MODE_KEY, it.name) }
            src.ids.activeIdsCopyAsArray.takeIf { it.isNotEmpty() }?.let { add(IDS_KEY, context[it.sortedArray()]) }
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LabelMaskConfig {
        val config = LabelMaskConfig()
        if (!json.isJsonObject)
            return config
        return config.apply {
            json.get<Boolean>(PAINT_OVER_LOCKED_LABELS_KEY) { paintOverLockedLabelsProperty.set(it) }
            json.get<String>(SEGMENT_MASK_MODE_KEY)?.let { name ->
                segmentMaskModeProperty.set(SegmentMaskMode.entries.firstOrNull { it.name == name })
            }
            context.get<LongArray>(json, IDS_KEY) { ids.activate(*it) }
        }
    }

    override fun getTargetClass() = LabelMaskConfig::class.java

    companion object {
        private const val PAINT_OVER_LOCKED_LABELS_KEY = "paintOverLockedLabels"
        private const val SEGMENT_MASK_MODE_KEY = "segmentMaskMode"
        private const val IDS_KEY = "ids"
    }
}
