package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.image.Image
import javafx.stage.Stage
import net.imglib2.realtransform.AffineTransform3D
import org.controlsfx.control.Notifications
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys.NAMED_COMBINATIONS
import org.janelia.saalfeldlab.paintera.Version.VERSION_STRING
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.dialogs.SaveAndQuitDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.SaveAsDialog
import org.janelia.saalfeldlab.util.PainteraCache
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess

class PainteraMainWindow(val gateway: PainteraGateway = PainteraGateway()) {

	val baseView = PainteraBaseView(
		PainteraBaseView.reasonableNumFetcherThreads(),
		ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()),
		KeyAndMouseConfig()
	)

	val keyTracker = KeyTracker()

	val mouseTracker = MouseTracker()

	val projectDirectory = ProjectDirectory()

	lateinit var properties: Properties
		private set

	internal lateinit var paneWithStatus: BorderPaneWithStatusBars

	val activeViewer = SimpleObjectProperty<ViewerPanelFX?>()

	internal val currentSource: SourceState<*, *>?
		get() = baseView.sourceInfo().currentState().get()

	internal val currentMode: ControlMode?
		get() = baseView.activeModeProperty.value

	internal lateinit var defaultHandlers: PainteraDefaultHandlers

	internal val pane: Parent
		get() = paneWithStatus.pane

	private lateinit var newProjectSettings: JsonElement

	private fun initProperties(properties: Properties) {
		this.properties = properties
		this.baseView.keyAndMouseBindings = this.properties.keyAndMouseConfig
		this.paneWithStatus = BorderPaneWithStatusBars(this)
		this.defaultHandlers = PainteraDefaultHandlers(this, paneWithStatus)
		activeViewer.bind(paintera.baseView.currentFocusHolder.createNullableValueBinding { it?.viewer() })

		/* Set newProjectSettings */
		val builder = GsonHelpers
			.builderWithAllRequiredSerializers(gateway.context, baseView) { projectDirectory.actualDirectory.absolutePath }
			.setPrettyPrinting()
		Paintera.n5Factory.gsonBuilder(builder)
		this.newProjectSettings = builder.create().toJsonTree(this)
	}

	fun deserialize() {

		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
		val arguments = StatefulSerializer.Arguments(baseView)
		val builder = GsonHelpers.builderWithAllRequiredDeserializers(
			gateway.context,
			arguments,
			{ projectDirectory.actualDirectory.absolutePath },
			{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory.actualDirectory.toURI()
			.let { Paintera.n5Factory.openReader(it.toString()).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
			?.takeIf { it.isJsonObject }
			?.asJsonObject
		Paintera.n5Factory.gsonBuilder(builder)
		deserialize(json, gson, indexToState)
		arguments.convertDeprecatedDatasets.let {
			if (it.wereAnyConverted.value)
				it.backupFiles += StatefulSerializer.Arguments.ConvertDeprecatedDatasets.BackupFile(getAttributesFile(), backupProjectAttributesWithDate())
		}
	}

	private fun isSaveNecessary(): Boolean {
		val builder = GsonHelpers
			.builderWithAllRequiredSerializers(gateway.context, baseView) { projectDirectory.actualDirectory.absolutePath }
			.setPrettyPrinting()
		Paintera.n5Factory.gsonBuilder(builder)
		Paintera.n5Factory.openReader(projectDirectory.actualDirectory.absolutePath).use {
			val expectedSettings = pruneNonEssentialSettings(builder.create().toJsonTree(this))
			val actualSettings = pruneNonEssentialSettings(it.getAttribute("/", PAINTERA_KEY, JsonObject::class.java))
			/* If settings file already exists, compare against them; If they don't, compare against newProjectSettings
			*   This helps avoid annoying "Save Before Quit?" prompts when you open Paintera, and immediately
			*   Open a different project. */
			return expectedSettings != (actualSettings ?: pruneNonEssentialSettings(newProjectSettings))
		}
	}

	private fun pruneNonEssentialSettings(allSettings: JsonElement?): JsonElement? {
		return allSettings?.asJsonObject?.also { mainWindow ->
			mainWindow.remove(GLOBAL_TRANSFORM_KEY)
			mainWindow.remove(Properties::windowProperties.name)
			mainWindow.remove(VERSION_KEY)
			/* Should match Viewer3DConfigSerializer AFFINE_KEY*/
			mainWindow.get("viewer3DConfig")?.asJsonObject?.remove("affine")
		}
	}

	fun save(notify: Boolean = true) {

		/* Not allowed to save if any source is RAI */
		baseView.sourceInfo().canSourcesBeSerialized().getOrNull()?.let { reasonSoureInfoCannotBeSerialized ->
			val alert = PainteraAlerts.alert(Alert.AlertType.WARNING)
			alert.title = "Cannot Serialize All Sources"
			alert.contentText = reasonSoureInfoCannotBeSerialized
			alert.showAndWait()
			return
		}

		// ensure that the application is in the normal mode when the project is saved
		val curMode = baseView.activeModeProperty.value
		baseView.setDefaultToolMode()

		val builder = GsonHelpers
			.builderWithAllRequiredSerializers(gateway.context, baseView) { projectDirectory.actualDirectory.absolutePath }
			.setPrettyPrinting()
		Paintera.n5Factory.gsonBuilder(builder)
		Paintera.n5Factory.remove(projectDirectory.actualDirectory.absolutePath)
		Paintera.n5Factory.newWriter(projectDirectory.actualDirectory.absolutePath).use {
			it.setAttribute("/", PAINTERA_KEY, this)
		}
		if (notify) {
			PainteraCache.RECENT_PROJECTS.appendLine(projectDirectory.directory.canonicalPath, 15)
			InvokeOnJavaFXApplicationThread {
				showSaveCompleteNotification()
			}
		}

		/* Change back to the correct mode. */
		baseView.changeMode(curMode)
	}

	private fun showSaveCompleteNotification(owner: Any = baseView.node.scene.window) {
		val saveNotification = Notifications.create()
			.graphic(FontAwesome[FontAwesomeIcon.CHECK_CIRCLE])
			.title("Save Project")
			.text("Save Complete")
			.owner(owner)
		saveNotification
			.threshold(1, saveNotification)
			.show()
	}

	internal fun saveAs(notify: Boolean = true): Boolean {
		if (SaveAsDialog.showAndWaitForResponse()) {
			LOG.info("Saving project to directory {}", projectDirectory.actualDirectory.absolutePath)
			save(notify)
			return true
		}
		return false
	}

	internal fun saveOrSaveAs(notify: Boolean = true) {
		projectDirectory.directory?.let { save(notify) } ?: saveAs(notify)
	}

	@JvmOverloads
	fun backupProjectAttributesWithDate(
		date: Date = Date(),
		dateFormat: DateFormat = SimpleDateFormat("'.bkp.'yyyy-mm-dd_HH-mm-ss"),
		overwrite: Boolean = false,
	) = backupProjectAttributes(dateFormat.format(date), overwrite)

	@JvmOverloads
	fun backupProjectAttributes(
		suffix: String = ".bkp",
		overwrite: Boolean = false,
	) = backupProjectAttributes(File("${getAttributesFile().absolutePath}$suffix"), overwrite)

	@JvmOverloads
	fun backupProjectAttributes(
		target: File,
		overwrite: Boolean = false,
	) = getAttributesFile()
		.takeIf { it.exists() }
		?.copyTo(target, overwrite)

	private fun getAttributesFile() = projectDirectory
		.actualDirectory
		.toPath()
		.toAbsolutePath()
		.resolve("attributes.json")
		.toFile()

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		/* Clear any errant null values from the properties. It shouldn't happen, but also is recoverable in case is does (did). */
		json?.entrySet()
			?.filter { (_, value) -> value == null || value.isJsonNull }?.toList()
			?.forEach { (key, _) -> json.remove(key) }

		val properties = gson.get<Properties>(json) ?: Properties()
		initProperties(properties)
		json?.get<JsonObject>(SOURCES_KEY)?.let { sourcesJson ->
			SourceInfoSerializer.populate(
				{ baseView.addState(it) },
				{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
				sourcesJson,
				{ k, v -> indexToState[k] = v },
				gson
			)
		}
		json?.get(GLOBAL_TRANSFORM_KEY)?.let {
			val globalTransform: AffineTransform3D = gson[it]!!
			baseView.manager().setTransform(globalTransform)
		}
	}

	internal var wasQuit = false
	fun setupStage(stage: Stage) {
		keyTracker.installInto(stage)
		projectDirectory.addListener { pd -> stage.title = if (pd.directory == null) NAME else "$NAME ${pd.directory.absolutePath.homeToTilde()}" }
		stage.icons.addAll(
			Image("/icon-16.png"),
			Image("/icon-32.png"),
			Image("/icon-48.png"),
			Image("/icon-64.png"),
			Image("/icon-96.png"),
			Image("/icon-128.png")
		)
		stage.fullScreenExitKeyProperty().bind(NAMED_COMBINATIONS[PainteraBaseKeys.TOGGLE_FULL_SCREEN]!!.primaryCombinationProperty)
		wasQuit = false
		stage.onCloseRequest = EventHandler { if (!doSaveAndQuit()) it.consume() }
		stage.onHiding = EventHandler { if (!doSaveAndQuit()) it.consume() }
	}

	internal fun askSaveAndQuit(): Boolean {
		return when {
			wasQuit -> false
			!isSaveNecessary() -> true
			else -> SaveAndQuitDialog.showAndWaitForResponse()
		}
	}

	internal fun doSaveAndQuit(): Boolean {

		return if (askSaveAndQuit()) {
			quit()
			wasQuit = true
			/* quit() calls platform.exit() and exitProcess, so we never really get here.
			 *  But it's useful to know if `false` so we do this */
			true
		} else false
	}

	private fun quit() {
		LOG.debug("Quitting!")
		baseView.stop()
		projectDirectory.close()
		Platform.exit()
		if (DeviceManager.closeDevices()) {
			/* due to a bug (https://bugs.openjdk.org/browse/JDK-8232862) when MIDI devices are opened, the thread
			* that is created does not exit when closing the devices. If the process is not explicitly exited
			* then the application hangs after exiting the window.  */
			exitProcess(0)
		}
	}


	companion object {

		@JvmStatic
		val NAME = "Paintera"

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

		private const val VERSION_KEY = "version"

		private const val GLOBAL_TRANSFORM_KEY = "globalTransform"

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val USER_HOME = System.getProperty("user.home")

		private const val TILDE = "~"

		internal fun String.homeToTilde() = if (startsWith(USER_HOME)) replaceFirst(USER_HOME, TILDE) else this

		internal fun String.tildeToHome() = if (startsWith(TILDE)) replaceFirst(TILDE, USER_HOME) else this

	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
		override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = context[mainWindow.properties].asJsonObject
			map.entrySet().filter { (_, value) -> value == null || value.isJsonNull }.toList().forEach { (key, _) -> map.remove(key) }
			map.add(SOURCES_KEY, context[mainWindow.baseView.sourceInfo()])
			map.addProperty(VERSION_KEY, VERSION_STRING)
			map.add(GLOBAL_TRANSFORM_KEY, context[AffineTransform3D().also { mainWindow.baseView.manager().getTransform(it) }])
			return map
		}

		override fun getTargetClass() = PainteraMainWindow::class.java
	}
}
