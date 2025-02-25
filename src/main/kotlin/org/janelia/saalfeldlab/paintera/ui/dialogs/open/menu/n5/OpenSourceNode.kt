package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.BooleanExpression
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.MenuButton
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState

class OpenSourceNode(
	openSourceState: OpenSourceState,
	containerLocationNode: TextField,
	browseNode: Node,
	isBusy: BooleanExpression
) : GridPane() {

	var resetAction : ((String) -> Unit)? = null

	init {
		/* Create the grid and add the root node */
		add(containerLocationNode, 0, 0)
		setColumnSpan(containerLocationNode, 2)
		setHgrow(containerLocationNode, Priority.ALWAYS)

		/* create and add the datasetDropdown Menu*/
		val datasetDropDown = openSourceState.createDatasetDropdownMenu()
		add(datasetDropDown, 0, 1)
		setHgrow(datasetDropDown, Priority.ALWAYS)

		val reparseContainer = Button("", GlyphScaleView(FontAwesomeIconView(FontAwesomeIcon.REFRESH).apply { styleClass += "refresh" }))
		reparseContainer.tooltip = Tooltip("Search for Datasets again at the current selection")
		reparseContainer.setOnAction {
			containerLocationNode.text?.let { resetAction?.invoke(it) }
		}
		add(reparseContainer, 1, 1)
		setHalignment(reparseContainer, javafx.geometry.HPos.LEFT)


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

				val nodes = validDatasets.values.toMutableList()
				nodes.removeAll(nodes.flatMap { it.childrenList() })
				return nodes.map { it.path }
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