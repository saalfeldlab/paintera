package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.MenuBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.StatusBarConfig
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.SettingsView
import org.janelia.saalfeldlab.paintera.ui.StatusBar.Companion.createPainteraStatusBar
import org.janelia.saalfeldlab.paintera.ui.menus.menuBar
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

	private val bottomGroup = HBox().also {
		it.isFillHeight = false
		it.mouseTransparentProperty().set(true)
	}

	private val topLeftGroup = Group()
	private val topRightGroup = Group()

	private val topGroup = BorderPane().apply {
		left = topLeftGroup
		right = topRightGroup
		center = HBox().also { HBox.setHgrow(it, Priority.ALWAYS) }
	}

	private val centerPaneTopLeftAlignGroup = Group().also { StackPane.setAlignment(it, Pos.TOP_LEFT) }

	private val centerPaneTopRightAlignGroup = Group().also { StackPane.setAlignment(it, Pos.TOP_RIGHT) }

	private val centerPaneBottomAlignGroup = HBox().also {
		StackPane.setAlignment(it, Pos.BOTTOM_LEFT)
		it.isFillHeight = false
		it.mouseTransparentProperty().set(true)
	}

	private val projectDirectory = SimpleObjectProperty<File>(null)

	private val centerPane = StackPane(center.orthogonalViews().pane(), centerPaneTopLeftAlignGroup, centerPaneBottomAlignGroup, centerPaneTopRightAlignGroup)

	private val orthoSlicesManager = OrthoSlicesManager(
		center.viewer3D().sceneGroup,
		center.orthogonalViews(),
		center.viewer3D().eyeToWorldTransformProperty
	)

	private val settingsView = SettingsView().apply {
		bindWidth(sideBarWidthProperty)
		bindOrthoSlices(orthoSlicesManager.orthoSlices)
	}

	val sourceTabs = SourceTabs(center.sourceInfo()).apply {
		val widthMinusMargins = sideBarWidthProperty.createNonNullValueBinding { it.toDouble() - 10.4 }
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

	@Suppress("unused")
	private val resizeSideBar = ResizeOnLeftSide(scrollPane, sideBarWidthProperty).apply {
		verifyPermission(MenuActionType.ResizePanel, MenuActionType.ResizeViewers)
		install()
	}

	private val statusBarPrefWidth = Bindings.createDoubleBinding(
		{ pane.width - if (painteraProperties.sideBarConfig.isVisible) painteraProperties.sideBarConfig.width else 0.0 },
		painteraProperties.sideBarConfig.isVisibleProperty,
		pane.widthProperty(),
		sideBarWidthProperty
	)

	private val statusBar = createPainteraStatusBar(pane.backgroundProperty(), statusBarPrefWidth, painteraProperties.statusBarConfig.isVisibleProperty())

	@Suppress("unused")
	private val statusBarParentProperty = SimpleObjectProperty<HBox?>(null).apply {
		addListener { _, old, new ->
			old?.children?.remove(statusBar)
			new?.children?.add(statusBar)
		}
		val replaceParentBinding = painteraProperties.statusBarConfig.modeProperty().createNullableValueBinding {
			when (it!!) {
				StatusBarConfig.Mode.OVERLAY -> centerPaneBottomAlignGroup
				StatusBarConfig.Mode.BOTTOM -> bottomGroup
			}
		}
		bind(replaceParentBinding)
	}

	init {
		LOG.debug("Init {}", BorderPaneWithStatusBars::class.java.name)
		initCrossHairs()
		toggleOnMenuBarConfigMode(menuBar)
		paintera.baseView.activeModeProperty.addListener { _, _, new ->
			(new as? ToolMode)?.also { toolMode ->
				val toolBar = toolMode.createToolBar()
				toolBar.visibleProperty().bind(painteraProperties.toolBarConfig.isVisibleProperty)
				toolBar.managedProperty().bind(toolBar.visibleProperty())
				toggleOnToolBarConfigMode(toolBar)
			}
		}
		center.viewer3D().meshesGroup.children.add(settingsView.getMeshGroup())
		paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }
	}

	private fun initCrossHairs() {
		val crossHairs = makeCrosshairs(
			center.orthogonalViews(),
			Colors.CREMI,
			Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5)
		)
		painteraProperties.crosshairConfig.bindCrosshairsToConfig(crossHairs.values)
	}

	fun bookmarkConfigNode() = this.settingsView.bookmarkConfigNode()

	private fun toggleOnMenuBarConfigMode(menuBar: MenuBar) {
		/* Call once, then listen */
		val tc = this.topLeftGroup.children
		val oc = this.centerPaneTopLeftAlignGroup.children
		val modeProperty = painteraProperties.menuBarConfig.modeProperty
		modeProperty.value.toggleMenuBarLocation(menuBar, tc, oc)
		modeProperty.addListener { _, _, newMode -> newMode.toggleMenuBarLocation(menuBar, tc, oc) }
	}

	private fun toggleOnToolBarConfigMode(toolBar: Node) {
		/* Call once, then listen */
		val tc = this.topRightGroup.children.also { it.clear() }
		val oc = this.centerPaneTopRightAlignGroup.children.also { it.clear() }
		val modeProperty = painteraProperties.menuBarConfig.modeProperty
		modeProperty.value.toggleMenuBarLocation(toolBar, tc, oc)
		modeProperty.addListener { _, _, newMode -> newMode.toggleMenuBarLocation(toolBar, tc, oc) }
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun MenuBarConfig.Mode.toggleMenuBarLocation(menuBar: Node, topChildren: ObservableList<Node>, centerChildren: ObservableList<Node>) {
			when (this) {
				MenuBarConfig.Mode.OVERLAY -> {
					topChildren -= menuBar
					centerChildren += menuBar
				}

				MenuBarConfig.Mode.TOP -> {
					centerChildren -= menuBar
					topChildren += menuBar
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
			ch.setHighlightColor(onFocusColor)
			ch.setRegularColor(offFocusColor)
			viewer.display.addOverlayRenderer(ch)
			ch.wasChangedProperty().addListener { _, _, _ -> viewer.display.drawOverlays() }
			ch.isHighlightProperty.bind(viewer.focusedProperty())
			return ch
		}

	}
}
