package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Modality
import javafx.util.Callback
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts

typealias FXC = FXCollections
typealias ARGBComposite = Composite<ARGBType, ARGBType>
typealias AC = SourceStateCompositePane.AvailableComposites

class SourceStateCompositePane {

	enum class AvailableComposites(
			val shortDescription: String,
			val description: String,
			val composite: ARGBComposite) {

		ALPHA_ADD("Alpha add", "TODO: long description", ARGBCompositeAlphaAdd()),
		ALPHA_YCBCR("Alpha YCbCr", "TODO: long description", ARGBCompositeAlphaYCbCr()),
		COPY("Copy", "TODO: long description", CompositeCopy());

		override fun toString() = shortDescription

		companion object {
			val MAPPING = mapOf(*values().map { Pair(it.composite::class.java, it) }.toTypedArray())
		}

	}

	companion object {

		@JvmStatic
		@JvmOverloads
		fun createComboBox(promptText: String? = null) = ComboBox(AVAILABLE_COMPOSITES)
				.also { it.cellFactory = CELL_FACTORY }
				.also { it.buttonCell = CELL_FACTORY.call(null) }
				.also { it.promptText = promptText }

		private fun createComboBoxAndBindBidrectionalImpl(
				composite: ObjectProperty<ARGBComposite?>,
				promptText: String? = null) = createComboBox(promptText)
				.also { it.valueProperty().addListener { _, _, new -> composite.value = new.composite} }
				.also { composite.addListener { _, _, new -> it.value = new?.let { AC.MAPPING[it::class.java] } } }
				.also { it.value = composite.value?.let { AC.MAPPING[it::class.java] } }

		@JvmStatic
		@JvmOverloads
		fun createComboBoxAndBindBidrectional(
				composite: ObjectProperty<ARGBComposite?>?,
				promptText: String? = null) = composite
				?.let { createComboBoxAndBindBidrectionalImpl(it, promptText) }
				?: createComboBox(promptText)

		@JvmStatic
		@JvmOverloads
		fun createTitledPane(
				composite: ObjectProperty<ARGBComposite?>? = null,
				title: String = "ARGB Composition Mode",
				promptText: String? = "Select composition mode",
				description: String? = DEFAULT_DESCRIPTION,
				expanded: Boolean = false): TitledPane {

			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = title }
					.also { it.contentText = description }

			val tpGraphics = HBox(
					Label(title),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
					createComboBoxAndBindBidrectional(composite, promptText),
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return with (TitledPaneExtensions) {
				TitledPane()//null, VBox(createComboBoxAndBindBidrectional(composite, promptText)).also { it.alignment = Pos.CENTER_LEFT })
						.also { it.isExpanded = expanded }
						.also { it.graphicsOnly(tpGraphics) }
						.also { it.alignment = Pos.CENTER_RIGHT }
						.also { it.tooltip = Tooltip(description) }
			}
		}

		private val AVAILABLE_COMPOSITES = FXC.observableArrayList(*AC.values())

		private val CELL_FACTORY = Callback<ListView<AC?>, ListCell<AC?>> {
			object : ListCell<AC?>() {
				override fun updateItem(item: AC?, empty: Boolean) {
					super.updateItem(item, empty)
					if (item == null || empty)
						graphic = null
					else {
						text = item.shortDescription
						tooltip = Tooltip(item.description)
						// TODO add pop-up window with description
						// graphic = Button("?").also { it.onAction = EventHandler {println(item.description)} }
					}


				}
			}
		}

		private const val DEFAULT_DESCRIPTION = "" +
				"The ARGB composition mode defines how a source is overlaid " +
				"over ARGB values after mapping the voxel values into ARGB space. " +
				"Hover the mouse cursor over each possible selection for a " +
				"detailed description of each composition mode."

	}
}
