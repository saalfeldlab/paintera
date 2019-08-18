package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
import bdv.viewer.Source
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableObjectValue
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.util.Duration
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.ui.SingleChildStackPane
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.cache.MaxSize
import org.janelia.saalfeldlab.paintera.cache.MemoryBoundedSoftRefLoaderCache
import org.janelia.saalfeldlab.paintera.config.ArbitraryMeshConfigNode
import org.janelia.saalfeldlab.paintera.config.BookmarkConfigNode
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode
import org.janelia.saalfeldlab.paintera.config.CrosshairConfigNode
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigNode
import org.janelia.saalfeldlab.paintera.config.ScaleBarOverlayConfigNode
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfigNode
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfigNode
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenu
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.MakeUnchecked
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.LongPredicate
import java.util.function.LongSupplier
import java.util.function.LongUnaryOperator
import kotlin.math.abs

class BorderPaneWithStatusBars2(private val paintera: PainteraMainWindow) {

	private val center = paintera.baseView

	private val properties = paintera.properties

	private val saveItem = MenuItem("_Save")
			.also { it.onAction = EventHandler { paintera.namedActions["save"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["save"]!!.primaryCombinationProperty()) }
	private val saveAsItem = MenuItem("Save _As")
			.also { it.onAction = EventHandler { paintera.namedActions["save as"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["save as"]!!.primaryCombinationProperty()) }
	val openMenu = OpenDialogMenu { LOG.error("Unable to open data", it); Exceptions.exceptionAlert("Unable to open data", it) }
			.getMenu("_Open Data", center) { paintera.projectDirectory.actualDirectory.absolutePath }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["open data"]!!.primaryCombinationProperty()) }
	private val fileMenu = Menu("_File", null, openMenu, saveItem, saveAsItem)

	val toggleMenuBarVisibility = MenuItem("Toggle _Visibility")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle menubar visibility"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["toggle menubar visibility"]!!.primaryCombinationProperty()) }
	val toggleMenuBarMode = MenuItem("Toggle _Mode")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle menubar mode"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["toggle menubar mode"]!!.primaryCombinationProperty()) }
	private val menuBarMenu = Menu("_Menu Bar", null, toggleMenuBarVisibility, toggleMenuBarMode)

	private val toggleSideBarMenuItem = MenuItem("Toggle _Visibility")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle side bar"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["toggle side bar"]!!.primaryCombinationProperty()) }
	private val sideBarMenu = Menu("_Side Bar", null, toggleSideBarMenuItem)

	private val viewMenu = Menu("_View", null, menuBarMenu, sideBarMenu)

