package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.util.Callback
import net.imglib2.type.numeric.ARGBType
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaCopy
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.AlphaCopy
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts

typealias FXC = FXCollections
typealias ARGBComposite = Composite<ARGBType, ARGBType>
typealias AC = SourceStateCompositePane.AvailableComposites

class SourceStateCompositePane {

	enum class AvailableComposites(
		val shortDescription: String,
		val description: String,
		val composite: ARGBComposite,
	) {

		ALPHA_ADD("Alpha Add", "Add the alpha-weighted colors from this source and the underlying source ", ARGBCompositeAlphaAdd()),
		ALPHA_YCBCR("Alpha YCbCr", "YCbCr composition weighted by this source's alpha ", ARGBCompositeAlphaYCbCr()),
		ALPHA_COPY("Alpha Copy", "Add the alpha-weight colors from this source to the underlying source, but use this source's alpha directly", ARGBCompositeAlphaCopy()),
		ALPHA_ONLY("Alpha Only", "Use the underlying source colors with this source's alpha", AlphaCopy()),
		COPY("Copy", "No Composition with the underlying sources. Use this source directly. ", CompositeCopy());

		override fun toString() = shortDescription

		companion object {
			val MAPPING = mapOf(*AvailableComposites.entries.map { Pair(it.composite::class.java, it) }.toTypedArray())
		}

	}

	companion object {

		@JvmStatic
		@JvmOverloads
		fun createComboBox(prompt: String? = null) = ComboBox(AVAILABLE_COMPOSITES).apply {
			cellFactory = CELL_FACTORY
			buttonCell = CELL_FACTORY.call(null)
			promptText = prompt
		}

		private fun createComboBoxAndBindBidirectionalImpl(
			composite: ObjectProperty<ARGBComposite?>,
			promptText: String? = null,
		) = createComboBox(promptText).apply {
			valueProperty().addListener { _, _, new -> composite.value = new.composite }
			composite.addListener { _, _, new -> value = new?.let { AC.MAPPING[it::class.java] } }
			value = composite.value?.let { AC.MAPPING[it::class.java] }
		}

		@JvmStatic
		@JvmOverloads
		fun createComboBoxAndBindBidrectional(
			composite: ObjectProperty<ARGBComposite?>?,
			promptText: String? = null,
		) = composite
			?.let { createComboBoxAndBindBidirectionalImpl(it, promptText) }
			?: createComboBox(promptText)

		@JvmStatic
		@JvmOverloads
		fun createTitledPane(
			composite: ObjectProperty<ARGBComposite?>? = null,
			title: String = "ARGB Composition Mode",
			promptText: String? = "Select composition mode",
			description: String? = DEFAULT_DESCRIPTION,
			expanded: Boolean = true,
		): TitledPane {

			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				headerText = title
				contentText = description
			}

			val tpGraphics = HBox(
				Label(title),
				NamedNode.bufferNode(),
				createComboBoxAndBindBidrectional(composite, promptText),
				Button("?").apply { onAction = EventHandler { helpDialog.show() } }
			).apply { alignment = Pos.CENTER }

			return with(TitledPaneExtensions) {
				TitledPane().apply {
					isExpanded = expanded
					isCollapsible = false
					graphicsOnly(tpGraphics)
					alignment = Pos.CENTER_RIGHT
					tooltip = Tooltip(description)
				}
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
