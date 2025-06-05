package org.janelia.saalfeldlab.paintera

import javafx.animation.Interpolator
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.MenuBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.TitledPane
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.util.Duration
import javafx.util.Subscription
import org.checkerframework.common.reflection.qual.Invoke
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.interpolate
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.MenuBarConfig
import org.janelia.saalfeldlab.paintera.config.StatusBarConfig
import org.janelia.saalfeldlab.paintera.config.ToolBarConfig
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.SettingsView
import org.janelia.saalfeldlab.paintera.ui.StatusBar
import org.janelia.saalfeldlab.paintera.ui.StatusBar.Companion.createPainteraStatusBar
import org.janelia.saalfeldlab.paintera.ui.menus.menuBar
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs
import org.janelia.saalfeldlab.paintera.ui.vGrow
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSlicesManager
import org.janelia.saalfeldlab.util.Colors
import org.reactfx.value.Val.animate
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles

class BorderPaneWithStatusBars(paintera: PainteraMainWindow) {

	private val center = paintera.baseView

	private val painteraProperties = paintera.properties.apply {
		screenScalesConfig.screenScalesProperty().addListener { _, _, newv -> center.orthogonalViews().setScreenScales(newv.scalesCopy) }
	}

	private val sideBarWidthProperty = painteraProperties.sideBarConfig.widthProperty

	private val centerPaneBottomGroup = HBox().apply {
		maxWidth = Double.MAX_VALUE
	}
	private val centerPaneBottomHBox = HBox().apply {
		isPickOnBounds = false
		maxWidth = Double.MAX_VALUE
		isFillHeight = false
		StackPane.setAlignment(this, Pos.BOTTOM_LEFT)
	}
	private val centerPaneBottomVBox = VBox().apply {
		isFillWidth = true
		isPickOnBounds = false
		maxWidth = Double.MAX_VALUE
		StackPane.setAlignment(this, Pos.BOTTOM_LEFT)
		alignment = Pos.BOTTOM_LEFT
		children += centerPaneBottomGroup
		children += centerPaneBottomHBox
	}
	private val centerPaneBottomLeftHBox = HBox().apply {
		isPickOnBounds = false
		isFillHeight = false
		maxWidth = Double.MAX_VALUE
		alignment = Pos.BOTTOM_LEFT

		centerPaneBottomHBox.children += this
	}


	private val centerPaneTopGroup = HBox().apply {
		maxWidth = Double.MAX_VALUE
	}
	private val centerPaneTopHBox = HBox().apply {
		isPickOnBounds = false
		maxWidth = Double.MAX_VALUE
		isFillHeight = false
		StackPane.setAlignment(this, Pos.TOP_LEFT)
	}
	private val centerPaneTopVBox = VBox().apply {
		isFillWidth = true
		isPickOnBounds = false
		maxWidth = Double.MAX_VALUE
		StackPane.setAlignment(this, Pos.TOP_LEFT)
		children += centerPaneTopGroup
		children += centerPaneTopHBox
	}
	private val centerPaneTopLeftHBox = HBox().apply {
		isPickOnBounds = false
		isFillHeight = false
		maxWidth = Double.MAX_VALUE
		alignment = Pos.TOP_LEFT

		centerPaneTopHBox.children += this
	}

	private val centerPaneTopRightHBox = HBox().apply {
		isPickOnBounds = false
		isFillHeight = false
		alignment = Pos.TOP_RIGHT
		maxWidth = Double.MAX_VALUE
		/* fill remaining width */
		prefWidthProperty().bind(centerPaneTopHBox.widthProperty().subtract(centerPaneTopLeftHBox.widthProperty()))
		centerPaneTopHBox.children += this
	}


	private val projectDirectory = SimpleObjectProperty<File>(null)

