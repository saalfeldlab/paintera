package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
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
import javafx.scene.image.Image
import javafx.stage.Stage
import net.imglib2.realtransform.AffineTransform3D
import org.controlsfx.control.Notifications
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys.NAMED_COMBINATIONS
import org.janelia.saalfeldlab.paintera.Version.VERSION_STRING
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.dialogs.SaveAndQuitDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.SaveAsDialog
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
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

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    lateinit var properties: Properties
        private set

    private lateinit var paneWithStatus: BorderPaneWithStatusBars

    val activeViewer = SimpleObjectProperty<ViewerPanelFX?>()

    private val activeOrthoAxisBinding = activeViewer.createNullableValueBinding {
        it?.let {
            when (it) {
                baseView.orthogonalViews().topLeft.viewer() -> 2
                baseView.orthogonalViews().topRight.viewer() -> 0
                else -> 1
            }
        } ?: -1
    }

    val activeOrthoAxis: Int by activeOrthoAxisBinding.nonnullVal()

    internal lateinit var defaultHandlers: PainteraDefaultHandlers

    val pane: Parent
        get() = paneWithStatus.pane

    private fun initProperties(properties: Properties) {
        this.properties = properties
        this.baseView.keyAndMouseBindings = this.properties.keyAndMouseConfig
        this.paneWithStatus = BorderPaneWithStatusBars(this)
        this.defaultHandlers = PainteraDefaultHandlers(this, paneWithStatus)
        activeViewer.bind(paintera.baseView.currentFocusHolder.createNullableValueBinding { it?.viewer() })
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
        val curMode = baseView.activeModeProperty.value
        baseView.setDefaultToolMode()

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

        /* Change back to the currect mode. */
        baseView.changeMode(curMode)
    }

    private fun showSaveCompleteNotification(owner: Any = baseView.node.scene.window) {
        Notifications.create()
            .graphic(FontAwesome[FontAwesomeIcon.CHECK_CIRCLE])
            .title("Save Project")
            .text("Save Complete")
            .owner(owner)
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
        stage.fullScreenExitKeyProperty().bind(NAMED_COMBINATIONS[PainteraBaseKeys.TOGGLE_FULL_SCREEN]!!.primaryCombinationProperty())
        stage.onCloseRequest = EventHandler { if (!askQuit()) it.consume() }
        stage.onHiding = EventHandler { quit() }
    }

    internal fun askAndQuit() {
        if (askQuit())
            pane.scene.window.hide()
    }

    private fun askQuit(): Boolean {
        return askSaveAndQuit()
    }

    private fun askSaveAndQuit(): Boolean {
        if (SaveAndQuitDialog.showAndWaitForResponse())
            return true
        return false
    }

    private fun quit() {
        LOG.debug("Quitting!")
        baseView.stop()
        projectDirectory.close()
        Platform.exit()
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

        internal fun String.homeToTilde() = replaceUserHomeWithTilde(this)

        internal fun String.tildeToHome() = if (this.startsWith(TILDE)) this.replaceFirst(TILDE, USER_HOME) else this

    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
        override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val map = context[mainWindow.properties].asJsonObject
            map.add(SOURCES_KEY, context[mainWindow.baseView.sourceInfo()])
            map.addProperty(VERSION_KEY, VERSION_STRING)
            map.add(GLOBAL_TRANSFORM_KEY, context[AffineTransform3D().also { mainWindow.baseView.manager().getTransform(it) }])
            return map
        }

        override fun getTargetClass() = PainteraMainWindow::class.java
    }
}
