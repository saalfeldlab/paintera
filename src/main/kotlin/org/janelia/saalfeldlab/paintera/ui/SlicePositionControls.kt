package org.janelia.saalfeldlab.paintera.ui

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState

/**
 * Ad-hoc UI: one slider (with an editable field) per non-spatial axis of a sliced source, so the fixed slice position
 * - the timepoint or channel the 3D view is taken at - can be changed live. On change the source re-projects to 3D at
 * the new positions and the viewers repaint; the same slice positions drive the commit, so edits write to that slice.
 * Holding C/T and scrolling in a viewer steps these same positions (see NavigationControlMode.nonSpatialSliceActions).
 *
 * Returns `null` for sources that are not reduced to 3D (a canonical 3D source, or a 4D source kept as channels), i.e.
 * when there is no non-spatial axis to scrub.
 */
object SlicePositionControls {

	fun create(metadataState: MetadataState, dataSource: DataSource<*, *>): Node? {
		val axes = metadataState.axes
		val xyzView = metadataState.xyzView
		if (xyzView.nonSpatialAxes.isEmpty()) return null

		val n5Source = (dataSource as? MaskedSource<*, *>)?.underlyingSource() as? N5DataSource<*, *>
			?: dataSource as? N5DataSource<*, *>
			?: return null

		val rows = xyzView.nonSpatialAxes.map { axis ->
			val axisName = axes.getOrNull(axis)?.name?.ifBlank { null } ?: "axis $axis"
			sliderRow(axisName, xyzView.fullInterval.dimension(axis), xyzView.slicePosition(axis)) { position ->
				/* the source projects to 3D live at the view's slice; PainteraBaseView repaints on the region change */
				xyzView.sliceAt(axis, position)
			}
		}
		return VBox(5.0, *rows.toTypedArray()).apply {
			/* the sliders and the C/T scroll are the same action; a mode that refuses one refuses both */
			val allowedActions = paintera.baseView.allowedActionsProperty()
			disableProperty().bind(allowedActions.createNonNullValueBinding { !allowedActions.isAllowed(NavigationActionType.NonSpatialSlice) })
		}
	}

	/** A labelled `[0, size-1]` slider with a synced editable field; [onChange] fires when the (integer) position changes. */
	private fun sliderRow(axisName: String, size: Long, initial: Long, onChange: (Long) -> Unit): Node {
		val maxPosition = (size - 1).coerceAtLeast(0)
		val field = TextField(initial.toString()).apply { prefColumnCount = 4; alignment = Pos.CENTER_RIGHT }
		val slider = Slider(0.0, maxPosition.toDouble(), initial.toDouble()).apply {
			isSnapToTicks = true
			majorTickUnit = 1.0
			minorTickCount = 0
			blockIncrement = 1.0
			HBox.setHgrow(this, Priority.ALWAYS)
		}
		var last = initial
		slider.valueProperty().subscribe { _, value ->
			val position = value.toLong().coerceIn(0, maxPosition)
			field.text = position.toString()
			if (position != last) {
				last = position
				onChange(position)
			}
		}
		field.setOnAction {
			val typed = field.text.toLongOrNull()?.coerceIn(0, maxPosition) ?: last
			slider.value = typed.toDouble()
			field.text = typed.toString()
		}
		return HBox(5.0, Label(axisName).apply { minWidth = 60.0 }, slider, field).apply { alignment = Pos.CENTER_LEFT }
	}
}
