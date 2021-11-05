package org.janelia.saalfeldlab.paintera.state.label

import bdv.util.volatiles.SharedQueue
import bdv.viewer.Interpolation
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import gnu.trove.set.hash.TLongHashSet
import javafx.beans.InvalidationListener
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Modality
import net.imglib2.Interval
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.apache.commons.lang.builder.HashCodeBuilder
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers
import org.janelia.saalfeldlab.fx.event.EventFX
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.createObjectBinding
import org.janelia.saalfeldlab.fx.extensions.getValue
import org.janelia.saalfeldlab.fx.extensions.setValue
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal
import org.janelia.saalfeldlab.paintera.control.selection.FragmentsInSelectedSegments
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.FloodFillState
import org.janelia.saalfeldlab.paintera.state.IntersectableSourceState
import org.janelia.saalfeldlab.paintera.state.LabelSourceStateIdSelectorHandler
import org.janelia.saalfeldlab.paintera.state.LabelSourceStateMergeDetachHandler
import org.janelia.saalfeldlab.paintera.state.LabelSourceStatePaintHandler
import org.janelia.saalfeldlab.paintera.state.LabelSourceStatePreferencePaneNode
import org.janelia.saalfeldlab.paintera.state.MeshCacheKey
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.SourceStateWithBackend
import org.janelia.saalfeldlab.paintera.stream.ARGBStreamSeedSetter
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream
import org.janelia.saalfeldlab.paintera.stream.ShowOnlySelectedInStreamToggle
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.Colors
import org.janelia.saalfeldlab.util.HashWrapper
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.IntFunction
import java.util.function.LongFunction
import java.util.function.Predicate
import java.util.function.Supplier

