package org.janelia.saalfeldlab.paintera.control.modes

import ai.onnxruntime.OnnxTensor
import bdv.util.Affine3DHelpers
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.event.EventHandler
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.*
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.midi.ToggleAction
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.CANCEL
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.EXIT_SHAPE_INTERPOLATION_MODE
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_APPLY_MASK
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_EDIT_FIRST_SELECTION
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_EDIT_LAST_SELECTION
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_EDIT_NEXT_SELECTION
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_EDIT_PREVIOUS_SELECTION
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.SHAPE_INTERPOLATION_TOGGLE_PREVIEW
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.ControllerState.Interpolate
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.ControllerState.Moving
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController.EditSelectionChoice
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.navigation.TranslationController
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintBrushTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.SamTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.util.get
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class ShapeInterpolationMode<D : IntegerType<D>>(val controller: ShapeInterpolationController<D>, val previousMode: ControlMode) : AbstractToolMode() {

    override val defaultTool: Tool? by lazy { shapeInterpolationTool }

    private inner class ShapeIntepolationToolProperty : SimpleObjectProperty<ShapeInterpolationTool?>() {

        private val keyAndMouseBindingsProperty = activeSourceStateProperty.createNullableValueBinding {
            it?.let {
                paintera.baseView.keyAndMouseBindings.getConfigFor(it)
            }
        }

        init {
            bind(keyAndMouseBindingsProperty.createNullableValueBinding {
                it?.let {
                    ShapeInterpolationTool(
                        controller,
                        it.keyCombinations,
                        previousMode,
                        this@ShapeInterpolationMode,
                        this@ShapeInterpolationMode.fill2DTool
                    )
                }
            })
        }
    }

    private val shapeInterpolationToolProperty = ShapeIntepolationToolProperty()
    private val shapeInterpolationTool by shapeInterpolationToolProperty.nullableVal()

    private val paintBrushTool = object : PaintBrushTool(activeSourceStateProperty, this@ShapeInterpolationMode) {

        override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
            mutableListOf(
                *getBrushActions(),
                *getPaintActions(),
                shapeInterpolationPaintBrushActions(),
                *(midiBrushActions() ?: arrayOf()),
                *getMidiNavigationActions().toTypedArray()
            )
        }

        private fun getMidiNavigationActions(): List<MidiActionSet> {
            val midiNavActions = listOfNotNull(
                NavigationTool.midiPanActions(),
                NavigationTool.midiSliceActions(),
                NavigationTool.midiZoomActions()
            )
            midiNavActions.forEach { it.verifyAll(Event.ANY, "Not Currently Painting") { !isPainting } }
            return midiNavActions
        }

        override fun activate() {
            /* Don't allow painting with depth during shape interpolation */
            brushProperties?.brushDepth = 1.0
            super.activate()
        }

        override fun deactivate() {
            paintClickOrDrag?.apply {
                if (isPainting()) {
                    finishPaintStroke()
                }
                release()
            }
            super.deactivate()
        }
    }

    private val fill2DTool = object : Fill2DTool(activeSourceStateProperty, this@ShapeInterpolationMode) {


        private val controllerPaintOnFill = ChangeListener<Interval?> { _, _, new ->
            new?.let { controller.paint(it) }
        }

        override fun activate() {
            fillLabel = { controller.interpolationId }
            /* Don't allow filling with depth during shape interpolation */
            brushProperties?.brushDepth = 1.0
            super.activate()
            fill2D.maskIntervalProperty.addListener(controllerPaintOnFill)
        }

        override fun deactivate() {
            fill2D.maskIntervalProperty.removeListener(controllerPaintOnFill)
            super.deactivate()
        }

        override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
            super.actionSets.also { it += additionalFloodFillActions(this) }
        }

    }

    private val samTool : SamTool = object : SamTool(activeSourceStateProperty, this@ShapeInterpolationMode) {

        private var lastEmbedding: OnnxTensor? = null
        private var globalTransformAtEmbedding = AffineTransform3D()

        init {
            activeViewerProperty.unbind()
            activeViewerProperty.bind(mode!!.activeViewerProperty)
        }

        override fun activate() {
            maskedSource?.resetMasks(false)
            viewerMask = controller.getMask()
            providedEmbedding = if (Affine3DHelpers.equals(paintera.baseView.manager().transform, globalTransformAtEmbedding)) lastEmbedding else null
            super.activate()
        }

        override fun deactivate() {
            super.deactivate()
            lastEmbedding = getImageEmbeddingTask.get()!!
            globalTransformAtEmbedding.set(paintera.baseView.manager().transform)
        }

        override fun setCurrentLabelToSelection() {
            currentLabelToPaint = controller.interpolationId
        }

        override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
            super.actionSets.also { it += additionalSamActions(this) }
        }
    }

    override val modeActions by lazy { modeActions() }

    override val allowedActions = AllowedActions.AllowedActionsBuilder()
        .add(PaintActionType.ShapeInterpolation, PaintActionType.Paint, PaintActionType.Erase, PaintActionType.SetBrushSize, PaintActionType.Fill)
        .add(MenuActionType.ToggleMaximizeViewer, MenuActionType.DetachViewer)
        .add(NavigationActionType.Pan, NavigationActionType.Slice, NavigationActionType.Zoom)
        .create()

    private val toolTriggerListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
        new?.viewer()?.apply { modeActions.forEach { installActionSet(it) } }
        old?.viewer()?.apply { modeActions.forEach { removeActionSet(it) } }
    }

    override val tools: ObservableList<Tool> by lazy { FXCollections.observableArrayList(shapeInterpolationTool, paintBrushTool, fill2DTool, samTool) }

    override fun enter() {
        activeViewerProperty.addListener(toolTriggerListener)
        paintera.baseView.disabledPropertyBindings[controller] = controller.isBusyProperty
        super.enter()
        /* unbind the activeViewerProperty, since we disabled other viewers during ShapeInterpolation mode*/
        activeViewerProperty.unbind()
        /* Try to initialize the tool, if state is valid. If not, change back to previous mode. */
        activeViewerProperty.get()?.viewer()?.let {
            disableUnfocusedViewers()
            shapeInterpolationTool?.let { shapeInterpolationTool ->
                controller.apply {
                    if (!isControllerActive && source.currentMask == null && source.isApplyingMaskProperty.not().get()) {
                        modifyFragmentAlpha()
                        switchTool(shapeInterpolationTool)
                        enterShapeInterpolation(it)
                    }
                }
            } ?: paintera.baseView.changeMode(previousMode)
        } ?: paintera.baseView.changeMode(previousMode)
    }

    override fun exit() {
        super.exit()
        enableAllViewers()
        paintera.baseView.disabledPropertyBindings.remove(controller)
        controller.resetFragmentAlpha()
        activeViewerProperty.removeListener(toolTriggerListener)
    }

    private fun ShapeInterpolationController<*>.modifyFragmentAlpha() {
        /* set the converter fragment alpha to be the same as the segment. We do this so we can use the fragment alpha for the
        * selected objects during shape interpolation flood fill. This needs to be un-done in the #deactivate */
        converter.activeFragmentAlphaProperty().apply {
            activeSelectionAlpha = get().toDouble() / 255.0
            set(converter.activeSegmentAlphaProperty().get())
        }
    }

    private fun ShapeInterpolationController<*>.resetFragmentAlpha() {
        /* Add the activeFragmentAlpha back when we are done */
        apply {
            converter.activeFragmentAlphaProperty().set((activeSelectionAlpha * 255).toInt())
        }
    }

    private fun modeActions(): List<ActionSet> {
        return mutableListOf(
            painteraActionSet(EXIT_SHAPE_INTERPOLATION_MODE) {
                with(controller) {
                    verifyAll(KEY_PRESSED, "Shape Interpolation Controller is Active ") { isControllerActive }
                    verifyAll(Event.ANY, "Shape Interpolation Tool is Active") { shapeInterpolationTool != null }
                    KEY_PRESSED {
                        graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "reject", "reject-shape-interpolation") } }
                        keyMatchesBinding(shapeInterpolationTool!!.keyCombinations, EXIT_SHAPE_INTERPOLATION_MODE)
                        onAction {
                            exitShapeInterpolation(false)
                            paintera.baseView.changeMode(previousMode)
                        }
                    }
                }
            },
            painteraActionSet("paint during shape interpolation", PaintActionType.Paint) {
                KEY_PRESSED(*paintBrushTool.keyTrigger.toTypedArray()) {
                    name = "switch to paint tool"
                    verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                    onAction { switchTool(paintBrushTool) }
                }

                KEY_RELEASED(*paintBrushTool.keyTrigger.toTypedArray()) {
                    name = "switch back to shape interpolation tool from paint brush"
                    filter = true
                    verify { activeTool is PaintBrushTool }
                    onAction { switchTool(shapeInterpolationTool) }
                }

                KEY_PRESSED(*fill2DTool.keyTrigger.toTypedArray()) {
                    name = "switch to fill2d tool"
                    verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                    onAction { switchTool(fill2DTool) }
                }
                KEY_RELEASED(*fill2DTool.keyTrigger.toTypedArray()) {
                    name = "switch to shape interpolation tool from fill2d"
                    filter = true
                    verify { activeTool is Fill2DTool }
                    onAction {
                        fill2DTool.fill2D.release()
                        switchTool(shapeInterpolationTool)
                    }
                }
                KEY_PRESSED(*samTool.keyTrigger.toTypedArray()) {
                    name = "toggle SAM tool"
                    verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                    onAction {
                        val nextTool = if (activeTool != samTool) samTool else shapeInterpolationTool
                        switchTool(nextTool)
                    }
                }
            },
            painteraActionSet("key slice navigation") {
                keyPressEditSelectionAction(EditSelectionChoice.First, SHAPE_INTERPOLATION_EDIT_FIRST_SELECTION, shapeInterpolationTool!!.keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Last, SHAPE_INTERPOLATION_EDIT_LAST_SELECTION, shapeInterpolationTool!!.keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Previous, SHAPE_INTERPOLATION_EDIT_PREVIOUS_SELECTION, shapeInterpolationTool!!.keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Next, SHAPE_INTERPOLATION_EDIT_NEXT_SELECTION, shapeInterpolationTool!!.keyCombinations)
            },
            DeviceManager.xTouchMini?.let { device ->
                activeViewerProperty.get()?.viewer()?.let { viewer ->
                    painteraMidiActionSet("midi paint tool switch actions", device, viewer, PaintActionType.Paint) {
                        val toggleToolActionMap = mutableMapOf<Tool, ToggleAction>()
                        activeToolProperty.addListener { obs, old, new ->
                            toggleToolActionMap[old]?.updateControlSilently(MCUButtonControl.TOGGLE_OFF)
                            toggleToolActionMap[new]?.updateControlSilently(MCUButtonControl.TOGGLE_ON)
                        }
                        toggleToolActionMap[shapeInterpolationTool!!] = MidiToggleEvent.BUTTON_TOGGLE(0) {
                            name = "midi switch back to shape interpolation tool"
                            filter = true
                            onAction {
                                InvokeOnJavaFXApplicationThread {
                                    if (activeTool is Fill2DTool) {
                                        fill2DTool.fill2D.release()
                                    }
                                    if (activeTool != shapeInterpolationTool)
                                        switchTool(shapeInterpolationTool)
                                    /* If triggered, ensure toggle is on. Only can be off when switching to another tool */
                                    updateControlSilently(MCUButtonControl.TOGGLE_ON)
                                }
                            }
                        }
                        toggleToolActionMap[paintBrushTool] = MidiToggleEvent.BUTTON_TOGGLE(1) {
                            name = "midi switch to paint tool"
                            verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                            onAction {
                                InvokeOnJavaFXApplicationThread {
                                    if (activeTool == paintBrushTool) {
                                        switchTool(shapeInterpolationTool)
                                    } else {
                                        switchTool(paintBrushTool)
                                        paintBrushTool.enteredWithoutKeyTrigger = true
                                    }
                                }
                            }
                        }
                        toggleToolActionMap[fill2DTool] = MidiToggleEvent.BUTTON_TOGGLE(2) {
                            name = "midi switch to fill2d tool"
                            verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                            onAction {
                                InvokeOnJavaFXApplicationThread {
                                    if (activeTool == fill2DTool) {
                                        switchTool(shapeInterpolationTool)
                                    } else {
                                        switchTool(fill2DTool)
                                    }
                                }
                            }
                        }
                        with(controller) {
                            MidiButtonEvent.BUTTON_PRESED(9) {
                                name = "midi go to first slice"
                                verify { controllerState != Moving }
                                onAction { editSelection(EditSelectionChoice.First) }

                            }
                            MidiButtonEvent.BUTTON_PRESED(10) {
                                name = "midi go to previous slice"
                                verify { controllerState != Moving }
                                onAction { editSelection(EditSelectionChoice.Previous) }

                            }
                            MidiButtonEvent.BUTTON_PRESED(11) {
                                name = "midi go to next slice"
                                verify { controllerState != Moving }
                                onAction { editSelection(EditSelectionChoice.Next) }

                            }
                            MidiButtonEvent.BUTTON_PRESED(12) {
                                name = "midi go to last slice"
                                verify { controllerState != Moving }
                                onAction { editSelection(EditSelectionChoice.Last) }
                            }
                        }
                    }
                }
            }
        ).filterNotNull()
    }

    private fun PaintBrushTool.finishPaintStroke() {
        paintClickOrDrag?.let {
            it.maskInterval?.let { interval ->
                controller.paint(interval)
            }
        }
    }

    private fun ActionSet.switchAndApplyShapeInterpolation() {
        KEY_PRESSED {
            keyMatchesBinding(shapeInterpolationTool?.keyCombinations!!, SHAPE_INTERPOLATION_APPLY_MASK)
            onAction {
                switchTool(shapeInterpolationTool!!)
                if (controller.applyMask()) {
                    paintera.baseView.changeMode(previousMode)
                }
            }
            handleException {
                paintera.baseView.changeMode(previousMode)
            }
        }
    }

    /**
     *  Additional paint brush actions for Shape Interpolation.
     *
     * @receiver the tool to add the actions to
     * @return the additional action sets
     */
    private fun PaintBrushTool.shapeInterpolationPaintBrushActions(): ActionSet {

        return painteraActionSet("Shape Interpolation Paint Brush Actions", PaintActionType.ShapeInterpolation) {
            MOUSE_PRESSED {
                name = "provide shape interpolation mask to paint brush"
                filter = true
                consume = false
                verify { activeTool == this@shapeInterpolationPaintBrushActions }
                onAction {
                    /* On click, generate a new mask, */
                    (activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
                        paintClickOrDrag!!.let { paintController ->
                            val previousMask: SourceMask? = source.currentMask
                            source.resetMasks(false)
                            paintController.provideMask(controller.getMask())
                        }
                    }
                }
            }

            MOUSE_PRESSED(MouseButton.PRIMARY) {
                name = "set mask value to label"
                filter = true
                consume = false
                verify { activeTool == this@shapeInterpolationPaintBrushActions }
                onAction {
                    paintClickOrDrag?.apply {
                        currentLabelToPaint = controller.interpolationId
                    }
                }
            }

            MOUSE_PRESSED(MouseButton.SECONDARY) {
                name = "set mask value to transparent label"
                filter = true
                consume = false
                verify { activeTool == this@shapeInterpolationPaintBrushActions }
                onAction {
                    paintClickOrDrag!!.apply {
                        currentLabelToPaint = Label.TRANSPARENT
                    }
                }
            }

            MOUSE_RELEASED {
                name = "set mask value to label from paint"
                filter = true
                consume = false
                verify { activeTool == this@shapeInterpolationPaintBrushActions }
                onAction { finishPaintStroke() }
            }
            switchAndApplyShapeInterpolation()
        }
    }

    /**
     * Additional fill actions for Shape Interpolation
     *
     * @param floodFillTool
     * @return the additional ActionSet
     *
     * */
    private fun additionalFloodFillActions(floodFillTool: Fill2DTool): ActionSet {
        return painteraActionSet("Shape Interpolation Fill 2D Actions", PaintActionType.ShapeInterpolation) {
            MOUSE_PRESSED {
                name = "provide shape interpolation mask to fill 2d"
                filter = true
                consume = false
                verify { activeTool == floodFillTool }
                onAction {
                    /* On click, provide the mask, */
                    (activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
                        fill2DTool.fill2D.let { fillController ->
                            source.resetMasks(false)
                            fillController.provideMask(controller.getMask())
                        }
                    }
                }
            }
            switchAndApplyShapeInterpolation()
        }
    }


    /**
     * Additional SAM actions for Shape Interpolation
     *
     * @param samTool
     * @return the additional ActionSet
     *
     * */
    private fun additionalSamActions(samTool: SamTool): ActionSet {
        return painteraActionSet("Shape Interpolation SAM Actions", PaintActionType.ShapeInterpolation) {
            KEY_PRESSED(KeyCode.ENTER) {
                name = "submit sam mask to shape interpolation controller"
                verify { activeTool == samTool }
                onAction {
                    samTool.lastPrediction?.let { prediction ->
                        controller.paint(prediction.maskInterval)
                    }
                    switchTool(shapeInterpolationTool)
                }
            }
            KEY_PRESSED(KeyCode.ESCAPE) {
                name = "toggle off sam tool, back to shapeinterpolation "
                filter = true
                verify { activeTool == samTool }
                onAction {
                    switchTool(shapeInterpolationTool)
                }
            }
            MOUSE_CLICKED(MouseButton.PRIMARY) {
                name = "submit sam mask to shape interpolation controller"
                verifyEventNotNull()
                verify("Control cannot be down") { it?.isControlDown == false }
                verify { activeTool == samTool }
                onAction {
                    samTool.lastPrediction?.let { prediction ->
                        controller.paint(prediction.maskInterval)
                    }
                    switchTool(shapeInterpolationTool)
                }
            }
            switchAndApplyShapeInterpolation()
        }
    }

    private fun ActionSet.keyPressEditSelectionAction(choice: EditSelectionChoice, keyName: String, keyCombinations: NamedKeyCombination.CombinationMap) =
        with(controller) {
            KEY_PRESSED(keyCombinations, keyName) {
                graphic = when (choice) {
                    EditSelectionChoice.First -> {
                        { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "interpolation-first-slice") } }
                    }

                    EditSelectionChoice.Previous -> {
                        { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "interpolation-previous-slice") } }
                    }

                    EditSelectionChoice.Next -> {
                        { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "interpolation-next-slice") } }
                    }

                    EditSelectionChoice.Last -> {
                        { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "interpolation-last-slice") } }
                    }
                }
                verify { controllerState != Moving }
                onAction { editSelection(choice) }
                handleException {
                    exitShapeInterpolation(false)
                    paintera.baseView.changeMode(previousMode)
                }
            }
        }
}


