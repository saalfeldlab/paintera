package org.janelia.saalfeldlab.paintera

import ch.qos.logback.classic.Level
import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.stage.StageHelper
import com.sun.javafx.stage.WindowHelper
import javafx.application.Application
import javafx.application.Platform
import javafx.application.Preloader
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
import javafx.stage.Window
import javafx.stage.WindowEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.janelia.saalfeldlab.fx.SaalFxStyle
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera.Companion.paintable
import org.janelia.saalfeldlab.paintera.Paintera.Companion.paintableRunnables
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.debug.DebugModeProperty
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.n5.universe.PainteraN5Factory
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess


internal lateinit var paintera: PainteraMainWindow

internal val properties
	get() = paintera.properties

fun main(args: Array<String>) {
	System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)
	Application.launch(Paintera::class.java, *args)
}

/* should be null on first load, and only should be set and handled manually when re-loading a project */
private var splashScreen: PainteraSplashScreen? = null

class Paintera : Application() {

	private lateinit var commandlineArguments: Array<String>
	private lateinit var painteraArgs: PainteraCommandLineArgs
	private var projectDir: String? = null

	internal lateinit var mainWindow: PainteraMainWindow

	init {
		application = this
	}

	override fun start(primaryStage: Stage) {
		start(primaryStage, null)
	}

	private fun start(primaryStage: Stage, windowPosition: WindowPosition?) {

		primaryStage.title = Constants.NAME
		primaryStage.scene = Scene(paintera.pane, -1.0, -1.0, true)
		primaryStage.scene.addEventFilter(MouseEvent.ANY, paintera.mouseTracker)
		registerStylesheets(primaryStage.scene)

		windowPosition?.let { (x, y, width, height, fullscreen) ->
			/* set the stage, and set the properties to align */
			windowPosition.setOn(primaryStage)
			paintera.properties.windowProperties.apply {
				widthProperty.set(width.toInt())
				heightProperty.set(height.toInt())
				fullScreenProperty.set(fullscreen)
			}
		}
		paintera.setupStage(primaryStage)

		if (windowPosition == null && projectDir == null) {
			primaryStage.sizeToScene()
		}
 		primaryStage.show()

		paintera.properties.viewer3DConfig.bindViewerToConfig(paintera.baseView.viewer3D())
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

		if (!setProjectPath(projectPath)) {
			Platform.exit()
			return
		}

		projectPath?.let {
			notifySplashScreen(SplashScreenUpdateNotification("Loading Project: ${it.path}", false))
			PainteraCache.RECENT_PROJECTS.appendLine(projectPath.canonicalPath, 10)
		} ?: let {
			notifySplashScreen(SplashScreenUpdateNumItemsNotification(2, false))
			notifySplashScreen(SplashScreenUpdateNotification("Launching Paintera...", true))
		}
		try {
			paintera.deserialize()
		} catch (error: Exception) {
			LOG.error("Unable to deserialize Paintera project `{}'.", projectPath, error)
			notifySplashScreen(SplashScreenFinishPreloader())
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
			notifySplashScreen(SplashScreenUpdateNotification("Finalizing Project: ${it.path}"))
		} ?:notifySplashScreen(SplashScreenUpdateNotification("Launching Paintera...", true))
		paintable = true
		runPaintable()
		runBlocking {
			InvokeOnJavaFXApplicationThread {
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
			}.join()
		}
		notifySplashScreen(SplashScreenFinishPreloader())
	}

	private fun restartPreloader() {
		runBlocking {
			InvokeOnJavaFXApplicationThread {
				splashScreen = PainteraSplashScreen()
			}.join()
		}

		splashScreen?.init()

		runBlocking {
			InvokeOnJavaFXApplicationThread {
				val primaryStage = Stage()
				StageHelper.setPrimary(primaryStage, true)
				splashScreen?.start(primaryStage)
			}.join()
		}
		/* this initialization sequence mimics the sequence in `LauncherImpl` */
		notifySplashScreen(Preloader.ProgressNotification(0.0))
		notifySplashScreen(Preloader.ProgressNotification(1.0))
		notifySplashScreen(Preloader.StateChangeNotification(Preloader.StateChangeNotification.Type.BEFORE_LOAD, null))
	}

	private val defaultScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	private fun restartPaintera(windowPosition: WindowPosition? = null) {
		restartPreloader()
		notifySplashScreen(Preloader.StateChangeNotification(Preloader.StateChangeNotification.Type.BEFORE_INIT, application))
		application.init()
		notifySplashScreen(Preloader.StateChangeNotification(Preloader.StateChangeNotification.Type.BEFORE_START, application))
		InvokeOnJavaFXApplicationThread {
			val stage = Stage()
			application.start(stage, windowPosition)
		}
	}

