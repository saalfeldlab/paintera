package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import net.imglib2.type.label.Label
import org.controlsfx.control.SegmentedButton
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.state.PartialDelegationError
import org.janelia.saalfeldlab.paintera.ui.hGrow

enum class InfillStrategy {
	Replace,
	Background,
	NearestLabel;
}

interface InfillStrategyModel {
	val infillStrategyProperty: ObjectProperty<InfillStrategy>
	val replacementLabelProperty: LongProperty

	fun nextId(): Long

	companion object {
		fun default() = object: InfillStrategyModel {
			override val infillStrategyProperty = SimpleObjectProperty(InfillStrategy.NearestLabel)
			override val replacementLabelProperty = SimpleLongProperty(Label.BACKGROUND)
			override fun nextId() = throw PartialDelegationError()
		}
	}
}

enum class InfillStrategyUI(val strategy: InfillStrategy, val makeNode: InfillStrategyModel.() -> ToggleButton) {
	NearestLabel(InfillStrategy.NearestLabel, {
		ToggleButton("Nearest Label").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.NearestLabel }
		}
	}),
	Background(InfillStrategy.Background, {
		ToggleButton("Background").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.Background }
		}
	}),
	Replace(InfillStrategy.Replace, {
		ToggleButton("Specify Label").apply {
			onAction = EventHandler { infillStrategyProperty.value = InfillStrategy.Replace }
		}
	});

	companion object {
		fun makeConfigurationNode(state: InfillStrategyModel) = with(state) {
			TitledPane("", null).apply titlePane@{
				isExpanded = true
				isCollapsible = false
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				graphic = HBox(5.0).hGrow {
					padding = Insets(0.0, 20.0, 0.0, 0.0)
					alignment = Pos.CENTER
					children += Label("Infill Options")
					children += Pane().hGrow()
				}
				content = VBox(10.0).apply {
					lateinit var replaceWithButton: ToggleButton
					children += InfillStrategyUI.entries
						.map {
							it.makeNode(state).apply {
								if (state.infillStrategyProperty.value == it.strategy)
									selectedProperty().set(true)
								if (it == Replace)
									replaceWithButton = this
							}
						}
						.toTypedArray()
						.let { SegmentedButton(*it) }
					children += HBox(10.0).apply {
						disableProperty().bind(replaceWithButton.selectedProperty().not())

						children += NumberField.longField(replacementLabelProperty.get(), { it >= Label.BACKGROUND }, *SubmitOn.entries.toTypedArray()).run {
							replacementLabelProperty.bindBidirectional(valueProperty())
							textField.hGrow()
						}
						children += Button().apply {
							addStyleClass(ADD_ICON)
							onAction = EventHandler { replacementLabelProperty.set(nextId()) }
							tooltip = Tooltip("Next New ID")
						}
					}
				}
			}

		}
	}
}