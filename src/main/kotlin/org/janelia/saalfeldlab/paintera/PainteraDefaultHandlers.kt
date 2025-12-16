package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import bdv.viewer.Interpolation
import bdv.viewer.Source
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.input.*
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.input.MouseEvent.MOUSE_PRESSED
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.transform.Affine
import javafx.stage.Stage
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.bdv.fx.viewer.multibox.MultiBoxOverlayRendererFX
import org.janelia.saalfeldlab.bdv.fx.viewer.scalebar.ScaleBarOverlayRenderer
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl.TOGGLE_OFF
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl.TOGGLE_ON
import org.janelia.saalfeldlab.fx.actions.Action.Companion.installAction
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.KeyAction.Companion.onAction
import org.janelia.saalfeldlab.fx.actions.MouseAction
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.painteraMidiActionSet
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.ortho.DynamicCellPane
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.paintera.config.BookmarkConfig
import org.janelia.saalfeldlab.paintera.config.BookmarkSelectionDialog
import org.janelia.saalfeldlab.paintera.control.FitToInterval
import org.janelia.saalfeldlab.paintera.control.RunWhenFirstElementIsAdded
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.NavigationControlMode
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.ui.StatusBar.Companion.createPainteraStatusBar
import org.janelia.saalfeldlab.paintera.ui.dialogs.ExportSourceDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.OpenSourceDialog
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Arrays
import java.util.function.Supplier

class PainteraDefaultHandlers(private val paintera: PainteraMainWindow, paneWithStatus: BorderPaneWithStatusBars) {

	private val baseView = paintera.baseView

	private val projectDirectory = Supplier { paintera.projectDirectory.actualDirectory.absolutePath }

	private val properties = paintera.properties

	private val orthogonalViews = baseView.orthogonalViews()

	private val viewersTopLeftTopRightBottomLeft = arrayOf(
		orthogonalViews.topLeft,
		orthogonalViews.topRight,
		orthogonalViews.bottomLeft
	)

	internal val globalActionHandlers = FXCollections.observableArrayList<ActionSet>().apply {
		addListener(ListChangeListener { change ->
			while (change.next()) {
				change.removed.forEach { paneWithStatus.pane.removeActionSet(it) }
				change.addedSubList.forEach { paneWithStatus.pane.installActionSet(it) }
			}
		})
	}

	private val mouseInsidePropertiesTopLeftTropRightBottomLeft = viewersTopLeftTopRightBottomLeft
		.map { it.viewer().isMouseInsideProperty }
		.toTypedArray()

	private val sourceInfo = baseView.sourceInfo()

	private val multiBoxes: Array<MultiBoxOverlayRendererFX>
	private val multiBoxVisibilities = mouseInsidePropertiesTopLeftTropRightBottomLeft
		.map { mouseInside ->
			Bindings.createBooleanBinding(
				{
					when (properties.multiBoxOverlayConfig.visibility) {
						MultiBoxOverlayConfig.Visibility.ON -> true
						MultiBoxOverlayConfig.Visibility.OFF -> false
						MultiBoxOverlayConfig.Visibility.ONLY_IN_FOCUSED_VIEWER -> mouseInside.value
					}
				},
				mouseInside,
				properties.multiBoxOverlayConfig.visibilityProperty()
			)
		}
		.toTypedArray()
		.also {
			it.forEachIndexed { index, isVisible -> isVisible.addListener { _, _, _ -> viewersTopLeftTopRightBottomLeft[index].viewer().display.drawOverlays() } }
		}

	private val globalInterpolationProperty = SimpleObjectProperty<Interpolation>()

