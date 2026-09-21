package org.janelia.saalfeldlab.paintera.ui

import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.data.XyzView
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis as MetadataAxis

/**
 * Node to coordinate slice positions for additional axes, across sources.
 *
 * Each valid non-spatial axis gets a single slider. Sliders can be groups to move together.
 */
internal class SlicePositionsPane : TitledPane("Slice Positions", VBox(10.0)) {

	private val sections = content as VBox
	private var subscriptions: Subscription? = null

	private data class AxisKey(val state: SourceState<*, *>, val axis: Int)

	private val grouped = mutableSetOf<AxisKey>()
	private var propagating = false

	init {
		isExpanded = false
		sections.padding = Insets(5.0)
		val sourceInfo = paintera.baseView.sourceInfo()
		sourceInfo.trackSources().addListener(ListChangeListener { rebuild() })
		sourceInfo.currentState().subscribe { _ -> rebuild() }
		rebuild()

		val allowedActions = paintera.baseView.allowedActionsProperty()
		disableProperty().bind(allowedActions.createNonNullValueBinding { !allowedActions.isAllowed(NavigationActionType.NonSpatialSlice) })
		managedProperty().bind(visibleProperty())
	}

	private fun rebuild() {
		subscriptions?.unsubscribe()
		val sourceInfo = paintera.baseView.sourceInfo()
		val subs = mutableListOf<Subscription>()
		val states = sourceInfo.trackSources().mapNotNull { sourceInfo.getState(it) }
		grouped.retainAll { it.state in states }
		val axesByState = states.associateWith { getSliceAxes(it) }.filterValues { it.isNotEmpty() }
		sections.children.setAll(axesByState.map { (state, axes) -> section(state, axes, subs) })
		if (sections.children.isNotEmpty())
			sections.children.add(0, HBox(Label("Group")).apply { alignment = Pos.CENTER_RIGHT })
		axesByState.forEach { (state, axes) -> subs += subscribeToGroupSlicePosition(state, axes) }
		subscriptions = Subscription.combine(*subs.toTypedArray())
		isVisible = sections.children.isNotEmpty()
	}

	private class SliceAxis(val axis: Int, val name: String)

	/** get the non-spatial axes of [state] to slice. Skip "channel" axes for raw sources, since they compose through the renderer, rather than slicing.  */
    private fun getSliceAxes(state: SourceState<*, *>): List<SliceAxis> {
        val metadataState = metadataState(state) ?: return emptyList()
        val isRaw = state is ConnectomicsRawState<*, *>
        return metadataState.xyzView.nonSpatialAxes.mapNotNull { axis ->
            val metadataAxis = metadataState.axes.getOrNull(axis)
            if (isRaw && (metadataAxis?.type == MetadataAxis.CHANNEL || metadataAxis?.name?.lowercase() == "c"))
                null
            else
                SliceAxis(axis, metadataAxis?.name?.ifBlank { null } ?: "axis $axis")
        }
    }

	private fun metadataState(state: SourceState<*, *>) = ((state as? SourceStateWithBackend<*, *>)?.backend as? SourceStateBackendN5<*, *>)?.metadataState

	private fun subscribeToGroupSlicePosition(state: SourceState<*, *>, axes: List<SliceAxis>): Subscription {
		val xyzView = metadataState(state)!!.xyzView
		return xyzView.activeIntervalProperty.subscribe { _, _ ->
			val changed = axes.firstOrNull { AxisKey(state, it.axis) in grouped } ?: return@subscribe
			sliceGroupTo(xyzView.slicePosition(changed.axis))
		}
	}

    private data class GroupBounds(val members: List<Pair<XyzView, Int>>, val min: Long, val max: Long)

    private fun getGroupBounds(): GroupBounds {
        val members = grouped.map { metadataState(it.state)!!.xyzView to it.axis }
        val min = members.maxOf { (view, axis) -> view.fullInterval.min(axis) }
        val max = members.minOf { (view, axis) -> view.fullInterval.max(axis) }
        return GroupBounds(members, min, max)
    }

	private fun sliceGroupTo(position: Long) {
		if (propagating)
			return
		propagating = true
		try {
            val (members, min, max) = getGroupBounds()
            val clamped = position.coerceIn(min, max)
			for ((view, axis) in members)
				view.sliceAt(axis, clamped)
		} finally {
			propagating = false
		}
	}

	private fun joinGroup(key: AxisKey) {
		if (grouped.isEmpty()) {
			grouped += key
			return
		}
		val current = grouped.first().let { metadataState(it.state)!!.xyzView.slicePosition(it.axis) }
		grouped += key
        val (_, min, max) = getGroupBounds()
		sliceGroupTo(current.takeIf { it in min..max } ?: min)
	}

	/* the source's name over one row per axis */
	private fun section(state: SourceState<*, *>, axes: List<SliceAxis>, subs: MutableList<Subscription>): VBox {
		val xyzView = metadataState(state)!!.xyzView
		val title = Label().apply {
			textProperty().bind(state.nameProperty())
			style = "-fx-font-weight: bold"
		}
		val rows = axes.map { sliderRow(it.name, xyzView, it.axis, AxisKey(state, it.axis), subs) }
		return VBox(3.0, title, *rows.toTypedArray())
	}

	private fun sliderRow(axisName: String, xyzView: XyzView, axis: Int, key: AxisKey, subs: MutableList<Subscription>): HBox {
		val min = xyzView.fullInterval.min(axis)
		val max = xyzView.fullInterval.max(axis)
		val field = TextField().apply { prefColumnCount = 4; alignment = Pos.CENTER_RIGHT }
		val slider = Slider(min.toDouble(), max.toDouble(), xyzView.slicePosition(axis).toDouble()).apply {
			isSnapToTicks = true
			majorTickUnit = 1.0
			minorTickCount = 0
			blockIncrement = 1.0
			HBox.setHgrow(this, Priority.ALWAYS)
		}

		var updating = false
		fun show(position: Long) {
			updating = true
			slider.value = position.toDouble()
			field.text = position.toString()
			updating = false
		}
		show(xyzView.slicePosition(axis))

		subs += xyzView.activeIntervalProperty.subscribe { _, _ -> show(xyzView.slicePosition(axis)) }
		subs += slider.valueProperty().subscribe { _, value ->
			if (updating)
				return@subscribe
			val position = value.toLong().coerceIn(min, max)
			if (position != xyzView.slicePosition(axis))
				xyzView.sliceAt(axis, position)
		}
		field.setOnAction {
			val typed = field.text.toLongOrNull()?.coerceIn(min, max) ?: xyzView.slicePosition(axis)
			xyzView.sliceAt(axis, typed)
			show(typed)
		}
		val group = CheckBox().apply {
			tooltip = Tooltip("Group: grouped axes slice together")
			isSelected = key in grouped
			selectedProperty().subscribe { _, selected -> if (selected) joinGroup(key) else grouped -= key }
		}
		return HBox(5.0, Label(axisName).apply { minWidth = 40.0 }, slider, field, group).apply { alignment = Pos.CENTER_LEFT }
	}
}