	private val showVersion = MenuItem("Show _Version").also { it.onAction = EventHandler { PainteraAlerts.versionDialog().show() } }
	private val showReadme = MenuItem("Show _Readme")
			.also { it.onAction = EventHandler { paintera.namedActions["open readme in webview"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(paintera.namedKeyCombinations["open readme in webview"]!!.primaryCombinationProperty()) }
	private val helpMenu = Menu("_Help", null, showReadme, showVersion)

	private val topGroup = Group()
	private val centerPaneGroup = Group()
	private val menuBar = MenuBar(fileMenu, viewMenu, helpMenu)
			.also { it.padding = Insets.EMPTY }
			.also { it.visibleProperty().bind(properties.menuBarConfig.isVisibleProperty()) }
			.also { it.managedProperty().bind(it.visibleProperty()) }
			.also { properties.menuBarConfig.modeProperty().addListener { _, _, newv -> updateMenuBarParent(it, newv) } }
			.also { updateMenuBarParent(it, properties.menuBarConfig.mode) }

	private val projectDirectory = SimpleObjectProperty<File>(null)

	private val projectDirectoryString = Bindings.createStringBinding(Callable {projectDirectory.get()?.absolutePath}, projectDirectory)

	private val projectDirectoryIsNull = projectDirectory.isNull

	private val projectDirectoryIsNotNull = projectDirectory.isNotNull

	private val centerPane = StackPane(center.orthogonalViews().pane(), centerPaneGroup).also { it.alignment = Pos.TOP_LEFT }

    val pane = BorderPane(centerPane).also { it.top = topGroup }

    private val statusBar: HBox

    val sideBar: VBox

	private val scrollPane: ScrollPane

	private val orthoSlices = makeOrthoSlices(center.orthogonalViews(), center.viewer3D().meshesGroup())

	private val crossHairs = makeCrosshairs(
			center.orthogonalViews(),
			Colors.CREMI,
			Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5))

    private val currentSourceStatus: Label

    private val viewerCoordinateStatus: Label

    private val worldCoordinateStatus: Label

    private val valueStatus: Label

    private val resizeSideBar: ResizeOnLeftSide

    private val navigationConfigNode = NavigationConfigNode(config = properties.navigationConfig, coordinateConfig = CoordinateConfigNode(center.manager()))

    private val crosshairConfigNode = CrosshairConfigNode(properties.crosshairConfig.also { it.bindCrosshairsToConfig(crossHairs.values) })

    private val orthoSliceConfigNode = OrthoSliceConfigNode(OrthoSliceConfig(properties.orthoSliceConfig, center) { orthoSlices[it]!! })

    private val viewer3DConfigNode = Viewer3DConfigNode(properties.viewer3DConfig)

    private val screenScaleConfigNode = ScreenScalesConfigNode(properties.screenScalesConfig)

    private val scaleBarConfigNode = ScaleBarOverlayConfigNode(properties.scaleBarOverlayConfig)

    private val bookmarkConfigNode = BookmarkConfigNode(
			properties.bookmarkConfig,
			Consumer {
				center.manager().setTransform(it.globalTransformCopy)
				center.viewer3D().setAffine(it.viewer3DTransformCopy)
			}
	)

    private val arbitraryMeshConfigNode = ArbitraryMeshConfigNode(properties.arbitraryMeshConfig)

    private val currentFocusHolderWithState: ObservableObjectValue<ViewerAndTransforms?>

    private val saveProjectButton = Buttons.withTooltip("_Save", "Save project state at current project location.") { paintera.save() }
	private val saveProjectAsButton = Buttons.withTooltip("Save _As", "Save project ") { paintera.saveAs() }

    fun currentFocusHolder(): ObservableObjectValue<ViewerAndTransforms?> = this.currentFocusHolderWithState

    fun setViewerCoordinateStatus(p: RealPoint?) {
        InvokeOnJavaFXApplicationThread.invoke {
            viewerCoordinateStatus.text = if (p == null)
                "N/A"
            else
                String.format("(% 4d, % 4d)",
                        p.getDoublePosition(0).toInt(),
                        p.getDoublePosition(1).toInt())
        }
    }

    fun setWorldCoorinateStatus(p: RealPoint?) {
        InvokeOnJavaFXApplicationThread.invoke {
            worldCoordinateStatus.text = if (p == null)
                "N/A"
            else
                CoordinateDisplayListener
                        .worldToString(
                                p)
        }
    }

    fun setCurrentValue(s: String) {
        InvokeOnJavaFXApplicationThread.invoke { valueStatus.text = s }
    }

    fun orthoSlices(): Map<ViewerAndTransforms, OrthoSliceFX> {
        return Collections.unmodifiableMap(this.orthoSlices)
    }

    init {
        LOG.debug("Construction {}", BorderPaneWithStatusBars2::class.java.name)
		this.currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews())
		properties.screenScalesConfig.screenScalesProperty().addListener { _, _, newv -> center.orthogonalViews().setScreenScales(newv.scalesCopy) }

		this.currentSourceStatus = Label()
        this.viewerCoordinateStatus = Label()
        this.worldCoordinateStatus = Label()
        this.valueStatus = Label()
        val showStatusBar = CheckBox()
        showStatusBar.isFocusTraversable = false
        showStatusBar.tooltip = Tooltip("If not selected, status bar will only show on mouse-over")

        val sourceDisplayStatus = SingleChildStackPane()
        center.sourceInfo().currentState().addListener { _, _, newv -> sourceDisplayStatus.setChild(newv.displayStatus) }

        // show source name by default, or override it with source status text if any
        center.sourceInfo().currentState().addListener { _, _, newv ->
            sourceDisplayStatus.setChild(newv.displayStatus)
            currentSourceStatus.textProperty().unbind()
            currentSourceStatus.textProperty().bind(Bindings.createStringBinding(
                    Callable {
                        if (newv.statusTextProperty() != null && newv.statusTextProperty().get() != null)
                            newv.statusTextProperty().get()
                        else if (newv.nameProperty().get() != null)
                            newv.nameProperty().get()
                        else
                            null
                    },
                    newv.nameProperty(),
                    newv.statusTextProperty()
            ))
        }

        // for positioning the 'show status bar' checkbox on the right
        val valueStatusSpacing = Region()
        HBox.setHgrow(valueStatusSpacing, Priority.ALWAYS)

        this.statusBar = HBox(5.0,
                sourceDisplayStatus,
                currentSourceStatus,
                viewerCoordinateStatus,
                worldCoordinateStatus,
                valueStatus,
                valueStatusSpacing,
                showStatusBar
        )

        val currentSourceStatusToolTip = Tooltip()
        currentSourceStatusToolTip.textProperty().bind(currentSourceStatus.textProperty())
        currentSourceStatus.tooltip = currentSourceStatusToolTip

        currentSourceStatus.prefWidth = 95.0
        viewerCoordinateStatus.prefWidth = 115.0
        worldCoordinateStatus.prefWidth = 245.0

        viewerCoordinateStatus.font = Font.font("Monospaced")
        worldCoordinateStatus.font = Font.font("Monospaced")

        val isWithinMarginOfBorder = SimpleBooleanProperty()
        pane.addEventFilter(
                MouseEvent.MOUSE_MOVED
        ) { e -> isWithinMarginOfBorder.set(e.y < pane.height && pane.height - e.y <= statusBar.height) }
        statusBar.visibleProperty().addListener { _, _, newv -> pane.bottom = if (newv) statusBar else null }
        statusBar.visibleProperty().bind(isWithinMarginOfBorder.or(showStatusBar.selectedProperty()))
        showStatusBar.isSelected = true

        val onRemoveException = BiConsumer<Source<*>, Exception> { _, e -> LOG.warn("Unable to remove source: {}", e.message) }

        val sourceTabs = SourceTabs(
                center.sourceInfo().currentSourceIndexProperty(),
                MakeUnchecked.onException(MakeUnchecked.CheckedConsumer<Source<*>> { center.sourceInfo().removeSource(it) }, onRemoveException),
                center.sourceInfo()
        )

        val sourcesContents = TitledPane("sources", sourceTabs.get())
        sourcesContents.isExpanded = false

        val toMegaBytes = LongUnaryOperator { bytes -> bytes / 1000 / 1000 }
        val currentMemory = LongSupplier { center.currentMemoryUsageInBytes }
        val maxMemory = LongSupplier { (center.globalBackingCache as MaxSize).maxSize }
        val currentMemoryStr = { toMegaBytes.applyAsLong(currentMemory.asLong).toString() }
        val maxMemoryStr = { toMegaBytes.applyAsLong(maxMemory.asLong).toString() }
        val memoryUsageField = Label(String.format("%s/%s", currentMemoryStr(), maxMemoryStr()))
        val currentMemoryUsageUPdateTask = Timeline(KeyFrame(
                Duration.seconds(1.0),
                EventHandler { memoryUsageField.text = String.format("%s/%s", currentMemoryStr(), maxMemoryStr()) }))
        currentMemoryUsageUPdateTask.cycleCount = Timeline.INDEFINITE
        currentMemoryUsageUPdateTask.play()

        // TODO put this stuff in a better place!
        val memoryCleanupScheduler = Executors.newScheduledThreadPool(1, NamedThreadFactory("cache clean up", true))
        memoryCleanupScheduler.scheduleAtFixedRate({ (center.globalBackingCache as MemoryBoundedSoftRefLoaderCache<*, *, *>).restrictToMaxSize() }, 0, 3, TimeUnit.SECONDS)

        val setButton = Button("Set")
        setButton.setOnAction {
            val dialog = Alert(Alert.AlertType.CONFIRMATION)
            val field = NumberField.longField(
                    maxMemory.asLong,
                    LongPredicate { it > 0 && it < Runtime.getRuntime().maxMemory() },
                    ObjectField.SubmitOn.ENTER_PRESSED,
                    ObjectField.SubmitOn.FOCUS_LOST)
            dialog.dialogPane.content = field.textField()
            if (ButtonType.OK == dialog.showAndWait().orElse(ButtonType.CANCEL)) {
                Thread {
                    (center.globalBackingCache as MaxSize).maxSize = field.valueProperty().get()
                    InvokeOnJavaFXApplicationThread.invoke { memoryUsageField.text = String.format("%s/%s", currentMemoryStr(), maxMemoryStr()) }
                }.start()
            }
        }


        val memoryUsage = TitledPanes.createCollapsed("Memory", HBox(Label("Cache Size"), memoryUsageField, setButton))

        val settingsContents = VBox(
                this.navigationConfigNode.getContents(),
                this.crosshairConfigNode.getContents(),
                this.orthoSliceConfigNode.getContents(),
                this.viewer3DConfigNode.contents,
                this.scaleBarConfigNode,
                this.bookmarkConfigNode,
                this.arbitraryMeshConfigNode,
                this.screenScaleConfigNode.contents,
                memoryUsage
        )
        val settings = TitledPane("settings", settingsContents)
        settings.isExpanded = false

        center.viewer3D().meshesGroup().children.add(this.arbitraryMeshConfigNode.getMeshGroup())

		paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }

