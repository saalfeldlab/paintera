package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Supplier

typealias PropertiesListener = BiConsumer<Properties2?, Properties2?>

class PainteraMainWindow {

    val baseView = PainteraBaseView(
            PainteraBaseView.reasonableNumFetcherThreads(),
            ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()))

    private lateinit var paneWithStatus: BorderPaneWithStatusBars2

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    private lateinit var defaultHandlers: PainteraDefaultHandlers2

	private lateinit var properties: Properties2

	fun getPane(): Parent = paneWithStatus.pane

	fun getProperties() = properties

	private fun initProperties(properties: Properties2) {
		this.properties = properties
		this.paneWithStatus = BorderPaneWithStatusBars2(this, this.properties)
		this.defaultHandlers = PainteraDefaultHandlers2(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				Supplier { projectDirectory.actualDirectory.absolutePath },
				this.properties)
		this.properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation())
		this.baseView.orthogonalViews().grid().manage(properties.gridConstraints)
	}

	private fun initProperties(json: JsonObject?, gson: Gson) {
		val properties = json?.let { gson.fromJson(it, Properties2::class.java) }
		initProperties(properties ?: Properties2())
	}

	fun deserialize() {
		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
		val builder = GsonHelpers
				.builderWithAllRequiredDeserializers(
						StatefulSerializer.Arguments(baseView),
						{ projectDirectory.actualDirectory.absolutePath },
						{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory
				.actualDirectory
				?.let { N5FSReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
				?.takeIf { it.isJsonObject }
				?.let { it.asJsonObject }
		deserialize(json, gson, indexToState)
	}

	fun save() {
		val builder = GsonHelpers
				.builderWithAllRequiredSerializers(baseView) { projectDirectory.actualDirectory.absolutePath }
				.setPrettyPrinting()
		N5FSWriter(projectDirectory.actualDirectory.absolutePath, builder).setAttribute("/", PAINTERA_KEY, this)
	}

	fun saveAs() {
		val dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
		dialog.headerText = "Save project directory at location"
		val directoryChooser = DirectoryChooser()
		val directory = SimpleObjectProperty<File?>(null)
		val noDirectorySpecified = directory.isNull
//		val fileAsString = Bindings.createStringBinding(Callable { directory.get()?.absolutePath }, directory)
		val cwd = FileSystems.getDefault().getPath(".").toAbsolutePath().toFile().absolutePath
		val userHome = System.getProperty("user.home")
		val directoryField = TextField()
				.also { it.tooltip = Tooltip().also { tt -> tt.textProperty().bindBidirectional(it.textProperty()) } }
				.also { it.promptText = "Project Directory" }
		val converter = object : StringConverter<File?>() {
			override fun toString(file: File?) = file?.path?.replaceFirst("^$userHome".toRegex(), "~")
			override fun fromString(path: String?): File? {
				return path?.replaceFirst("^~".toRegex(), userHome)?.let { Paths.get(it).toAbsolutePath().toFile() }
			}
		}
		Bindings.bindBidirectional(directoryField.textProperty(), directory, converter)
		directoryChooser.initialDirectoryProperty().addListener { _, _, f -> f?.mkdirs() }
		val browseButton = Buttons.withTooltip("_Browse", "Browse") {
			directoryChooser.initialDirectory = directory.get()?.let { it.takeUnless { it.isFile } ?: it.parentFile }
			directoryChooser.showDialog(this.getPane().scene.window)?.let { directory.set(it) }
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

					if (useIt) {
						val useItProperty = SimpleBooleanProperty(useIt)
						projectDirectory.setDirectory(dir) {
							useItProperty.value = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
									.also { it.headerText = "Paintera project locked" }
									.also { it.contentText = "" +
											"Paintera project at `$dir' is currently locked. " +
											"A project is locked if it is accessed by a currently running Paintera instance " +
											"or a Paintera instance did not terminate properly, in which case you " +
											"may ignore the lock. " +
											"Please make sure that no currently running Paintera instances " +
											"access the project directory to avoid inconsistent project files." }
									.also { (it.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_Ignore Lock" }
									.also { (it.dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel" }
									.showAndWait().filter { ButtonType.OK == it }.isPresent
							useItProperty.get()
						}
						useIt = useItProperty.get()
					}

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
		}
	}

	fun saveOrSaveAs() {
		if (projectDirectory.directory === null) saveAs() else save()
	}

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		initProperties(json, gson)
		json
				?.takeIf { it.has(SOURCES_KEY) }
				?.get(SOURCES_KEY)
				?.takeIf { it.isJsonObject }
				?.asJsonObject
				?.let { SourceInfoSerializer.populate(
						{ baseView.addState(it) },
						{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
						it.asJsonObject,
						{ k, v -> indexToState.put(k, v) },
						gson)
				}
	}

	companion object{

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

		private const val VERSION_KEY = "version"

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
		override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = context.serialize(mainWindow.properties).asJsonObject
			map.add(SOURCES_KEY, context.serialize(mainWindow.baseView.sourceInfo()))
			map.addProperty(VERSION_KEY, Version.VERSION_STRING)
			return map
		}

		override fun getTargetClass() = PainteraMainWindow::class.java
	}


}
