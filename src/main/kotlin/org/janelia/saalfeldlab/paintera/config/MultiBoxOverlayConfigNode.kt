package org.janelia.saalfeldlab.paintera.config

import bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts

class MultiBoxOverlayConfigNode() {

	constructor(config: MultiBoxOverlayConfig) : this() {
		this.bind(config)
	}

	private val visibility = SimpleObjectProperty(MultiBoxOverlayConfig.DefaultValues.VISIBILITY)

	val contents: Node
		get() {

			val visibilityChoiceBox = ComboBox(VISIBILITY_CHOICES).apply {
				tooltip = Tooltip("Set the visibility of the multi-box overlay.")
				valueProperty().bindBidirectional(visibility)
				cellFactory = VISIBILITY_CELL_FACTORY
				prefWidth = 80.0
			}

			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				headerText = "Multi-Box Overlay"
				contentText = "Draw overlays of the current screen and all sources in world space to " +
						"indicate orientation and position of current cross-section in world and relative to data. " +
						"Uncheck to disable the overlay."
			}

			val tpGraphics = HBox(
				Label("Multi-Box Overlay"),
				NamedNode.bufferNode(),
				visibilityChoiceBox,
				Button("?").apply { onAction = EventHandler { helpDialog.show() } }
			).apply { alignment = Pos.CENTER }

			return TitledPane("Meshes", null).apply {
				isExpanded = false
				with(TitledPaneExtensions) { graphicsOnly(tpGraphics) }
				alignment = Pos.CENTER_RIGHT
			}

		}

	fun bind(config: MultiBoxOverlayConfig) {
		visibility.bindBidirectional(config.visibilityProperty())
	}

	private class VisibilityCell : ListCell<MultiBoxOverlayConfig.Visibility>() {
		override fun updateItem(visibility: MultiBoxOverlayConfig.Visibility?, empty: Boolean) {
			super.updateItem(visibility, empty)
			text = item?.name
			tooltip = item?.description?.let { Tooltip(it) }
		}
	}

	companion object {
		private val VISIBILITY_CHOICES = FXCollections.observableArrayList(*MultiBoxOverlayConfig.Visibility.values())
		private val VISIBILITY_CELL_FACTORY =
			Callback<ListView<MultiBoxOverlayConfig.Visibility>, ListCell<MultiBoxOverlayConfig.Visibility>> { VisibilityCell() }
	}

}
