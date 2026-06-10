package org.janelia.saalfeldlab.paintera.ui.dialogs

import com.google.gson.JsonElement
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
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.Companion.homeToTilde
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.Companion.tildeToHome
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import java.io.File
import java.nio.file.Paths
import kotlin.jvm.optionals.getOrNull

internal object OpenProjectDialog {
	private const val DIALOG_HEADER = "Open project directory at location"
	private const val DIRECTORY_FIELD_PROMPT_TEXT = "Project Directory"

	private const val BROWSE_LABEL = "_Browse"

	private const val INVALID_PERMISSIONS = "Invalid Permissions"
	private const val INVALID_DIR = "Invalid Directory"
	private const val DIR_DOES_NOT_EXIST = "Directory does not exist"
	private const val FILE_WHEN_DIR_EXPECTED = "Directory expected but got file"
	private const val PICK_VALID_PROJECT_DIR = "Please specify a valid project directory."
	private const val NO_READ_PERMISSIONS = "Paintera does not have read permissions for the provided project directory"
	private const val NO_PROJECT = "No Paintera Project"
	private const val NO_PROJECT_AT = "No Paintera project found at"
	private const val CANCEL_LABEL = "_Cancel"
	private const val OPEN_LABEL = "_Open"

	private const val PAINTERA_PROJECT_KEY = "paintera"

	private val relativeToAbsolutePathConverter = object : StringConverter<File?>() {
		override fun toString(file: File?) = file?.path?.homeToTilde()
		override fun fromString(string: String?) = string?.tildeToHome()?.let { Paths.get(it).toAbsolutePath().toFile() }
	}

	private val directoryChooser by lazy { DirectoryChooser() }

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
			directoryChooser.initialDirectory = directory
				?.let { it.takeUnless { it.isFile } ?: it.parentFile }
				?.takeIf { it.exists() }
			/* own the chooser by the main window, so it centers on the application instead of pinning to the dialog */
			directoryChooser.showDialog(paintera.pane.scene.window)?.let { directory = it }
		}.apply {
			prefWidth = 100.0
		}
	}

	private val dialog: Alert by lazy {
		PainteraAlerts.confirmation(OPEN_LABEL, CANCEL_LABEL).apply {
			initAppDialog(paintera.pane.scene.window)
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
				val dir = directory

				var errorHeader: String? = null
				var errorContent: String? = null
				if (dir === null || !dir.exists()) {
					errorHeader = INVALID_DIR
					errorContent = "$DIR_DOES_NOT_EXIST:\n\t`$dir'.\n\n$PICK_VALID_PROJECT_DIR"
				} else if (dir.isFile) {
					errorHeader = INVALID_DIR
					errorContent = "$FILE_WHEN_DIR_EXPECTED:\n\t`$dir'.\n\n$PICK_VALID_PROJECT_DIR"
				} else if (!dir.canRead()) {
					errorHeader = INVALID_PERMISSIONS
					errorContent = "$NO_READ_PERMISSIONS:\n\t`$dir'.\n\n$PICK_VALID_PROJECT_DIR"
				} else if (!dir.containsPainteraProject()) {
					errorHeader = NO_PROJECT
					errorContent = "$NO_PROJECT_AT:\n\t`$dir'.\n\n$PICK_VALID_PROJECT_DIR"
				}

				if (errorHeader != null) {
					PainteraAlerts.alert(Alert.AlertType.ERROR, true).apply {
						initAppDialog(scene.window)
						headerText = errorHeader
						contentText = errorContent
					}.show()
					it.consume()
				}
			}
			bt.disableProperty().bind(directoryProperty.isNull)
		}
	}

	/* a valid project is an N5 container whose root attributes contain the `paintera` key */
	private fun File.containsPainteraProject(): Boolean = runCatching {
		Paintera.n5Factory.openReaderOrNull(toURI().toString())
			?.getAttribute("/", PAINTERA_PROJECT_KEY, JsonElement::class.java)
			?.isJsonObject == true
	}.getOrDefault(false)

	internal fun showAndWaitForResponse(): File? {
		return dialog.showAndWait().getOrNull()
			?.takeIf { it == ButtonType.OK }
			?.let { directory }
	}
}
