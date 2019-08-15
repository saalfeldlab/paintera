package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
import bdv.fx.viewer.multibox.MultiBoxOverlayRendererFX
import bdv.fx.viewer.scalebar.ScaleBarOverlayRenderer
import bdv.viewer.Interpolation
import bdv.viewer.Source
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.IntegerBinding
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableObjectValue
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.input.*
import javafx.scene.transform.Affine
import net.imglib2.FinalRealInterval
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers
import org.janelia.saalfeldlab.fx.event.EventFX
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedColumn
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedRow
import org.janelia.saalfeldlab.fx.ortho.GridResizer
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.config.BookmarkConfig
import org.janelia.saalfeldlab.paintera.config.BookmarkSelectionDialog
import org.janelia.saalfeldlab.paintera.control.*
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize
import org.janelia.saalfeldlab.paintera.ui.ARGBStreamSeedSetter
import org.janelia.saalfeldlab.paintera.ui.ToggleMaximize
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenu
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.DoubleSupplier
import java.util.function.Predicate
import java.util.function.Supplier

class PainteraDefaultHandlers2(
        private val paintera: PainteraMainWindow, paneWithStatus: BorderPaneWithStatusBars2) {

	private val baseView = paintera.baseView

	private val keyTracker = paintera.keyTracker

	private val mouseTracker = paintera.mouseTracker

	private val projectDirectory = Supplier { paintera.projectDirectory.actualDirectory.absolutePath }

	private val properties = paintera.getProperties()

    private val orthogonalViews = baseView.orthogonalViews()

	private val sourceInfo = baseView.sourceInfo()

	private val numSources: IntegerBinding

    private val hasSources: BooleanBinding

    private val navigation = Navigation(
			baseView.manager(),
			{ viewerToTransforms[it]!!.displayTransform() },
			{ viewerToTransforms[it]!!.globalToViewerTransform() },
			keyTracker,
			baseView.allowedActionsProperty())

    private val onEnterOnExit: Consumer<OnEnterOnExit>

    private val toggleMaximizeTopLeft: ToggleMaximize
    private val toggleMaximizeTopRight: ToggleMaximize
    private val toggleMaximizeBottomLeft: ToggleMaximize

    private val multiBoxes: Array<MultiBoxOverlayRendererFX>

    private val resizer: GridResizer

    private val globalInterpolationProperty = SimpleObjectProperty<Interpolation>()

    private val openDatasetContextMenuHandler: EventHandler<KeyEvent>

    private val sourceSpecificGlobalEventHandler: ObjectBinding<EventHandler<Event>>

    private val sourceSpecificGlobalEventFilter: ObjectBinding<EventHandler<Event>>

    private val sourceSpecificViewerEventHandler: ObjectBinding<EventHandler<Event>>

    private val sourceSpecificViewerEventFilter: ObjectBinding<EventHandler<Event>>

    private val scaleBarOverlays = listOf(
			ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig),
			ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig),
			ScaleBarOverlayRenderer(properties.scaleBarOverlayConfig))

    private val viewerToTransforms = HashMap<ViewerPanelFX, ViewerAndTransforms>()

    fun getSourceSpecificGlobalEventHandler() = DelegateEventHandlers.fromSupplier { sourceSpecificGlobalEventHandler.get() }

    fun getSourceSpecificGlobalEventFilter() = DelegateEventHandlers.fromSupplier { sourceSpecificGlobalEventFilter.get() }

    fun getSourceSpecificViewerEventHandler() = DelegateEventHandlers.fromSupplier { sourceSpecificViewerEventHandler.get() }

    fun getSourceSpecificViewerEventFilter() = DelegateEventHandlers.fromSupplier { sourceSpecificViewerEventFilter.get() }

    init {

		properties.navigationConfig.bindNavigationToConfig(navigation)

		this.numSources = Bindings.size(sourceInfo.trackSources())
        this.hasSources = numSources.greaterThan(0)

        val currentState = sourceInfo.currentState()
        this.sourceSpecificGlobalEventHandler = Bindings.createObjectBinding(
                Callable { currentState.get()?.stateSpecificGlobalEventHandler(baseView, keyTracker) ?: DEFAULT_HANDLER },
                currentState)

        this.sourceSpecificGlobalEventFilter = Bindings.createObjectBinding(
                Callable { currentState.get()?.stateSpecificGlobalEventFilter(baseView, keyTracker) ?: DEFAULT_HANDLER },
                currentState)

        this.sourceSpecificViewerEventHandler = Bindings.createObjectBinding(
                Callable { currentState.get()?.stateSpecificViewerEventHandler(baseView, keyTracker) ?: DEFAULT_HANDLER },
                currentState)

        this.sourceSpecificViewerEventFilter = Bindings.createObjectBinding(
                Callable { currentState.get()?.stateSpecificViewerEventFilter(baseView, keyTracker) ?: DEFAULT_HANDLER },
                currentState)

        this.onEnterOnExit = createOnEnterOnExit(paneWithStatus.currentFocusHolder())
        onEnterOnExit.accept(navigation.onEnterOnExit())
        baseView.orthogonalViews().topLeft().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler())
        baseView.orthogonalViews().topLeft().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter())
        baseView.orthogonalViews().topRight().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler())
        baseView.orthogonalViews().topRight().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter())
        baseView.orthogonalViews().bottomLeft().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler())
        baseView.orthogonalViews().bottomLeft().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter())

        paneWithStatus.pane.addEventHandler(Event.ANY, this.getSourceSpecificGlobalEventHandler())
        paneWithStatus.pane.addEventFilter(Event.ANY, this.getSourceSpecificGlobalEventFilter())


        grabFocusOnMouseOver(
                baseView.orthogonalViews().topLeft().viewer(),
                baseView.orthogonalViews().topRight().viewer(),
                baseView.orthogonalViews().bottomLeft().viewer())

        this.openDatasetContextMenuHandler = addOpenDatasetContextMenuHandler(
                paneWithStatus.pane,
                baseView,
                keyTracker,
                projectDirectory,
                DoubleSupplier { this.mouseTracker.x },
                DoubleSupplier { this.mouseTracker.y },
                KeyCode.CONTROL,
                KeyCode.O)

        this.toggleMaximizeTopLeft = toggleMaximizeNode(orthogonalViews, properties.gridConstraints, 0, 0)
        this.toggleMaximizeTopRight = toggleMaximizeNode(orthogonalViews, properties.gridConstraints, 1, 0)
        this.toggleMaximizeBottomLeft = toggleMaximizeNode(orthogonalViews, properties.gridConstraints, 0, 1)

        viewerToTransforms[orthogonalViews.topLeft().viewer()] = orthogonalViews.topLeft()
        viewerToTransforms[orthogonalViews.topRight().viewer()] = orthogonalViews.topRight()
        viewerToTransforms[orthogonalViews.bottomLeft().viewer()] = orthogonalViews.bottomLeft()

        multiBoxes = arrayOf(MultiBoxOverlayRendererFX(
                Supplier { baseView.orthogonalViews().topLeft().viewer().state },
                sourceInfo.trackSources(),
                sourceInfo.trackVisibleSources()), MultiBoxOverlayRendererFX(
                Supplier { baseView.orthogonalViews().topRight().viewer().state },
                sourceInfo.trackSources(),
                sourceInfo.trackVisibleSources()), MultiBoxOverlayRendererFX(
                Supplier { baseView.orthogonalViews().bottomLeft().viewer().state },
                sourceInfo.trackSources(),
                sourceInfo.trackVisibleSources()))

        multiBoxes[0].isVisibleProperty.bind(baseView.orthogonalViews().topLeft().viewer().focusedProperty())
        multiBoxes[1].isVisibleProperty.bind(baseView.orthogonalViews().topRight().viewer().focusedProperty())
        multiBoxes[2].isVisibleProperty.bind(baseView.orthogonalViews().bottomLeft().viewer().focusedProperty())

        orthogonalViews.topLeft().viewer().display.addOverlayRenderer(multiBoxes[0])
        orthogonalViews.topRight().viewer().display.addOverlayRenderer(multiBoxes[1])
        orthogonalViews.bottomLeft().viewer().display.addOverlayRenderer(multiBoxes[2])

        updateDisplayTransformOnResize(baseView.orthogonalViews(), baseView.manager())

        val borderPane = paneWithStatus.pane
        val toggleSideBar = EventFX.KEY_RELEASED(
                "toggle sidebar",
                { paneWithStatus.toggleSideBar() },
                { keyTracker.areOnlyTheseKeysDown(KeyCode.P) }
        )
        borderPane.sceneProperty().addListener { _, _, newv ->
            newv.addEventHandler(
                    KeyEvent.KEY_PRESSED,
                    toggleSideBar)
        }

        baseView.allowedActionsProperty().addListener { _, _, newv -> paneWithStatus.sideBar?.isDisable = !newv.isAllowed(MenuActionType.SidePanel) }

        sourceInfo.trackSources().addListener(createSourcesInterpolationListener())

        EventFX.KEY_PRESSED(
                "toggle interpolation",
                { toggleInterpolation() },
                { keyTracker.areOnlyTheseKeysDown(KeyCode.I) }).installInto(borderPane)
        EventFX.KEY_PRESSED(
                "cycle current source",
                { sourceInfo.incrementCurrentSourceIndex() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ChangeActiveSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.TAB) }).installInto(borderPane)
        EventFX.KEY_PRESSED(
                "backwards cycle current source",
                { sourceInfo.decrementCurrentSourceIndex() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ChangeActiveSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB) }).installInto(borderPane)

        this.resizer = GridResizer(properties.gridConstraints, 5.0, baseView.pane(), keyTracker)
        this.resizer.installInto(baseView.pane())

        val currentSource = sourceInfo.currentSourceProperty()

        val vdl = OrthogonalViewsValueDisplayListener(
                Consumer { paneWithStatus.setCurrentValue(it) },
                currentSource,
                java.util.function.Function { sourceInfo.getState(it).interpolationProperty().get() })

        val cdl = OrthoViewCoordinateDisplayListener(
                Consumer { paneWithStatus.setViewerCoordinateStatus(it) },
                Consumer { paneWithStatus.setWorldCoorinateStatus(it) })

        onEnterOnExit.accept(OnEnterOnExit(vdl.onEnter(), vdl.onExit()))
        onEnterOnExit.accept(OnEnterOnExit(cdl.onEnter(), cdl.onExit()))

        onEnterOnExit.accept(ARGBStreamSeedSetter(sourceInfo, keyTracker).onEnterOnExit())

        sourceInfo.trackSources().addListener(FitToInterval.fitToIntervalWhenSourceAddedListener(
                baseView.manager()
        ) { baseView.orthogonalViews().topLeft().viewer().widthProperty().get() })
        sourceInfo.trackSources().addListener(RunWhenFirstElementIsAdded {
            baseView.viewer3D()
                    .setInitialTransformToInterval(
                            sourceIntervalInWorldSpace(it.addedSubList[0]))
        })


        EventFX.KEY_PRESSED(
                "toggle maximize viewer",
                { toggleMaximizeTopLeft.toggleMaximizeViewer() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M) }).installInto(orthogonalViews.topLeft().viewer())
        EventFX.KEY_PRESSED(
                "toggle maximize viewer",
                { toggleMaximizeTopRight.toggleMaximizeViewer() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M) }).installInto(orthogonalViews.topRight().viewer())
        EventFX.KEY_PRESSED(
                "toggle maximize viewer",
                { toggleMaximizeBottomLeft.toggleMaximizeViewer() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M) }).installInto(orthogonalViews.bottomLeft().viewer())

        EventFX.KEY_PRESSED(
                "toggle maximize viewer and orthoslice",
                { toggleMaximizeTopLeft.toggleMaximizeViewerAndOrthoslice() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT) }).installInto(orthogonalViews.topLeft().viewer())

        EventFX.KEY_PRESSED(
                "toggle maximize viewer and orthoslice",
                { toggleMaximizeTopRight.toggleMaximizeViewerAndOrthoslice() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT) }).installInto(orthogonalViews.topRight().viewer())

        EventFX.KEY_PRESSED(
                "toggle maximize viewer and orthoslice",
                { toggleMaximizeBottomLeft.toggleMaximizeViewerAndOrthoslice() },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT) }).installInto(orthogonalViews.bottomLeft().viewer())


        val csv = CurrentSourceVisibilityToggle(sourceInfo.currentState())
        EventFX.KEY_PRESSED(
                "toggle visibility",
                { csv.toggleIsVisible() },
                { keyTracker.areOnlyTheseKeysDown(KeyCode.V) }).installInto(borderPane)

        val sosist = ShowOnlySelectedInStreamToggle(
                Supplier { sourceInfo.currentState().get() },
                sourceInfo.removedSourcesTracker())
        EventFX.KEY_PRESSED(
                "toggle non-selected labels visibility",
                { sosist.toggleNonSelectionVisibility() },
                { keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT, KeyCode.V) }).installInto(borderPane)


        // TODO does MouseEvent.getPickResult make the coordinate tracker
        // TODO obsolete?
        val contextMenuFactory = MeshesGroupContextMenu(
                baseView.manager(),
                baseView.viewer3D().coordinateTracker())
        baseView.viewer3D().addEventHandler(
                MouseEvent.MOUSE_CLICKED
        ) {
            LOG.debug("Handling event {}", it)
            if (baseView.allowedActionsProperty().get().isAllowed(MenuActionType.OrthoslicesContextMenu) &&
                    MouseButton.SECONDARY == it.button &&
                    it.clickCount == 1 &&
                    !mouseTracker.isDragging) {
                LOG.debug("Check passed for event {}", it)
                it.consume()
                val menu = contextMenuFactory.createMenu()
                menu.show(baseView.viewer3D(), it.screenX, it.screenY)
            }
        }

        EventFX.KEY_PRESSED(
                "Create new label dataset",
                {
                    CreateDatasetHandler.createAndAddNewLabelDataset(
                            baseView,
                            projectDirectory.get(),
                            Exceptions.handler("Paintera", "Unable to create new Dataset"),
                            baseView.sourceInfo().currentSourceProperty().get())
                },
                { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.CreateNewLabelSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.N) }).installInto(paneWithStatus.pane)

        this.baseView.orthogonalViews().topLeft().viewer().addTransformListener(scaleBarOverlays[0])
        this.baseView.orthogonalViews().topLeft().viewer().display.addOverlayRenderer(scaleBarOverlays[0])
        this.baseView.orthogonalViews().topRight().viewer().addTransformListener(scaleBarOverlays[1])
        this.baseView.orthogonalViews().topRight().viewer().display.addOverlayRenderer(scaleBarOverlays[1])
        this.baseView.orthogonalViews().bottomLeft().viewer().addTransformListener(scaleBarOverlays[2])
        this.baseView.orthogonalViews().bottomLeft().viewer().display.addOverlayRenderer(scaleBarOverlays[2])
		properties.scaleBarOverlayConfig.change.addListener { this.baseView.orthogonalViews().applyToAll { vp -> vp.display.drawOverlays() } }

        val addBookmarkKeyCode = KeyCodeCombination(KeyCode.B)
        val addBookmarkWithCommentKeyCode = KeyCodeCombination(KeyCode.B, KeyCombination.SHIFT_DOWN)
        val applyBookmarkKeyCode = KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN)
        paneWithStatus.pane.addEventHandler(KeyEvent.KEY_PRESSED) {
            if (!baseView.allowedActionsProperty().get().isAllowed(NavigationActionType.Bookmark)) {
				// Do not do anything
			}
            else if (addBookmarkKeyCode.match(it)) {
                it.consume()
                val globalTransform = AffineTransform3D()
                baseView.manager().getTransform(globalTransform)
                val viewer3DTransform = Affine()
                baseView.viewer3D().getAffine(viewer3DTransform)
                properties.bookmarkConfig.addBookmark(BookmarkConfig.Bookmark(globalTransform, viewer3DTransform, null))
            } else if (addBookmarkWithCommentKeyCode.match(it)) {
                it.consume()
                val globalTransform = AffineTransform3D()
                baseView.manager().getTransform(globalTransform)
                val viewer3DTransform = Affine()
                baseView.viewer3D().getAffine(viewer3DTransform)
                paneWithStatus.bookmarkConfigNode().requestAddNewBookmark(globalTransform, viewer3DTransform)
            } else if (applyBookmarkKeyCode.match(it)) {
                it.consume()
                BookmarkSelectionDialog(properties.bookmarkConfig.unmodifiableBookmarks)
                        .showAndWaitForBookmark()
                        .ifPresent { bm ->
                            baseView.manager().setTransform(bm.globalTransformCopy, properties.bookmarkConfig.getTransitionTime())
                            baseView.viewer3D().setAffine(bm.viewer3DTransformCopy, properties.bookmarkConfig.getTransitionTime())
                        }
            }
        }

		// save & save as
		paneWithStatus.pane.addEventHandler(KeyEvent.KEY_PRESSED) { ev ->
			if (DEFAULT_SAVE_KEY_COMBINATIONS.filter { it.match(ev) }.isNotEmpty()) {
				ev.consume()
				paintera.saveOrSaveAs()
			} else if (DEFAULT_SAVE_AS_KEY_COMBINATIONS.filter { it.match(ev) }.isNotEmpty()) {
				ev.consume()
				paintera.saveAs()
			}
		}

    }

    fun toggleInterpolation() {
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

    fun navigation(): Navigation {
        return this.navigation
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private val DEFAULT_HANDLER = EventHandler<Event> { LOG.debug("Default event handler: Use if no source is present") }

		private val DEFAULT_SAVE_KEY_COMBINATIONS = arrayOf(KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN))

		private val DEFAULT_SAVE_AS_KEY_COMBINATIONS = arrayOf(KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN))

        fun updateDisplayTransformOnResize(
                views: OrthogonalViews<*>,
                lock: Any): Array<DisplayTransformUpdateOnResize> {
            return arrayOf(updateDisplayTransformOnResize(views.topLeft(), lock), updateDisplayTransformOnResize(views.topRight(), lock), updateDisplayTransformOnResize(views.bottomLeft(), lock))
        }

        fun updateDisplayTransformOnResize(vat: ViewerAndTransforms, lock: Any): DisplayTransformUpdateOnResize {
            val viewer = vat.viewer()
            val displayTransform = vat.displayTransform()
            val updater = DisplayTransformUpdateOnResize(
                    displayTransform,
                    viewer.widthProperty(),
                    viewer.heightProperty(),
                    lock
            )
            updater.listen()
            return updater
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
                    focusBL)

        }

        fun createOnEnterOnExit(currentFocusHolder: ObservableObjectValue<ViewerAndTransforms?>): Consumer<OnEnterOnExit> {
            val onEnterOnExits = ArrayList<OnEnterOnExit>()

            val onEnterOnExit = ChangeListener<ViewerAndTransforms?> { _, oldv, newv ->
                if (oldv != null) {
                    onEnterOnExits.stream().map { it.onExit() }.forEach { it.accept(oldv.viewer()) }
                }
                if (newv != null) {
                    onEnterOnExits.stream().map { it.onEnter() }.forEach { it.accept(newv.viewer()) }
                }
            }

            currentFocusHolder.addListener(onEnterOnExit)

            return Consumer { onEnterOnExits.add(it) }
        }

        fun grabFocusOnMouseOver(vararg nodes: Node) {
            grabFocusOnMouseOver(listOf(*nodes))
        }

        fun grabFocusOnMouseOver(nodes: Collection<Node>) {
            nodes.forEach(Consumer { grabFocusOnMouseOver(it) })
        }

        fun grabFocusOnMouseOver(node: Node) {
            node.addEventFilter(MouseEvent.MOUSE_ENTERED) { node.requestFocus() }
        }

        fun sourceIntervalInWorldSpace(source: Source<*>): Interval {
            val min = Arrays.stream(Intervals.minAsLongArray(source.getSource(
                    0,
                    0))).asDoubleStream().toArray()
            val max = Arrays.stream(Intervals.maxAsLongArray(source.getSource(
                    0,
                    0))).asDoubleStream().toArray()
            val tf = AffineTransform3D()
            source.getSourceTransform(0, 0, tf)
            tf.apply(min, min)
            tf.apply(max, max)
            return Intervals.smallestContainingInterval(FinalRealInterval(min, max))
        }

        fun setFocusTraversable(
                view: OrthogonalViews<*>,
                isTraversable: Boolean) {
            view.topLeft().viewer().isFocusTraversable = isTraversable
            view.topRight().viewer().isFocusTraversable = isTraversable
            view.bottomLeft().viewer().isFocusTraversable = isTraversable
            view.grid().bottomRight.isFocusTraversable = isTraversable
        }

        fun addOpenDatasetContextMenuHandler(
                target: Node,
                baseView: PainteraBaseView,
                keyTracker: KeyTracker,
                projectDirectory: Supplier<String>,
                currentMouseX: DoubleSupplier,
                currentMouseY: DoubleSupplier,
                vararg triggers: KeyCode): EventHandler<KeyEvent> {

            assert(triggers.isNotEmpty())

            val handler = OpenDialogMenu.keyPressedHandler(
                    target,
                    Consumer { exception -> Exceptions.exceptionAlert(Paintera.NAME, "Unable to show open dataset menu", exception) },
                    Predicate { baseView.allowedActionsProperty().get().isAllowed(MenuActionType.AddSource) && keyTracker.areOnlyTheseKeysDown(*triggers) },
                    "Open dataset",
                    baseView,
                    projectDirectory,
                    currentMouseX,
                    currentMouseY)

            target.addEventHandler(KeyEvent.KEY_PRESSED, handler)
            return handler
        }

        fun toggleMaximizeNode(
                orthogonalViews: OrthogonalViews<out Node>,
                manager: GridConstraintsManager,
                column: Int,
                row: Int): ToggleMaximize {
            return ToggleMaximize(
                    orthogonalViews,
                    manager,
                    MaximizedColumn.fromIndex(column),
                    MaximizedRow.fromIndex(row))
        }
    }
}
