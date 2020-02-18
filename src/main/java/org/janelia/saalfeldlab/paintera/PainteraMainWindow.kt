package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.Window
import javafx.util.StringConverter
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfigNode
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.serialization.Properties
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.scijava.Context
import org.scijava.plugin.Plugin
import org.scijava.scripting.fx.SciJavaReplFXDialog
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.nio.file.Paths
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class PainteraMainWindow(val gateway: PainteraGateway = PainteraGateway()) {

	val baseView = PainteraBaseView(
			PainteraBaseView.reasonableNumFetcherThreads(),
			ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()),
			KeyAndMouseConfig())

	val namedActions = NamedAction.ActionMap(
			NamedAction("save", Runnable { this.saveOrSaveAs() }),
			NamedAction("save as", Runnable { this.saveAs() }),
			NamedAction("toggle menubar visibility", Runnable { this.properties.menuBarConfig.toggleIsVisible() }),
			NamedAction("toggle menubar mode", Runnable { this.properties.menuBarConfig.cycleModes() }),
			NamedAction("toggle statusbar visibility", Runnable { this.properties.statusBarConfig.toggleIsVisible() }),
			NamedAction("toggle statusbar mode", Runnable { this.properties.statusBarConfig.cycleModes() }),
			NamedAction("toggle side bar", Runnable { this.properties.sideBarConfig.toggleIsVisible() } ),
			NamedAction("quit", Runnable { askAndQuit() }),
			NamedAction(BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD, Runnable { baseView.sourceInfo().incrementCurrentSourceIndex() }),
			NamedAction(BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD, Runnable { baseView.sourceInfo().decrementCurrentSourceIndex() }),
			NamedAction(BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY, Runnable { CurrentSourceVisibilityToggle(baseView.sourceInfo().currentState()).toggleIsVisible() }),
			NamedAction(BindingKeys.CREATE_NEW_LABEL_DATASET, Runnable { CreateDatasetHandler.createAndAddNewLabelDataset(baseView) { projectDirectory.actualDirectory.absolutePath } }),
			NamedAction(BindingKeys.SHOW_REPL_TABS, Runnable { replDialog.show() }),
			NamedAction(BindingKeys.TOGGLE_FULL_SCREEN, Runnable { properties.windowProperties.isFullScreen.let { it.value = !it.value } }),
			NamedAction("open help", Runnable {
				val readmeButton = Buttons.withTooltip("_README", "Open README.md") {
					// TODO make render when loaded from jar
					val vs = Version.VERSION_STRING
					val tag = if (vs.endsWith("SNAPSHOT"))
						"master"
					else
						"^.*-SNAPSHOT-([A-Za-z0-9]+)$"
								.toRegex()
								.find(vs)
								?.let { it.groupValues[1] }
								?: "paintera-$vs"
					val ghurl = "https://github.com/saalfeldlab/paintera/blob/$tag/README.md"
					javaClass.getResource("/README.html")?.toExternalForm()?.let { res ->
						val dialog = PainteraAlerts.information("_Close", true).also { it.initModality(Modality.NONE) }
						val wv = WebView()
								.also { it.engine.load(res) }
								.also { it.maxHeight = Double.POSITIVE_INFINITY }
						val contents = VBox(
								HBox(
										TextField(ghurl).also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.tooltip = Tooltip(ghurl) }.also { it.isEditable = false },
										Button(null, RefreshButton.createFontAwesome(2.0)).also { it.onAction = EventHandler { wv.engine.load(res) } }),
								wv)
						VBox.setVgrow(wv, Priority.ALWAYS)
						dialog.dialogPane.content = contents
						dialog.graphic = null
						dialog.headerText = null
						dialog.initOwner(pane.scene.window)
						dialog.show()
					} ?: LOG.info("Resource `/README.html' not available")
				}
				val keyBindingsDialog = KeyAndMouseConfigNode(properties.keyAndMouseConfig, baseView.sourceInfo()).node
				val keyBindingsPane = TitledPane("Key Bindings", keyBindingsDialog)
				val dialog = PainteraAlerts.information("_Close", true).also { it.initModality(Modality.NONE) }
				dialog.dialogPane.content = VBox(keyBindingsPane, readmeButton)
				dialog.graphic = null
				dialog.headerText = null
				dialog.initOwner(pane.scene.window)
				dialog.dialogPane.minWidth = 1000.0
				dialog.show()
			}))

    private lateinit var paneWithStatus: BorderPaneWithStatusBars

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

	private val replDialog = ReplDialog(gateway.context, { pane.scene.window }, Pair("paintera", this))

    private lateinit var defaultHandlers: PainteraDefaultHandlers

	private lateinit var _properties: Properties

	val pane: Parent
		get() = paneWithStatus.pane

	val properties: Properties
		get() = _properties

	@JvmOverloads constructor(properties: Properties, gateway: PainteraGateway = PainteraGateway()): this(gateway = gateway) {
		initProperties(properties)
	}

	private fun initProperties(properties: Properties) {
		this._properties = properties
		this.baseView.keyAndMouseBindings = properties.keyAndMouseConfig
		this.paneWithStatus = BorderPaneWithStatusBars(this)
		this.defaultHandlers = PainteraDefaultHandlers(this, paneWithStatus)
		this._properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation())
		this.baseView.orthogonalViews().grid().manage(properties.gridConstraints)
	}

	private fun initProperties(json: JsonObject?, gson: Gson) {
		val properties = json?.let { gson.fromJson(it, Properties::class.java) }
		initProperties(properties ?: Properties())
	}

	fun deserialize() {
		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
        val arguments = StatefulSerializer.Arguments(baseView)
		val builder = GsonHelpers
				.builderWithAllRequiredDeserializers(
						gateway.context,
						arguments,
						{ projectDirectory.actualDirectory.absolutePath },
						{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory
            .actualDirectory
            ?.let { N5FSReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
		deserialize(json, gson, indexToState)
        arguments.convertDeprecatedDatasets.let {
            if (it.wereAnyConverted.value)
                it.backupFiles += StatefulSerializer.Arguments.ConvertDeprecatedDatasets.BackupFile(getAttributesFile(), backupProjectAttributesWithDate())
        }
	}

	fun save() {
		val builder = GsonHelpers
				.builderWithAllRequiredSerializers(gateway.context, baseView) { projectDirectory.actualDirectory.absolutePath }
				.setPrettyPrinting()
		N5FSWriter(projectDirectory.actualDirectory.absolutePath, builder).setAttribute("/", PAINTERA_KEY, this)
	}

	fun saveAs(): Boolean {
		val dialog = PainteraAlerts.confirmation("_Save", "_Cancel", true)
		dialog.headerText = "Save project directory at location"
		val directoryChooser = DirectoryChooser()
		val directory = SimpleObjectProperty<File?>(null)
		val noDirectorySpecified = directory.isNull
		val directoryField = TextField()
				.also { it.tooltip = Tooltip().also { tt -> tt.textProperty().bindBidirectional(it.textProperty()) } }
				.also { it.promptText = "Project Directory" }
		val converter = object : StringConverter<File?>() {
			override fun toString(file: File?) = file?.path?.homeToTilde()
			override fun fromString(path: String?) = path?.tildeToHome()?.let { Paths.get(it).toAbsolutePath().toFile() }
		}
		Bindings.bindBidirectional(directoryField.textProperty(), directory, converter)
		directoryChooser.initialDirectoryProperty().addListener { _, _, f -> f?.mkdirs() }
		val browseButton = Buttons.withTooltip("_Browse", "Browse") {
			directoryChooser.initialDirectory = directory.get()?.let { it.takeUnless { it.isFile } ?: it.parentFile }
			directoryChooser.showDialog(this.pane.scene.window)?.let { directory.set(it) }
		}
		browseButton.prefWidth = 100.0
		HBox.setHgrow(directoryField, Priority.ALWAYS)
		val box = HBox(directoryField, browseButton)
		dialog.dialogPane.content = box
		box.alignment = Pos.CENTER

		(dialog.dialogPane.lookupButton(ButtonType.OK) as Button).also { bt ->
			bt.addEventFilter(ActionEvent.ACTION) {
				val dir = directory.get()
				var useIt = true
				if (dir === null || dir.isFile) {
					PainteraAlerts.alert(Alert.AlertType.ERROR, true)
							.also { it.headerText = "Invalid directory" }
							.also { it.contentText = "Directory expected but got file `$dir'. Please specify valid directory." }
							.show()
					useIt = false
				} else {
					val attributes = dir.toPath().toAbsolutePath().resolve("attributes.json").toFile()
					if (attributes.exists()) {
						useIt = useIt && PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
								.also { it.headerText = "Container exists" }
								.also { it.contentText = "N5 container (and potentially a Paintera project) exists at `$dir'. Overwrite?" }
								.also { (it.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_Overwrite" }
								.also { (it.dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel" }
								.showAndWait().filter { ButtonType.OK == it }.isPresent
					}

					useIt = useIt && PainteraAlerts.ignoreLockFileDialog(projectDirectory, dir)

				}
				if (!useIt) it.consume()
			}
			bt.disableProperty().bind(noDirectorySpecified)
		}

		directory.value = projectDirectory.directory

		val bt = dialog.showAndWait()
		if (bt.filter { ButtonType.OK == it }.isPresent && directory.value != null) {
			LOG.info("Saving project to directory {}", directory.value)
			save()
			return true
		}
		return false
	}

	fun saveOrSaveAs() = if (projectDirectory.directory === null) saveAs() else save()

    @JvmOverloads
    fun backupProjectAttributesWithDate(
        date: Date = Date(),
        dateFormat: DateFormat = SimpleDateFormat("'.bkp.'yyyy-mm-dd_HH-mm-ss"),
        overwrite: Boolean = false) = backupProjectAttributes(dateFormat.format(date), overwrite)

    @JvmOverloads
    fun backupProjectAttributes(
        suffix: String = ".bkp",
        overwrite: Boolean = false) = backupProjectAttributes(File("${getAttributesFile().absolutePath}$suffix"), overwrite)

    @JvmOverloads
    fun backupProjectAttributes(
        target: File,
        overwrite: Boolean = false) = getAttributesFile()
            .takeIf { it.exists() }
            ?.copyTo(target, overwrite)

    private fun getAttributesFile() = projectDirectory
        .actualDirectory
        .toPath()
        .toAbsolutePath()
        .resolve("attributes.json")
        .toFile()

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		initProperties(json, gson)
		with(GsonExtensions) {
			json
					?.getJsonObject(SOURCES_KEY)
					?.let {
						SourceInfoSerializer.populate(
								{ baseView.addState(it) },
								{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
								it.asJsonObject,
								{ k, v -> indexToState.put(k, v) },
								gson)
					}
			json
					?.getJsonArray(GLOBAL_TRANSFORM_KEY)
					?.let { baseView.manager().setTransform(gson.fromJson(it, AffineTransform3D::class.java)) }
		}
	}

	fun setupStage(stage: Stage) {
		keyTracker.installInto(stage)
		projectDirectory.addListener { pd -> stage.title = if (pd.directory == null) NAME else "$NAME ${pd.directory.absolutePath.homeToTilde()}" }
		stage.icons.addAll(
				Image(javaClass.getResourceAsStream("/icon-16.png")),
				Image(javaClass.getResourceAsStream("/icon-32.png")),
				Image(javaClass.getResourceAsStream("/icon-48.png")),
				Image(javaClass.getResourceAsStream("/icon-64.png")),
				Image(javaClass.getResourceAsStream("/icon-96.png")),
				Image(javaClass.getResourceAsStream("/icon-128.png")))
		stage.fullScreenExitKeyProperty().bind(NAMED_COMBINATIONS[BindingKeys.TOGGLE_FULL_SCREEN]!!.primaryCombinationProperty())
		// to disable message entirely:
		// stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
		stage.onCloseRequest = EventHandler { if(!askQuit()) it.consume() }
		stage.onHiding = EventHandler { quit() }
	}

	private fun askAndQuit() {
		if (askQuit())
			pane.scene.window.hide()
	}

	private fun askQuit(): Boolean {
		val saveAs = ButtonType("Save _As And Quit", ButtonBar.ButtonData.OK_DONE)
		val save = ButtonType("_Save And Quit", ButtonBar.ButtonData.APPLY)
		val alert = PainteraAlerts
				.confirmation("_Quit Without Saving", "_Cancel", true)
				.also { it.headerText = "Save project state before exiting?" }
				.also { it.dialogPane.buttonTypes.addAll(save, saveAs) }
				.also { it.dialogPane.buttonTypes.map { bt -> it.dialogPane.lookupButton(bt) as Button }.forEach { it.isDefaultButton = false } }
		val saveButton = alert.dialogPane.lookupButton(save) as Button
		val saveAsButton = alert.dialogPane.lookupButton(saveAs) as Button
		val okButton = alert.dialogPane.lookupButton(ButtonType.OK) as Button
		val projectDirectory: File? = projectDirectory.directory
		if (projectDirectory === null) {
			saveAsButton.isDefaultButton = true
			saveButton.isDisable = true
		} else
			saveButton.isDefaultButton = true
		saveButton.onAction = EventHandler { save(); okButton.fire() }
		// to display other dialog before closing, event filter is necessary:
		// https://stackoverflow.com/a/38696246
		saveAsButton.addEventFilter(ActionEvent.ACTION) { it.consume(); if (saveAs()) okButton.fire() }
		val bt = alert.showAndWait()
		LOG.debug("Returned button type is {}", bt)
		if (bt.filter { ButtonType.OK == it }.isPresent)
			return true
		return false
	}

	private fun quit() {
		LOG.debug("Quitting!")
		baseView.stop()
		projectDirectory.close()
	}

	object BindingKeys {
		const val CYCLE_INTERPOLATION_MODES = "cycle interpolation modes"
		const val CYCLE_CURRENT_SOURCE_FORWARD = "cycle current source forward"
		const val CYCLE_CURRENT_SOURCE_BACKWARD = "cycle current source backward"
		const val TOGGLE_CURRENT_SOURCE_VISIBILITY = "toggle current soruce visibility"
		const val MAXIMIZE_VIEWER = "toggle maximize viewer"
		const val MAXIMIZE_VIEWER_AND_3D = "toggle maximize viewer and 3D"
		const val SHOW_OPEN_DATASET_MENU = "show open dataset menu"
		const val CREATE_NEW_LABEL_DATASET = "create new label dataset"
		const val SHOW_REPL_TABS = "open repl"
		const val TOGGLE_FULL_SCREEN = "toggle full screen"
	}


	companion object{
		@JvmStatic
		val NAME = "Paintera"

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

		private const val VERSION_KEY = "version"

		private const val GLOBAL_TRANSFORM_KEY = "globalTransform"

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val USER_HOME = System.getProperty("user.home")

		private const val TILDE = "~"

		private fun replaceUserHomeWithTilde(path: String) = if (path.startsWith(USER_HOME)) path.replaceFirst(USER_HOME, TILDE) else path

		private fun String.homeToTilde() = replaceUserHomeWithTilde(this)

		private fun String.tildeToHome() = if (this.startsWith(TILDE)) this.replaceFirst(TILDE, USER_HOME) else this

		private val NAMED_COMBINATIONS = NamedKeyCombination.CombinationMap(
				NamedKeyCombination("open data", KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN)),
				NamedKeyCombination("save", KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)),
				NamedKeyCombination("save as", KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination("toggle menubar visibility", KeyCodeCombination(KeyCode.F2)),
				NamedKeyCombination("toggle menubar mode", KeyCodeCombination(KeyCode.F2, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination("toggle statusbar visibility", KeyCodeCombination(KeyCode.F3)),
				NamedKeyCombination("toggle statusbar mode", KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination("open help", KeyCodeCombination(KeyCode.F1)),
				NamedKeyCombination("quit", KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN)),
				NamedKeyCombination("toggle side bar", KeyCodeCombination(KeyCode.P)),
				NamedKeyCombination(BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD, KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN)),
				NamedKeyCombination(BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD, KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination(BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY, KeyCodeCombination(KeyCode.V)),
				NamedKeyCombination(BindingKeys.CYCLE_INTERPOLATION_MODES, KeyCodeCombination(KeyCode.I)),
				NamedKeyCombination(BindingKeys.MAXIMIZE_VIEWER, KeyCodeCombination(KeyCode.M)),
				NamedKeyCombination(BindingKeys.MAXIMIZE_VIEWER_AND_3D, KeyCodeCombination(KeyCode.M, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination(BindingKeys.CREATE_NEW_LABEL_DATASET, KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)),
				NamedKeyCombination(BindingKeys.SHOW_REPL_TABS, KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN)),
				NamedKeyCombination(BindingKeys.TOGGLE_FULL_SCREEN, KeyCodeCombination(KeyCode.F11)))


		@JvmStatic
		val namedCombinations
			get() = NAMED_COMBINATIONS.deepCopy

		private class ReplDialog(
				private val context: Context,
				private val window: () -> Window,
				private vararg val bindings: Pair<String, *>
		) {
			private lateinit var dialog: SciJavaReplFXDialog

			fun show() {
				synchronized(this) {
					if (!this::dialog.isInitialized)
						dialog = SciJavaReplFXDialog(context, *bindings)
								.also { it.initOwner(window()) }
								.also { it.title = "${Paintera.Constants.NAME} - Scripting REPL" }
				}
				dialog.show()
				dialog.dialogPane.addEventHandler(KeyEvent.KEY_PRESSED) {
					if (KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).match(it)) {
						it.consume()
						dialog.hide()
					}
				}
			}
		}

	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
		override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = context.serialize(mainWindow._properties).asJsonObject
			map.add(SOURCES_KEY, context.serialize(mainWindow.baseView.sourceInfo()))
			map.addProperty(VERSION_KEY, Version.VERSION_STRING)
			map.add(GLOBAL_TRANSFORM_KEY, context.serialize(AffineTransform3D().also { mainWindow.baseView.manager().getTransform(it) }))
			return map
		}

		override fun getTargetClass() = PainteraMainWindow::class.java
	}


}
