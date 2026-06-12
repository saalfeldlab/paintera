package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.actions.OpenSourceModel
import org.janelia.saalfeldlab.paintera.ui.hGrow

class OpenSourceNode(model: OpenSourceModel) : HBox(5.0) {

	init {
		alignment = Pos.CENTER_LEFT

		val datasetDropDown = model.createDatasetDropdownMenu()
		val reparseButton = Button("").apply {
			addStyleClass(Style.REFRESH_ICON)
			tooltip = Tooltip("Search for Datasets again at the current selection")
			setOnAction {
				model.containerSelectionProperty.get()?.let { container ->
					model.reparseSelection(container)
				}
			}
		}
		val progressIndicator = ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS).apply {
			prefWidth = 24.0
			prefHeight = 24.0
			maxWidth = 24.0
			maxHeight = 24.0
			visibleProperty().bind(model.isBusyProperty)
			managedProperty().bind(model.isBusyProperty)
		}

		children += datasetDropDown
		children += Pane().hGrow()
		children += reparseButton
		children += progressIndicator
	}

	companion object {
		const val DATASET_PROMPT = "_Dataset"

		private fun OpenSourceModel.createDatasetDropdownMenu(): MenuButton {

			fun trimValidDatasetChoices(): List<String> {
				val nodes = validDatasets.values.toMutableList()
				nodes.removeAll(nodes.flatMap { it.childrenList() })
				return nodes.map { it.path }
			}

			val choices: ObservableList<String> = FXCollections.observableArrayList()

			val dropDownMenuButton = MatchSelectionMenuButton(choices, null, null) { selection ->
				activeNode = validDatasets.get()[selection]
			}.apply {
				cutoff = 50

				val disableWhenEmpty = validDatasets.createObservableBinding { it.get().isEmpty() }
				disableProperty().bind(disableWhenEmpty)

				val datasetDropDownText = activeNodeProperty.createObservableBinding {
						val pathPart = it.value?.path?.let { path -> " : $path" } ?: ""
						"$DATASET_PROMPT$pathPart"
					}
				textProperty().bind(datasetDropDownText)

				val prevOnShowing = onShowing
				setOnShowing {
					choices.setAll(trimValidDatasetChoices())
					prevOnShowing.handle(it)
				}
			}

			/* if the dataset choices change, update the menu items */
			validDatasets.addListener(MapChangeListener {
				val trimmedDatasets = trimValidDatasetChoices()
				InvokeOnJavaFXApplicationThread {
					if (!dropDownMenuButton.isShowing)
						choices.setAll(trimmedDatasets)
				}
			})

			return dropDownMenuButton
		}
	}
}