	private val centerPane = StackPane(center.orthogonalViews().pane(), centerPaneTopVBox, centerPaneBottomVBox)

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
		maxWidthProperty().bind(widthMinusMargins)
	}

	val sourcesContents = TitledPane("Sources", sourceTabs).apply {
		isExpanded = false
		padding = Insets.EMPTY
		maxWidthProperty().bind(sideBarWidthProperty)
	}

	val scrollPane = ScrollPane().apply {
		hbarPolicy = ScrollBarPolicy.NEVER
		vbarPolicy = ScrollBarPolicy.AS_NEEDED
		padding = Insets.EMPTY
		content = VBox(sourcesContents, settingsView).apply {
			isFillWidth = true
			maxWidth = Double.MAX_VALUE
		}
	}

	private val bottomVBox = VBox().apply {
		maxWidth = Double.MAX_VALUE
		alignment = Pos.BOTTOM_LEFT
		isPickOnBounds = false
		isFillWidth = true
	}

	val bottomGroup = HBox().apply {
		maxWidth = Double.MAX_VALUE
		alignment = Pos.BOTTOM_LEFT
		maxWidth = Double.MAX_VALUE
	}

	private val rightTopGroup = HBox().vGrow(Priority.NEVER)

	internal val rightGroup = VBox().apply {
		alignment = Pos.TOP_RIGHT
		isFillWidth = true
		children += rightTopGroup
		children += scrollPane
		managedProperty().bind(visibleProperty())
	}

	val pane = BorderPane(centerPane, null, rightGroup, bottomVBox, null)

	@Suppress("unused")
	private val resizeSideBar = ResizeOnLeftSide(rightGroup, sideBarWidthProperty).apply {
		verifyPermission(MenuActionType.ResizePanel, MenuActionType.ResizeViewers)
		rightGroup.prefWidthProperty().bind(sideBarWidthProperty)
		install()
	}

	private val statusBar = createPainteraStatusBar(pane.backgroundProperty(), painteraProperties.statusBarConfig.isVisibleProperty)

	init {
		LOG.debug("Init {}", BorderPaneWithStatusBars::class.java.name)
		initCrossHairs()
		toggleOnMenuBarConfigMode(menuBar)
		toggleOnStatusBarConfigMode(statusBar)
		var prevSub: Subscription? = null
		paintera.baseView.activeModeProperty.addListener { _, old, new ->
			prevSub?.unsubscribe()
			(new as? ToolMode)?.createToolBar()?.also { toolBar ->
				prevSub = painteraProperties.toolBarConfig.isVisibleProperty.subscribe { isVisible ->
					toolBar.show(isVisible)
				}.and(toggleOnToolBarConfigMode(toolBar))
			}
		}

		center.viewer3D().meshesGroup.children.add(settingsView.getMeshGroup())
		paintera.projectDirectory.addListener { projectDirectory.set(it.directory) }


		val animateSideBar = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()
		fun toggleSideBar(isVisible: Boolean, animate: Boolean = true) {
			if (isVisible)
				animateSideBar.submit { expandSideBar(animate) }
			else
				animateSideBar.submit { minimizeSideBar(animate) }
		}
		/* Trigger once w/o animation */
		toggleSideBar(painteraProperties.sideBarConfig.isVisible, animate = false)

		/* listen for changes*/
		properties.sideBarConfig.isVisibleProperty.subscribe { _, isVisible ->
			toggleSideBar(isVisible)
		}
	}

	internal fun minimizeSideBar(animate: Boolean = true) {
		if ("animating" in rightGroup.properties.keys)
			return

		if (!animate) {
			rightGroup.visibleProperty().value = false
			return
		}

		rightGroup.properties["animating"] = true
		val prevWidth = sideBarWidthProperty.value
		sideBarWidthProperty.asObject().interpolate(
			to = 0.0,
			interpolator = Interpolator.EASE_OUT,
			time = Duration.millis(350.0)
		) {
			rightGroup.visibleProperty().value = false
			sideBarWidthProperty.set(prevWidth)
			rightGroup.properties -= "animating"
		}
	}

	internal fun expandSideBar(animate : Boolean = true) {
		if ("animating" in rightGroup.properties.keys)
			return

		if (!animate) {
			rightGroup.visibleProperty().value = true
			return
		}

		rightGroup.properties["animating"] = true
		val prevWidth = sideBarWidthProperty.value
		sideBarWidthProperty.value = 0.0
		rightGroup.visibleProperty().value = true
		sideBarWidthProperty.asObject().interpolate(
			to = prevWidth,
			interpolator = Interpolator.EASE_IN,
			time = Duration.millis(350.0)
		) {
			sideBarWidthProperty.set(prevWidth)
			rightGroup.properties -= "animating"

		}
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

	private fun toggleOnMenuBarConfigMode(menuBar: MenuBar): Subscription {
		val modeProperty = painteraProperties.menuBarConfig.modeProperty
		var removeOld: Subscription? = null
		return modeProperty.subscribe { mode ->
			removeOld?.unsubscribe()
			removeOld = mode.moveMenuBarLocation(menuBar, centerPaneTopGroup, centerPaneTopLeftHBox)
		}.and { removeOld?.unsubscribe() }
	}

	private fun toggleOnStatusBarConfigMode(statusBar: StatusBar): Subscription {
		val modeProperty = painteraProperties.statusBarConfig.modeProperty
		var removeOld: Subscription? = null
		return modeProperty.subscribe { mode ->
			removeOld?.unsubscribe()
			removeOld = mode.moveStatusBarLocation(statusBar, pane, centerPaneBottomLeftHBox)
		}.and { removeOld?.unsubscribe() }
	}

	private fun toggleOnToolBarConfigMode(toolBar: FlowPane): Subscription {
		val modeProperty = painteraProperties.toolBarConfig.modeProperty
		var removeOld: Subscription? = null
		return modeProperty.subscribe { mode ->
			removeOld?.unsubscribe()
			removeOld = mode.moveToolBarLocation(toolBar, rightTopGroup, centerPaneTopRightHBox)
		}.and { removeOld?.unsubscribe() }
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun MenuBarConfig.Mode.moveMenuBarLocation(menuBar: Node, topGroup: Pane, overlayGroup: Pane): Subscription {
			return when (this) {
				MenuBarConfig.Mode.OVERLAY -> {
					overlayGroup.children += menuBar
					Subscription { overlayGroup.children -= menuBar }
				}

				MenuBarConfig.Mode.TOP -> {
					topGroup.styleClass += "menu-bar"
					topGroup.children += menuBar
					Subscription {
						topGroup.children -= menuBar
						topGroup.styleClass -= "menu-bar"
					}
				}
			}
		}

		private fun StatusBarConfig.Mode.moveStatusBarLocation(statusBar: Node, borderPane: BorderPane, overlayGroup: Pane): Subscription {
			return when (this) {
				StatusBarConfig.Mode.OVERLAY -> {
					overlayGroup.children += statusBar
					Subscription { overlayGroup.children -= statusBar }
				}

				StatusBarConfig.Mode.BOTTOM -> {
					borderPane.bottom = statusBar
					borderPane.styleClass += "status-bar"
					Subscription {
						borderPane.bottom = null
						borderPane.styleClass -= "status-bar"
					}
				}
			}
		}

		private fun ToolBarConfig.Mode.moveToolBarLocation(toolBar: Node, rightGroup: Pane, overlayGroup: Pane): Subscription {
			return when (this) {
				ToolBarConfig.Mode.OVERLAY -> {
					overlayGroup.children += toolBar
					Subscription { overlayGroup.children -= toolBar }
				}

				ToolBarConfig.Mode.RIGHT -> {
					rightGroup.children += toolBar
					rightGroup.styleClass += "toolbar"
					Subscription {
						rightGroup.children -= toolBar
						rightGroup.styleClass -= "toolbar"
					}
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
