package org.janelia.saalfeldlab.paintera.control.paint

import com.google.gson.*
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.CheckBox
import javafx.scene.control.Separator
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.paintera.control.modes.ModeSetting
import org.janelia.saalfeldlab.paintera.control.paint.LabelMaskConfig.SegmentFilterMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks.SegmentMask.Blacklist
import org.janelia.saalfeldlab.paintera.data.mask.AcceptLabelMasks.SegmentMask.Whitelist
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.IdListSection
import org.scijava.plugin.Plugin
import java.lang.reflect.Type


/**
 * Label mask setting exposed for supported [org.janelia.saalfeldlab.paintera.control.modes.ControlMode]s
 *
 * @property model observable model to bind this Setting to
 */
class LabelMaskSetting(private val model: ObservableValue<LabelMaskModel?>) : ModeSetting {

    override val title = "Label Mask"

    override val node get() = LabelMaskUI(model)

    companion object {
        fun forObservableSourceState(activeSource: ObservableValue<SourceState<*, *>?>) =
            LabelMaskSetting(activeSource.map { (it as? ConnectomicsLabelState<*, *>)?.labelMaskModel })
    }
}

/** Binding layer between [LabelMaskConfig], [LabelMaskUI], and the actual LabelMask logic  */
class LabelMaskModel(
    config: LabelMaskConfig,
    val selectedSegments: SelectedSegments,
) {

    val paintOverLockedLabelsProperty = SimpleBooleanProperty().apply { bindBidirectional(config.paintOverLockedLabelsProperty) }

    val segmentFilterEnabledProperty = SimpleBooleanProperty().apply { bindBidirectional(config.segmentFilterEnabledProperty) }
	private val segmentFilterEnabled by segmentFilterEnabledProperty.nonnullVal()

    val segmentFilterModeProperty = SimpleObjectProperty<SegmentFilterMode>().apply { bindBidirectional(config.segmentFilterModeProperty) }
	private val segmentFilterMode by segmentFilterModeProperty.nonnullVal()

    val segmentIds = config.segmentIds

    private val filterSegments = SelectedSegments(segmentIds, selectedSegments.assignment)

    val rejectsAllSegments get() = segmentFilterEnabled && segmentFilterMode == SegmentFilterMode.Whitelist && segmentIds.isEmpty

    /** Segment mask for [segmentIds]; null if the filter is off or an empty blacklist */
    fun segmentMask(): AcceptLabelMasks.SegmentMask? {
        if (!segmentFilterEnabledProperty.get() || (segmentFilterModeProperty.get() == SegmentFilterMode.Blacklist && segmentIds.isEmpty))
            return null
        return when (segmentFilterModeProperty.get()) {
            SegmentFilterMode.Whitelist -> Whitelist(filterSegments)
            SegmentFilterMode.Blacklist -> Blacklist(filterSegments)
        }
    }
}

class LabelMaskConfig {

	enum class SegmentFilterMode(val label: String) {
		Whitelist("Whitelist"),
		Blacklist("Blacklist")
	}

	val paintOverLockedLabelsProperty = SimpleBooleanProperty(false)

	val segmentFilterEnabledProperty = SimpleBooleanProperty(false)

	val segmentFilterModeProperty = SimpleObjectProperty(SegmentFilterMode.Blacklist)

	val segmentIds = SelectedIds()

	fun copyFrom(other: LabelMaskConfig) {
		paintOverLockedLabelsProperty.set(other.paintOverLockedLabelsProperty.get())
		segmentFilterEnabledProperty.set(other.segmentFilterEnabledProperty.get())
		segmentFilterModeProperty.set(other.segmentFilterModeProperty.get())
		segmentIds.activate(*other.segmentIds.activeIdsCopyAsArray)
	}

	/** The mode and ids are only serialized when the segment filter is enabled  */
	fun isDefault() = !paintOverLockedLabelsProperty.get() && !segmentFilterEnabledProperty.get()
}

class LabelMaskUI(model: ObservableValue<LabelMaskModel?>) : VBox(8.0) {

	private val paintOverLocked = CheckBox("Paint over Locked Labels")
	private val segmentMask = IdListSection()
	private var boundPaintOverLocked: BooleanProperty? = null

	init {
		styleClass += "label-mask-ui"
		children.addAll(paintOverLocked, Separator(), segmentMask)
		isDisable = true
		model.subscribe { activeModel -> bind(activeModel) }
	}

	private fun bind(model: LabelMaskModel?) {
		boundPaintOverLocked?.let { paintOverLocked.selectedProperty().unbindBidirectional(it) }
		boundPaintOverLocked = model?.paintOverLockedLabelsProperty?.also { paintOverLocked.selectedProperty().bindBidirectional(it) }
		segmentMask.bind(model?.segmentFilterEnabledProperty, model?.segmentFilterModeProperty, model?.segmentIds, { model?.selectedSegments?.selectedSegmentsCopyAsArray ?: longArrayOf() })
		isDisable = model == null
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
            if (src.segmentFilterEnabledProperty.get()) {
                add(SEGMENT_FILTER_KEY, JsonObject().apply {
                    addProperty(SEGMENT_MASK_MODE_KEY, src.segmentFilterModeProperty.get().name)
                    add(IDS_KEY, context[src.segmentIds.activeIdsCopyAsArray.sortedArray()])
                })
            }
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LabelMaskConfig {
        val config = LabelMaskConfig()
        if (!json.isJsonObject)
            return config
        return config.apply {
            json.get<Boolean>(PAINT_OVER_LOCKED_LABELS_KEY) { paintOverLockedLabelsProperty.set(it) }
            json.asJsonObject.get(SEGMENT_FILTER_KEY)?.takeIf { it.isJsonObject }?.let { filterJson ->
                segmentFilterEnabledProperty.set(true)
                filterJson.get<String>(SEGMENT_MASK_MODE_KEY)?.let { name ->
                    SegmentFilterMode.entries.firstOrNull { it.name == name }?.let { segmentFilterModeProperty.set(it) }
                }
                context.get<LongArray>(filterJson, IDS_KEY) { segmentIds.activate(*it) }
            }
        }
    }

    override fun getTargetClass() = LabelMaskConfig::class.java

    companion object {
        private const val PAINT_OVER_LOCKED_LABELS_KEY = "paintOverLockedLabels"
        private const val SEGMENT_FILTER_KEY = "segmentFilter"
        private const val SEGMENT_MASK_MODE_KEY = "segmentMaskMode"
        private const val IDS_KEY = "ids"
    }
}
