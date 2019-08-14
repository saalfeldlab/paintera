package org.janelia.saalfeldlab.paintera

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles

class Paintera2 : Application() {

	val mainWindow = PainteraMainWindow()

	override fun start(primaryStage: Stage) {
		val painteraArgs = PainteraCommandLineArgs()
		val cmd = CommandLine(painteraArgs)
		val exitCode = cmd.execute(*parameters.raw.toTypedArray())
		val parsedSuccessfully = (cmd.getExecutionResult() ?: false) && exitCode == 0
		if (!parsedSuccessfully) {
			Platform.exit()
			return
		}
		Platform.setImplicitExit(true)
		setupStage(primaryStage)
		primaryStage.addEventHandler(WindowEvent.WINDOW_HIDDEN) { mainWindow.projectDirectory.close() }

		mainWindow.projectDirectory.setDirectory(painteraArgs.project()?.let { File(it).absoluteFile }) { false }
		mainWindow.deserialize()

		if (painteraArgs.wereScreenScalesProvided())
			mainWindow.getProperties().screenScalesConfig.screenScalesProperty().set(ScreenScalesConfig.ScreenScales(*painteraArgs.screenScales()))

		// TODO figure out why this update is necessary?
		mainWindow.getProperties().screenScalesConfig.screenScalesProperty().let {
			val scales = ScreenScalesConfig.ScreenScales(*it.get().scalesCopy.clone())
			it.set(ScreenScalesConfig.ScreenScales(*scales.scalesCopy.map { it * 0.5 }.toDoubleArray()))
			it.set(scales)
		}

		val scene = Scene(mainWindow.getPane(), 1600.0, 1000.0)
		mainWindow.keyTracker.installInto(scene)
		scene.addEventFilter(MouseEvent.ANY, mainWindow.mouseTracker)
		primaryStage.scene = scene
		primaryStage.show()

		println("Please remove this handler (was added for testing purposes")
		scene.addEventHandler(KeyEvent.KEY_PRESSED) {
			println("Please remove this handler (was added for testing purposes")
			if (KeyCodeCombination(KeyCode.CLOSE_BRACKET, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN).match(it)) {
				it.consume()
				val alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
				(alert.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_OK"
				(alert.dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
				alert.initModality(Modality.NONE)
				alert.show()
			}
		}

		mainWindow.getProperties().viewer3DConfig.bindViewerToConfig(mainWindow.baseView.viewer3D())

	}

	private fun setupStage(stage: Stage) {
		mainWindow.projectDirectory.addListener { pd -> stage.title = if (pd.directory == null) NAME else "$NAME ${replaceUserHomeWithTilde(pd.directory.absolutePath)}" }
		stage.icons.addAll(
				Image(javaClass.getResourceAsStream("/icon-16.png")),
				Image(javaClass.getResourceAsStream("/icon-32.png")),
				Image(javaClass.getResourceAsStream("/icon-48.png")),
				Image(javaClass.getResourceAsStream("/icon-64.png")),
				Image(javaClass.getResourceAsStream("/icon-96.png")),
				Image(javaClass.getResourceAsStream("/icon-128.png")))
	}

	companion object {
		@JvmStatic
		val NAME = "Paintera"

		private fun gsonBuilder(
				baseView: PainteraBaseView,
				projectDirectory: ProjectDirectory) = GsonHelpers
					.builderWithAllRequiredSerializers(baseView) { projectDirectory.actualDirectory.absolutePath }
					.setPrettyPrinting()

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val USER_HOME_AT_BEGINNING_REGEX = "$^${System.getProperty("user.home")}"

		private fun replaceUserHomeWithTilde(path: String) = path.replaceFirst(USER_HOME_AT_BEGINNING_REGEX, "~")

		@JvmStatic
		fun main(args: Array<String>) {
			launch(Paintera2::class.java, *args)
		}
	}

}


