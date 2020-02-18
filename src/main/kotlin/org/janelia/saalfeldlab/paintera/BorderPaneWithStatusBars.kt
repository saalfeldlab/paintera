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
import javafx.scene.control.*
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.*
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

	private val properties = paintera.properties

	private val namedKeyCombinations = properties.keyAndMouseConfig.painteraConfig.keyCombinations

	private val saveItem = MenuItem("_Save")
			.also { it.graphic = FontAwesome[FontAwesomeIcon.SAVE, 1.5] }
			.also { it.onAction = EventHandler { paintera.namedActions["save"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["save"]!!.primaryCombinationProperty()) }
	private val saveAsItem = MenuItem("Save _As")
			.also { it.graphic = FontAwesome[FontAwesomeIcon.FLOPPY_ALT, 1.5] }
			.also { it.onAction = EventHandler { paintera.namedActions["save as"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["save as"]!!.primaryCombinationProperty()) }
	private val openDataMenu = paintera
			.gateway
			.openDialogMenu()// { LOG.error("Unable to open data", it); Exceptions.exceptionAlert("Unable to open data", it) }
			.getMenu(
					"_Data",
					center,
					{ paintera.projectDirectory.actualDirectory.absolutePath },
					{ LOG.error("Unable to open data", it); Exceptions.exceptionAlert(Paintera.Constants.NAME, "Unable to open data", it).show() })
			.get()
			.also { it.acceleratorProperty().bind(namedKeyCombinations["open data"]!!.primaryCombinationProperty()) }
	private val openMenu = Menu("_Open", null, openDataMenu)
			.also { it.graphic = FontAwesome[FontAwesomeIcon.FOLDER_OPEN_ALT, 1.5] }
	private val quitItem = MenuItem("_Quit")
			.also { it.graphic = FontAwesome[FontAwesomeIcon.SIGN_OUT, 1.5] }
			.also { it.onAction = EventHandler { paintera.namedActions["quit"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["quit"]!!.primaryCombinationProperty()) }
	private val fileMenu = Menu("_File", null, openMenu, saveItem, saveAsItem, quitItem)

	private val currentSourceName = MenuItem(null)
			.also { it.textProperty().bind(center.sourceInfo().currentState().let { Bindings.createStringBinding(Callable { it.value?.nameProperty()?.value }, it ) } ) }
			.also { it.visibleProperty().bind(it.textProperty().isNotNull) }
			.also { it.isMnemonicParsing = false }
			.also { it.isDisable = true }

	private val cycleForward = MenuItem("Cycle _Forward")
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD]!!.primaryCombinationProperty()) }
			.also { it.setOnAction { paintera.namedActions[PainteraMainWindow.BindingKeys.CYCLE_CURRENT_SOURCE_FORWARD]!!.action.run() } }

	private val cycleBackward = MenuItem("Cycle _Backward")
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD]!!.primaryCombinationProperty()) }
			.also { it.setOnAction { paintera.namedActions[PainteraMainWindow.BindingKeys.CYCLE_CURRENT_SOURCE_BACKWARD]!!.action.run() } }

	private val toggleVisibility = MenuItem("Toggle _Visibility")
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY]!!.primaryCombinationProperty()) }
			.also { it.setOnAction { paintera.namedActions[PainteraMainWindow.BindingKeys.TOGGLE_CURRENT_SOURCE_VISIBILITY]!!.action.run() } }

	private val currentSourceMenu = Menu(
			"_Current",
			null,
			currentSourceName,
			SeparatorMenuItem().also { it.visibleProperty().bind(currentSourceName.visibleProperty()) },
			cycleForward,
			cycleBackward,
			toggleVisibility)

	// TODO how to get this to work?
	// TODO baseView.allowedActionsProperty().get().isAllowed(MenuActionType.CreateNewLabelSource)
	private val newLabelSource = MenuItem("_Label Source (N5)")
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.CREATE_NEW_LABEL_DATASET]!!.primaryCombinationProperty()) }
			.also { it.setOnAction { paintera.namedActions[PainteraMainWindow.BindingKeys.CREATE_NEW_LABEL_DATASET]!!.action.run() } }
	private val newSourceMenu = Menu("_New", null, newLabelSource)

	private val sourcesMenu = Menu("_Sources", null, currentSourceMenu, newSourceMenu)

	private val toggleMenuBarVisibility = MenuItem("Toggle _Visibility")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle menubar visibility"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["toggle menubar visibility"]!!.primaryCombinationProperty()) }
	private val toggleMenuBarMode = MenuItem("Toggle _Mode")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle menubar mode"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["toggle menubar mode"]!!.primaryCombinationProperty()) }
	private val menuBarMenu = Menu("_Menu Bar", null, toggleMenuBarVisibility, toggleMenuBarMode)

	private val toggleStatusBarVisibility = MenuItem("Toggle _Visibility")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle statusbar visibility"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["toggle statusbar visibility"]!!.primaryCombinationProperty()) }
	private val toggleStatusBarMode = MenuItem("Toggle _Mode")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle statusbar mode"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["toggle statusbar mode"]!!.primaryCombinationProperty()) }
	private val statusBarMenu = Menu("S_tatus Bar", null, toggleStatusBarVisibility, toggleStatusBarMode)

	private val toggleSideBarMenuItem = MenuItem("Toggle _Visibility")
			.also { it.onAction = EventHandler { paintera.namedActions["toggle side bar"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["toggle side bar"]!!.primaryCombinationProperty()) }
	private val sideBarMenu = Menu("_Side Bar", null, toggleSideBarMenuItem)

	private val fullScreenItem = MenuItem("Toggle _Fullscreen")
			.also { it.onAction = EventHandler { paintera.namedActions[PainteraMainWindow.BindingKeys.TOGGLE_FULL_SCREEN]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.TOGGLE_FULL_SCREEN]!!.primaryCombinationProperty()) }

	private val replItem = MenuItem("Show _REPL")
			.also { it.onAction = EventHandler { paintera.namedActions[PainteraMainWindow.BindingKeys.SHOW_REPL_TABS]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations[PainteraMainWindow.BindingKeys.SHOW_REPL_TABS]!!.primaryCombinationProperty()) }

	private val viewMenu = Menu("_View", null, menuBarMenu, sideBarMenu, statusBarMenu, fullScreenItem, replItem)

	private val showVersion = MenuItem("Show _Version").also { it.onAction = EventHandler { PainteraAlerts.versionDialog().show() } }
	private val showReadme = MenuItem("Show _Readme")
			.also { it.graphic = FontAwesome[FontAwesomeIcon.QUESTION, 1.5] }
			.also { it.onAction = EventHandler { paintera.namedActions["open help"]!!.action.run() } }
			.also { it.acceleratorProperty().bind(namedKeyCombinations["open help"]!!.primaryCombinationProperty()) }
	private val helpMenu = Menu("_Help", null, showReadme, showVersion)

	private val bottomGroup = Group()
	private val topGroup = Group()
	private val centerPaneTopAlignGroup = Group().also { StackPane.setAlignment(it, Pos.TOP_LEFT) }
	private val centerPaneBottomAlignGroup = Group().also { StackPane.setAlignment(it, Pos.BOTTOM_LEFT) }
	private val menuBar = MenuBar(fileMenu, sourcesMenu, viewMenu, helpMenu)
			.also { it.padding = Insets.EMPTY }
			.also { it.visibleProperty().bind(properties.menuBarConfig.isVisibleProperty()) }
			.also { it.managedProperty().bind(it.visibleProperty()) }
			.also { properties.menuBarConfig.modeProperty().addListener { _, _, newv -> updateMenuBarParent(it, newv) } }
			.also { updateMenuBarParent(it, properties.menuBarConfig.mode) }

	private val projectDirectory = SimpleObjectProperty<File>(null)

	private val projectDirectoryString = Bindings.createStringBinding(Callable {projectDirectory.get()?.absolutePath}, projectDirectory)

	private val projectDirectoryIsNull = projectDirectory.isNull

	private val projectDirectoryIsNotNull = projectDirectory.isNotNull

	private val centerPane = StackPane(center.orthogonalViews().pane(), centerPaneTopAlignGroup, centerPaneBottomAlignGroup)

	val pane = BorderPane(centerPane).also { it.top = topGroup }.also { it.bottom = bottomGroup }

	private val statusBar: HBox

	private val statusBarPrefWidth = Bindings.createDoubleBinding(
			Callable { pane.width - if (properties.sideBarConfig.isVisible) properties.sideBarConfig.width else 0.0 },
			properties.sideBarConfig.isVisibleProperty(),
			pane.widthProperty(),
			properties.sideBarConfig.widthProperty())

    val sideBar: VBox

	private val scrollPane: ScrollPane

	private val orthoSlicesManager = OrthoSlicesManager(
		center.viewer3D().sceneGroup(),
		center.orthogonalViews(),
		center.viewer3D().eyeToWorldTransformProperty())

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

    private val multiBoxOverlayConfigNode = MultiBoxOverlayConfigNode(config = properties.multiBoxOverlayConfig)

    private val crosshairConfigNode = CrosshairConfigNode(properties.crosshairConfig.also { it.bindCrosshairsToConfig(crossHairs.values) })

    private val orthoSliceConfigNode = OrthoSliceConfigNode(OrthoSliceConfig(properties.orthoSliceConfig, center) { orthoSlices()[it]!! })

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

    private val arbitraryMeshConfigNode = ArbitraryMeshConfigNode(paintera.gateway.triangleMeshFormat, properties.arbitraryMeshConfig)

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
        return orthoSlicesManager.orthoSlices;
    }

    init {
        LOG.debug("Construction {}", BorderPaneWithStatusBars::class.java.name)
		this.currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews())
		properties.screenScalesConfig.screenScalesProperty().addListener { _, _, newv -> center.orthogonalViews().setScreenScales(newv.scalesCopy) }

		this.currentSourceStatus = Label()
        this.viewerCoordinateStatus = Label()
        this.worldCoordinateStatus = Label()
        this.valueStatus = Label()

        val sourceDisplayStatus = StackPane()
        // show source name by default, or override it with source status text if any
        center.sourceInfo().currentState().addListener { _, _, newv ->
            sourceDisplayStatus.children.let { if (newv === null || newv.displayStatus === null) it.clear() else it.setAll(newv.displayStatus) }
            currentSourceStatus.textProperty().unbind()
            newv?.let {
				currentSourceStatus.textProperty().bind(Bindings.createStringBinding(
						Callable {
							if (it.statusTextProperty() != null && it.statusTextProperty().get() != null && !it.statusTextProperty().get().isEmpty())
								newv.statusTextProperty().get()
							else if (newv.nameProperty().get() != null)
								newv.nameProperty().get()
							else
								null
						},
						it.nameProperty(),
						it.statusTextProperty()
				))
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
                valueStatus,
                valueStatusSpacing,
				Region().also { HBox.setHgrow(it, Priority.ALWAYS) })
				.also { it.backgroundProperty().bind(pane.backgroundProperty()) }
				.also { it.prefWidthProperty().bind(statusBarPrefWidth) }

        val currentSourceStatusToolTip = Tooltip()
        currentSourceStatusToolTip.textProperty().bind(currentSourceStatus.textProperty())
        currentSourceStatus.tooltip = currentSourceStatusToolTip

        currentSourceStatus.prefWidth = 95.0
        viewerCoordinateStatus.prefWidth = 115.0
        worldCoordinateStatus.prefWidth = 245.0

        viewerCoordinateStatus.font = Font.font("Monospaced")
        worldCoordinateStatus.font = Font.font("Monospaced")

		statusBar.visibleProperty().bind(properties.statusBarConfig.isVisibleProperty())
		statusBar.managedProperty().bind(statusBar.visibleProperty())
		val statusBarParent = SimpleObjectProperty<Group?>(null)
		statusBarParent.addListener { _, oldv, newv ->
			oldv?.children?.remove(statusBar)
			newv?.children?.add(statusBar)
		}
		val modeToStatusBarGroup: (StatusBarConfig.Mode) -> Group = {
			when(it) {
				StatusBarConfig.Mode.OVERLAY -> centerPaneBottomAlignGroup
				StatusBarConfig.Mode.BOTTOM -> bottomGroup
			}
		}
		properties.statusBarConfig.modeProperty().addListener { _, _, mode -> statusBarParent.value = modeToStatusBarGroup(mode) }
		statusBarParent.value = modeToStatusBarGroup(properties.statusBarConfig.mode)

        val sourceTabs = SourceTabs(center.sourceInfo())

        val sourcesContents = TitledPane("Sources", sourceTabs.node)
				.also { it.isExpanded = false }
				.also { properties.sideBarConfig.widthProperty().addListener{ _, _, new -> it.maxWidth = new.toDouble() } }
				.also { it.padding = Insets.EMPTY }
				.also { it.widthProperty().addListener { _, _, new -> LOG.debug("sourceContents width is {} ({})", new, properties.sideBarConfig.width) } }

        val settingsContents = VBox(
            this.navigationConfigNode.getContents(),
            this.multiBoxOverlayConfigNode.contents,
            this.crosshairConfigNode.getContents(),
            this.orthoSliceConfigNode.getContents(),
            this.viewer3DConfigNode.contents,
            this.scaleBarConfigNode,
            this.bookmarkConfigNode,
            this.arbitraryMeshConfigNode,
            this.screenScaleConfigNode.contents)
        val settings = TitledPane("Settings", settingsContents)
        settings.isExpanded = false

        center.viewer3D().meshesGroup().children.add(this.arbitraryMeshConfigNode.getMeshGroup())

		paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }


		this.scrollPane = ScrollPane(VBox(sourcesContents, settings).also { it.prefWidthProperty().bind(properties.sideBarConfig.widthProperty()) })
				.also { it.prefWidthProperty().bind(properties.sideBarConfig.widthProperty()) }
				.also { it.maxWidthProperty().bind(properties.sideBarConfig.widthProperty()) }
				.also { it.widthProperty().addListener { _, _, new -> LOG.debug("scrollPane width is {} ({})", new, properties.sideBarConfig.width) } }
        this.sideBar = VBox(this.scrollPane)
				.also { it.prefWidthProperty().bind(properties.sideBarConfig.widthProperty()) }
				.also { it.maxWidthProperty().bind(properties.sideBarConfig.widthProperty()) }
				.also { it.visibleProperty().bind(properties.sideBarConfig.isVisibleProperty()) }
				.also { it.managedProperty().bind(it.visibleProperty()) }
				.also { it.widthProperty().addListener { _, _, new -> LOG.debug("sideBar width is {} ({})", new, properties.sideBarConfig.width) } }
        this.scrollPane.hbarPolicy = ScrollBarPolicy.NEVER
        this.scrollPane.vbarPolicy = ScrollBarPolicy.AS_NEEDED
		this.scrollPane.padding = Insets.EMPTY
		this.sideBar.padding = Insets.EMPTY
        sourceTabs.widthProperty().bind(sideBar.prefWidthProperty())
        settingsContents.prefWidthProperty().bind(sideBar.prefWidthProperty())
        pane.right = sideBar
		resizeSideBar = ResizeOnLeftSide(sideBar, properties.sideBarConfig.widthProperty()).also { it.install() }

	}

    fun bookmarkConfigNode() = this.bookmarkConfigNode

	private fun updateMenuBarParent(menuBar: MenuBar, mode: MenuBarConfig.Mode)  {
		val tc = this.topGroup.children
		val oc = this.centerPaneTopAlignGroup.children
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
