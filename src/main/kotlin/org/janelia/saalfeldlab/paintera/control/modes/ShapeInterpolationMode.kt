package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.*
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.NamedKeyCombination
import org.janelia.saalfeldlab.fx.actions.PainteraActionSet
import org.janelia.saalfeldlab.fx.actions.PainteraDragActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.labels.Label
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
import org.janelia.saalfeldlab.paintera.control.navigation.TranslateWithinPlane
import org.janelia.saalfeldlab.paintera.control.paint.PaintClickOrDragController
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintBrushTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class ShapeInterpolationMode<D : IntegerType<D>>(val controller: ShapeInterpolationController<D>, val previousMode: ControlMode) : AbstractToolMode() {

    private inner class ShapeIntepolationToolProperty : SimpleObjectProperty<ShapeInterpolationTool?>() {

        private val keyAndMouseBindingsProperty = activeSourceStateProperty.createNullableValueBinding {
            it?.let {
                paintera.baseView.keyAndMouseBindings.getConfigFor(it)
            }
        }

        init {
            bind(keyAndMouseBindingsProperty.createNullableValueBinding {
                it?.let {
                    ShapeInterpolationTool(controller, it.keyCombinations, previousMode)
                }
            })
        }
    }

    private val shapeInterpolationToolProperty = ShapeIntepolationToolProperty()

    internal val paintBrushTool = PaintBrushTool(activeSourceStateProperty)

    override val modeActions by lazy { modeActions() }

    override val allowedActions = AllowedActions.AllowedActionsBuilder()
        .add(PaintActionType.ShapeInterpolation, PaintActionType.Paint, PaintActionType.Erase, PaintActionType.SetBrushSize)
        .add(MenuActionType.ToggleMaximizeViewer)
        .add(NavigationActionType.Pan, NavigationActionType.Slice, NavigationActionType.Zoom)
        .create()

    private val toolTriggerListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
        new?.viewer()?.apply { modeActions.forEach { installActionSet(it) } }
        old?.viewer()?.apply { modeActions.forEach { removeActionSet(it) } }
    }

    override fun enter() {
        activeViewerProperty.addListener(toolTriggerListener)
        paintera.baseView.disabledPropertyBindings[controller] = Bindings.createBooleanBinding({ controller.isBusy }, controller.isBusyProperty)
        super.enter()
        /* Try to initialize the tool, if state is valid. If not, change back to previous mode. */
        activeViewerProperty.get()?.viewer()?.let {
            shapeInterpolationToolProperty.get()?.let { shapeInterpolationTool ->
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
        return FXCollections.observableArrayList(
            PainteraActionSet("paint during shape interpolation", PaintActionType.Paint) {
                KEY_PRESSED(KeyCode.SPACE) {
                    name = "switch to paint tool"
                    verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
                    verify { activeTool !is PaintBrushTool }
                    onAction {
                        /* Don't allow painting with depth during shape interpolation */
                        paintBrushTool.brushProperties?.brushDepth = 1.0
                        switchTool(paintBrushTool)
                    }
                }

                MOUSE_PRESSED {
                    name = "provide shape interpolation mask to paint brush"
                    filter = true
                    consume = false
                    verify { activeTool is PaintBrushTool }
                    onAction {
                        /* On click, generate a new mask, */
                        (activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
                            paintBrushTool.paintClickOrDrag!!.let { paintController ->
                                source.resetMasks(false)
                                controller.currentViewerMask = controller.getMask()
                                paintController.provideMask(controller.currentViewerMask!!)
                            }
                        }
                    }
                }

                MOUSE_PRESSED(MouseButton.PRIMARY) {
                    name = "set mask value to label"
                    filter = true
                    consume = false
                    verify { activeTool is PaintBrushTool }
                    onAction {
                        paintBrushTool.paintClickOrDrag?.apply {
                            paintBrushTool.paintClickOrDrag!!.fillLabelProperty.apply {
                                resetFillLabel()
                                bindFillLabel()
                            }
                        }
                    }
                }

                MOUSE_PRESSED(MouseButton.SECONDARY) {
                    name = "set mask value to label"
                    filter = true
                    consume = false
                    verify { activeTool is PaintBrushTool }
                    onAction {
                        paintBrushTool.paintClickOrDrag!!.apply {
                            removeFillLabelBinding()
                            setFillLabel(Label.TRANSPARENT)
                        }
                    }
                }

                MOUSE_RELEASED {
                    name = "set mask value to label"
                    filter = true
                    consume = false
                    verify { activeTool is PaintBrushTool }
                    onAction { finishPaintStroke() }
                }

                KEY_RELEASED {
                    name = "switch back to shape interpolation tool"
                    filter = true
                    keysReleased(KeyCode.SPACE)
                    verify { activeTool is PaintBrushTool }
                    onAction {
                        paintBrushTool.apply {
                            paintClickOrDrag?.let {
                                if (it.isPainting()) {
                                    finishPaintStroke()
                                }
                                it.release()
                            }
                        }
                        switchTool(shapeInterpolationToolProperty.get())
                    }
                }
            }
        )
    }

    private fun finishPaintStroke() {
        paintBrushTool.paintClickOrDrag?.let {
            it.viewerInterval?.let { interval ->
                it.removeFillLabelBinding()
                controller.paint(interval)
            }
        }
    }

    private fun PaintClickOrDragController.bindFillLabel() {
        fillLabelProperty.bindBidirectional(this@ShapeInterpolationMode.controller.currentFillValuePropery)
    }

    private fun PaintClickOrDragController.removeFillLabelBinding() {
        fillLabelProperty.unbindBidirectional(controller.currentFillValuePropery)
    }
}


class ShapeInterpolationTool(val controller: ShapeInterpolationController<*>, keyCombinations: NamedKeyCombination.CombinationMap, val previousMode: ControlMode) : ViewerTool() {

    private val baseView = paintera.baseView

    override val actionSets: List<ActionSet> = listOf(
        shapeInterpolationActions(keyCombinations)
    )

    override fun activate() {
        super.activate()
        /* This action set allows us to translate through the unfocused viewers */
        paintera.baseView.orthogonalViews().viewerAndTransforms()
            .filter { !it.viewer().isFocusable }
            .forEach { disabledViewerAndTransform ->
                val translateWhileDisabled = disabledViewerTranslateOnlyMap.computeIfAbsent(disabledViewerAndTransform, disabledViewerTranslateOnly)
                disabledViewerAndTransform.viewer().installActionSet(translateWhileDisabled)
            }
        NavigationTool.activate()
    }

    override fun deactivate() {
        NavigationTool.deactivate()
        disabledViewerTranslateOnlyMap.forEach { (vat, actionSet) -> vat.viewer().removeActionSet(actionSet) }
        disabledViewerTranslateOnlyMap.clear()
        super.deactivate()
    }

    override val statusProperty = SimpleStringProperty().apply {

        val statusBinding = controller.controllerStateProperty.createNullableValueBinding(controller.sectionDepthProperty) {
            controller.getStatusText()
        }
        bind(statusBinding)
    }

    private fun ShapeInterpolationController<*>.getStatusText() =
        when {
            controllerState == Interpolate -> "Interpolating..."
            numSections == 0 -> "Select or Paint ..."
            else -> {
                val sectionIdx = sortedSectionDepths.indexOf(sectionDepthProperty.get())
                "Section: ${if (sectionIdx == -1) "N/A" else "${sectionIdx + 1}"} / ${numSections}"
            }
        }

    val disabledViewerTranslateOnlyMap = mutableMapOf<OrthogonalViews.ViewerAndTransforms, PainteraDragActionSet>()

    private val disabledViewerTranslateOnly = { vat: OrthogonalViews.ViewerAndTransforms ->
        val translator = vat.run {
            val globalTransformManager = paintera.baseView.manager()
            TranslateWithinPlane(globalTransformManager, displayTransform(), globalToViewerTransform())
        }
        PainteraDragActionSet(NavigationActionType.Pan, "disabled translate xy") {
            verify { it.isSecondaryButtonDown }
            verify { controller.controllerState != Interpolate }
            onDragDetected { translator.init() }
            onDrag { translator.translate(it.x - startX, it.y - startY) }
        }
    }

    private fun shapeInterpolationActions(keyCombinations: NamedKeyCombination.CombinationMap): ActionSet {
        return PainteraActionSet("shape interpolation", PaintActionType.ShapeInterpolation) {
            with(controller) {
                verifyAll(KEY_PRESSED) { isControllerActive }
                KEY_PRESSED {
                    keyMatchesBinding(keyCombinations, EXIT_SHAPE_INTERPOLATION_MODE)
                    onAction {
                        exitShapeInterpolation(false)
                        baseView.changeMode(previousMode)
                    }
                }
                KEY_PRESSED {
                    keyMatchesBinding(keyCombinations, SHAPE_INTERPOLATION_APPLY_MASK)
                    onAction {
                        if (applyMask()) {
                            baseView.changeMode(previousMode)
                        }
                    }
                    handleException {
                        baseView.changeMode(previousMode)
                    }
                }

                KEY_PRESSED {
                    keyMatchesBinding(keyCombinations, SHAPE_INTERPOLATION_TOGGLE_PREVIEW)
                    onAction { controller.togglePreviewMode() }
                    handleException {
                        baseView.changeMode(previousMode)
                    }
                }

                listOf(KeyCode.DELETE, KeyCode.BACK_SPACE).forEach { key ->
                    KEY_PRESSED(key) {
                        name = "remove section"
                        filter = true
                        consume = false
                        verify {
                            sectionDepthProperty.get() in sortedSectionDepths
                        }
                        onAction { deleteCurrentSection() }
                    }
                }

                keyPressEditSelectionAction(EditSelectionChoice.First, SHAPE_INTERPOLATION_EDIT_FIRST_SELECTION, keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Last, SHAPE_INTERPOLATION_EDIT_LAST_SELECTION, keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Previous, SHAPE_INTERPOLATION_EDIT_PREVIOUS_SELECTION, keyCombinations)
                keyPressEditSelectionAction(EditSelectionChoice.Next, SHAPE_INTERPOLATION_EDIT_NEXT_SELECTION, keyCombinations)
                MOUSE_CLICKED {
                    name = "select object in current section"

                    verifyNoKeysDown()
                    verify { !paintera.mouseTracker.isDragging }
                    verify { it.button == MouseButton.PRIMARY } // respond to primary click
                    verify { controllerState != Interpolate } // need to be in the select state
                    onAction {
                        source.resetMasks(false)
                        controller.currentViewerMask = controller.getMask()
                        selectObject(it.x, it.y, true)
                    }
                }
                MOUSE_CLICKED {
                    name = "toggle object in current section"
                    verify { !paintera.mouseTracker.isDragging }
                    verify { controllerState != Interpolate }
                    verify {
                        val triggerByRightClick = (it.button == MouseButton.SECONDARY) && keyTracker!!.noKeysActive()
                        val triggerByCtrlLeftClick = (it.button == MouseButton.PRIMARY) && keyTracker!!.areOnlyTheseKeysDown(KeyCode.CONTROL)
                        triggerByRightClick || triggerByCtrlLeftClick
                    }
                    onAction {
                        source.resetMasks(false)
                        controller.currentViewerMask = controller.getMask()
                        selectObject(it.x, it.y, false)
                    }
                }
            }
        }
    }

    private fun ActionSet.keyPressEditSelectionAction(choice: EditSelectionChoice, keyName: String, keyCombinations: NamedKeyCombination.CombinationMap) = with(controller) {
        KEY_PRESSED {
            keyMatchesBinding(keyCombinations, keyName)
            verify { controllerState != Moving }
            onAction { editSelection(choice) }
            handleException {
                exitShapeInterpolation(false)
                baseView.changeMode(previousMode)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}
