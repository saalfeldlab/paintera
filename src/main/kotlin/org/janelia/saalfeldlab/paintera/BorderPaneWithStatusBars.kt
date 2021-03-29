package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableObjectValue
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraDefaultHandlers.Companion.currentFocusHolder
import org.janelia.saalfeldlab.paintera.PainteraMainWindow.BindingKeys
import org.janelia.saalfeldlab.paintera.config.ArbitraryMeshConfigNode
import org.janelia.saalfeldlab.paintera.config.BookmarkConfigNode
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode
import org.janelia.saalfeldlab.paintera.config.CrosshairConfigNode
import org.janelia.saalfeldlab.paintera.config.LoggingConfigNode
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.MultiBoxOverlayConfigNode
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigNode
import org.janelia.saalfeldlab.paintera.config.ScaleBarOverlayConfigNode
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfigNode
import org.janelia.saalfeldlab.paintera.config.StatusBarConfig
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfigNode
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSlicesManager
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer

class BorderPaneWithStatusBars(private val paintera: PainteraMainWindow) {

    private val center = paintera.baseView

    private val painteraProperties = paintera.properties

    private val namedKeyCombinations = painteraProperties.keyAndMouseConfig.painteraConfig.keyCombinations

    private val saveItem = namedActionMenuItem("_Save", BindingKeys.SAVE, FontAwesomeIcon.SAVE)

    private val saveAsItem = namedActionMenuItem("Save _As", BindingKeys.SAVE_AS, FontAwesomeIcon.FLOPPY_ALT)

    private val openMenu = paintera.gateway.openDialogMenu().getMenu(
        "_Open",
        center,
        { paintera.projectDirectory.actualDirectory.absolutePath },
        {
            LOG.error("Unable to open data", it)
            Exceptions.exceptionAlert(Paintera.Constants.NAME, "Unable to open data", it, owner = paintera.pane.scene?.window).show()
        }).get().apply {
        graphic = FontAwesome[FontAwesomeIcon.FOLDER_OPEN_ALT, 1.5]
        acceleratorProperty().bind(namedKeyCombinations[BindingKeys.OPEN_DATA]!!.primaryCombinationProperty())
    }

    private val quitItem = namedActionMenuItem("_Quit", BindingKeys.QUIT, FontAwesomeIcon.SIGN_OUT)

    private val fileMenu = Menu("_File", null, openMenu, saveItem, saveAsItem, quitItem)

    private val currentSourceName = MenuItem(null).apply {
        textProperty().bind(center.sourceInfo().currentState().let { Bindings.createStringBinding({ it.value?.nameProperty()?.value }, it) })
        visibleProperty().bind(textProperty().isNotNull)
        isMnemonicParsing = false
        isDisable = true
    }

    private val cycleForward = namedActionMenuItem("Cycle _Forward", BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD)

    private val cycleBackward = namedActionMenuItem("Cycle _Backward", BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD)

    private val toggleVisibility = namedActionMenuItem("Toggle _Visibility", BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY)

    private val currentSourceMenu = Menu(
        "_Current",
        null,
        currentSourceName,
        SeparatorMenuItem().apply { visibleProperty().bind(currentSourceName.visibleProperty()) },
        cycleForward,
        cycleBackward,
        toggleVisibility
    )

    private val newLabelSource = namedActionMenuItem("_Label Source (N5)", BindingKeys.CREATE_NEW_LABEL_DATASET)


    private val newSourceMenu = Menu("_New", null, newLabelSource)

    private val newConnectedComponentSource = namedActionMenuItem("_Fill Connected Components", BindingKeys.FILL_CONNECTED_COMPONENTS)
    private val newThresholdedSource = namedActionMenuItem("_Thresholded", BindingKeys.THRESHOLDED)
    private val newVirtualSourceMenu = Menu("_Virtual", null, newConnectedComponentSource, newThresholdedSource)


    private val sourcesMenu = Menu("_Sources", null, currentSourceMenu, newSourceMenu, newVirtualSourceMenu)