	private val scaleBarOverlays = listOf(
		ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig),
		ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig),
		ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig)
	)

	private val viewerToTransforms = HashMap<ViewerPanelFX, ViewerAndTransforms>()

	init {
		sourceInfo.currentState().subscribe { newState ->
			paintera.baseView.changeMode(newState?.defaultMode ?: NavigationControlMode)
		}
		sourceInfo.currentSourceProperty().addListener { _, oldSource, newSource ->
			(oldSource as? MaskedSource<*, *>)?.apply {
				paintera.baseView.disabledPropertyBindings.remove(oldSource)
			}
			(newSource as? MaskedSource<*, *>)?.apply {
				paintera.baseView.disabledPropertyBindings[newSource] = isBusyProperty
			}
		}

		baseView.orthogonalViews().views().forEach { grabFocusOnMouseOver(it) }

		globalActionHandlers + addOpenDatasetAction(paneWithStatus.pane)
		globalActionHandlers + addExportDatasetAction(paneWithStatus.pane)

		viewerToTransforms[orthogonalViews.topLeft.viewer()] = orthogonalViews.topLeft
		viewerToTransforms[orthogonalViews.topRight.viewer()] = orthogonalViews.topRight
		viewerToTransforms[orthogonalViews.bottomLeft.viewer()] = orthogonalViews.bottomLeft

		multiBoxes = arrayOf(
			MultiBoxOverlayRendererFX(
				{ baseView.orthogonalViews().topLeft.viewer().state },
				sourceInfo.trackSources(),
				sourceInfo.trackVisibleSources()
			),
			MultiBoxOverlayRendererFX(
				{ baseView.orthogonalViews().topRight.viewer().state },
				sourceInfo.trackSources(),
				sourceInfo.trackVisibleSources()
			),
			MultiBoxOverlayRendererFX(
				{ baseView.orthogonalViews().bottomLeft.viewer().state },
				sourceInfo.trackSources(),
				sourceInfo.trackVisibleSources()
			)
		).also { m -> m.forEachIndexed { idx, mb -> mb.isVisibleProperty.bind(multiBoxVisibilities[idx]) } }

		orthogonalViews.topLeft.viewer().display.addOverlayRenderer(multiBoxes[0])
		orthogonalViews.topRight.viewer().display.addOverlayRenderer(multiBoxes[1])
		orthogonalViews.bottomLeft.viewer().display.addOverlayRenderer(multiBoxes[2])

		updateDisplayTransformOnResize(baseView.orthogonalViews(), baseView.manager())

		val borderPane = paneWithStatus.pane

		baseView.allowedActionsProperty().addListener { _, _, new ->
			val disableSidePanel = new.isAllowed(MenuActionType.ToggleSidePanel).not()
			paneWithStatus.scrollPane.disableProperty().set(disableSidePanel)
		}

		sourceInfo.trackSources().addListener(createSourcesInterpolationListener())

		val keyCombinations = ControlMode.keyAndMouseBindings.keyCombinations


		DeviceManager.xTouchMini?.let { device ->
			val midiToggle = painteraMidiActionSet("Toggle Interpolation", device, borderPane) {
				MidiToggleEvent.BUTTON_TOGGLE(16) {
					control.value = if (globalInterpolationProperty.get() == Interpolation.NLINEAR) TOGGLE_ON else TOGGLE_OFF
					var setSilently = false
					val globalInterpListener = ChangeListener<Interpolation> { _, old, new ->
						if (new != old) {
							setSilently = true
							control.value = if (new == Interpolation.NLINEAR) TOGGLE_ON else TOGGLE_OFF
							setSilently = false
						}
					}
					afterRegisterEvent = { globalInterpolationProperty.addListener(globalInterpListener) }
					afterRemoveEvent = { globalInterpolationProperty.removeListener(globalInterpListener) }
					verifyEventNotNull()
					onAction {
						if (!setSilently) toggleInterpolation()
					}

				}
			}
			borderPane.installActionSet(midiToggle)
		}

		val toggleInterpolation = KEY_PRESSED.onAction(keyCombinations, PainteraBaseKeys.CYCLE_INTERPOLATION_MODES) { toggleInterpolation() }
		borderPane.installAction(toggleInterpolation)


		sourceInfo.trackSources().addListener(
			FitToInterval.fitToIntervalWhenSourceAddedListener(baseView.manager()) { baseView.orthogonalViews().topLeft.viewer().widthProperty().get() }
		)
		sourceInfo.trackSources().addListener(RunWhenFirstElementIsAdded {
			baseView.viewer3D().setInitialTransformToInterval(sourceIntervalInWorldSpace(it.addedSubList[0]))
		})

		orthogonalViews.pane().apply {
			cells().forEach { cell ->
				/* Toggle Maxmizing the Viewers */
				val maximizeCellActions = painteraActionSet("Maximize Viewer", MenuActionType.ToggleMaximizeViewer) {
					KEY_PRESSED(keyCombinations, PainteraBaseKeys.MAXIMIZE_VIEWER) {
						keysExclusive = true
						verify("Can Only Maximize From the Main Window ") { cell.scene == paintera.baseView.node.scene }
						onAction { toggleMaximize(cell) }
					}
					KEY_PRESSED(keyCombinations, PainteraBaseKeys.MAXIMIZE_VIEWER_AND_3D) {
						keysExclusive = true
						verify("Can Only Maximize with 3D From the Main Window ") { cell.scene == paintera.baseView.node.scene }
						onAction { toggleMaximize(cell, orthogonalViews.bottomRight) }
					}
				}
				val detachCellActions = painteraActionSet("Detach Viewer", MenuActionType.DetachViewer) {
					KEY_PRESSED(keyCombinations, PainteraBaseKeys.DETACH_VIEWER_WINDOW) {
						keysExclusive = true
						verify("Dont Detach If Only One Cell Already") { if (cell.scene == paintera.baseView.node.scene) cells().count() > 1 else true }
						onAction { detachCell(cell) }
					}
				}
				cell.installActionSet(maximizeCellActions)
				cell.installActionSet(detachCellActions)
			}
		}

		val contextMenuFactory = MeshesGroupContextMenu(baseView.manager())

		val contextMenuProperty = SimpleObjectProperty<ContextMenu>()
		var contextMenu by contextMenuProperty.nullable()
		val hideContextMenu = {
			contextMenu?.let {
				it.hide()
				contextMenu = null
			}
		}

		val meshContextMenuActions = painteraActionSet("3D_mesh_context_menu",  MenuActionType.OrthoslicesContextMenu, ignoreDisable = true) {
			 MOUSE_CLICKED(MouseButton.SECONDARY) {
				 verify("single click") { it?.clickCount == 1 }
				 verify("not dragging") { !paintera.mouseTracker.isDragging }
				 onAction {
					 it?.pickResult?.let { result ->
						 if (result.intersectedNode != null) {
							 val point = result.intersectedPoint
							 val menu = contextMenuFactory.createMenu(doubleArrayOf(point.x, point.y, point.z))
							 contextMenuProperty.set(menu)
							 menu.show(baseView.viewer3D(), it.screenX, it.screenY)
						 }
					 } ?: hideContextMenu()
				 }
			 }
		}
		baseView.viewer3D().meshesGroup.installActionSet(meshContextMenuActions)
		baseView.viewer3D().installAction (
			MouseAction(MOUSE_PRESSED).apply {
				onAction { hideContextMenu() }
			}
		)

		this.baseView.orthogonalViews().topLeft.viewer().addTransformListener(scaleBarOverlays[0])
		this.baseView.orthogonalViews().topLeft.viewer().display.addOverlayRenderer(scaleBarOverlays[0])
		this.baseView.orthogonalViews().topRight.viewer().addTransformListener(scaleBarOverlays[1])
		this.baseView.orthogonalViews().topRight.viewer().display.addOverlayRenderer(scaleBarOverlays[1])
		this.baseView.orthogonalViews().bottomLeft.viewer().addTransformListener(scaleBarOverlays[2])
		this.baseView.orthogonalViews().bottomLeft.viewer().display.addOverlayRenderer(scaleBarOverlays[2])
		properties.scaleBarOverlayConfig.change.addListener { this.baseView.orthogonalViews().applyToAll { vp -> vp.display.drawOverlays() } }

		val addBookmarkKeyCode = KeyCodeCombination(KeyCode.B)
		val addBookmarkWithCommentKeyCode = KeyCodeCombination(KeyCode.B, KeyCombination.SHIFT_DOWN)
		val applyBookmarkKeyCode = KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN)
		//TODO Caleb: Add as global action?
		paneWithStatus.pane.addEventHandler(KEY_PRESSED) {
			if (baseView.isActionAllowed(NavigationActionType.Bookmark)) {
				when {
					addBookmarkKeyCode.match(it) -> {
						it.consume()
						val globalTransform = AffineTransform3D()
						baseView.manager().getTransform(globalTransform)
						val viewer3DTransform = Affine()
						baseView.viewer3D().getAffine(viewer3DTransform)
						properties.bookmarkConfig.addBookmark(BookmarkConfig.Bookmark(globalTransform, viewer3DTransform, null))
					}

					addBookmarkWithCommentKeyCode.match(it) -> {
						it.consume()
						val globalTransform = AffineTransform3D()
						baseView.manager().getTransform(globalTransform)
						val viewer3DTransform = Affine()
						baseView.viewer3D().getAffine(viewer3DTransform)
						paneWithStatus.bookmarkConfigNode().requestAddNewBookmark(globalTransform, viewer3DTransform)
					}

					applyBookmarkKeyCode.match(it) -> {
						it.consume()
						BookmarkSelectionDialog(properties.bookmarkConfig.unmodifiableBookmarks)
							.showAndWaitForBookmark()
							.ifPresent { bm ->
								baseView.manager().setTransformAndAnimate(bm.globalTransformCopy, properties.bookmarkConfig.getTransitionTime())
								baseView.viewer3D().setAffine(bm.viewer3DTransformCopy, properties.bookmarkConfig.getTransitionTime())
							}
					}
				}
			}
		}

	}

	private fun DynamicCellPane.detachCell(cell: Node) {
		val closeNotifier = SimpleBooleanProperty(false)

		val uiCallback = { stackPane: StackPane, borderPane: BorderPane ->

			val setupToolbar: (StackPane, Node) -> Unit = { pane, toolbar ->
				val group = Group(toolbar)
				group.visibleProperty().bind(paintera.properties.toolBarConfig.isVisibleProperty)
				group.managedProperty().bind(group.visibleProperty())
				pane.children.removeIf { it.id == "toolbar" }
				group.id = "toolbar"
				pane.children.add(group)
				StackPane.setAlignment(group, Pos.TOP_RIGHT)
			}

			val toolBarListener = ChangeListener<ControlMode> { _, _, new ->
				(new as? ToolMode)?.createToolBar()?.let { toolbar -> setupToolbar(stackPane, toolbar) }

			}

			paintera.baseView.activeModeProperty.let { modeProp ->
				modeProp.addListener(toolBarListener)
				(modeProp.value as? ToolMode)?.let { mode -> setupToolbar(stackPane, mode.createToolBar()) }
			}

			closeNotifier.addListener { _, _, closed ->
				if (closed) paintera.baseView.activeModeProperty.removeListener(toolBarListener)
			}

			borderPane.bottom =
				createPainteraStatusBar(paintera.properties.statusBarConfig.isVisibleProperty)
		}


		val onClose = { stage: Stage ->
			closeNotifier.set(true)
			paintera.keyTracker.removeFrom(stage)
			stage.removeEventFilter(MouseEvent.ANY, paintera.mouseTracker)
		}

		val beforeShow = { stage: Stage ->
			paintera.keyTracker.installInto(stage)
			stage.addEventFilter(MouseEvent.ANY, paintera.mouseTracker)
		}

		val row = if (cell == orthogonalViews.bottomLeft.viewer()) 1 else 0
		val col = if (cell == orthogonalViews.topRight.viewer()) 1 else 0

		/* Hack to keep viewer on the bottom row */
		if (items.size == 1 && row == 1) {
			addRow(0)
			toggleNodeDetach(cell, "Paintera", onClose, beforeShow, row to col, uiCallback)
			removeRow(0)
		} else {
			toggleNodeDetach(cell, "Paintera", onClose, beforeShow, row to col, uiCallback)
		}
	}

	private fun toggleInterpolation() {
		if (globalInterpolationProperty.get() != null) {
			globalInterpolationProperty.set(if (globalInterpolationProperty.get() == Interpolation.NLINEAR) Interpolation.NEARESTNEIGHBOR else Interpolation.NLINEAR)
			baseView.orthogonalViews().requestRepaint()
		}
	}

	private fun createSourcesInterpolationListener(): InvalidationListener {
		return InvalidationListener {
			if (globalInterpolationProperty.get() == null && !sourceInfo.trackSources().isEmpty()) {
				// initially set the global interpolation state based on source interpolation
				val source = sourceInfo.trackSources().iterator().next()
				val sourceState = sourceInfo.getState(source)
				globalInterpolationProperty.set(sourceState.interpolationProperty().get())
			}

			// bind all source interpolation states to the global state
			for (source in sourceInfo.trackSources()) {
				val sourceState = sourceInfo.getState(source)
				sourceState.interpolationProperty().bind(globalInterpolationProperty)
			}
		}
	}

	fun addOpenDatasetAction(target: Node): ActionSet {

		val actionSet = OpenSourceDialog.actionSet(baseView)
		target.installActionSet(actionSet)
		return actionSet
	}

	fun addExportDatasetAction(target: Node): ActionSet {

		val actionSet = ExportSourceDialog.exportSourceDialogAction(baseView)
		target.installActionSet(actionSet)
		return actionSet
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		fun updateDisplayTransformOnResize(
			views: OrthogonalViews<*>,
			lock: Any,
		): Array<DisplayTransformUpdateOnResize> {
			return arrayOf(
				updateDisplayTransformOnResize(views.topLeft, lock),
				updateDisplayTransformOnResize(views.topRight, lock),
				updateDisplayTransformOnResize(views.bottomLeft, lock)
			)
		}

		fun updateDisplayTransformOnResize(vat: ViewerAndTransforms, lock: Any): DisplayTransformUpdateOnResize {
			val viewer = vat.viewer()
			val displayTransform = vat.displayTransform
			val updater = DisplayTransformUpdateOnResize(
				displayTransform,
				viewer.widthProperty(),
				viewer.heightProperty(),
				lock
			)
			updater.listen()
			return updater
		}

		fun grabFocusOnMouseOver(node: Node) {
			node.addEventFilter(MouseEvent.MOUSE_ENTERED) {
				if (!node.focusedProperty().get()) {
					node.requestFocus()
				}
			}
		}

		fun sourceIntervalInWorldSpace(source: Source<*>): Interval {
			val min = Arrays.stream(
				Intervals.minAsLongArray(
					source.getSource(
						0,
						0
					)
				)
			).asDoubleStream().toArray()
			val max = Arrays.stream(
				Intervals.maxAsLongArray(
					source.getSource(
						0,
						0
					)
				)
			).asDoubleStream().toArray()
			val tf = AffineTransform3D()
			source.getSourceTransform(0, 0, tf)
			tf.apply(min, min)
			tf.apply(max, max)
			return Intervals.smallestContainingInterval(FinalRealInterval(min, max))
		}
	}
}
