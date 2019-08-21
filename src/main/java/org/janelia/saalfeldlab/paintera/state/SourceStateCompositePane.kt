package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.VBox
import javafx.util.Callback
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy

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
				expanded: Boolean = false) = TitledPane(title, null)
				.also { it.content = VBox(
						Labels.withTooltip(description).also { it.isWrapText = true },
						createComboBoxAndBindBidrectional(composite, promptText)) }
				.also { it.isExpanded = expanded }
				.also { it.padding = Insets.EMPTY }

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
				"The ARGB composite defines how a source is overlaid " +
				"over ARGB values after mapping the voxel values into ARGB space. " +
				"Hover the mouse cursor over each possible selection for a " +
				"detailed description of each composition mode."

	}
}