	private fun parsePainteraCommandLine(vararg args: String): Boolean {
		val cmd = CommandLine(painteraArgs).apply {
			registerConverter(Level::class.java, LogUtils.Logback.Levels.CmdLineConverter())
		}
		val exitCode = cmd.execute(*args)
		return (cmd.getExecutionResult() ?: false) && exitCode == 0
	}

	private fun setProjectPath(projectPath: File?): Boolean {
		when {
			projectPath?.exists() == false ->  runCatching { projectPath.mkdirs() }.onFailure {
				Exceptions.exceptionAlert(Constants.NAME, "Unable to create project directory", it).show()
				return false
			}
			projectPath?.isDirectory == false -> {
				PainteraAlerts.alert(Alert.AlertType.ERROR).apply {
					contentText = "Project path `$projectPath' is not a directory."
					title = "Invalid Project Path"
				}.show()
				return false
			}
		}

		if (projectPath?.canWrite() == false) {
			val openReadOnly = PainteraAlerts.askOpenReadOnlyProject(projectPath)
			if (openReadOnly)
				paintera.projectDirectory.setReadOnlyDirectory(projectPath)
			return openReadOnly
		}

		val useProject = AtomicBoolean(false)
		runBlocking {
			InvokeOnJavaFXApplicationThread {
				val validProject = PainteraAlerts.ignoreLockFileDialog(paintera.projectDirectory, projectPath, "_Quit", false)
				useProject.set(validProject)
			}.join()
		}
		return useProject.get()
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

		val isTempProject = paintera.projectDirectory.directory == null

		paintera.baseView.stop()
		paintera.projectDirectory.close()
		n5Factory.clear()

		var windowPosition : WindowPosition? = null
		paintera.pane.scene.window.let { window ->
			if (!isTempProject)
				windowPosition = WindowPosition(window)
			Platform.setImplicitExit(false)
			window.onHiding = EventHandler { /*Typically exits paintera, but we don't want to here, so do nothing*/ }
			window.onCloseRequest = EventHandler { /* typically asks to save and quite, but we ask above, so do nothing */ }
			WindowHelper.setFocused(window, false)
			window.hide()
			Event.fireEvent(window, WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST))
		}
		application.commandlineArguments = projectDirectory?.let { arrayOf(it) } ?: emptyArray()
		defaultScope.launch {
			restartPaintera(windowPosition)
		}
	}

	companion object {

		private data class WindowPosition(val x: Double, val y: Double, val width: Double, val height: Double, val isFullscreen: Boolean) {
			constructor(window: Window): this(window.x, window.y, window.width, window.height, (window as? Stage)?.isFullScreen ?: false)

			fun setOn(stage: Stage) {
				/* when fullscreen, don't manually try and position the window*/
				if (isFullscreen)
					stage.isFullScreen = true
				else {
					stage.x = x
					stage.y = y
					stage.width = width
					stage.height = height
					stage.isFullScreen = isFullscreen
				}
			}
		}

		@JvmStatic
		val n5Factory = PainteraN5Factory()

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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
			"style/graphics.css",
			"style/ikonli.css",
			"style/menubar.css",
			"style/statusbar.css",
			"style/toolbar.css",
			"style/navigation.css",
			"style/interpolation.css",
			"style/sam.css",
			"style/paint.css",
			"style/raw-source.css",
			"style/mesh.css"
		)

		private fun registerPainteraStylesheets(styleable: Scene) {
			DynamicCssProperty.register(styleable.stylesheets)
			styleable.stylesheets.addAll(stylesheets)
		}

		private fun registerPainteraStylesheets(styleable: Parent) {
			DynamicCssProperty.register(styleable.stylesheets)
			styleable.stylesheets.addAll(stylesheets)
		}

		@JvmStatic
		internal var debugMode by DebugModeProperty.nonnull()


		private fun Preloader.notifyRestartedPreloader(info: Preloader.PreloaderNotification) {
			runBlocking {
				InvokeOnJavaFXApplicationThread {
					when (info) {
						is Preloader.StateChangeNotification -> handleStateChangeNotification(info)
						is Preloader.ProgressNotification -> handleProgressNotification(info)
						is Preloader.ErrorNotification -> handleErrorNotification(info)
						else -> handleApplicationNotification(info)
					}
				}.join()
			}
		}

		@JvmStatic
		@JvmName("notifySplashScreen")
		internal fun notifySplashScreen(info: Preloader.PreloaderNotification) {

			/* We should expect `splashScreen` to be null on the initial application startup, since the JavaFX Preloader will handle it the first time.
			* Unfortunately, we need to do it ourselves the next time(s) since it's not really a supported usecase to re-trigger the preloader */
			splashScreen?.notifyRestartedPreloader(info) ?: application.notifyPreloader(info)
		}
	}

}


