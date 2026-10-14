package org.janelia.saalfeldlab.paintera.filters

import javafx.geometry.HPos
import javafx.scene.Node
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.janelia.saalfeldlab.paintera.ui.resetButton

/** The settings of a filter as a label and control per row, under a reset button; disabled with the filter */
abstract class RenderFilterSettingsGrid(settings: RenderFilterSettings, resetTooltip: String) : GridPane() {

	private var nextRow = 1

	init {
		hgap = 5.0
		vgap = 5.0
		val reset = resetButton(resetTooltip) { settings.reset() }
		add(reset, 0, 0, 2, 1)
		setHalignment(reset, HPos.RIGHT)
		disableProperty().bind(settings.enabledProperty.not())
	}

	protected fun addSetting(label: Node, control: Region) {
		addRow(nextRow++, label, control)
		control.maxWidth = Double.MAX_VALUE
		setHgrow(control, Priority.ALWAYS)
	}

	protected fun addWideRow(node: Region) {
		add(node, 0, nextRow++, 2, 1)
		setHgrow(node, Priority.ALWAYS)
	}
}