    private val toggleMenuBarVisibility = namedActionMenuItem("Toggle _Visibility", BindingKeys.TOGGLE_MENUBAR_VISIBILITY)

    private val toggleMenuBarMode = namedActionMenuItem("Toggle _Mode", BindingKeys.TOGGLE_MENUBAR_MODE)
    private val menuBarMenu = Menu("_Menu Bar", null, toggleMenuBarVisibility, toggleMenuBarMode)
    private val toggleStatusBarVisibility = namedActionMenuItem("Toggle _Visibility", BindingKeys.TOGGLE_STATUSBAR_VISIBILITY)

    private val toggleStatusBarMode = namedActionMenuItem("Toggle _Mode", BindingKeys.TOGGLE_STATUSBAR_MODE)
    private val statusBarMenu = Menu("S_tatus Bar", null, toggleStatusBarVisibility, toggleStatusBarMode)
    private val toggleSideBarMenuItem = namedActionMenuItem("Toggle _Visibility", BindingKeys.TOGGLE_SIDE_BAR)

    private val sideBarMenu = Menu("_Side Bar", null, toggleSideBarMenuItem)
    private val fullScreenItem = namedActionMenuItem("Toggle _Fullscreen", BindingKeys.TOGGLE_FULL_SCREEN)

    private val replItem = namedActionMenuItem("Show _REPL", BindingKeys.SHOW_REPL_TABS)

    private val viewMenu = Menu("_View", null, menuBarMenu, sideBarMenu, statusBarMenu, fullScreenItem, replItem)

    private val showVersion = MenuItem("Show _Version").apply { onAction = EventHandler { PainteraAlerts.versionDialog().show() } }

    private val showReadme = namedActionMenuItem("Show _Readme", BindingKeys.OPEN_HELP, FontAwesomeIcon.QUESTION)

    private val helpMenu = Menu("_Help", null, showReadme, showVersion)
    private val bottomGroup = Group()

    private val topGroup = Group()
    private val centerPaneTopAlignGroup = Group().also { StackPane.setAlignment(it, Pos.TOP_LEFT) }
    private val centerPaneBottomAlignGroup = Group().also { StackPane.setAlignment(it, Pos.BOTTOM_LEFT) }
    private val menuBar = MenuBar(fileMenu, sourcesMenu, viewMenu, helpMenu).apply {
        padding = Insets.EMPTY
        visibleProperty().bind(painteraProperties.menuBarConfig.isVisibleProperty())
        managedProperty().bind(visibleProperty())
        painteraProperties.menuBarConfig.modeProperty().addListener { _, _, newv -> updateMenuBarParent(this, newv) }
        updateMenuBarParent(this, painteraProperties.menuBarConfig.mode)
    }
    private val projectDirectory = SimpleObjectProperty<File>(null)

    private val centerPane = StackPane(center.orthogonalViews().grid(), centerPaneTopAlignGroup, centerPaneBottomAlignGroup)

    val pane = BorderPane(centerPane).also { it.top = topGroup }.also { it.bottom = bottomGroup }

    private val statusBar: HBox

    private val statusBarPrefWidth = Bindings.createDoubleBinding(
        { pane.width - if (painteraProperties.sideBarConfig.isVisible) painteraProperties.sideBarConfig.width else 0.0 },
        painteraProperties.sideBarConfig.isVisibleProperty(),
        pane.widthProperty(),
        painteraProperties.sideBarConfig.widthProperty()
    )

    val sideBar: VBox

    private val scrollPane: ScrollPane

    private val orthoSlicesManager = OrthoSlicesManager(
        center.viewer3D().sceneGroup(),
        center.orthogonalViews(),
        center.viewer3D().eyeToWorldTransformProperty()
    )

