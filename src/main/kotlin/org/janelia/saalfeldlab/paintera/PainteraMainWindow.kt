package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
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
import org.controlsfx.control.Notifications
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfigNode
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.Properties
import org.janelia.saalfeldlab.paintera.serialization.SourceInfoSerializer
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.intersecting.IntersectingSourceStateOpener
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.thresholded.ThresholdedRawSourceStateOpenerDialog
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
import java.util.Date
import kotlin.collections.set

class PainteraMainWindow(val gateway: PainteraGateway = PainteraGateway()) {

    val baseView = PainteraBaseView(
        PainteraBaseView.reasonableNumFetcherThreads(),
        ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()),
        KeyAndMouseConfig()
    )

    val namedActions = with(BindingKeys) {
        NamedAction.ActionMap(
            NamedAction(SAVE) { saveOrSaveAs() },
            NamedAction(SAVE_AS) { saveAs() },
            NamedAction(TOGGLE_MENUBAR_VISIBILITY) { properties.menuBarConfig.toggleIsVisible() },
            NamedAction(TOGGLE_MENUBAR_MODE) { properties.menuBarConfig.cycleModes() },
            NamedAction(TOGGLE_STATUSBAR_VISIBILITY) { properties.statusBarConfig.toggleIsVisible() },
            NamedAction(TOGGLE_STATUSBAR_MODE) { properties.statusBarConfig.cycleModes() },
            NamedAction(TOGGLE_SIDE_BAR) { properties.sideBarConfig.toggleIsVisible() },
            NamedAction(QUIT) { askAndQuit() },
            NamedAction(CYCLE_CURRENT_SOURCE_FORWARD) { baseView.sourceInfo().incrementCurrentSourceIndex() },
            NamedAction(CYCLE_CURRENT_SOURCE_BACKWARD) { baseView.sourceInfo().decrementCurrentSourceIndex() },
            NamedAction(TOGGLE_CURRENT_SOURCE_VISIBILITY) { CurrentSourceVisibilityToggle(baseView.sourceInfo().currentState()).toggleIsVisible() },
            NamedAction(CREATE_NEW_LABEL_DATASET) { CreateDatasetHandler.createAndAddNewLabelDataset(baseView) { projectDirectory.actualDirectory.absolutePath } },
            NamedAction(SHOW_REPL_TABS) { replDialog.show() },
            NamedAction(TOGGLE_FULL_SCREEN) { properties.windowProperties.isFullScreen.let { it.value = !it.value } },
            NamedAction(OPEN_HELP) { openReadme() },
            NamedAction(FILL_CONNECTED_COMPONENTS) { IntersectingSourceStateOpener.createAndAddVirtualIntersectionSource(baseView) { projectDirectory.actualDirectory.absolutePath } },
            NamedAction(THRESHOLDED) { ThresholdedRawSourceStateOpenerDialog.createAndAddNewVirtualThresholdSource(baseView) { projectDirectory.actualDirectory.absolutePath } }
        )
    }

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    private val replDialog = ReplDialog(gateway.context, { pane.scene.window }, Pair("paintera", this))

    lateinit var properties: Properties
        private set

    private lateinit var paneWithStatus: BorderPaneWithStatusBars

    private lateinit var defaultHandlers: PainteraDefaultHandlers

    val pane: Parent
        get() = paneWithStatus.pane

    private fun initProperties(properties: Properties) {
        this.properties = properties
        this.baseView.keyAndMouseBindings = this.properties.keyAndMouseConfig
        this.paneWithStatus = BorderPaneWithStatusBars(this)
        this.defaultHandlers = PainteraDefaultHandlers(this, paneWithStatus)
        this.properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation())
        this.baseView.orthogonalViews().grid().manage(this.properties.gridConstraints)
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
        val json = projectDirectory.actualDirectory
            ?.let { Paintera.n5Factory.openReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
        Paintera.n5Factory.gsonBuilder(builder)
        deserialize(json, gson, indexToState)
        arguments.convertDeprecatedDatasets.let {
            if (it.wereAnyConverted.value)
                it.backupFiles += StatefulSerializer.Arguments.ConvertDeprecatedDatasets.BackupFile(getAttributesFile(), backupProjectAttributesWithDate())
        }
    }

    fun save(notify: Boolean = true) {

        // ensure that the application is in the normal mode when the project is saved
        baseView.setDefaultAllowedActions()


        val builder = GsonHelpers
            .builderWithAllRequiredSerializers(gateway.context, baseView) { projectDirectory.actualDirectory.absolutePath }
            .setPrettyPrinting()
        Paintera.n5Factory.gsonBuilder(builder)
        Paintera.n5Factory.openWriter(projectDirectory.actualDirectory.absolutePath).setAttribute("/", PAINTERA_KEY, this)
        if (notify) {
            InvokeOnJavaFXApplicationThread {
                showSaveCompleteNotification()
            }
        }
    }

    private fun showSaveCompleteNotification(owner: Any = baseView.pane.scene.window) {
        Notifications.create()
            .graphic(FontAwesome[FontAwesomeIcon.CHECK_CIRCLE])
            .title("Save Project")
            .text("Save Complete")
            .owner(owner)
            .show()
    }

    fun saveAs(notify: Boolean = true): Boolean {
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
            bt.addEventFilter(ActionEvent.ACTION) { it ->
                val dir = directory.get()
                dir?.apply {
                    /* Let's create the directories first (or at least try). This let's us check permission later on.
                    *   If we can't mkdirs, it wont have any effect. If we CAN, then it would have later anyway. */
                    mkdirs()
                }
                var useIt = true
                if (dir === null || dir.isFile) {
                    PainteraAlerts.alert(Alert.AlertType.ERROR, true)
                        .also { it.headerText = "Invalid directory" }
                        .also { it.contentText = "Directory expected but got file `$dir'. Please specify valid directory." }
                        .show()
                    useIt = false
                } else if (!dir.canWrite()) {
                    PainteraAlerts.alert(Alert.AlertType.ERROR, true).apply {
                        headerText = "Invalid Permissions"
                        contentText = "Paintera does not have write permissions for the provided projected direct: $dir'.\nPlease specify valid directory."
                    }.show()
                    useIt = false
                } else {
                    val attributes = dir.toPath().toAbsolutePath().resolve("attributes.json").toFile()
                    if (attributes.exists()) {
                        useIt = useIt && PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true).apply {
                            headerText = "Container exists"
                            contentText = "N5 container (and potentially a Paintera project) exists at `$dir'. Overwrite?"
                            (dialogPane.lookupButton(ButtonType.OK) as Button).text = "_Overwrite"
                            (dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
                        }.showAndWait().filter { btn -> ButtonType.OK == btn }.isPresent
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
            save(notify)
            return true
        }
        return false
    }

    fun saveOrSaveAs(notify: Boolean = true) {
        if (projectDirectory.directory === null) saveAs(notify) else save(notify)
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
        initProperties(gson.get<Properties>(json) ?: Properties())
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
        stage.fullScreenExitKeyProperty().bind(NAMED_COMBINATIONS[BindingKeys.TOGGLE_FULL_SCREEN]!!.primaryCombinationProperty())
        // to disable message entirely:
        // stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        stage.onCloseRequest = EventHandler { if (!askQuit()) it.consume() }
        stage.onHiding = EventHandler { quit() }
    }

    private fun askAndQuit() {
        if (askQuit())
            pane.scene.window.hide()
    }

    private fun askQuit(): Boolean {
        return askSaveAndQuit()
    }

    private fun askSaveAndQuit(): Boolean {
        val saveAs = ButtonType("Save _As And Quit", ButtonBar.ButtonData.OK_DONE)
        val save = ButtonType("_Save And Quit", ButtonBar.ButtonData.APPLY)
        val alert = PainteraAlerts
            .confirmation("_Quit Without Saving", "_Cancel", true, pane.scene?.window).apply {
                headerText = "Save project state before exiting?"
                dialogPane.buttonTypes.addAll(save, saveAs)
                dialogPane.buttonTypes.map { dialogPane.lookupButton(it) as Button }.forEach { it.isDefaultButton = false }
            }
        val saveButton = alert.dialogPane.lookupButton(save) as Button
        val saveAsButton = alert.dialogPane.lookupButton(saveAs) as Button
        val okButton = alert.dialogPane.lookupButton(ButtonType.OK) as Button
        val projectDirectory: File? = projectDirectory.directory
        if (projectDirectory === null) {
            saveAsButton.isDefaultButton = true
            saveButton.isDisable = true
        } else
            saveButton.isDefaultButton = true
        saveButton.onAction = EventHandler { save(notify = false); okButton.fire() }
        // to display other dialog before closing, event filter is necessary:
        // https://stackoverflow.com/a/38696246
        saveAsButton.addEventFilter(ActionEvent.ACTION) { it.consume(); if (saveAs(notify = false)) okButton.fire() }
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

    private fun openReadme() {
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
                    wv
                )
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
    }


    object BindingKeys {
        const val CYCLE_INTERPOLATION_MODES = "cycle interpolation modes"
        const val CYCLE_CURRENT_SOURCE_FORWARD = "cycle current source forward"
        const val CYCLE_CURRENT_SOURCE_BACKWARD = "cycle current source backward"
        const val TOGGLE_CURRENT_SOURCE_VISIBILITY = "toggle current soruce visibility"
        const val MAXIMIZE_VIEWER = "toggle maximize viewer"
        const val DEDICATED_VIEWER_WINDOW = "toggle dedicated viewer window"
        const val MAXIMIZE_VIEWER_AND_3D = "toggle maximize viewer and 3D"
        const val SHOW_OPEN_DATASET_MENU = "show open dataset menu"
        const val CREATE_NEW_LABEL_DATASET = "create new label dataset"
        const val SHOW_REPL_TABS = "open repl"
        const val TOGGLE_FULL_SCREEN = "toggle full screen"
        const val OPEN_DATA = "open data"
        const val SAVE = "save"
        const val SAVE_AS = "save as"
        const val TOGGLE_MENUBAR_VISIBILITY = "toggle menubar visibility"
        const val TOGGLE_MENUBAR_MODE = "toggle menubar mode"
        const val TOGGLE_STATUSBAR_VISIBILITY = "toggle statusbar visibility"
        const val TOGGLE_STATUSBAR_MODE = "toggle statusbar mode"
        const val OPEN_HELP = "open help"
        const val QUIT = "quit"
        const val TOGGLE_SIDE_BAR = "toggle side bar"
        const val FILL_CONNECTED_COMPONENTS = "fill connected components"
        const val THRESHOLDED = "thresholded"
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

        private fun replaceUserHomeWithTilde(path: String) = if (path.startsWith(USER_HOME)) path.replaceFirst(USER_HOME, TILDE) else path

        private fun String.homeToTilde() = replaceUserHomeWithTilde(this)

        private fun String.tildeToHome() = if (this.startsWith(TILDE)) this.replaceFirst(TILDE, USER_HOME) else this

        val NAMED_COMBINATIONS = with(BindingKeys) {
            NamedKeyCombination.CombinationMap(
                NamedKeyCombination(OPEN_DATA, KeyCode.O, KeyCombination.CONTROL_DOWN),
                NamedKeyCombination(SAVE, KeyCode.S, KeyCombination.CONTROL_DOWN),
                NamedKeyCombination(SAVE_AS, KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(TOGGLE_MENUBAR_VISIBILITY, KeyCode.F2),
                NamedKeyCombination(TOGGLE_MENUBAR_MODE, KeyCode.F2, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(TOGGLE_STATUSBAR_VISIBILITY, KeyCode.F3),
                NamedKeyCombination(TOGGLE_STATUSBAR_MODE, KeyCode.F3, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(OPEN_HELP, KeyCode.F1),
                NamedKeyCombination(QUIT, KeyCode.Q, KeyCombination.CONTROL_DOWN),
                NamedKeyCombination(TOGGLE_SIDE_BAR, KeyCode.P),
                NamedKeyCombination(CYCLE_CURRENT_SOURCE_FORWARD, KeyCode.TAB, KeyCombination.CONTROL_DOWN),
                NamedKeyCombination(CYCLE_CURRENT_SOURCE_BACKWARD, KeyCode.TAB, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(TOGGLE_CURRENT_SOURCE_VISIBILITY, KeyCode.V),
                NamedKeyCombination(CYCLE_INTERPOLATION_MODES, KeyCode.I),
                NamedKeyCombination(MAXIMIZE_VIEWER, KeyCode.M),
                NamedKeyCombination(MAXIMIZE_VIEWER_AND_3D, KeyCode.M, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(CREATE_NEW_LABEL_DATASET, KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                NamedKeyCombination(SHOW_REPL_TABS, KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
                NamedKeyCombination(TOGGLE_FULL_SCREEN, KeyCode.F11),
            )
        }

        @JvmStatic
        val namedCombinations
            get() = NAMED_COMBINATIONS.deepCopy

        private class ReplDialog(
            private val context: Context,
            private val window: () -> Window,
            private vararg val bindings: Pair<String, *>,
        ) {
            private lateinit var dialog: SciJavaReplFXDialog

            fun show() {
                synchronized(this) {
                    if (!this::dialog.isInitialized)
                        dialog = SciJavaReplFXDialog(context, *bindings).apply {
                            initOwner(window())
                            title = "${Paintera.Constants.NAME} - Scripting REPL"
                        }
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
            val map = context.serialize(mainWindow.properties).asJsonObject
            map.add(SOURCES_KEY, context.serialize(mainWindow.baseView.sourceInfo()))
            map.addProperty(VERSION_KEY, Version.VERSION_STRING)
            map.add(GLOBAL_TRANSFORM_KEY, context.serialize(AffineTransform3D().also { mainWindow.baseView.manager().getTransform(it) }))
            return map
        }

        override fun getTargetClass() = PainteraMainWindow::class.java
    }


}
