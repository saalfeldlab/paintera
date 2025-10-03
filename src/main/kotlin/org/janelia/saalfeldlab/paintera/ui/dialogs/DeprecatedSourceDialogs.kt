package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.property.BooleanProperty
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.TextArea
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.confirmation
import kotlin.jvm.optionals.getOrNull

@Deprecated("")
internal object DeprecatedSourceDialogs {

	@Deprecated("")
	fun askConvertDeprecatedStatesShowAndWait(
		choiceProperty: BooleanProperty,
		rememberChoiceProperty: BooleanProperty,
		deprecatedStateType: Class<*>,
		convertedStateType: Class<*>,
		datasetDescriptor: Any?,
	): Boolean {
		if (rememberChoiceProperty.get())
			return choiceProperty.get()

		val askConvertDialog = askConvertDeprecatedStates(rememberChoiceProperty, deprecatedStateType, convertedStateType, datasetDescriptor)
		return when (askConvertDialog.showAndWait().getOrNull()) {
			ButtonType.OK -> {
				choiceProperty.value = true
				true
			}
			else -> false
		}
	}

	@Deprecated("")
	fun askConvertDeprecatedStates(
		rememberChoiceProperty: BooleanProperty?,
		deprecatedStateType: Class<*>,
		convertedStateType: Class<*>,
		datasetDescriptor: Any?,
	) = confirmation("_Update", "_Skip").apply {

		val message = TextArea(
			"""
				Dataset '$datasetDescriptor' was opened in a deprecated format (${deprecatedStateType.simpleName}). 
				Paintera can try to convert and update the dataset into a new format (${convertedStateType.simpleName}) that supports relative data locations. 
				The new format is incompatible with Paintera versions 0.21.0 and older but updating datasets is recommended. 
				Backup files are generated for the Paintera files as well as for any dataset attributes that may have been modified during the process.
			""".trimIndent()
		).apply {
			isEditable = false
			isWrapText = true
			prefColumnCount *= 2
			prefRowCount *= 2
		}

		val rememberChoice = CheckBox("_Remember choice for all datasets in project")
		rememberChoice.selectedProperty().bindBidirectional(rememberChoiceProperty)
		headerText = "Update deprecated data set"
		dialogPane.content = VBox(message, rememberChoice)
	}
}