    private val crossHairs = makeCrosshairs(
        center.orthogonalViews(),
        Colors.CREMI,
        Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5)
    )

    private val currentSourceStatus: Label

    private val viewerCoordinateStatus: Label

    private val worldCoordinateStatus: Label

    private val statusValue: Label

    private val resizeSideBar: ResizeOnLeftSide

    private val navigationConfigNode = NavigationConfigNode(config = painteraProperties.navigationConfig, coordinateConfig = CoordinateConfigNode(center.manager()))

    private val multiBoxOverlayConfigNode = MultiBoxOverlayConfigNode(config = painteraProperties.multiBoxOverlayConfig)

    private val crosshairConfigNode = CrosshairConfigNode(painteraProperties.crosshairConfig.also { it.bindCrosshairsToConfig(crossHairs.values) })

    private val orthoSliceConfigNode = OrthoSliceConfigNode(OrthoSliceConfig(painteraProperties.orthoSliceConfig, center) { orthoSlices()[it]!! })

    private val viewer3DConfigNode = Viewer3DConfigNode(painteraProperties.viewer3DConfig)

    private val screenScaleConfigNode = ScreenScalesConfigNode(painteraProperties.screenScalesConfig)

    private val scaleBarConfigNode = ScaleBarOverlayConfigNode(painteraProperties.scaleBarOverlayConfig)

    private val bookmarkConfigNode = BookmarkConfigNode(painteraProperties.bookmarkConfig) {
        center.manager().setTransform(it.globalTransformCopy)
        center.viewer3D().setAffine(it.viewer3DTransformCopy)
    }

    private val loggingConfigNode = LoggingConfigNode(painteraProperties.loggingConfig)

    private val arbitraryMeshConfigNode = ArbitraryMeshConfigNode(paintera.gateway.triangleMeshFormat, painteraProperties.arbitraryMeshConfig)

    private val currentFocusHolderWithState: ObservableObjectValue<ViewerAndTransforms?>

    private fun namedActionMenuItem(text: String, keys: String, icon: FontAwesomeIcon? = null): MenuItem {
        paintera.namedActions[keys] ?: error("No namedActions for $keys")
        return MenuItem(text).apply {
            icon?.let {
                graphic = FontAwesome[it, 1.5]
            }
            onAction = EventHandler { paintera.namedActions[keys]!!() }
            namedKeyCombinations[keys]?.let {
                acceleratorProperty().bind(it.primaryCombinationProperty())
            }
        }
    }

    fun currentFocusHolder(): ObservableObjectValue<ViewerAndTransforms?> = this.currentFocusHolderWithState

    fun setViewerCoordinateStatus(point: RealPoint?) {
        when (point) {
            null -> NOT_APPLICABLE
            else -> String.format("(% 4d, % 4d)", point.getDoublePosition(0).toInt(), point.getDoublePosition(1).toInt())
        }.let {
            InvokeOnJavaFXApplicationThread {
                viewerCoordinateStatus.text = it
            }
        }
    }

    fun setWorldCoorinateStatus(p: RealPoint?) {
        when (p) {
            null -> NOT_APPLICABLE
            else -> CoordinateDisplayListener.worldToString(p)
        }.let {
            InvokeOnJavaFXApplicationThread {
                worldCoordinateStatus.text = it
            }
        }
    }

    fun setCurrentStatus(status: String) {
        InvokeOnJavaFXApplicationThread { statusValue.text = status }
    }

    fun orthoSlices(): Map<ViewerAndTransforms, OrthoSliceFX> {
        return orthoSlicesManager.orthoSlices;
    }

    init {
        LOG.debug("Construction {}", BorderPaneWithStatusBars::class.java.name)
        this.currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews())
        painteraProperties.screenScalesConfig.screenScalesProperty().addListener { _, _, newv -> center.orthogonalViews().setScreenScales(newv.scalesCopy) }

        this.currentSourceStatus = Label()
        this.viewerCoordinateStatus = Label()
        this.worldCoordinateStatus = Label()
        this.statusValue = Label()

        val sourceDisplayStatus = StackPane()
        // show source name by default, or override it with source status text if any
        center.sourceInfo().currentState().addListener { _, _, newv ->
            sourceDisplayStatus.children.let { if (newv === null || newv.displayStatus === null) it.clear() else it.setAll(newv.displayStatus) }
            currentSourceStatus.textProperty().unbind()
            newv?.let {
                currentSourceStatus.textProperty().bind(
                    Bindings.createStringBinding(
                        {
                            if (it.statusTextProperty() != null && it.statusTextProperty().get() != null && it.statusTextProperty().get().isNotEmpty())
                                newv.statusTextProperty().get()
                            else if (newv.nameProperty().get() != null)
                                newv.nameProperty().get()
                            else
                                null
                        },
                        it.nameProperty(),
                        it.statusTextProperty()
                    )
                )
            }
        }

        // for positioning the 'show status bar' checkbox on the right
        val valueStatusSpacing = Region()
        HBox.setHgrow(valueStatusSpacing, Priority.ALWAYS)

        this.statusBar = HBox(5.0,
            sourceDisplayStatus,
            currentSourceStatus,
            viewerCoordinateStatus,
            worldCoordinateStatus,
            statusValue,
            valueStatusSpacing,
            Region().also { HBox.setHgrow(it, Priority.ALWAYS) }).apply {
            backgroundProperty().bind(pane.backgroundProperty())
            prefWidthProperty().bind(statusBarPrefWidth)
        }

        val currentSourceStatusToolTip = Tooltip()
        currentSourceStatusToolTip.textProperty().bind(currentSourceStatus.textProperty())
        currentSourceStatus.tooltip = currentSourceStatusToolTip

        currentSourceStatus.prefWidth = 95.0
        viewerCoordinateStatus.prefWidth = 115.0
        worldCoordinateStatus.prefWidth = 245.0

        viewerCoordinateStatus.font = Font.font("Monospaced")
        worldCoordinateStatus.font = Font.font("Monospaced")

        statusBar.visibleProperty().bind(painteraProperties.statusBarConfig.isVisibleProperty())
        statusBar.managedProperty().bind(statusBar.visibleProperty())
        val statusBarParent = SimpleObjectProperty<Group?>(null)
        statusBarParent.addListener { _, oldv, newv ->
            oldv?.children?.remove(statusBar)
            newv?.children?.add(statusBar)
        }
        val modeToStatusBarGroup: (StatusBarConfig.Mode) -> Group = {
            when (it) {
                StatusBarConfig.Mode.OVERLAY -> centerPaneBottomAlignGroup
                StatusBarConfig.Mode.BOTTOM -> bottomGroup
            }
        }
        painteraProperties.statusBarConfig.modeProperty().addListener { _, _, mode -> statusBarParent.value = modeToStatusBarGroup(mode) }
        statusBarParent.value = modeToStatusBarGroup(painteraProperties.statusBarConfig.mode)

        val sourceTabs = SourceTabs(center.sourceInfo())

        val sourcesContents = TitledPane("Sources", sourceTabs.node).apply {
            isExpanded = false
            painteraProperties.sideBarConfig.widthProperty().addListener { _, _, new -> maxWidth = new.toDouble() }
            padding = Insets.EMPTY
            widthProperty().addListener { _, _, new -> LOG.debug("sourceContents width is {} ({})", new, painteraProperties.sideBarConfig.width) }
        }

        val settingsContents = VBox(
            this.navigationConfigNode.getContents(),
            this.multiBoxOverlayConfigNode.contents,
            this.crosshairConfigNode.getContents(),
            this.orthoSliceConfigNode.getContents(),
            this.viewer3DConfigNode.contents,
            this.scaleBarConfigNode,
            this.bookmarkConfigNode,
            this.arbitraryMeshConfigNode,
            this.screenScaleConfigNode.contents,
            this.loggingConfigNode.node
        )
        val settings = TitledPane("Settings", settingsContents)
        settings.isExpanded = false

        center.viewer3D().meshesGroup().children.add(this.arbitraryMeshConfigNode.getMeshGroup())

        paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }


        this.scrollPane = ScrollPane(VBox(sourcesContents, settings)).apply {
            prefWidthProperty().bind(painteraProperties.sideBarConfig.widthProperty())
            prefWidthProperty().bind(painteraProperties.sideBarConfig.widthProperty())
            maxWidthProperty().bind(painteraProperties.sideBarConfig.widthProperty())
            widthProperty().addListener { _, _, new -> LOG.debug("scrollPane width is {} ({})", new, painteraProperties.sideBarConfig.width) }
        }

        this.sideBar = VBox(this.scrollPane).apply {
            prefWidthProperty().bind(painteraProperties.sideBarConfig.widthProperty())
            maxWidthProperty().bind(painteraProperties.sideBarConfig.widthProperty())
            visibleProperty().bind(painteraProperties.sideBarConfig.isVisibleProperty())
            managedProperty().bind(visibleProperty())
            widthProperty().addListener { _, _, new -> LOG.debug("sideBar width is {} ({})", new, painteraProperties.sideBarConfig.width) }
        }
        this.scrollPane.hbarPolicy = ScrollBarPolicy.NEVER
        this.scrollPane.vbarPolicy = ScrollBarPolicy.AS_NEEDED
        this.scrollPane.padding = Insets.EMPTY
        this.sideBar.padding = Insets.EMPTY
        sourceTabs.widthProperty().bind(sideBar.prefWidthProperty())
        settingsContents.prefWidthProperty().bind(sideBar.prefWidthProperty())
        pane.right = sideBar
        resizeSideBar = ResizeOnLeftSide(sideBar, painteraProperties.sideBarConfig.widthProperty()).apply { install() }

        paintera.baseView.allowedActionsProperty().addListener { _, _, newv -> updateAllowedActions(newv) }
    }

    fun bookmarkConfigNode() = this.bookmarkConfigNode

    private fun updateMenuBarParent(menuBar: MenuBar, mode: MenuBarConfig.Mode) {
        val tc = this.topGroup.children
        val oc = this.centerPaneTopAlignGroup.children
        when (mode) {
            MenuBarConfig.Mode.OVERLAY -> {
                tc.remove(menuBar); oc.add(menuBar)
            }
            MenuBarConfig.Mode.TOP -> {
                oc.remove(menuBar); tc.add(menuBar)
            }
        }
    }

    private fun updateAllowedActions(allowedActions: AllowedActions) {
        this.saveItem.isDisable = !allowedActions.isAllowed(MenuActionType.SaveProject)
        this.saveAsItem.isDisable = !allowedActions.isAllowed(MenuActionType.SaveProject)
        this.cycleForward.isDisable = !allowedActions.isAllowed(MenuActionType.ChangeActiveSource)
        this.cycleBackward.isDisable = !allowedActions.isAllowed(MenuActionType.ChangeActiveSource)
        this.newLabelSource.isDisable = !allowedActions.isAllowed(MenuActionType.AddSource)
        this.openMenu.isDisable = !allowedActions.isAllowed(MenuActionType.AddSource)
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        fun makeCrosshairs(
            views: OrthogonalViews<*>,
            onFocusColor: Color,
            offFocusColor: Color,
        ): Map<ViewerAndTransforms, Crosshair> {
            val map = HashMap<ViewerAndTransforms, Crosshair>()
            map[views.topLeft] = makeCrossHairForViewer(views.topLeft.viewer(), onFocusColor, offFocusColor)
            map[views.topRight] = makeCrossHairForViewer(views.topRight.viewer(), onFocusColor, offFocusColor)
            map[views.bottomLeft] = makeCrossHairForViewer(views.bottomLeft.viewer(), onFocusColor, offFocusColor)
            return map
        }

        fun makeCrossHairForViewer(
            viewer: ViewerPanelFX,
            onFocusColor: Color,
            offFocusColor: Color,
        ): Crosshair {
            val ch = Crosshair()
            viewer.display.addOverlayRenderer(ch)
            ch.wasChangedProperty().addListener { _, _, _ -> viewer.display.drawOverlays() }
            ch.isHighlightProperty.bind(viewer.focusedProperty())
            return ch
        }

        private const val NOT_APPLICABLE = "N/A"
    }
}