		saveProjectButton.disableProperty().bind(this.projectDirectoryIsNull)
		val projectDirectoryLabel = Labels.withTooltip("Project", "Project directory for serialization of Paintera state")
		val projectDirectoryValue = Labels.withTooltip(projectDirectoryString.get())
		HBox.setHgrow(projectDirectoryValue, Priority.ALWAYS)
		projectDirectoryValue.textProperty().bind(projectDirectoryString)
		projectDirectoryValue.tooltip.textProperty().bind(projectDirectoryString)
		projectDirectoryLabel.prefWidth = 100.0
		saveProjectButton.prefWidth = 100.0
		saveProjectAsButton.prefWidth = 100.0
		val projectDirectoryBox = HBox(projectDirectoryLabel, projectDirectoryValue)
		val saveButtonsBox = HBox(saveProjectButton, saveProjectAsButton)

		val projectBox = VBox()

		projectDirectory.addListener { _, _, newv -> projectBox.children.let { if (newv === null) it.setAll(saveButtonsBox) else it.setAll(saveButtonsBox, projectDirectoryBox) } }
		projectBox.children.let { if (projectDirectoryIsNull.get()) it.setAll(saveButtonsBox) else it.setAll(saveButtonsBox, projectDirectoryBox) }
		projectDirectory.set(paintera.projectDirectory.directory)


