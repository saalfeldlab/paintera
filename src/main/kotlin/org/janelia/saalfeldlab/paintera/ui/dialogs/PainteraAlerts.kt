package org.janelia.saalfeldlab.paintera.ui.dialogs

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.Modality
import javafx.stage.Window
import kotlinx.coroutines.runBlocking
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.*
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initOwnerWithDefault
import java.io.File
import java.io.IOException
import java.util.function.Function
import kotlin.jvm.optionals.getOrNull

object PainteraAlerts {

	private val LOG = KotlinLogging.logger { }

	/**
	 *
	 *
	 * @param type type of alert
	 * @param isResizable set to `true` if dialog should be resizable
	 * @return [Alert] with the title set to [Constants.NAME]
	 */
	@JvmStatic
	@JvmOverloads
	fun alert(type: Alert.AlertType?, isResizable: Boolean = true) = runBlocking {
		InvokeOnJavaFXApplicationThread.Companion {
			Alert(type)
		}.run {
			invokeOnCompletion { cause ->
				cause?.let { LOG.error(it) { "Could not create alert" } }
			}
			await()
		}
	}.also { alert ->
		alert.title = Constants.NAME
		alert.isResizable = isResizable

		/* Keep the alert on top */
		alert.initAppDialog()
	}

	/**
	 * initOwner as specified by [initOwnerWithDefault].

	 * initModality with [Modality.APPLICATION_MODAL].
	 *
	 * @receiver initOwner and initModality for
	 * @param owner  to init owner and modality with
	 * @param modality to initModality with
	 */
	@JvmStatic
	@JvmOverloads
	fun Dialog<*>.initAppDialog(owner: Window? = null, modality: Modality? = Modality.APPLICATION_MODAL) {
		initOwnerWithDefault(owner)
		initModality(modality)
		Paintera.registerStylesheets(dialogPane.scene)
	}


	/**
	 * If owner is provided, use it. Otherwise, fallback vai [.initOwner]
	 *
	 * @receiver to init owner for
	 * @param owner to initOwner with
	 */
	@JvmStatic
	@JvmOverloads
	fun Dialog<*>.initOwnerWithDefault(owner: Window? = null) {
		(owner ?: Window.getWindows().firstOrNull())?.let { this.initOwner(it) }
	}

	@JvmStatic
	@JvmOverloads
	fun confirmation(
		okButtonText: String? = null,
		cancelButtonText: String? = null,
		isResizable: Boolean = true,
		window: Window? = null,
	) = alert(Alert.AlertType.CONFIRMATION, isResizable).apply {
		setButtonText(ButtonType.OK to okButtonText, ButtonType.CANCEL to cancelButtonText)
		initAppDialog(window)
	}

	@JvmStatic
	@JvmOverloads
	fun information(
		okButtonText: String? = null,
		isResizable: Boolean = true,
		window: Window? = null,
	) = alert(Alert.AlertType.INFORMATION, isResizable).apply {
		setButtonText(ButtonType.OK to okButtonText)
		initAppDialog(window)
	}

	fun Dialog<*>.denyClose(blockExit: BooleanExpression) {
		listOf(
			DialogEvent.DIALOG_HIDING,
			DialogEvent.DIALOG_HIDDEN,
			DialogEvent.DIALOG_CLOSE_REQUEST,
		).forEach { event ->
			addEventFilter(event) {
				if (blockExit.get())
					it.consume()
			}
		}

	}

	internal fun Dialog<*>.setButtonText(vararg buttonToText: Pair<ButtonType, String?>) = buttonToText.toMap()
		.filterValues { it != null }
		.forEach { (buttonType, text) ->
			(dialogPane.lookupButton(buttonType) as Button).text = text
		}

	@JvmOverloads
	fun ignoreLockFileDialog(
		projectDirectory: ProjectDirectory,
		directory: File?,
		cancelButtontext: String? = "_Cancel",
		logFailure: Boolean = true,
	): Boolean {
		val useItProperty = SimpleBooleanProperty(true)
		val alert = confirmation("_Ignore Lock", cancelButtontext).apply {

			headerText = "Paintera project locked"
			contentText = """
				Paintera project at '$directory' is currently locked.
				A project is locked if it is accessed by a currently running Paintera instance or a Paintera instance did not terminate properly, in which case you may ignore the lock.
				Please make sure that no currently running Paintera instances access the project directory to avoid inconsistent project files.
			""".trimIndent()
		}
		try {
			projectDirectory.setDirectory(directory, Function { it: LockFile.UnableToCreateLock? ->
				useItProperty.set(alert.showAndWait().getOrNull() == ButtonType.OK)
				useItProperty.get()
			})
		} catch (e: LockFile.UnableToCreateLock) {
			if (logFailure) {
				LOG.error(e) { "Unable to ignore lock file" }
				Exceptions.Companion.exceptionAlert(Constants.NAME, "Unable to ignore lock file", e).show()
			}
			return false
		} catch (e: IOException) {
			if (logFailure) {
				LOG.error(e) { "Unable to ignore lock file" }
				Exceptions.Companion.exceptionAlert(Constants.NAME, "Unable to ignore lock file", e).show()
			}
			return false
		}
		return useItProperty.get()
	}

	fun versionDialog(): Alert {
		val versionField = TextField(Version.VERSION_STRING).apply {
			isEditable = false
			tooltip = Tooltip(text)
			HBox.setHgrow(this, Priority.ALWAYS)
		}
		val versionBox = HBox(Label("Paintera Version"), versionField).apply {
			alignment = Pos.CENTER
		}
		return information(isResizable = false).apply {
			dialogPane.content = versionBox
			headerText = "Paintera Version"
		}
	}
}