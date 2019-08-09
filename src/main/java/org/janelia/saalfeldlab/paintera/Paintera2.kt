package org.janelia.saalfeldlab.paintera

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers
import org.janelia.saalfeldlab.paintera.serialization.Properties2
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
		mainWindow.projectDirectory.addListener { mainWindow.properties = painteraArgs.project()?.let { null } ?: Properties2(mainWindow.gridConstraintsManager) }
		mainWindow.projectDirectory.setDirectory(painteraArgs.project()?.let { File(it).absoluteFile }) { false }

		val scene = Scene(mainWindow.paneWithStatus.pane, 1600.0, 1000.0)
		primaryStage.scene = scene
		primaryStage.show()
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