		this.scrollPane = ScrollPane(VBox(sourcesContents, settings))
        this.sideBar = VBox(projectBox, this.scrollPane)
        this.scrollPane.hbarPolicy = ScrollBarPolicy.NEVER
        this.scrollPane.vbarPolicy = ScrollBarPolicy.AS_NEEDED
        sourceTabs.widthProperty().bind(sideBar.prefWidthProperty())
        settingsContents.prefWidthProperty().bind(sideBar.prefWidthProperty())
        sideBar.prefWidthProperty().bind(properties.sideBarConfig.widthProperty())
        sideBar.visibleProperty().bind(properties.sideBarConfig.isVisibleProperty())
        sideBar.managedProperty().bind(sideBar.visibleProperty())
        pane.right = sideBar
		resizeSideBar = ResizeOnLeftSide(sideBar, properties.sideBarConfig.widthProperty()) { dist -> abs(dist) < 5 }.also { it.install() }

	}

    fun bookmarkConfigNode() = this.bookmarkConfigNode

	private fun updateMenuBarParent(menuBar: MenuBar, mode: MenuBarConfig.Mode)  {
		val tc = this.topGroup.children
		val oc = this.centerPaneGroup.children
		when(mode) {
			MenuBarConfig.Mode.OVERLAY -> { tc.remove(menuBar); oc.add(menuBar) }
			MenuBarConfig.Mode.TOP -> { oc.remove(menuBar); tc.add(menuBar) }
		}
	}

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun makeCrosshairs(
                views: OrthogonalViews<*>,
                onFocusColor: Color,
                offFocusColor: Color): Map<ViewerAndTransforms, Crosshair> {
            val map = HashMap<ViewerAndTransforms, Crosshair>()
            map[views.topLeft()] = makeCrossHairForViewer(views.topLeft().viewer(), onFocusColor, offFocusColor)
            map[views.topRight()] = makeCrossHairForViewer(views.topRight().viewer(), onFocusColor, offFocusColor)
            map[views.bottomLeft()] = makeCrossHairForViewer(views.bottomLeft().viewer(), onFocusColor, offFocusColor)
            return map
        }

        fun makeCrossHairForViewer(
                viewer: ViewerPanelFX,
                onFocusColor: Color,
                offFocusColor: Color): Crosshair {
            val ch = Crosshair()
            viewer.display.addOverlayRenderer(ch)
            ch.wasChangedProperty().addListener { _, _, _ -> viewer.display.drawOverlays() }
            ch.isHighlightProperty.bind(viewer.focusedProperty())
            return ch
        }

        fun makeOrthoSlices(views: OrthogonalViews<*>, scene: Group): Map<ViewerAndTransforms, OrthoSliceFX> {
            val map = HashMap<ViewerAndTransforms, OrthoSliceFX>()
            map[views.topLeft()] = OrthoSliceFX(scene, views.topLeft().viewer())
            map[views.topRight()] = OrthoSliceFX(scene, views.topRight().viewer())
            map[views.bottomLeft()] = OrthoSliceFX(scene, views.bottomLeft().viewer())
            return map
        }

        fun currentFocusHolder(views: OrthogonalViews<*>): ObservableObjectValue<ViewerAndTransforms?> {
            val tl = views.topLeft()
            val tr = views.topRight()
            val bl = views.bottomLeft()
            val focusTL = tl.viewer().focusedProperty()
            val focusTR = tr.viewer().focusedProperty()
            val focusBL = bl.viewer().focusedProperty()

            return Bindings.createObjectBinding(
                    Callable { if (focusTL.get()) tl else if (focusTR.get()) tr else if (focusBL.get()) bl else null },
                    focusTL,
                    focusTR,
                    focusBL
            )

        }
    }
}
