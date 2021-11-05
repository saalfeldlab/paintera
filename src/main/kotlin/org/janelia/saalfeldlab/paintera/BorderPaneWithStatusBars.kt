package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableObjectValue
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.MenuBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.TitledPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.extensions.createObjectBinding
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraDefaultHandlers.Companion.currentFocusHolder
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.StatusBarConfig
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.SettingsView
import org.janelia.saalfeldlab.paintera.ui.StatusBar
import org.janelia.saalfeldlab.paintera.ui.menus.MENU_BAR
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSlicesManager
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles

class BorderPaneWithStatusBars(paintera: PainteraMainWindow) {

    private val center = paintera.baseView

    private val painteraProperties = paintera.properties.apply {
        screenScalesConfig.screenScalesProperty().addListener { _, _, newv -> center.orthogonalViews().setScreenScales(newv.scalesCopy) }
    }

    private val sideBarWidthProperty = painteraProperties.sideBarConfig.widthProperty

    private val bottomGroup = Group()

    private val topGroup = Group()

    private val centerPaneTopAlignGroup = Group().also { StackPane.setAlignment(it, Pos.TOP_LEFT) }

    private val centerPaneBottomAlignGroup = Group().also { StackPane.setAlignment(it, Pos.BOTTOM_LEFT) }

    private val projectDirectory = SimpleObjectProperty<File>(null)

    private val centerPane = StackPane(center.orthogonalViews().grid(), centerPaneTopAlignGroup, centerPaneBottomAlignGroup)

    private val currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews())

    private val orthoSlicesManager = OrthoSlicesManager(
        center.viewer3D().sceneGroup(),
        center.orthogonalViews(),
        center.viewer3D().eyeToWorldTransformProperty()
    )

    private val settingsView = SettingsView().apply {
        bindWidth(sideBarWidthProperty)
        bindOrthoSlices(orthoSlicesManager.orthoSlices)
    }

    val sourceTabs = SourceTabs(center.sourceInfo()).apply {
        val widthMinusMargins = sideBarWidthProperty.createObjectBinding { it.value - 10.4 }
        widthProperty.bind(widthMinusMargins)
    }

    val sourcesContents = TitledPane("Sources", sourceTabs.node).apply {
        isExpanded = false
        padding = Insets.EMPTY
        maxWidthProperty().bind(sideBarWidthProperty)
        minWidthProperty().bind(sideBarWidthProperty)
        prefWidthProperty().bind(sideBarWidthProperty)
        widthProperty().addListener { _, _, new -> LOG.trace("sourceContents width is {} ({})", new, painteraProperties.sideBarConfig.width) }
    }

    val scrollPane = ScrollPane().apply {
        widthProperty().addListener { _, _, new -> LOG.trace("scrollPane width is {} ({})", new, painteraProperties.sideBarConfig.width) }

        hbarPolicy = ScrollBarPolicy.NEVER
        vbarPolicy = ScrollBarPolicy.AS_NEEDED
        padding = Insets.EMPTY
        content = VBox(sourcesContents, settingsView).apply {
            maxWidthProperty().bind(sideBarWidthProperty)
            minWidthProperty().bind(sideBarWidthProperty)
            prefWidthProperty().bind(sideBarWidthProperty)
        }

        maxWidthProperty().bind(sideBarWidthProperty)
        minWidthProperty().bind(sideBarWidthProperty)
        prefWidthProperty().bind(sideBarWidthProperty)
        visibleProperty().bind(painteraProperties.sideBarConfig.isVisibleProperty)
        managedProperty().bind(visibleProperty())

    }

    val pane = BorderPane(centerPane, topGroup, scrollPane, bottomGroup, null)

    private val resizeSideBar = ResizeOnLeftSide(scrollPane, sideBarWidthProperty).apply { install() }

    private val statusBarPrefWidth = Bindings.createDoubleBinding(
        { pane.width - if (painteraProperties.sideBarConfig.isVisible) painteraProperties.sideBarConfig.width else 0.0 },
        painteraProperties.sideBarConfig.isVisibleProperty,
        pane.widthProperty(),
        sideBarWidthProperty
    )

    private val statusBar = StatusBar(pane.backgroundProperty(), statusBarPrefWidth).apply {
        visibleProperty().bind(painteraProperties.statusBarConfig.isVisibleProperty())
        managedProperty().bind(visibleProperty())
    }

    private val statusBarParentProperty = SimpleObjectProperty<Group?>(null).apply {
        addListener { _, old, new ->
            old?.children?.remove(statusBar)
            new?.children?.add(statusBar)
        }
        val replaceParentBinding = painteraProperties.statusBarConfig.modeProperty().createObjectBinding {
            when (it.value!!) {
                StatusBarConfig.Mode.OVERLAY -> centerPaneBottomAlignGroup
                StatusBarConfig.Mode.BOTTOM -> bottomGroup
            }
        }
        bind(replaceParentBinding)
    }

    fun currentFocusHolder(): ObservableObjectValue<ViewerAndTransforms?> = this.currentFocusHolderWithState

    fun setViewerCoordinateStatus(point: RealPoint?) {
        statusBar.setViewerCoordinateStatus(point)
    }

    fun setWorldCoorinateStatus(p: RealPoint?) {
        statusBar.setWorldCoordinateStatus(p)
    }

    fun setCurrentStatus(status: String) {
        InvokeOnJavaFXApplicationThread { statusBar.statusValue = status }
    }

    init {
        LOG.debug("Init {}", BorderPaneWithStatusBars::class.java.name)
        initCrossHairs()
        toggleOnMenuBarConfigMode(MENU_BAR, painteraProperties.menuBarConfig.modeProperty)
        center.viewer3D().meshesGroup().children.add(settingsView.getMeshGroup())
        paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }
    }

    private fun initCrossHairs() {
        val crossHairs = makeCrosshairs(
            center.orthogonalViews(),
            Colors.CREMI,
            Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5))
        painteraProperties.crosshairConfig.bindCrosshairsToConfig(crossHairs.values)
    }

    fun bookmarkConfigNode() = this.settingsView.bookmarkConfigNode()

    private fun toggleOnMenuBarConfigMode(menuBar: MenuBar, modeProperty: SimpleObjectProperty<MenuBarConfig.Mode>) {
        /* Call once, then listen */
        val tc = this.topGroup.children
        val oc = this.centerPaneTopAlignGroup.children
        modeProperty.value.toggleMenuBarLocation(menuBar, tc, oc)
        modeProperty.addListener { _, _, newMode -> newMode.toggleMenuBarLocation(menuBar, tc, oc) }
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun MenuBarConfig.Mode.toggleMenuBarLocation(menuBar: MenuBar, topChildren: ObservableList<Node>, centerChildren: ObservableList<Node>) {
            when (this) {
                MenuBarConfig.Mode.OVERLAY -> {
                    topChildren.remove(menuBar)
                    centerChildren.add(menuBar)
                }
                MenuBarConfig.Mode.TOP -> {
                    centerChildren.remove(menuBar)
                    topChildren.add(menuBar)
                }
            }
        }

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
    }
}
