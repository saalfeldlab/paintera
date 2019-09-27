package org.janelia.saalfeldlab.paintera

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.input.MouseEvent
import javafx.stage.Stage
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
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

		val projectPath = painteraArgs.project()?.let { File(it).absoluteFile }
		if (!PainteraAlerts.ignoreLockFileDialog(mainWindow.projectDirectory, projectPath, "_Quit", false)) {
			LOG.info("Paintera project `$projectPath' is locked, will exit.")
			Platform.exit()
		}
		else {
			mainWindow.deserialize()

			painteraArgs.addToViewer(mainWindow.baseView) { mainWindow.projectDirectory.actualDirectory?.absolutePath }

			if (painteraArgs.wereScreenScalesProvided())
				mainWindow.properties.screenScalesConfig.screenScalesProperty().set(ScreenScalesConfig.ScreenScales(*painteraArgs.screenScales()))

			// TODO figure out why this update is necessary?
			mainWindow.properties.screenScalesConfig.screenScalesProperty().let {
				val scales = ScreenScalesConfig.ScreenScales(*it.get().scalesCopy.clone())
				it.set(ScreenScalesConfig.ScreenScales(*scales.scalesCopy.map { it * 0.5 }.toDoubleArray()))
				it.set(scales)
			}

			primaryStage.scene = Scene(mainWindow.pane)
			primaryStage.scene.addEventFilter(MouseEvent.ANY, mainWindow.mouseTracker)
			mainWindow.setupStage(primaryStage)
			primaryStage.show()

			mainWindow.properties.viewer3DConfig.bindViewerToConfig(mainWindow.baseView.viewer3D())
			// window settings seem to work only when set during runlater
			Platform.runLater {
				mainWindow.properties.windowProperties.let {
					primaryStage.width = it.widthProperty.get().toDouble()
					primaryStage.height = it.heightProperty.get().toDouble()
					it.widthProperty.bind(primaryStage.widthProperty())
					it.heightProperty.bind(primaryStage.heightProperty())
					it.isFullScreen.addListener { _, _, newv -> primaryStage.isFullScreen = newv }
					// have to runLater here because otherwise width and height take weird values
					Platform.runLater { primaryStage.isFullScreen = it.isFullScreen.value }
				}
			}
		}
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		@JvmStatic
		fun main(args: Array<String>) = launch(Paintera2::class.java, *args)
	}

}


