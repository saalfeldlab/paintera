package org.janelia.saalfeldlab.paintera.ui

import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.util.n5.SpatialMapping

/**
 * Ad-hoc UI: one slider (with an editable field) per non-spatial axis of a sliced source, so the fixed slice position
 * - the timepoint or channel the 3D view is taken at - can be changed live. On change the source re-projects to 3D at
 * the new positions and the viewers repaint; the same slice positions drive the commit, so edits write to that slice.
 * Holding C/T and scrolling in a viewer steps these same positions (see NavigationControlMode.sliceDimensionActions).
 *
 * Returns `null` for sources that are not reduced to 3D (a canonical 3D source, or a 4D source kept as channels), i.e.
 * when there is no non-spatial axis to scrub.
 */
object SlicePositionControls {

	fun create(metadataState: MetadataState, dataSource: DataSource<*, *>): Node? {
		val axes = metadataState.axes
		val numDimensions = metadataState.datasetAttributes.numDimensions
		val xyzSourceAxes = SpatialMapping.xyzSourceAxes(axes)
		val allThreeSpatial = xyzSourceAxes.none { it < 0 }
		val canonical3D = numDimensions == 3 && xyzSourceAxes.contentEquals(intArrayOf(0, 1, 2))
		val channels4D = allThreeSpatial && numDimensions == 4 && !metadataState.isLabel
		if (canonical3D || channels4D) return null

		val spatialAxes = xyzSourceAxes.filter { it >= 0 }.toSet()
		val nonSpatialAxes = (0 until numDimensions).filterNot { it in spatialAxes }
		if (nonSpatialAxes.isEmpty()) return null

		val n5Source = (dataSource as? MaskedSource<*, *>)?.underlyingSource() as? N5DataSource<*, *>
			?: dataSource as? N5DataSource<*, *>
			?: return null

		val dimensions = metadataState.datasetAttributes.dimensions
		val rows = nonSpatialAxes.map { axis ->
			val axisName = axes.getOrNull(axis)?.name?.ifBlank { null } ?: "axis $axis"
			sliderRow(axisName, dimensions[axis], metadataState.slicePositions[axis]) { position ->
				/* the source projects to 3D live at these positions, so just re-view the same (still-cached) backing */
				metadataState.slicePositions[axis] = position
				Paintera.getPaintera().baseView.orthogonalViews().requestRepaint()
			}
		}
		return VBox(5.0, *rows.toTypedArray())
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
