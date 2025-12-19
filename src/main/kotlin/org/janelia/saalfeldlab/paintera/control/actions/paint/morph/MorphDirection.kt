package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.ui.hGrow
import kotlin.math.roundToInt

enum class MorphDirection(val info: String) {
	Shrink( "Only apply the operation inward over the selected labels (Shrink)"),
	Expand( "Only apply the operation outward from the selected labels (Expand)") {
		override fun defaultKernelSize(resolution: DoubleArray): Int {
			val min = resolution.min()
			val max = resolution.max()
			return (min + (max - min) / 4.0).roundToInt()
		}
	},
	Both( "Apply the operation into and out from the selected labels (Grow and Shrink)");

	open fun defaultKernelSize(resolution: DoubleArray): Int {
		val min = resolution.min()
		val max = resolution.max()
		return (min + (max - min) / 2.0).roundToInt()
	}

}

interface MorphDirectionModel {
	val morphDirectionProperty: ObjectProperty<MorphDirection>

	companion object {
		fun default() = object: MorphDirectionModel {
			override val morphDirectionProperty: ObjectProperty<MorphDirection> = SimpleObjectProperty(MorphDirection.Both)
		}
	}
}

enum class MorphDirectionUI(val direction: MorphDirection) {
	In(MorphDirection.Shrink),
	Out(MorphDirection.Expand),
	Both(MorphDirection.Both);

	fun makeNode(model: MorphDirectionModel): ToggleButton {
		return RadioButton(direction.name).apply {
			tooltip = Tooltip(direction.info)
			setOnAction { model.morphDirectionProperty.value = direction}
		}
	}

	companion object {

		fun makeConfigurationNode(state: MorphDirectionModel) = let {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				graphic = HBox(5.0).hGrow {
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Direction")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					val toggleGroup = ToggleGroup()
					children += MorphDirectionUI.entries
						.map {
							it.makeNode(state).apply {
								this.toggleGroup = toggleGroup
								if (state.morphDirectionProperty.value == it.direction)
									selectedProperty().set(true)
							}
						}.toTypedArray()
				}
			}

		}
	}
}