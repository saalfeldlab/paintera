package org.janelia.saalfeldlab.paintera

import bdv.fx.viewer.ViewerPanelFX
import bdv.viewer.Source
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableObjectValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Group
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.util.Duration
import net.imglib2.RealPoint
import net.imglib2.cache.LoaderCache
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.ui.ResizeOnLeftSide
import org.janelia.saalfeldlab.fx.ui.SingleChildStackPane
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.cache.Invalidate
import org.janelia.saalfeldlab.paintera.cache.MaxSize
import org.janelia.saalfeldlab.paintera.cache.MemoryBoundedSoftRefLoaderCache
import org.janelia.saalfeldlab.paintera.config.ArbitraryMeshConfigNode
import org.janelia.saalfeldlab.paintera.config.BookmarkConfigNode
import org.janelia.saalfeldlab.paintera.config.CrosshairConfigNode
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigNode
import org.janelia.saalfeldlab.paintera.config.ScaleBarOverlayConfigNode
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfigNode
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfigNode
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.ui.Crosshair
import org.janelia.saalfeldlab.paintera.ui.source.SourceTabs
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.MakeUnchecked
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.invoke.MethodHandles
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.LongPredicate
import java.util.function.LongSupplier
import java.util.function.LongUnaryOperator
import java.util.function.Predicate
import java.util.function.Supplier

class BorderPaneWithStatusBars2(center: PainteraBaseView) {

    val pane: BorderPane

    private val statusBar: HBox

    val sideBar: ScrollPane

    private val currentSourceStatus: Label

    private val viewerCoordinateStatus: Label

    private val worldCoordinateStatus: Label

    private val valueStatus: Label

    private val resizeSideBar: ResizeOnLeftSide

    private val navigationConfigNode = NavigationConfigNode()

    private val crosshairConfigNode = CrosshairConfigNode()

    private val orthoSliceConfigNode = OrthoSliceConfigNode()

    private val viewer3DConfigNode = Viewer3DConfigNode()

    private val screenScaleConfigNode = ScreenScalesConfigNode()

    private val scaleBarConfigNode = ScaleBarOverlayConfigNode()

    private val bookmarkConfigNode: BookmarkConfigNode

    private val arbitraryMeshConfigNode = ArbitraryMeshConfigNode()

    private val crossHairs: Map<ViewerAndTransforms, Crosshair>

    private val orthoSlices: Map<ViewerAndTransforms, OrthoSliceFX>

    private val currentFocusHolderWithState: ObservableObjectValue<ViewerAndTransforms?>

    private val saveProjectButton: Button

    fun currentFocusHolder(): ObservableObjectValue<ViewerAndTransforms?> = this.currentFocusHolderWithState

    fun setViewerCoordinateStatus(p: RealPoint?) {
        InvokeOnJavaFXApplicationThread.invoke {
            viewerCoordinateStatus.text = if (p == null)
                "N/A"
            else
                String.format("(% 4d, % 4d)",
                        p.getDoublePosition(0).toInt(),
                        p
                                .getDoublePosition(1).toInt()
                )
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
        this.pane = BorderPane(center.orthogonalViews().pane())

        this.currentFocusHolderWithState = currentFocusHolder(center.orthogonalViews())

        this.currentSourceStatus = Label()
        this.viewerCoordinateStatus = Label()
        this.worldCoordinateStatus = Label()
        this.valueStatus = Label()
        val showStatusBar = CheckBox()
        showStatusBar.isFocusTraversable = false
        showStatusBar.tooltip = Tooltip("If not selected, status bar will only show on mouse-over")

        this.bookmarkConfigNode = BookmarkConfigNode { bm ->
            center.manager().setTransform(bm.globalTransformCopy)
            center.viewer3D().setAffine(bm.viewer3DTransformCopy)
        }

        this.crossHairs = makeCrosshairs(center.orthogonalViews(), Colors.CREMI, Color.WHITE.deriveColor(0.0, 1.0, 1.0,
                0.5))
        this.orthoSlices = makeOrthoSlices(
                center.orthogonalViews(),
                center.viewer3D().meshesGroup(),
                center.sourceInfo()
        )

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
                this.navigationConfigNode.contents,
                this.crosshairConfigNode.contents,
                this.orthoSliceConfigNode.contents,
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

        saveProjectButton = Button("Save")

        this.sideBar = ScrollPane(VBox(sourcesContents, settings, saveProjectButton))
        this.sideBar.hbarPolicy = ScrollBarPolicy.NEVER
        this.sideBar.vbarPolicy = ScrollBarPolicy.AS_NEEDED
        this.sideBar.isVisible = true
        this.sideBar.prefWidthProperty().set(280.0)
        sourceTabs.widthProperty().bind(sideBar.prefWidthProperty())
        settingsContents.prefWidthProperty().bind(sideBar.prefWidthProperty())

        resizeSideBar = ResizeOnLeftSide(sideBar, sideBar.prefWidthProperty()) { dist -> Math.abs(dist) < 5 }
    }

    fun toggleSideBar() {
        if (pane.right == null) {
            pane.right = sideBar
            resizeSideBar.install()
        } else {
            resizeSideBar.remove()
            pane.right = null
        }
    }

    fun saveProjectButtonOnActionProperty(): ObjectProperty<EventHandler<ActionEvent>> {
        return this.saveProjectButton.onActionProperty()
    }

    fun navigationConfigNode(): NavigationConfigNode {
        return this.navigationConfigNode
    }

    fun crosshairConfigNode(): CrosshairConfigNode {
        return this.crosshairConfigNode
    }

    fun orthoSliceConfigNode(): OrthoSliceConfigNode {
        return this.orthoSliceConfigNode
    }

    fun screenScalesConfigNode(): ScreenScalesConfigNode {
        return this.screenScaleConfigNode
    }

    fun viewer3DConfigNode(): Viewer3DConfigNode {
        return this.viewer3DConfigNode
    }

    fun scaleBarOverlayConfigNode(): ScaleBarOverlayConfigNode {
        return this.scaleBarConfigNode
    }

    fun bookmarkConfigNode(): BookmarkConfigNode {
        return this.bookmarkConfigNode
    }

    fun arbitraryMeshConfigNode(): ArbitraryMeshConfigNode {
        return this.arbitraryMeshConfigNode
    }

    fun crosshairs(): Map<ViewerAndTransforms, Crosshair> {
        return Collections.unmodifiableMap(crossHairs)
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
            ch.wasChangedProperty().addListener { obs, oldv, newv -> viewer.display.drawOverlays() }
            ch.isHighlightProperty.bind(viewer.focusedProperty())
            return ch
        }

        fun makeOrthoSlices(
                views: OrthogonalViews<*>,
                scene: Group,
                sourceInfo: SourceInfo): Map<ViewerAndTransforms, OrthoSliceFX> {
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
