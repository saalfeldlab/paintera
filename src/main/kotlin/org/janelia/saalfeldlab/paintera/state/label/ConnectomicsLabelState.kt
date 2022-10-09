package org.janelia.saalfeldlab.paintera.state.label

import bdv.util.volatiles.SharedQueue
import bdv.viewer.Interpolation
import com.google.gson.*
import gnu.trove.set.hash.TLongHashSet
import javafx.beans.InvalidationListener
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Modality
import net.imglib2.Interval
import net.imglib2.RealInterval
import net.imglib2.Volatile
import net.imglib2.converter.Converter
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.label.Label
import net.imglib2.type.label.LabelMultisetEntry
import net.imglib2.type.label.LabelMultisetType
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.ARGBType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.apache.commons.lang.builder.HashCodeBuilder
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr
import org.janelia.saalfeldlab.paintera.composition.Composite
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode
import org.janelia.saalfeldlab.paintera.control.modes.PaintLabelMode
import org.janelia.saalfeldlab.paintera.control.modes.ViewLabelMode
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
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.*
import org.janelia.saalfeldlab.paintera.stream.*
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
    labelBlockLookup: LabelBlockLookup? = null,
) : SourceStateWithBackend<D, T>, IntersectableSourceState<D, T, FragmentLabelMeshCacheKey>
    where T : net.imglib2.type.Type<T>, T : Volatile<D> {

    private val source: DataSource<D, T> = backend.createSource(queue, priority, name)
    override fun getDataSource(): DataSource<D, T> = source

    internal val maskForLabel = equalsMaskForType(source.dataType)!!

    val fragmentSegmentAssignment = backend.fragmentSegmentAssignment

    val lockedSegments = LockedSegmentsOnlyLocal({})

    val selectedIds = SelectedIds()

    val selectedSegments = SelectedSegments(selectedIds, fragmentSegmentAssignment)

    private val fragmentsInSelectedSegments = selectedSegments.createObservableBinding { FragmentsInSelectedSegments(selectedSegments) }

    internal val idService = backend.createIdService(source)

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

    val meshCacheKeyProperty: ObjectBinding<FragmentLabelMeshCacheKey> = fragmentsInSelectedSegments.createNonNullValueBinding { FragmentLabelMeshCacheKey(it) }

    override fun getMeshCacheKeyBinding(): ObjectBinding<FragmentLabelMeshCacheKey> = meshCacheKeyProperty

    override fun getGetBlockListFor(): GetBlockListFor<FragmentLabelMeshCacheKey> = this.meshManager.getBlockListForMeshCacheKey

    override fun getIntersectableMask(): DataSource<BoolType, Volatile<BoolType>> = labelToBooleanFragmentMaskSource(this)

    private val idSelectorHandler = LabelSourceStateIdSelectorHandler(source, idService, selectedIds, fragmentSegmentAssignment, lockedSegments, meshManager::refreshMeshes)

    private val mergeDetachHandler = LabelSourceStateMergeDetachHandler(source, selectedIds, fragmentSegmentAssignment, idService)

    private val commitHandler = CommitHandler(this, this::fragmentSegmentAssignment)

    private val streamSeedSetter = ARGBStreamSeedSetter(stream)

    private val showOnlySelectedInStreamToggle = ShowOnlySelectedInStreamToggle(stream)

    internal fun refreshMeshes() = meshManager.refreshMeshes()

    // ARGB composite
    private val _composite: ObjectProperty<Composite<ARGBType, ARGBType>> = SimpleObjectProperty(
        this,
        "composite",
        ARGBCompositeAlphaYCbCr()
    )
    var composite: Composite<ARGBType, ARGBType> by _composite.nonnull()

    override fun compositeProperty(): ObjectProperty<Composite<ARGBType, ARGBType>> = _composite

    fun nextId(activate: Boolean = false) = idSelectorHandler.nextId(activate)

    // source name
    private val _name = SimpleStringProperty(name)
    var name: String by _name.nonnull()
    override fun nameProperty(): StringProperty = _name

    // status text
    private val _statusText = SimpleStringProperty(this, "status text", "")
    override fun statusTextProperty(): StringProperty = _statusText

    // visibility
    private val _isVisible = SimpleBooleanProperty(true)
    var isVisible: Boolean by _isVisible.nonnull()
    override fun isVisibleProperty(): BooleanProperty = _isVisible

    // interpolation
    private val _interpolation = SimpleObjectProperty(this, "interpolation", Interpolation.NEARESTNEIGHBOR)
    var interpolation: Interpolation by _interpolation.nonnull()
    override fun interpolationProperty(): ObjectProperty<Interpolation> = _interpolation

    // source dependencies
    override fun dependsOn(): Array<SourceState<*, *>> = arrayOf()

    // flood fill state
    internal val floodFillState = SimpleObjectProperty<FloodFillState>()

    //Brush properties
    internal val brushProperties = BrushProperties()

    // display status
    private val displayStatus: HBox = createDisplayStatus(dataSource, floodFillState, selectedIds, fragmentSegmentAssignment, stream)
    override fun getDisplayStatus(): Node = displayStatus

    val keyBindings = paintera.baseView.keyAndMouseBindings.getConfigFor(this).keyCombinations

    private val globalActions = listOf(
        ActionSet("Connectomics Label State Global Actions") {
            KEY_PRESSED(keyBindings, LabelSourceStateKeys.REFRESH_MESHES) {
                onAction {
                    refreshMeshes()
                    LOG.debug("Key event triggered refresh meshes")
                }
            }
            KEY_PRESSED(keyBindings, LabelSourceStateKeys.TOGGLE_NON_SELECTED_LABELS_VISIBILITY) {
                onAction {
                    showOnlySelectedInStreamToggle.toggleNonSelectionVisibility()
                    paintera.baseView.orthogonalViews().requestRepaint()
                }
            }
            KEY_PRESSED(keyBindings, LabelSourceStateKeys.ARGB_STREAM_INCREMENT_SEED) {
                onAction { streamSeedSetter.incrementStreamSeed() }
            }
            KEY_PRESSED(keyBindings, LabelSourceStateKeys.ARGB_STREAM_DECREMENT_SEED) {
                onAction { streamSeedSetter.decrementStreamSeed() }
            }
        },
        commitHandler.makeActionSet(keyBindings, paintera.baseView)
    )

    @JvmSynthetic
    private val viewerActions = listOf(
        *idSelectorHandler.makeActionSets(keyBindings, paintera.keyTracker, paintera.activeViewer::get).toTypedArray(),
        *mergeDetachHandler.makeActionSets(keyBindings, paintera.activeViewer::get).toTypedArray()
    )

    override fun getViewerActionSets(): List<ActionSet> = viewerActions
    override fun getGlobalActionSets(): List<ActionSet> = globalActions


    private fun requestRepaint(paintera: PainteraBaseView, interval: RealInterval? = null) {
        if (isVisible && Paintera.paintable) {
            interval?.let {
                paintera.orthogonalViews().requestRepaint(it)
            } ?: let {
                paintera.orthogonalViews().requestRepaint()
            }
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
    }

    override fun onRemoval(sourceInfo: SourceInfo) {
        LOG.info("Removed LabelSourceState {}", name)
        meshManager.removeAllMeshes()
        CommitHandler.showCommitDialog(
            this,
            sourceInfo.indexOf(this.dataSource),
            false,
            { index, name ->
                """
                    Removing source $index: $name.
                    Uncommitted changes to the canvas and/or fragment-segment assignment will be lost if skipped.
                """.trimIndent()
            },
            false,
            "_Skip",
            fragmentSegmentAssignmentState = fragmentSegmentAssignment
        )
    }

    override fun onShutdown(paintera: PainteraBaseView) {
        CommitHandler.showCommitDialog(
            this,
            paintera.sourceInfo().indexOf(this.dataSource),
            false,
            { index, name ->
                """
                    Shutting down Paintera.
                    Uncommitted changes to the canvas will be lost for source $index: $name if skipped.
                    Uncommitted changes to the fragment-segment-assigment will be stored in the Paintera project (if any)
                    but can be committed to the data backend, as well
                """.trimIndent()
            },
            false,
            "_Skip",
            fragmentSegmentAssignmentState = fragmentSegmentAssignment
        )
    }

    override fun createKeyAndMouseBindings() = KeyAndMouseBindings(LabelSourceStateKeys.namedCombinationsCopy())

    override fun getDefaultMode(): ControlMode {
        return if (backend.canWriteToSource()) {
            PaintLabelMode
        } else {
            ViewLabelMode
        }
    }

    override fun preferencePaneNode(): Node {
        val node = LabelSourceStatePreferencePaneNode(
            dataSource,
            compositeProperty(),
            converter(),
            meshManager,
            meshManager.managedSettings,
            brushProperties
        ).node.let { if (it is VBox) it else VBox(it) }

        val backendMeta = backend.createMetaDataNode()

        // TODO make resolution/offset configurable
        val metaDataContents = VBox(backendMeta)

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
        fun createDisplayStatus(
            dataSource: DataSource<*, *>,
            simpleObjectProperty: ObjectProperty<FloodFillState>,
            selectedIds: SelectedIds,
            fragmentSegmentAssignmentState: FragmentSegmentAssignmentState,
            stream: AbstractHighlightingARGBStream
        ): HBox {

            val lastSelectedLabelColorRect = Rectangle(13.0, 13.0)
            lastSelectedLabelColorRect.stroke = Color.BLACK

            val lastSelectedLabelColorRectTooltip = Tooltip()
            Tooltip.install(lastSelectedLabelColorRect, lastSelectedLabelColorRectTooltip)

            val lastSelectedIdUpdater = InvalidationListener {
                InvokeOnJavaFXApplicationThread {
                    if (selectedIds.isLastSelectionValid) {
                        val lastSelectedLabelId = selectedIds.lastSelection
                        val currSelectedColor = Colors.toColor(stream.argb(lastSelectedLabelId))
                        lastSelectedLabelColorRect.fill = currSelectedColor
                        lastSelectedLabelColorRect.isVisible = true

                        val activeIdText = StringBuilder()
                        val segmentId = fragmentSegmentAssignmentState.getSegment(lastSelectedLabelId)
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
            fragmentSegmentAssignmentState.addListener(lastSelectedIdUpdater)

            // add the same listener to the color stream (for example, the color should change when a new random seed value is set)
            stream.addListener(lastSelectedIdUpdater)

            val paintingProgressIndicator = ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS).apply {
                prefWidth = 15.0
                prefHeight = 15.0
                minWidth = Control.USE_PREF_SIZE
                minHeight = Control.USE_PREF_SIZE
                isVisible = false
                tooltip = Tooltip()
            }


            val resetProgressIndicatorContextMenu = {
                paintingProgressIndicator.apply {
                    contextMenu?.hide()
                    contextMenu = null
                    onMouseClicked = null
                    cursor = Cursor.DEFAULT
                }
            }

            val setProgressIndicatorContextMenu = { ctxMenu: ContextMenu ->
                resetProgressIndicatorContextMenu()
                paintingProgressIndicator.apply {
                    contextMenu = ctxMenu
                    setOnMouseClicked { ctxMenu.show(this, it.screenX, it.screenY) }
                    cursor = Cursor.HAND
                }
            }

            (dataSource as? MaskedSource<*, *>)?.let { maskedSource ->
                maskedSource.isApplyingMaskProperty.addListener { _, _, newv ->
                    InvokeOnJavaFXApplicationThread {
                        paintingProgressIndicator.apply {
                            isVisible = newv
                            if (newv) {
                                maskedSource.currentMask?.let {
                                    tooltip.text = "Applying mask to canvas, label ID: " + it.info.value.get()
                                }
                            }
                        }
                    }
                }
            }

            simpleObjectProperty.addListener { _, _, newv: FloodFillState? ->
                InvokeOnJavaFXApplicationThread {
                    newv?.let { newFloodFillState ->
                        paintingProgressIndicator.apply {
                            isVisible = true
                            tooltip.text = "Flood-filling, label ID: " + newFloodFillState.labelId
                        }

                        val floodFillContextMenuCancelItem = MenuItem("Cancel")
                        newFloodFillState.interrupt?.let { interrupt ->
                            floodFillContextMenuCancelItem.setOnAction { interrupt.run() }
                        } ?: let {
                            floodFillContextMenuCancelItem.isDisable = true
                        }
                        setProgressIndicatorContextMenu(ContextMenu(floodFillContextMenuCancelItem))
                    } ?: let {
                        paintingProgressIndicator.isVisible = false
                        resetProgressIndicatorContextMenu()
                    }
                }
            }

            return HBox(5.0, lastSelectedLabelColorRect, paintingProgressIndicator).apply {
                alignment = Pos.CENTER_LEFT
                padding = Insets(0.0, 3.0, 0.0, 3.0)
            }
        }

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

                lmt.entrySet().any { fragmentsInSelectedSegments.contains(it.element.id()) }
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
            Converter { s: LabelMultisetType, t: BoolType ->
                t.set(s.contains(it, LabelMultisetEntry()))
            }
        }

        private fun equalMaskForIntegerType(): LongFunction<Converter<IntegerType<*>, BoolType>> = LongFunction {
            Converter { s: IntegerType<*>, t: BoolType -> t.set(s.integerLong == it) }
        }

        private fun equalMaskForRealType(): LongFunction<Converter<RealType<*>, BoolType>> = LongFunction {
            Converter { s: RealType<*>, t: BoolType -> t.set(s.realDouble == it.toDouble()) }
        }

    }

    private object SerializationKeys {
        //@formatter:off
        const val BACKEND                         = "backend"
        const val SELECTED_IDS                    = "selectedIds"
        const val LAST_SELECTION                  = "lastSelection"
        const val NAME                            = "name"
        const val MANAGED_MESH_SETTINGS           = "meshSettings"
        const val COMPOSITE                       = "composite"
        const val CONVERTER                       = "converter"
        const val CONVERTER_SEED                  = "seed"
        const val CONVERTER_USER_SPECIFIED_COLORS = "userSpecifiedColors"
        const val INTERPOLATION                   = "interpolation"
        const val IS_VISIBLE                      = "isVisible"
        const val RESOLUTION                      = "resolution"
        const val OFFSET                          = "offset"
        const val LABEL_BLOCK_LOOKUP              = "labelBlockLookup"
        const val LOCKED_SEGMENTS                 = "lockedSegments"
        //@formatter:on
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D : IntegerType<D>, T> : PainteraSerialization.PainteraSerializer<ConnectomicsLabelState<D, T>>
        where T : net.imglib2.type.Type<T>, T : Volatile<D> {
        override fun serialize(state: ConnectomicsLabelState<D, T>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val map = JsonObject()
            with(SerializationKeys) {
                map.add(BACKEND, context.withClassInfo(state.backend))
                state.selectedIds.activeIdsCopyAsArray.takeIf { it.isNotEmpty() }?.let { map.add(SELECTED_IDS, context[it]) }
                state.selectedIds.lastSelection.takeIf { Label.regular(it) }?.let { map.addProperty(LAST_SELECTION, it) }
                map.addProperty(NAME, state.name)
                map.add(MANAGED_MESH_SETTINGS, context[state.meshManager.managedSettings])
                map.add(COMPOSITE, context.withClassInfo(state.composite))
                JsonObject().let { m ->
                    m.addProperty(CONVERTER_SEED, state.converter.seedProperty().get())
                    state.converter.userSpecifiedColors().asJsonObject()?.let { m.add(CONVERTER_USER_SPECIFIED_COLORS, it) }
                    map.add(CONVERTER, m)
                }
                map.add(INTERPOLATION, context[state.interpolation])
                map.addProperty(IS_VISIBLE, state.isVisible)
                map.add(RESOLUTION, context[state.resolution])
                map.add(OFFSET, context[state.offset])
                state.labelBlockLookup.takeUnless { state.backend.providesLookup }?.let { map.add(LABEL_BLOCK_LOOKUP, context[it]) }
                state.lockedSegments.lockedSegmentsCopy().takeIf { it.isNotEmpty() }?.let { map.add(LOCKED_SEGMENTS, context[it]) }

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
                        val backend = context.fromClassInfo<ConnectomicsLabelBackend<D, T>>(json, BACKEND)!!
                        val name = json[NAME] ?: backend.name
                        val resolution = context[json, RESOLUTION] ?: backend.getMetadataState().resolution
                        val offset = context[json, OFFSET] ?: backend.getMetadataState().translation
                        backend.getMetadataState().updateTransform(resolution, offset)

                        val labelBlockLookup: LabelBlockLookup? = if (backend.providesLookup) null else context[json, LABEL_BLOCK_LOOKUP]
                        val state = ConnectomicsLabelState(
                            backend,
                            viewer.viewer3D().meshesGroup(),
                            viewer.viewer3D().viewFrustumProperty(),
                            viewer.viewer3D().eyeToWorldTransformProperty(),
                            viewer.meshManagerExecutorService,
                            viewer.meshWorkerExecutorService,
                            viewer.queue,
                            0,
                            name,
                            labelBlockLookup
                        )
                        return state.apply {
                            get<Long>(LAST_SELECTION) { selectedIds.activateAlso(it) }
                            get<JsonObject>(CONVERTER) { converter ->
                                converter.apply {
                                    get<JsonObject>(CONVERTER_USER_SPECIFIED_COLORS) { it.toColorMap().forEach { (id, c) -> state.converter.setColor(id, c) } }
                                    get<Long>(CONVERTER_SEED) { seed -> state.converter.seedProperty().set(seed) }
                                }
                            }

                            composite = context.fromClassInfo(json, COMPOSITE)!!
                            interpolation = context[json, INTERPOLATION]!!
                            isVisible = context[json, IS_VISIBLE]!!
                            context.get<LongArray>(json, SELECTED_IDS) { selectedIds.activate(*it) }
                            context.get<ManagedMeshSettings>(json, ManagedMeshSettings.MESH_SETTINGS_KEY) { meshManager.managedSettings.set(it) }
                            context.get<LongArray>(json, LOCKED_SEGMENTS)?.forEach {
                                lockedSegments.lock(it)
                            }
                        }
                    }
                }
            }
        }

        companion object {
            private fun JsonObject.toColorMap() = this.keySet().map {
                Pair(it.toLong(), Color.web(this[it].asString))
            }
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
