package org.janelia.saalfeldlab.paintera

import ch.qos.logback.classic.Level
import com.sun.javafx.application.PlatformImpl
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.extensions.getValue
import org.janelia.saalfeldlab.fx.extensions.setValue
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.util.n5.universe.N5Factory
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles
import kotlin.system.exitProcess

internal val paintera = PainteraMainWindow()
internal val properties
    get() = paintera.properties

fun main(args: Array<String>) {
    System.setProperty("javafx.preloader", SplashScreen::class.java.canonicalName)
    Application.launch(Paintera::class.java, *args)
}

class Paintera : Application() {

    private val painteraArgs = PainteraCommandLineArgs()
    private var projectDir: String? = null

    init {
        application = this
    }

    override fun init() {
        val cmd = CommandLine(painteraArgs).apply {
            registerConverter(Level::class.java, LogUtils.Logback.Levels.CmdLineConverter())
        }
        val exitCode = cmd.execute(*parameters.raw.toTypedArray())
        val parsedSuccessfully = (cmd.getExecutionResult() ?: false) && exitCode == 0
        if (!parsedSuccessfully) {
            Platform.exit()
            return
        }
        Platform.setImplicitExit(true)

        projectDir = painteraArgs.project()
        val projectPath = painteraArgs.project()?.let { File(it).absoluteFile }
        if (projectPath != null && !projectPath.exists()) {
            /* does the project dir exist? If not, try to make it*/
            projectPath.mkdirs()
        }
        PlatformImpl.runAndWait {
            if (projectPath != null && !projectPath.canWrite()) {
                LOG.info("User doesn't have write permissions for project at '$projectPath'. Exiting.")
                PainteraAlerts.alert(Alert.AlertType.ERROR).apply {
                    headerText = "Invalid Permissions"
                    contentText = "User doesn't have write permissions for project at '$projectPath'. Exiting."
                }.showAndWait()
                Platform.exit()
            } else if (!PainteraAlerts.ignoreLockFileDialog(paintera.projectDirectory, projectPath, "_Quit", false)) {
                LOG.info("Paintera project `$projectPath' is locked, will exit.")
                Platform.exit()
            }
        }
        projectPath?.let {
            notifyPreloader(SplashScreenShowPreloader())
            notifyPreloader(SplashScreenUpdateNotification("Loading Project: ${it.path}", false))
        }
        try {
            paintera.deserialize()
        } catch (error: Exception) {
            LOG.error("Unable to deserialize Paintera project `{}'.", projectPath, error)
            notifyPreloader(SplashScreenFinishPreloader())
            PlatformImpl.runAndWait {
                Exceptions.exceptionAlert(Constants.NAME, "Unable to open Paintera project", error).apply {
                    setOnHidden { exitProcess(Error.UNABLE_TO_DESERIALIZE_PROJECT.code) }
                    initModality(Modality.NONE)
                    showAndWait()
                }
            }
            return
        }
        projectPath?.let {
            notifyPreloader(SplashScreenUpdateNotification("Finalizing Project: ${it.path}"))
        }
        paintable = true
        runPaintable()
        PlatformImpl.runAndWait {
            paintera.properties.loggingConfig.apply {
                painteraArgs.logLevel?.let { rootLoggerLevel = it }
                painteraArgs.logLevelsByName?.forEach { (name, level) -> name?.let { setLogLevelFor(it, level) } }
            }
            painteraArgs.addToViewer(paintera.baseView) { paintera.projectDirectory.actualDirectory?.absolutePath }

            if (painteraArgs.wereScreenScalesProvided())
                paintera.properties.screenScalesConfig.screenScalesProperty().set(ScreenScalesConfig.ScreenScales(*painteraArgs.screenScales()))

            // TODO figure out why this update is necessary?
            paintera.properties.screenScalesConfig.screenScalesProperty().apply {
                val scales = ScreenScalesConfig.ScreenScales(*get().scalesCopy.clone())
                set(ScreenScalesConfig.ScreenScales(*scales.scalesCopy.map { it * 0.5 }.toDoubleArray()))
                set(scales)
            }
        }
        notifyPreloader(SplashScreenFinishPreloader())
    }

    override fun start(primaryStage: Stage) {

        primaryStage.scene = Scene(paintera.pane)
        primaryStage.scene.addEventFilter(MouseEvent.ANY, paintera.mouseTracker)
        paintera.setupStage(primaryStage)
        primaryStage.show()

        paintera.properties.viewer3DConfig.bindViewerToConfig(paintera.baseView.viewer3D())
        // window settings seem to work only when set during runlater
        paintera.properties.windowProperties.apply {
            primaryStage.width = widthProperty.get().toDouble()
            primaryStage.height = heightProperty.get().toDouble()
            widthProperty.bind(primaryStage.widthProperty())
            heightProperty.bind(primaryStage.heightProperty())
            isFullScreen.addListener { _, _, newv -> primaryStage.isFullScreen = newv }
            // have to runLater here because otherwise width and height take weird values
            primaryStage.isFullScreen = isFullScreen.value
        }
    }

    object Constants {
        const val NAME = "Paintera"
        const val PAINTERA_KEY = "paintera"
    }

    enum class Error(val code: Int, val description: String) {
        NO_PROJECT_SPECIFIED(1, "No Paintera project specified"),
        UNABLE_TO_DESERIALIZE_PROJECT(2, "Unable to deserialize Paintera project")
    }

    companion object {

        @JvmStatic
        val n5Factory = N5Factory()

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("javafx.preloader", SplashScreen::class.java.canonicalName)
            launch(Paintera::class.java, *args)
        }

        @JvmStatic
        lateinit var application: Application
            private set

        private val paintableRunnables = mutableListOf<Runnable>()

        private val paintableProperty = SimpleBooleanProperty(false)

        @JvmStatic
        var paintable: Boolean by paintableProperty
            @JvmName(name = "isPaintable")
            get
            private set

        @JvmStatic
        fun whenPaintable(onChangeToPaintable: Runnable) {
            if (paintable) {
                onChangeToPaintable.run()
            } else {
                paintableRunnables += onChangeToPaintable
            }
        }

        private fun runPaintable() {
            val onPaintableRunIter = paintableRunnables.iterator()
            for (runnable in onPaintableRunIter) {
                runnable.run()
                onPaintableRunIter.remove()
            }
        }
    }

}