class ConnectomicsLabelState<D : IntegerType<D>, T>(
    override val backend: ConnectomicsLabelBackend<D, T>,
    meshesGroup: Group,
    viewFrustumProperty: ObjectProperty<ViewFrustum>,
    eyeToWorldTransformProperty: ObjectProperty<AffineTransform3D>,
    meshManagerExecutors: ExecutorService,
    meshWorkersExecutors: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
    queue: SharedQueue,
    priority: Int,
    name: String,
    private val resolution: DoubleArray = DoubleArray(3) { 1.0 },
    private val offset: DoubleArray = DoubleArray(3) { 0.0 },
    labelBlockLookup: LabelBlockLookup? = null,
) : SourceStateWithBackend<D, T>, IntersectableSourceState<D, T, FragmentLabelMeshCacheKey>
    where T : net.imglib2.type.Type<T>, T : Volatile<D> {

    private val source: DataSource<D, T> = backend.createSource(queue, priority, name, resolution, offset)
    override fun getDataSource(): DataSource<D, T> = source

    private val maskForLabel = equalsMaskForType(source.dataType)

    val fragmentSegmentAssignment = backend.fragmentSegmentAssignment

    val lockedSegments = LockedSegmentsOnlyLocal({})

    val selectedIds = SelectedIds()

    val selectedSegments = SelectedSegments(selectedIds, fragmentSegmentAssignment)

    private val fragmentsInSelectedSegments = selectedSegments.createObjectBinding { FragmentsInSelectedSegments(selectedSegments) }

    private val idService = backend.createIdService(source)

    private val labelBlockLookup = labelBlockLookup ?: backend.createLabelBlockLookup(source)

    private val stream = ModalGoldenAngleSaturatedHighlightingARGBStream(selectedSegments, lockedSegments)

    private val converter = HighlightingStreamConverter.forType(stream, dataSource.type)

    override fun converter(): HighlightingStreamConverter<T> = converter
    val meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
        source,
        selectedSegments,
        stream,
        viewFrustumProperty,
        eyeToWorldTransformProperty,
        this.labelBlockLookup,
        meshManagerExecutors,
        meshWorkersExecutors
    ).apply {
        InvokeOnJavaFXApplicationThread {
            refreshMeshes()
        }
    }

    val meshCacheKeyProperty: ObjectBinding<FragmentLabelMeshCacheKey> = fragmentsInSelectedSegments.createObjectBinding { FragmentLabelMeshCacheKey(fragmentsInSelectedSegments.value) }

    override fun getMeshCacheKeyBinding(): ObjectBinding<FragmentLabelMeshCacheKey> = meshCacheKeyProperty

    override fun getGetBlockListFor(): GetBlockListFor<FragmentLabelMeshCacheKey> = this.meshManager.getBlockListForMeshCacheKey

    override fun getIntersectableMask(): DataSource<BoolType, Volatile<BoolType>> = labelToBooleanFragmentMaskSource(this)

    private val paintHandler = when (source) {
        is MaskedSource<D, *> -> LabelSourceStatePaintHandler(
            source,
            fragmentSegmentAssignment,
            { isVisible },
            { floodFillState.set(it) },
            selectedIds,
            maskForLabel
        )
        else -> null
    }

    private val idSelectorHandler = LabelSourceStateIdSelectorHandler(source, idService, selectedIds, fragmentSegmentAssignment, lockedSegments)

    private val mergeDetachHandler = LabelSourceStateMergeDetachHandler(source, selectedIds, fragmentSegmentAssignment, idService)

    private val commitHandler = CommitHandler(this)

    private val shapeInterpolationMode = (source as? MaskedSource<D, *>)?.let {
        ShapeInterpolationMode(it, this::refreshMeshes, selectedIds, idService, converter, fragmentSegmentAssignment)
    }

    private val streamSeedSetter = ARGBStreamSeedSetter(stream)

    private val showOnlySelectedInStreamToggle = ShowOnlySelectedInStreamToggle(stream)

    private fun refreshMeshes() = meshManager.refreshMeshes()

    // ARGB composite
    private val _composite: ObjectProperty<Composite<ARGBType, ARGBType>> = SimpleObjectProperty(
        this,
        "composite",
        ARGBCompositeAlphaYCbCr()
    )
    var composite: Composite<ARGBType, ARGBType> by _composite

    override fun compositeProperty(): ObjectProperty<Composite<ARGBType, ARGBType>> = _composite

    // source name
    private val _name = SimpleStringProperty(name)
    var name: String by _name
    override fun nameProperty(): StringProperty = _name

    // status text
    private val _statusText = SimpleStringProperty(this, "status text", "")
    override fun statusTextProperty(): StringProperty = _statusText

    // visibility
    private val _isVisible = SimpleBooleanProperty(true)
    var isVisible: Boolean by _isVisible
    override fun isVisibleProperty(): BooleanProperty = _isVisible

    // interpolation
    private val _interpolation = SimpleObjectProperty(this, "interpolation", Interpolation.NEARESTNEIGHBOR)
    var interpolation: Interpolation by _interpolation
    override fun interpolationProperty(): ObjectProperty<Interpolation> = _interpolation

    // source dependencies
    override fun dependsOn(): Array<SourceState<*, *>> = arrayOf()

    // flood fill state
    private val floodFillState = SimpleObjectProperty<FloodFillState>()

    // display status
    private val displayStatus: HBox = createDisplayStatus()
    override fun getDisplayStatus(): Node = displayStatus

    override fun stateSpecificGlobalEventHandler(paintera: PainteraBaseView, keyTracker: KeyTracker): EventHandler<Event> {
        LOG.debug("Returning {}-specific global handler", javaClass.simpleName)
        val keyBindings = paintera.keyAndMouseBindings.getConfigFor(this).keyCombinations
        val handler = DelegateEventHandlers.handleAny()
        handler.addEventHandler(
            KeyEvent.KEY_PRESSED,
            EventFX.KEY_PRESSED(
                BindingKeys.REFRESH_MESHES,
                {
                    it.consume()
                    LOG.debug("Key event triggered refresh meshes")
                    refreshMeshes()
                },
                { keyBindings[BindingKeys.REFRESH_MESHES]!!.matches(it) })
        )
        handler.addEventHandler(
            KeyEvent.KEY_PRESSED,
            EventFX.KEY_PRESSED(
                BindingKeys.CANCEL_3D_FLOODFILL,
                {
                    it.consume()
                    val state = floodFillState.get()
                    state?.interrupt?.run()
                },
                { e -> floodFillState.get() != null && keyBindings[BindingKeys.CANCEL_3D_FLOODFILL]!!.matches(e) })
        )
        handler.addEventHandler(
            KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED(
            BindingKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY,
            {
                it.consume()
                this.showOnlySelectedInStreamToggle.toggleNonSelectionVisibility()
            },
            { keyBindings[BindingKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY]!!.matches(it) })
        )
        handler.addEventHandler(
            KeyEvent.KEY_PRESSED,
            streamSeedSetter.incrementHandler({ keyBindings[BindingKeys.ARGB_STREAM_INCREMENT_SEED]!!.primaryCombination })
        )
        handler.addEventHandler(
            KeyEvent.KEY_PRESSED,
            streamSeedSetter.decrementHandler({ keyBindings[BindingKeys.ARGB_STREAM_DECREMENT_SEED]!!.primaryCombination })
        )
        val listHandler = DelegateEventHandlers.listHandler<Event>()
        listHandler.addHandler(handler)
        listHandler.addHandler(commitHandler.globalHandler(paintera, paintera.keyAndMouseBindings.getConfigFor(this), keyTracker))
        return listHandler
    }

    override fun stateSpecificViewerEventHandler(paintera: PainteraBaseView, keyTracker: KeyTracker): EventHandler<Event> {
        LOG.debug("Returning {}-specific handler", javaClass.simpleName)
        val handler = DelegateEventHandlers.listHandler<Event>()
        paintHandler?.viewerHandler(paintera, keyTracker)?.let { handler.addHandler(it) }
        handler.addHandler(
            idSelectorHandler.viewerHandler(
                paintera,
                paintera.keyAndMouseBindings.getConfigFor(this),
                keyTracker,
                BindingKeys.SELECT_ALL,
                BindingKeys.SELECT_ALL_IN_CURRENT_VIEW,
                BindingKeys.LOCK_SEGEMENT,
                BindingKeys.NEXT_ID
            )
        )
        handler.addHandler(
            mergeDetachHandler.viewerHandler(
                paintera,
                paintera.keyAndMouseBindings.getConfigFor(this),
                keyTracker,
                BindingKeys.MERGE_ALL_SELECTED
            )
        )
        return handler
    }

    override fun stateSpecificViewerEventFilter(paintera: PainteraBaseView, keyTracker: KeyTracker): EventHandler<Event> {
        LOG.debug("Returning {}-specific filter", javaClass.simpleName)
        val filter = DelegateEventHandlers.listHandler<Event>()
        val bindings = paintera.keyAndMouseBindings.getConfigFor(this)
        paintHandler?.viewerFilter(paintera, keyTracker)?.let { filter.addHandler(it) }
        if (shapeInterpolationMode != null)
            filter.addHandler(
                shapeInterpolationMode.modeHandler(
                    paintera,
                    keyTracker,
                    bindings,
                    BindingKeys.ENTER_SHAPE_INTERPOLATION_MODE,
                    BindingKeys.EXIT_SHAPE_INTERPOLATION_MODE,
                    BindingKeys.SHAPE_INTERPOLATION_APPLY_MASK,
                    BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_1,
                    BindingKeys.SHAPE_INTERPOLATION_EDIT_SELECTION_2
                )
            )
        return filter
    }

    private fun requestRepaint(paintera: PainteraBaseView) {
        if (isVisible && Paintera.paintable) {
            paintera.orthogonalViews().requestRepaint()
        }
    }

    override fun onAdd(paintera: PainteraBaseView) {
        stream.addListener { requestRepaint(paintera) }
        selectedIds.addListener { requestRepaint(paintera) }
        lockedSegments.addListener { requestRepaint(paintera) }
        fragmentSegmentAssignment.addListener { requestRepaint(paintera) }
        paintera.viewer3D().meshesGroup().children.add(meshManager.meshesGroup)
        selectedSegments.addListener { meshManager.setMeshesToSelection() }

        meshManager.viewerEnabledProperty().bind(paintera.viewer3D().meshesEnabledProperty())
        meshManager.rendererSettings.showBlockBoundariesProperty.bind(paintera.viewer3D().showBlockBoundariesProperty())
        meshManager.rendererSettings.blockSizeProperty.bind(paintera.viewer3D().rendererBlockSizeProperty())
        meshManager.rendererSettings.numElementsPerFrameProperty.bind(paintera.viewer3D().numElementsPerFrameProperty())
        meshManager.rendererSettings.frameDelayMsecProperty.bind(paintera.viewer3D().frameDelayMsecProperty())
        meshManager.rendererSettings.sceneUpdateDelayMsecProperty.bind(paintera.viewer3D().sceneUpdateDelayMsecProperty())
        meshManager.refreshMeshes()


        // TODO make resolution/offset configurable
//		_resolutionX.addListener { _ -> requestRepaint(paintera) }
//		_resolutionY.addListener { _ -> requestRepaint(paintera) }
//		_resolutionZ.addListener { _ -> requestRepaint(paintera) }
//		_offsetX.addListener { _ -> requestRepaint(paintera) }
//		_offsetY.addListener { _ -> requestRepaint(paintera) }
//		_offsetZ.addListener { _ -> requestRepaint(paintera) }
    }

    override fun onRemoval(sourceInfo: SourceInfo) {
        LOG.info("Removed LabelSourceState {}", name)
        meshManager.removeAllMeshes()
        CommitHandler.showCommitDialog(
            this,
            sourceInfo.indexOf(this.dataSource),
            false,
            { index, name ->
                String.format(
                    "" +
                        "Removing source %d: %s. " +
                        "Uncommitted changes to the canvas and/or fragment-segment assignment will be lost if skipped.", index, name
                )
            },
            false,
            "_Skip"
        )
    }

    override fun onShutdown(paintera: PainteraBaseView) {
        CommitHandler.showCommitDialog(
            this,
            paintera.sourceInfo().indexOf(this.dataSource),
            false,
            { index, name ->
                "Shutting down Paintera. " +
                    "Uncommitted changes to the canvas will be lost for source $index: $name if skipped. " +
                    "Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any) " +
                    "but can be committed to the data backend, as well."
            },
            false,
            "_Skip"
        )
    }

    override fun createKeyAndMouseBindings(): KeyAndMouseBindings {
        val bindings = KeyAndMouseBindings()
        return try {
            createKeyAndMouseBindingsImpl(bindings)
        } catch (e: NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted) {
            e.printStackTrace()
            bindings
        }
    }

    private fun createDisplayStatus(): HBox {
        val lastSelectedLabelColorRect = Rectangle(13.0, 13.0)
        lastSelectedLabelColorRect.stroke = Color.BLACK

        val lastSelectedLabelColorRectTooltip = Tooltip()
        Tooltip.install(lastSelectedLabelColorRect, lastSelectedLabelColorRectTooltip)

        val lastSelectedIdUpdater = InvalidationListener {
            InvokeOnJavaFXApplicationThread.invoke {
                if (selectedIds.isLastSelectionValid) {
                    val lastSelectedLabelId = selectedIds.lastSelection
                    val currSelectedColor = Colors.toColor(stream.argb(lastSelectedLabelId))
                    lastSelectedLabelColorRect.fill = currSelectedColor
                    lastSelectedLabelColorRect.isVisible = true

                    val activeIdText = StringBuilder()
                    val segmentId = fragmentSegmentAssignment.getSegment(lastSelectedLabelId)
                    if (segmentId != lastSelectedLabelId)
                        activeIdText.append("Segment: $segmentId").append(". ")
                    activeIdText.append("Fragment: $lastSelectedLabelId")
                    lastSelectedLabelColorRectTooltip.text = activeIdText.toString()
                } else {
                    lastSelectedLabelColorRect.isVisible = false
                }
            }
        }
        selectedIds.addListener(lastSelectedIdUpdater)
        fragmentSegmentAssignment.addListener(lastSelectedIdUpdater)

        // add the same listener to the color stream (for example, the color should change when a new random seed value is set)
        stream.addListener(lastSelectedIdUpdater)

        val paintingProgressIndicator = ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS)
        paintingProgressIndicator.prefWidth = 15.0
        paintingProgressIndicator.prefHeight = 15.0
        paintingProgressIndicator.minWidth = Control.USE_PREF_SIZE
        paintingProgressIndicator.minHeight = Control.USE_PREF_SIZE
        paintingProgressIndicator.isVisible = false

        val paintingProgressIndicatorTooltip = Tooltip()
        paintingProgressIndicator.tooltip = paintingProgressIndicatorTooltip

        val resetProgressIndicatorContextMenu = Runnable {
            val contextMenu = paintingProgressIndicator.contextMenuProperty().get()
            contextMenu?.hide()
            paintingProgressIndicator.contextMenu = null
            paintingProgressIndicator.onMouseClicked = null
            paintingProgressIndicator.cursor = Cursor.DEFAULT
        }

        val setProgressIndicatorContextMenu = Consumer<ContextMenu> { contextMenu ->
            resetProgressIndicatorContextMenu.run()
            paintingProgressIndicator.contextMenu = contextMenu
            paintingProgressIndicator.setOnMouseClicked { event ->
                contextMenu.show(
                    paintingProgressIndicator,
                    event.screenX,
                    event.screenY
                )
            }
            paintingProgressIndicator.cursor = Cursor.HAND
        }

        if (this.dataSource is MaskedSource<*, *>) {
            val maskedSource = this.dataSource as MaskedSource<D, *>
            maskedSource.isApplyingMaskProperty.addListener { _, _, newv ->
                InvokeOnJavaFXApplicationThread.invoke {
                    paintingProgressIndicator.isVisible = newv
                    if (newv) {
                        val currentMask = maskedSource.currentMask
                        if (currentMask != null)
                            paintingProgressIndicatorTooltip.text = "Applying mask to canvas, label ID: " + currentMask.info.value.get()
                    }
                }
            }
        }

        this.floodFillState.addListener { _, _, newv ->
            InvokeOnJavaFXApplicationThread.invoke {
                if (newv != null) {
                    paintingProgressIndicator.isVisible = true
                    paintingProgressIndicatorTooltip.text = "Flood-filling, label ID: " + newv.labelId

                    val floodFillContextMenuCancelItem = MenuItem("Cancel")
                    if (newv.interrupt != null) {
                        floodFillContextMenuCancelItem.setOnAction { newv.interrupt.run() }
                    } else {
                        floodFillContextMenuCancelItem.isDisable = true
                    }
                    setProgressIndicatorContextMenu.accept(ContextMenu(floodFillContextMenuCancelItem))
                } else {
                    paintingProgressIndicator.isVisible = false
                    resetProgressIndicatorContextMenu.run()
                }
            }
        }

        // only necessary if we actually have shape interpolation
        if (this.shapeInterpolationMode != null) {
            val shapeInterpolationModeStatusUpdater = InvalidationListener {
                InvokeOnJavaFXApplicationThread.invoke {
                    val modeState = this.shapeInterpolationMode.modeStateProperty().get()
                    val activeSection = this.shapeInterpolationMode.activeSectionProperty().get()
                    if (modeState != null) {
                        when (modeState) {
                            ShapeInterpolationMode.ModeState.Select -> statusTextProperty().set("Select #$activeSection")
                            ShapeInterpolationMode.ModeState.Interpolate -> statusTextProperty().set("Interpolating")
                            ShapeInterpolationMode.ModeState.Preview -> statusTextProperty().set("Preview")
                            else -> statusTextProperty().set(null)
                        }
                    } else {
                        statusTextProperty().set(null)
                    }
                    val showProgressIndicator = modeState == ShapeInterpolationMode.ModeState.Interpolate
                    paintingProgressIndicator.isVisible = showProgressIndicator
                    paintingProgressIndicatorTooltip.text = if (showProgressIndicator) "Interpolating between sections..." else ""
                }
            }

            this.shapeInterpolationMode.modeStateProperty().addListener(shapeInterpolationModeStatusUpdater)
            this.shapeInterpolationMode.activeSectionProperty().addListener(shapeInterpolationModeStatusUpdater)
        }

        val displayStatus = HBox(5.0, lastSelectedLabelColorRect, paintingProgressIndicator)
        displayStatus.alignment = Pos.CENTER_LEFT
        displayStatus.padding = Insets(0.0, 3.0, 0.0, 3.0)

        return displayStatus
    }

    override fun preferencePaneNode(): Node {
        val node = LabelSourceStatePreferencePaneNode(
            dataSource,
            compositeProperty(),
            converter(),
            meshManager,
            meshManager.managedSettings,
            paintHandler?.brushProperties
        ).node.let { if (it is VBox) it else VBox(it) }

        val backendMeta = backend.createMetaDataNode()

        // TODO make resolution/offset configurable
//		val resolutionPane = run {
//			val resolutionXField = NumberField.doubleField(resolutionX, DoublePredicate { it > 0.0 }, *ObjectField.SubmitOn.values())
//			val resolutionYField = NumberField.doubleField(resolutionX, DoublePredicate { it > 0.0 }, *ObjectField.SubmitOn.values())
//			val resolutionZField = NumberField.doubleField(resolutionX, DoublePredicate { it > 0.0 }, *ObjectField.SubmitOn.values())
//			resolutionXField.valueProperty().bindBidirectional(_resolutionX)
//			resolutionYField.valueProperty().bindBidirectional(_resolutionY)
//			resolutionZField.valueProperty().bindBidirectional(_resolutionZ)
//			HBox.setHgrow(resolutionXField.textField(), Priority.ALWAYS)
//			HBox.setHgrow(resolutionYField.textField(), Priority.ALWAYS)
//			HBox.setHgrow(resolutionZField.textField(), Priority.ALWAYS)
//			val helpDialog = PainteraAlerts
//					.alert(Alert.AlertType.INFORMATION, true)
//					.also { it.initModality(Modality.NONE) }
//					.also { it.headerText = "Resolution for label source." }
//					.also { it.contentText = "Spatial extent of the label source along the coordinate axis." }
//			val tpGraphics = HBox(
//					Label("Resolution"),
//					Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
//					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
//					.also { it.alignment = Pos.CENTER }
//			with (TitledPaneExtensions) {
//				TitledPane(null, HBox(resolutionXField.textField(), resolutionYField.textField(), resolutionZField.textField()))
//						.also { it.graphicsOnly(tpGraphics) }
//						.also { it.alignment = Pos.CENTER_RIGHT }
//			}
//		}
//
//		val offsetPane = run {
//			val offsetXField = NumberField.doubleField(offsetX, DoublePredicate { true }, *ObjectField.SubmitOn.values())
//			val offsetYField = NumberField.doubleField(offsetX, DoublePredicate { true }, *ObjectField.SubmitOn.values())
//			val offsetZField = NumberField.doubleField(offsetX, DoublePredicate { true }, *ObjectField.SubmitOn.values())
//			offsetXField.valueProperty().bindBidirectional(_offsetX)
//			offsetYField.valueProperty().bindBidirectional(_offsetY)
//			offsetZField.valueProperty().bindBidirectional(_offsetZ)
//			HBox.setHgrow(offsetXField.textField(), Priority.ALWAYS)
//			HBox.setHgrow(offsetYField.textField(), Priority.ALWAYS)
//			HBox.setHgrow(offsetZField.textField(), Priority.ALWAYS)
//			val helpDialog = PainteraAlerts
//					.alert(Alert.AlertType.INFORMATION, true)
//					.also { it.initModality(Modality.NONE) }
//					.also { it.headerText = "Offset for label source." }
//					.also { it.contentText = "Offset in some arbitrary global/world coordinates." }
//			val tpGraphics = HBox(
//					Label("Offset"),
//					Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
//					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
//					.also { it.alignment = Pos.CENTER }
//			with (TitledPaneExtensions) {
//				TitledPane(null, HBox(offsetXField.textField(), offsetYField.textField(), offsetZField.textField()))
//						.also { it.graphicsOnly(tpGraphics) }
//						.also { it.alignment = Pos.CENTER_RIGHT }
//			}
//		}

        // TODO make resolution/offset configurable
        val metaDataContents = VBox(backendMeta) // , resolutionPane, offsetPane)

        val helpDialog = PainteraAlerts
            .alert(Alert.AlertType.INFORMATION, true).apply {
                initModality(Modality.NONE)
                headerText = "Meta data for label source."
                contentText = "TODO"
            }
        val tpGraphics = HBox(
            Label("Meta Data"),
            NamedNode.bufferNode(),
            Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } }
        ).apply { alignment = Pos.CENTER }
        val metaData = with(TitledPaneExtensions) {
            TitledPanes.createCollapsed(null, metaDataContents).apply {
                graphicsOnly(tpGraphics)
                alignment = Pos.CENTER_RIGHT
            }
        }
        return node.apply { children.add(metaData) }
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @JvmStatic
        fun <D, T> labelToBooleanFragmentMaskSource(labelSource: ConnectomicsLabelState<D, T>): DataSource<BoolType, Volatile<BoolType>>
            where D : IntegerType<D>, T : Volatile<D>, T : net.imglib2.type.Type<T> {
            return with(labelSource) {
                val fragmentsInSelectedSegments = FragmentsInSelectedSegments(selectedSegments)
                PredicateDataSource(dataSource, checkForType(dataSource.dataType, fragmentsInSelectedSegments), name)
            }
        }

        @JvmStatic
        fun checkForLabelMultisetType(fragmentsInSelectedSegments: FragmentsInSelectedSegments): Predicate<LabelMultisetType> {
            return Predicate { lmt ->
                lmt.entrySet().forEach {
                    if (fragmentsInSelectedSegments.contains(it.element.id())) {
                        return@Predicate true
                    }
                }
                return@Predicate false
            }
        }

        @JvmStatic
        fun <T> checkForType(t: T, fragmentsInSelectedSegments: FragmentsInSelectedSegments): Predicate<T>? {
            if (t is LabelMultisetType) {
                if (fragmentsInSelectedSegments.fragments.isEmpty()) {
                    return Predicate { false }
                }
                return checkForLabelMultisetType(fragmentsInSelectedSegments) as Predicate<T>
            }
            return null
        }

        @JvmStatic
        fun getGetBlockListFor(labelBlockLookup: LabelBlockLookup): GetBlockListFor<FragmentLabelMeshCacheKey> {
            return GetBlockListFor { level: Int, key: FragmentLabelMeshCacheKey ->
                val mapNotNull = key.fragments.toArray().asSequence()
                    .map { getBlocksUnchecked(labelBlockLookup, level, it) }.toList()
                val flatMap = mapNotNull.asSequence()
                    .flatMap { it.asSequence() }
                val mapNotNull1 = flatMap.toList().asSequence()
                    .map { HashWrapper.interval(it) }.toList()
                val toList = mapNotNull1.asSequence()
                    .distinct()
                    .map { it.data }
                    .toList()
                return@GetBlockListFor toList.toTypedArray()
            }
        }

        private fun getBlocksUnchecked(lookup: LabelBlockLookup, level: Int, id: Long): Array<Interval> {
            return lookup.read(LabelBlockLookupKey(level, id))
        }

        @Throws(NamedKeyCombination.CombinationMap.KeyCombinationAlreadyInserted::class)
        private fun createKeyAndMouseBindingsImpl(bindings: KeyAndMouseBindings): KeyAndMouseBindings {
            val c = bindings.keyCombinations
            with(BindingKeys) {
                c.addCombination(NamedKeyCombination(SELECT_ALL, KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN)))
                c.addCombination(
                    NamedKeyCombination(
                        SELECT_ALL_IN_CURRENT_VIEW,
                        KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
                    )
                )
                c.addCombination(NamedKeyCombination(LOCK_SEGEMENT, KeyCodeCombination(KeyCode.L)))
                c.addCombination(NamedKeyCombination(NEXT_ID, KeyCodeCombination(KeyCode.N)))
                c.addCombination(NamedKeyCombination(COMMIT_DIALOG, KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN)))
                c.addCombination(NamedKeyCombination(MERGE_ALL_SELECTED, KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN)))
                c.addCombination(NamedKeyCombination(ENTER_SHAPE_INTERPOLATION_MODE, KeyCodeCombination(KeyCode.S)))
                c.addCombination(NamedKeyCombination(EXIT_SHAPE_INTERPOLATION_MODE, KeyCodeCombination(KeyCode.ESCAPE)))
                c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_APPLY_MASK, KeyCodeCombination(KeyCode.ENTER)))
                c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_EDIT_SELECTION_1, KeyCodeCombination(KeyCode.DIGIT1)))
                c.addCombination(NamedKeyCombination(SHAPE_INTERPOLATION_EDIT_SELECTION_2, KeyCodeCombination(KeyCode.DIGIT2)))
                c.addCombination(NamedKeyCombination(ARGB_STREAM_INCREMENT_SEED, KeyCodeCombination(KeyCode.C)))
                c.addCombination(NamedKeyCombination(ARGB_STREAM_DECREMENT_SEED, KeyCodeCombination(KeyCode.C, KeyCombination.SHIFT_DOWN)))
                c.addCombination(NamedKeyCombination(REFRESH_MESHES, KeyCodeCombination(KeyCode.R)))
                c.addCombination(NamedKeyCombination(CANCEL_3D_FLOODFILL, KeyCodeCombination(KeyCode.ESCAPE)))
                c.addCombination(NamedKeyCombination(TOGGLE_NON_SELECTED_LABELS_VISIBILITY, KeyCodeCombination(KeyCode.V, KeyCombination.SHIFT_DOWN)))
            }
            return bindings
        }

        @SuppressWarnings("unchecked")
        private fun <D> equalsMaskForType(d: D): LongFunction<Converter<D, BoolType>>? {
            return when (d) {
                is LabelMultisetType -> equalMaskForLabelMultisetType() as LongFunction<Converter<D, BoolType>>
                is IntegerType<*> -> equalMaskForIntegerType() as LongFunction<Converter<D, BoolType>>
                is RealType<*> -> equalMaskForRealType() as LongFunction<Converter<D, BoolType>>
                else -> null
            }
        }

        private fun equalMaskForLabelMultisetType(): LongFunction<Converter<LabelMultisetType, BoolType>> = LongFunction {
            Converter { s: LabelMultisetType, t: BoolType -> t.set(s.contains(it)) }
        }

        private fun equalMaskForIntegerType(): LongFunction<Converter<IntegerType<*>, BoolType>> = LongFunction {
            Converter { s: IntegerType<*>, t: BoolType -> t.set(s.integerLong == it) }
        }

        private fun equalMaskForRealType(): LongFunction<Converter<RealType<*>, BoolType>> = LongFunction {
            Converter { s: RealType<*>, t: BoolType -> t.set(s.realDouble == it.toDouble()) }
        }

    }

    class BindingKeys {
        companion object {
            const val SELECT_ALL = "select all"
            const val SELECT_ALL_IN_CURRENT_VIEW = "select all in current view"
            const val LOCK_SEGEMENT = "lock segment"
            const val NEXT_ID = "next id"
            const val COMMIT_DIALOG = "commit dialog"
            const val MERGE_ALL_SELECTED = "merge all selected"
            const val ENTER_SHAPE_INTERPOLATION_MODE = "shape interpolation: enter mode"
            const val EXIT_SHAPE_INTERPOLATION_MODE = "shape interpolation: exit mode"
            const val SHAPE_INTERPOLATION_APPLY_MASK = "shape interpolation: apply mask"
            const val SHAPE_INTERPOLATION_EDIT_SELECTION_1 = "shape interpolation: edit selection 1"
            const val SHAPE_INTERPOLATION_EDIT_SELECTION_2 = "shape interpolation: edit selection 2"
            const val ARGB_STREAM_INCREMENT_SEED = "argb stream: increment seed"
            const val ARGB_STREAM_DECREMENT_SEED = "argb stream: decrement seed"
            const val REFRESH_MESHES = "refresh meshes"
            const val CANCEL_3D_FLOODFILL = "3d floodfill: cancel"
            const val TOGGLE_NON_SELECTED_LABELS_VISIBILITY = "toggle non-selected labels visibility"
        }
    }

    private object SerializationKeys {
        const val BACKEND = "backend"
        const val SELECTED_IDS = "selectedIds"
        const val LAST_SELECTION = "lastSelection"
        const val NAME = "name"
        const val MANAGED_MESH_SETTINGS = "meshSettings"
        const val COMPOSITE = "composite"
        const val CONVERTER = "converter"
        const val CONVERTER_SEED = "seed"
        const val CONVERTER_USER_SPECIFIED_COLORS = "userSpecifiedColors"
        const val INTERPOLATION = "interpolation"
        const val IS_VISIBLE = "isVisible"
        const val RESOLUTION = "resolution"
        const val OFFSET = "offset"
        const val LABEL_BLOCK_LOOKUP = "labelBlockLookup"
        const val LOCKED_SEGMENTS = "lockedSegments"
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D : IntegerType<D>, T> : PainteraSerialization.PainteraSerializer<ConnectomicsLabelState<D, T>>
        where T : net.imglib2.type.Type<T>, T : Volatile<D> {
        override fun serialize(state: ConnectomicsLabelState<D, T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val map = JsonObject()
            with(SerializationKeys) {
                map.add(BACKEND, SerializationHelpers.serializeWithClassInfo(state.backend, context))
                state.selectedIds.activeIdsCopyAsArray.takeIf { it.isNotEmpty() }?.let { map.add(SELECTED_IDS, context.serialize(it)) }
                state.selectedIds.lastSelection.takeIf { Label.regular(it) }?.let { map.addProperty(LAST_SELECTION, it) }
                map.addProperty(NAME, state.name)
                map.add(MANAGED_MESH_SETTINGS, context.serialize(state.meshManager.managedSettings))
                map.add(COMPOSITE, SerializationHelpers.serializeWithClassInfo(state.composite, context))
                JsonObject().let { m ->
                    m.addProperty(CONVERTER_SEED, state.converter.seedProperty().get())
                    state.converter.userSpecifiedColors().asJsonObject()?.let { m.add(CONVERTER_USER_SPECIFIED_COLORS, it) }
                    map.add(CONVERTER, m)
                }
                map.add(INTERPOLATION, context.serialize(state.interpolation))
                map.addProperty(IS_VISIBLE, state.isVisible)
                state.resolution.takeIf { r -> r.any { it != 1.0 } }?.let { map.add(RESOLUTION, context.serialize(it)) }
                state.offset.takeIf { o -> o.any { it != 0.0 } }?.let { map.add(OFFSET, context.serialize(it)) }
                state.labelBlockLookup.takeUnless { state.backend.providesLookup }?.let { map.add(LABEL_BLOCK_LOOKUP, context.serialize(it)) }
                state.lockedSegments.lockedSegmentsCopy().takeIf { it.isNotEmpty() }?.let { map.add(LOCKED_SEGMENTS, context.serialize(it)) }

            }
            return map
        }

        override fun getTargetClass(): Class<ConnectomicsLabelState<D, T>> = ConnectomicsLabelState::class.java as Class<ConnectomicsLabelState<D, T>>

        companion object {
            private fun Map<Long, Color>.asJsonObject(): JsonObject? {
                if (isEmpty())
                    return null
                val colors = JsonObject()
                this.forEach { (id, color) -> colors.addProperty(id.toString(), Colors.toHTML(color)) }
                return colors
            }
        }
    }

    class Deserializer<D : IntegerType<D>, T>(val viewer: PainteraBaseView) : JsonDeserializer<ConnectomicsLabelState<D, T>>

        where T : net.imglib2.type.Type<T>, T : Volatile<D> {

        @Plugin(type = StatefulSerializer.DeserializerFactory::class)
        class Factory<D : IntegerType<D>, T> : StatefulSerializer.DeserializerFactory<ConnectomicsLabelState<D, T>, Deserializer<D, T>>

            where T : net.imglib2.type.Type<T>, T : Volatile<D> {
            override fun createDeserializer(
                arguments: StatefulSerializer.Arguments,
                projectDirectory: Supplier<String>,
                dependencyFromIndex: IntFunction<SourceState<*, *>>,
            ): Deserializer<D, T> = Deserializer(arguments.viewer)

            override fun getTargetClass(): Class<ConnectomicsLabelState<D, T>> = ConnectomicsLabelState::class.java as Class<ConnectomicsLabelState<D, T>>

        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConnectomicsLabelState<D, T> {
            with(SerializationKeys) {
                with(GsonExtensions) {
                    with(json) {
                        val backend = SerializationHelpers.deserializeFromClassInfo<ConnectomicsLabelBackend<D, T>>(json.getJsonObject(BACKEND)!!, context)
                        val name = json.getStringProperty(NAME) ?: backend.defaultSourceName
                        val resolution = letProperty(RESOLUTION) { context.deserialize(it, DoubleArray::class.java) } ?: DoubleArray(3) { 1.0 }
                        val offset = letProperty(OFFSET) { context.deserialize(it, DoubleArray::class.java) } ?: DoubleArray(3) { 0.0 }
                        val labelBlockLookup = getProperty(LABEL_BLOCK_LOOKUP)?.takeUnless { backend.providesLookup }?.let { context.deserialize<LabelBlockLookup>(it, LabelBlockLookup::class.java) }
                        val state = ConnectomicsLabelState<D, T>(
                            SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(BACKEND)!!, context),
                            viewer.viewer3D().meshesGroup(),
                            viewer.viewer3D().viewFrustumProperty(),
                            viewer.viewer3D().eyeToWorldTransformProperty(),
                            viewer.meshManagerExecutorService,
                            viewer.meshWorkerExecutorService,
                            viewer.queue,
                            0,
                            name,
                            resolution,
                            offset,
                            labelBlockLookup)
                        return state.apply {
                            letProperty(SELECTED_IDS) { selectedIds.activate(*context.deserialize(it, LongArray::class.java)) }
                            letLongProperty(LAST_SELECTION) { selectedIds.activateAlso(it) }
                            letProperty(ManagedMeshSettings.MESH_SETTINGS_KEY) { meshManager.managedSettings.set(context.deserialize(it, ManagedMeshSettings::class.java)) }
                            letJsonObject(COMPOSITE) { composite = SerializationHelpers.deserializeFromClassInfo(it, context) }
                            letJsonObject(CONVERTER) { converter ->
                                converter.apply {
                                    letJsonObject(CONVERTER_USER_SPECIFIED_COLORS) { toColorMap().forEach { (id, c) -> state.converter.setColor(id, c) } }
                                    letLongProperty(CONVERTER_SEED) { seed -> state.converter.seedProperty().set(seed) }
                                }
                            }
                            letProperty(INTERPOLATION) { interpolation = context.deserialize(it, Interpolation::class.java) }
                            letBooleanProperty(IS_VISIBLE) { isVisible = it }
                            letProperty(LOCKED_SEGMENTS) { context.deserialize<LongArray>(it, LongArray::class.java) }?.forEach { lockedSegments.lock(it) }
                        }
                    }
                }
            }
        }

        companion object {
            private fun JsonObject.toColorMap() = this.keySet().map { Pair(it.toLong(), Color.web(this[it].asString)) }
        }

    }

}

class FragmentLabelMeshCacheKey constructor(fragmentsInSelectedSegments: FragmentsInSelectedSegments) : MeshCacheKey {

    val fragments: TLongHashSet = TLongHashSet(fragmentsInSelectedSegments.fragments)
    val segments: TLongHashSet = TLongHashSet(fragmentsInSelectedSegments.selectedSegments.selectedSegmentsCopyAsArray)

    override fun hashCode(): Int {
        return HashCodeBuilder().append(fragments).toHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return (other as? FragmentLabelMeshCacheKey)?.let { other.fragments == this.fragments } ?: let { false }
    }

    override fun toString(): String {
        return "FragmentLabelMeshCacheKey: ($segments)"
    }
}
