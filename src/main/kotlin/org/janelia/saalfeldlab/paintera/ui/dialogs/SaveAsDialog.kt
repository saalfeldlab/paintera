package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.Companion.homeToTilde
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.Companion.tildeToHome
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.io.File
import java.nio.file.Paths
import kotlin.jvm.optionals.getOrNull

internal object SaveAsDialog {
	private const val DIALOG_HEADER = "Save project directory at location"
	private const val DIRECTORY_FIELD_PROMPT_TEXT = "Project Directory"

	private const val BROWSE_LABEL = "_Browse"

	private const val INVALID_PERMISSIONS = "Invalid Permissions"
	private const val INVALID_DIR = "Invalid Directory"
	private const val FILE_WHEN_DIR_EXPECTED = "Directory expected but got file"
	private const val PICK_VALID_DIR = "Please specify valid directory."
	private const val NO_WRITE_PERMISSIONS = "Paintera does not have write permissions for the provided projected directory:"
	private const val CONTAINER_EXISTS = "Container exists"
	private const val CONTAINER_EXISTS_AT = "N5 container (and potentially a Paintera project) exists at "
	private const val ASK_OVERWRITE = "Overwrite?"
	private const val OVERWRITE_LABEL = "_Overwrite"
	private const val CANCEL_LABEL = "_Cancel"
	private const val SAVE_LABEL = "_Save"


	private val relativeToAbsolutePathConverter = object : StringConverter<File?>() {
		override fun toString(file: File?) = file?.path?.homeToTilde()
		override fun fromString(string: String?) = string?.tildeToHome()?.let { Paths.get(it).toAbsolutePath().toFile() }
	}

	private val directoryChooser by lazy {
		DirectoryChooser().apply {
			initialDirectoryProperty().addListener { _, _, dir ->
				dir?.mkdirs()
			}
		}
	}
	private val directoryProperty = SimpleObjectProperty(paintera.projectDirectory.directory)
	private var directory by directoryProperty.nullable()

	private val directoryField by lazy {
		TextField().also {
			it.promptText = DIRECTORY_FIELD_PROMPT_TEXT
			HBox.setHgrow(it, Priority.ALWAYS)
		}
	}

	private val browseButton by lazy {
		Buttons.withTooltip(BROWSE_LABEL, null) {
			directoryChooser.initialDirectory = directory?.let { it.takeUnless { it.isFile } ?: it.parentFile }
			directoryChooser.showDialog(paintera.pane.scene.window)?.let { directory = it }
		}.apply {
			prefWidth = 100.0
		}
	}


	private val dialog: Alert by lazy {
		PainteraAlerts.confirmation(SAVE_LABEL, CANCEL_LABEL).apply {
			headerText = DIALOG_HEADER
			dialogPane.apply {
				content = HBox(directoryField, browseButton).apply { alignment = Pos.CENTER }
				setupOkButtonAction()
			}
		}
	}

	init {
		Bindings.bindBidirectional(directoryField.textProperty(), directoryProperty, relativeToAbsolutePathConverter)
	}

	private fun DialogPane.setupOkButtonAction() {
		lookupButton(ButtonType.OK).also { bt ->
			bt.addEventFilter(ActionEvent.ACTION) { it ->
				directory?.apply {
					/* Let's create the directories first (or at least try). This let's us check permission later on.
							*   If we can't mkdirs, it wont have any effect. If we CAN, then it would have later anyway. */
					mkdirs()
				}
				var useIt = true
				val dir = directory

				var errorHeader: String? = null
				var errorContent: String? = null
				if (directory === null || dir!!.isFile) {
					errorHeader = INVALID_DIR
					errorContent = "$FILE_WHEN_DIR_EXPECTED `$dir'. $PICK_VALID_DIR"
					useIt = false
				} else if (!dir.canWrite()) {
					errorHeader = INVALID_PERMISSIONS
					errorContent = "$NO_WRITE_PERMISSIONS $dir'.\n$PICK_VALID_DIR"
					useIt = false
				}

				if (!useIt) {
					PainteraAlerts.alert(Alert.AlertType.ERROR, true).apply {
						headerText = errorHeader
						contentText = errorContent
					}.show()
				} else {
					val attributes = dir!!.toPath().toAbsolutePath().resolve("attributes.json").toFile()
					if (attributes.exists()) {
						useIt = useIt && PainteraAlerts.alert(Alert.AlertType.CONFIRMATION).apply {
							headerText = CONTAINER_EXISTS
							contentText = "$CONTAINER_EXISTS_AT '$dir'. $ASK_OVERWRITE"
							(dialogPane.lookupButton(ButtonType.OK) as Button).text = OVERWRITE_LABEL
							(dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = CANCEL_LABEL
						}.showAndWait().filter { btn -> ButtonType.OK == btn }.isPresent
					}

					useIt = useIt && PainteraAlerts.ignoreLockFileDialog(paintera.projectDirectory, dir)
				}
				if (!useIt) it.consume()
			}
			bt.disableProperty().bind(directoryProperty.isNull)
		}
	}

	internal fun showAndWaitForResponse(): Boolean {
		return dialog.showAndWait().getOrNull() == ButtonType.OK
	}

}