class ShapeInterpolationTool(
    private val controller: ShapeInterpolationController<*>,
    val keyCombinations: NamedKeyCombination.CombinationMap,
    private val previousMode: ControlMode,
    mode: ToolMode? = null,
    private var fill2D: Fill2DTool
) : ViewerTool(mode) {

    override val actionSets: MutableList<ActionSet> = mutableListOf(
        shapeInterpolationActions(keyCombinations)
    )

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "navigation-tool") } }
    override val name: String = "Shape Interpolation"
    override val keyTrigger = listOf(KeyCode.S)
    private var currentTask: UtilityTask<*>? = null

    override fun activate() {

        super.activate()
        /* This action set allows us to translate through the unfocused viewers */
        paintera.baseView.orthogonalViews().viewerAndTransforms()
            .filter { !it.viewer().isFocusable }
            .forEach { disabledViewerAndTransform ->
                val translateWhileDisabled = disabledViewerTranslateOnlyMap.computeIfAbsent(disabledViewerAndTransform, disabledViewerTranslateOnly)
                disabledViewerAndTransform.viewer().installActionSet(translateWhileDisabled)
            }
        /* Activate, but we want to bind it to our activeViewer bindings instead of the default. */
        NavigationTool.activate()
        NavigationTool.activeViewerProperty.unbind()
        NavigationTool.activeViewerProperty.bind(activeViewerProperty)
        NavigationTool.installInto(activeViewer!!)
    }

    override fun deactivate() {
        /* We intentionally unbound the activeViewer for this, to support the button toggle.
        * We now need to explicitly remove the NavigationTool from the activeViewer we care about.
        * Still deactive it first, to handle the rest of the cleanup */
        NavigationTool.removeFrom(activeViewer!!)
        NavigationTool.deactivate()
        disabledViewerTranslateOnlyMap.forEach { (vat, actionSet) -> vat.viewer().removeActionSet(actionSet) }
        disabledViewerTranslateOnlyMap.clear()
        super.deactivate()
    }

    override val statusProperty = SimpleStringProperty().apply {

        val statusBinding = controller.controllerStateProperty.createNullableValueBinding(controller.sliceDepthProperty) {
            controller.getStatusText()
        }
        bind(statusBinding)
    }

    private fun ShapeInterpolationController<*>.getStatusText() =
        when {
            controllerState == Interpolate -> "Interpolating..."
            numSlices == 0 -> "Select or Paint ..."
            else -> {
                val sliceIdx = sortedSliceDepths.indexOf(sliceDepthProperty.get())
                "Slice: ${if (sliceIdx == -1) "N/A" else "${sliceIdx + 1}"} / ${numSlices}"
            }
        }

    private val disabledViewerTranslateOnlyMap = mutableMapOf<OrthogonalViews.ViewerAndTransforms, DragActionSet>()

    private val disabledViewerTranslateOnly = { vat: OrthogonalViews.ViewerAndTransforms ->
        val translator = vat.run {
            val globalTransformManager = paintera.baseView.manager()
            TranslationController(globalTransformManager, displayTransform(), globalToViewerTransform())
        }
        painteraDragActionSet("disabled translate xy", NavigationActionType.Pan) {
            verify { it.isSecondaryButtonDown }
            verify { controller.controllerState != Interpolate }
            onDragDetected { translator.init() }
            onDrag { translator.translate(it.x - startX, it.y - startY) }
        }
    }

    private fun shapeInterpolationActions(keyCombinations: NamedKeyCombination.CombinationMap): ActionSet {
        return painteraActionSet("shape interpolation", PaintActionType.ShapeInterpolation) {
            with(controller) {
                verifyAll(KEY_PRESSED) { isControllerActive }
                KEY_PRESSED {
                    keyMatchesBinding(keyCombinations, SHAPE_INTERPOLATION_APPLY_MASK)
                    graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "accept", "accept-shape-interpolation") } }
                    onAction {
                        if (applyMask()) {
                            paintera.baseView.changeMode(previousMode)
                        }
                    }
                    handleException {
                        it.printStackTrace()
                        paintera.baseView.changeMode(previousMode)
                    }
                }

                KEY_PRESSED {
                    val iconClsBinding = controller.previewProperty.createNonNullValueBinding { if (it) "toggle-on" else "toggle-off" }
                    val iconCls by iconClsBinding.nonnullVal()
                    graphic = {
                        FontAwesomeIconView().also {
                            it.styleClass.addAll(iconCls, "toolbar-tool")
                            it.id = iconCls
                            iconClsBinding.addListener { _, old, new ->
                                it.styleClass.removeAll(old)
                                it.styleClass.add(new)

                            }
                        }
                    }
                    keyMatchesBinding(keyCombinations, SHAPE_INTERPOLATION_TOGGLE_PREVIEW)
                    onAction { controller.togglePreviewMode() }
                    handleException {
                        paintera.baseView.changeMode(previousMode)
                    }
                }

                listOf(KeyCode.DELETE, KeyCode.BACK_SPACE).forEach { key ->
                    KEY_PRESSED(key) {
                        name = "remove section"
                        filter = true
                        consume = false
                        verify {
                            sliceDepthProperty.get() in sortedSliceDepths
                        }
                        onAction { deleteCurrentSlice() }
                    }
                }
                MOUSE_CLICKED {
                    name = "select object in current slice"

                    verifyNoKeysDown()
                    verifyEventNotNull()
                    verify { !paintera.mouseTracker.isDragging }
                    verify { it!!.button == MouseButton.PRIMARY } // respond to primary click
                    verify { controllerState != Interpolate } // need to be in the select state
                    onAction { event ->
                        /* get value at position */
                        deleteCurrentSliceOrInterpolant()
                        currentTask = fillObjectInSlice(event!!)
                    }
                }
                MOUSE_CLICKED {
                    name = "toggle object in current slice"
                    verify { !paintera.mouseTracker.isDragging }
                    verify { controllerState != Interpolate }
                    verifyEventNotNull()
                    verify {
                        val triggerByRightClick = (it?.button == MouseButton.SECONDARY) && keyTracker!!.noKeysActive()
                        val triggerByCtrlLeftClick = (it?.button == MouseButton.PRIMARY) && keyTracker!!.areOnlyTheseKeysDown(KeyCode.CONTROL)
                        triggerByRightClick || triggerByCtrlLeftClick
                    }
                    onAction { event ->
                        currentTask = fillObjectInSlice(event!!)
                    }
                }
                KEY_PRESSED(keyCombinations, CANCEL) {
                    name = "cancel current shape interpolation tool  task"
                    filter = true
                    verify { currentTask != null }
                    onAction {
                        currentTask?.cancel()
                        currentTask = null
                    }
                }
            }
        }
    }

    private fun fillObjectInSlice(event: MouseEvent): UtilityTask<Interval> {
        with(controller) {
            source.resetMasks(false)
            val mask = getMask()

            fill2D.fill2D.provideMask(mask)
            val pointInMask = mask.displayPointToInitialMaskPoint(event.x, event.y)
            val maskLabel = mask.rai[pointInMask].get()
            fill2D.brushProperties?.brushDepth = 1.0
            fill2D.fillLabel = { if (maskLabel == interpolationId) Label.TRANSPARENT else interpolationId }
            return fill2D.executeFill2DAction(event.x, event.y) {
                paint(it)
                currentTask = null
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}


