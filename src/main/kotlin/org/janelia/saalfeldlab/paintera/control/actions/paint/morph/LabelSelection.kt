package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.control.actions.state.PartialDelegationError
import org.janelia.saalfeldlab.paintera.ui.hGrow
import kotlin.collections.plusAssign

enum class LabelSelection {
	ActiveFragments,
	ActiveSegments;

}

interface LabelSelectionModel {

	val labelSelectionProperty: ObjectProperty<LabelSelection>

	val activeFragments: LongArray
	val fragmentsForActiveSegments: LongArray

	fun getSelectedLabels(): LongArray {
		return when (labelSelectionProperty.get()) {
			LabelSelection.ActiveFragments -> activeFragments
			LabelSelection.ActiveSegments -> fragmentsForActiveSegments
		}
	}

	companion object {
		fun default() = object: LabelSelectionModel {
			override val labelSelectionProperty = SimpleObjectProperty(LabelSelection.ActiveSegments)
			override val activeFragments by lazy { throw PartialDelegationError() }
			override val fragmentsForActiveSegments by lazy { throw PartialDelegationError() }
		}
	}

}

enum class LabelSelectionUI(val strategy: LabelSelection, val makeNode: LabelSelectionModel.() -> ToggleButton) {
	ActiveFragments(LabelSelection.ActiveFragments, {
		RadioButton("Active Fragments").apply {
			onAction = EventHandler { labelSelectionProperty.value = LabelSelection.ActiveFragments }
		}
	}),
	ActiveSegments(LabelSelection.ActiveSegments, {
		RadioButton("Active Segments").apply {
			onAction = EventHandler { labelSelectionProperty.value = LabelSelection.ActiveSegments }
		}
	});

	companion object {
		fun makeConfigurationNode(state: LabelSelectionModel) = let {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				graphic = HBox(5.0).hGrow {
					minWidthProperty().bind(this@titlePane.widthProperty())
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Label Selection")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					val toggleGroup = ToggleGroup()
					children += LabelSelectionUI.entries
						.map {
							it.makeNode(state).apply {
								this.toggleGroup = toggleGroup
								if (state.labelSelectionProperty.value == it.strategy)
									selectedProperty().set(true)
							}
						}
						.toTypedArray()
				}
			}

		}
	}
}