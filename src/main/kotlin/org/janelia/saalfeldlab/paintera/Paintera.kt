package org.janelia.saalfeldlab.paintera

import ch.qos.logback.classic.Level
import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.stage.WindowHelper
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.janelia.saalfeldlab.fx.SaalFxStyle
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera.Companion.paintable
import org.janelia.saalfeldlab.paintera.Paintera.Companion.paintableRunnables
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.n5.universe.N5FactoryWithCache
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles
import kotlin.system.exitProcess


internal lateinit var paintera: PainteraMainWindow

internal val properties
	get() = paintera.properties

fun main(args: Array<String>) {
	System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)
	Application.launch(Paintera::class.java, *args)
}

class Paintera : Application() {

	private lateinit var commandlineArguments: Array<String>
	private lateinit var painteraArgs: PainteraCommandLineArgs
	private var projectDir: String? = null

	internal lateinit var mainWindow: PainteraMainWindow

	init {
		application = this
		/* add window listener for scenes */
	}

	override fun init() {
		paintable = false
		if (!::commandlineArguments.isInitialized) {
			commandlineArguments = parameters?.raw?.toTypedArray() ?: emptyArray()
		}
		painteraArgs = PainteraCommandLineArgs()
		if (commandlineArguments.isNotEmpty() && !parsePainteraCommandLine(*commandlineArguments)) {
			Platform.exit()
			return
		}
		Platform.setImplicitExit(true)
		paintera = PainteraMainWindow().also {
			mainWindow = it
		}

		projectDir = painteraArgs.project()
		val projectPath = projectDir?.let { File(it).absoluteFile }
		if (!canAccessProjectDir(projectPath)) {
			Platform.exit()
			return
		}

		projectPath?.let {
			notifyPreloader(SplashScreenUpdateNotification("Loading Project: ${it.path}", false))
			PainteraCache.RECENT_PROJECTS.appendLine(projectPath.canonicalPath, 10)
		} ?: let {
			notifyPreloader(SplashScreenUpdateNumItemsNotification(2, false))
			notifyPreloader(SplashScreenUpdateNotification("Launching Paintera...", true))
		}
		try {
			paintera.deserialize()
		} catch (error: Exception) {
			LOG.error("Unable to deserialize Paintera project `{}'.", projectPath, error)
			notifyPreloader(SplashScreenFinishPreloader())
			PlatformImpl.runAndWait {
				Exceptions.exceptionAlert(Constants.NAME, "Unable to open Paintera project", error).apply {
					setOnHidden { exitProcess(0); }
					initModality(Modality.NONE)
					showAndWait()
				}
			}
			Platform.exit()
			return
		}
		projectPath?.let {
			notifyPreloader(SplashScreenUpdateNotification("Finalizing Project: ${it.path}"))
		} ?: notifyPreloader(SplashScreenUpdateNotification("Launching Paintera...", true))
		paintable = true
		runPaintable()
		InvokeOnJavaFXApplicationThread.invokeAndWait {
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

	private fun parsePainteraCommandLine(vararg args: String): Boolean {
		val cmd = CommandLine(painteraArgs).apply {
			registerConverter(Level::class.java, LogUtils.Logback.Levels.CmdLineConverter())
		}
		val exitCode = cmd.execute(*args)
		return (cmd.getExecutionResult() ?: false) && exitCode == 0
	}

	private fun canAccessProjectDir(projectPath: File?): Boolean {
		if (projectPath != null && !projectPath.exists()) {
			/* does the project dir exist? If not, try to make it*/
			projectPath.mkdirs()
		}
		var projectDirAccess = true
		PlatformImpl.runAndWait {
			if (projectPath != null && !projectPath.canWrite()) {
				LOG.info("User doesn't have write permissions for project at '$projectPath'. Exiting.")
				PainteraAlerts.alert(Alert.AlertType.ERROR).apply {
					headerText = "Invalid Permissions"
					contentText = "User doesn't have write permissions for project at '$projectPath'. Exiting."
				}.showAndWait()
				projectDirAccess = false
			} else if (!PainteraAlerts.ignoreLockFileDialog(paintera.projectDirectory, projectPath, "_Quit", false)) {
				LOG.info("Paintera project `$projectPath' is locked, will exit.")
				projectDirAccess = false
			}
		}
		return projectDirAccess
	}

	override fun start(primaryStage: Stage) {

		primaryStage.scene = Scene(paintera.pane)
		primaryStage.scene.addEventFilter(MouseEvent.ANY, paintera.mouseTracker)
		registerStylesheets(primaryStage.scene)

		paintera.setupStage(primaryStage)
		primaryStage.show()

		paintera.properties.viewer3DConfig.bindViewerToConfig(paintera.baseView.viewer3D())

		paintera.properties.windowProperties.apply {
			primaryStage.width = widthProperty.get().toDouble()
			primaryStage.height = heightProperty.get().toDouble()
			widthProperty.bind(primaryStage.widthProperty())
			heightProperty.bind(primaryStage.heightProperty())
			fullScreenProperty.addListener { _, _, newv -> primaryStage.isFullScreen = newv }
			primaryStage.isFullScreen = fullScreenProperty.value
		}
	}

	fun loadProject(projectDirectory: String? = null) {
		projectDirectory?.let {
			if (n5Factory.openReaderOrNull(it) == null) {
				val alert = PainteraAlerts.alert(Alert.AlertType.WARNING)
				alert.headerText = "Paintera Project Not Found"
				alert.contentText = """
					No Paintera project at:
						${projectDirectory}
				""".trimIndent()
				alert.showAndWait()
				return
			}
		}

		paintera.baseView.sourceInfo().apply {
			trackSources()
				.filterIsInstance(MaskedSource::class.java)
				.forEach { source ->
					(getState(source) as? ConnectomicsLabelState)?.let { state ->
						val responseButton = state.promptForCommitIfNecessary(paintera.baseView) { index, name ->
							"""
							Closing current Paintera project.
							Uncommitted changes to the canvas will be lost for source $index: $name if skipped.
							Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any)
							but can be committed to the data backend, as well
							""".trimIndent()
						}

						when (responseButton) {
							ButtonType.OK -> Unit
							ButtonType.NO -> state.skipCommit = true
							ButtonType.CANCEL, ButtonType.CLOSE -> return
							null -> Unit
						}
					}
				}
		}


		if (!paintera.askSaveAndQuit()) {
			return
		}
		paintera.baseView.stop()
		paintera.projectDirectory.close()
		n5Factory.clearCache()

		paintera.pane.scene.window.let { window ->
			Platform.setImplicitExit(false)
			window.onHiding = EventHandler { /*Typically exits paintera, but we don't want to here, so do nothing*/ }
			window.onCloseRequest = EventHandler { /* typically asks to save and quite, but we ask above, so do nothing */ }
			WindowHelper.setFocused(window, false)
			window.hide()
			Event.fireEvent(window, WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST))
		}
		application.commandlineArguments = projectDirectory?.let { arrayOf(it) } ?: emptyArray()
		application.init()
		InvokeOnJavaFXApplicationThread { application.start(Stage()) }

	}

	companion object {

		@JvmStatic
		internal var debugMode = System.getenv("PAINTERA_DEBUG")?.equals("1") ?: false

		@JvmStatic
		val n5Factory = N5FactoryWithCache().apply {
			cacheAttributes(true)
		}

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		@JvmStatic
		fun main(args: Array<String>) {
			System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)
			launch(Paintera::class.java, *args)
		}

		@JvmStatic
		fun getPaintera() = paintera

		@JvmStatic
		lateinit var application: Paintera
			private set

		private val paintableRunnables = mutableListOf<Runnable>()

		private val paintableProperty = SimpleBooleanProperty(false)

		@JvmStatic
		private var paintable: Boolean by paintableProperty.nonnull()

		/**
		 * Run [runIfPaintable] if [paintable]. Otherwise, do nothing
		 *
		 * @param runIfPaintable the runnable to execute if [paintable]
		 */
		@JvmStatic
		fun ifPaintable(runIfPaintable: Runnable) {
			if (paintable) {
				whenPaintable(runIfPaintable)
			}
		}

		/**
		 * Add [onChangeToPaintable] to a queue, to be executed (FIFO) when Paintera is [paintable].
		 * If already [paintable], and the [paintableRunnables] queue is empty, [onChangeToPaintable] will be executed immediately
		 *
		 * @param onChangeToPaintable
		 */
		@JvmStatic
		fun whenPaintable(onChangeToPaintable: Runnable) {
			synchronized(paintableRunnables) {
				if (paintable) {
					if (paintableRunnables.isNotEmpty()) {
						paintableRunnables.add(onChangeToPaintable)
					} else {
						onChangeToPaintable.run()
					}
				} else {
					paintableRunnables += onChangeToPaintable
				}
			}
		}

		private fun runPaintable() {
			synchronized(paintableRunnables) {
				while (paintableRunnables.isNotEmpty()) {
					paintableRunnables.removeAt(0).run()
				}
			}
		}

		@JvmStatic
		fun registerStylesheets(styleable: Scene) {
			registerPainteraStylesheets(styleable)
			SaalFxStyle.registerStylesheets(styleable)
		}

		@JvmStatic
		fun registerStylesheets(styleable: Parent) {
			registerPainteraStylesheets(styleable)
			SaalFxStyle.registerStylesheets(styleable)
		}

		private val stylesheets: List<String> = listOf(
			"style/glyphs.css",
			"style/toolbar.css",
			"style/navigation.css",
			"style/interpolation.css",
			"style/sam.css",
			"style/paint.css",
			"style/raw-source.css",
			"style/mesh-status-bar.css"
		)

		private fun registerPainteraStylesheets(styleable: Scene) {
			styleable.stylesheets.addAll(stylesheets)
		}

		private fun registerPainteraStylesheets(styleable: Parent) {
			styleable.stylesheets.addAll(stylesheets)
		}
	}

}


