package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import javafx.beans.binding.BooleanExpression
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.MenuButton
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState
import se.sawano.java.text.AlphanumericComparator

class OpenSourceNode(
	openSourceState: OpenSourceState,
	containerLocationNode: Node,
	browseNode: Node,
	isBusy: BooleanExpression
) : GridPane() {

	init {
		/* Create the grid and add the root node */
		add(containerLocationNode, 0, 0)
		setColumnSpan(containerLocationNode, 2)
		setHgrow(containerLocationNode, Priority.ALWAYS)

		/* create and add the datasetDropdown Menu*/
		val datasetDropDown = openSourceState.createDatasetDropdownMenu()
		add(datasetDropDown, 1, 1)
		setHgrow(datasetDropDown, Priority.ALWAYS)

		add(browseNode, 2, 0)

		val progressIndicator = ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS)
		progressIndicator.scaleX = .75
		progressIndicator.scaleY = .75

		add(progressIndicator, 2, 1)
		setHgrow(progressIndicator, Priority.NEVER)
		setVgrow(progressIndicator, Priority.NEVER)
		progressIndicator.visibleProperty().bind(isBusy)
	}


	companion object {
		const val DATASET_PROMPT = "_Dataset"

		private fun OpenSourceState.createDatasetDropdownMenu(): MenuButton {

			fun trimValidDatasetChoices(): List<String> {
				var prev = 0
				val sortedDatasets = validDatasets.keys.sortedWith(AlphanumericComparator()).toMutableList()
				val trimmedDatasets = sortedDatasets.filterIndexed { idx, dataset ->
					if (idx == prev)
						return@filterIndexed true

					var prefixExists = dataset.startsWith(sortedDatasets[prev])
					if (!prefixExists)
						prev = idx
					!prefixExists
				}.filterNotNull().toList()
				return trimmedDatasets
			}

			val choices: ObservableList<String> = FXCollections.observableArrayList()


			val dropDownMenuButton = MatchSelectionMenuButton(choices, null, null) { selection ->
				activeNodeProperty.set(validDatasets.get()[selection])
			}.apply {
				cutoff = 50

				var datasetPathTooltip = activeNodeProperty.createObservableBinding { datasetPath?.let { Tooltip(it) } }
				tooltipProperty().bind(datasetPathTooltip)

				val disableWhenEmpty = validDatasets.createObservableBinding { validDatasets.get().isEmpty() }
				disableProperty().bind(disableWhenEmpty)

				val datasetDropDownText = activeNodeProperty.createObservableBinding {
					if (datasetPath.isNullOrEmpty()) DATASET_PROMPT
					else "$DATASET_PROMPT: $datasetPath"
				}
				textProperty().bind(datasetDropDownText)

				val prevOnShowing = onShowing
				setOnShowing {
					choices.setAll(trimValidDatasetChoices())
					prevOnShowing.handle(it)
				}
			}

			/* If the dataset choices are changed, create new menuItems, and update*/
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