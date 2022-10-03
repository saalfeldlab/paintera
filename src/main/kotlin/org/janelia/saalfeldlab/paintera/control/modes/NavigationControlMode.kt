package org.janelia.saalfeldlab.paintera.control.modes

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.BooleanExpression
import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.ObjectExpression
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.HPos
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.util.Duration
import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.control.VPotControl.DisplayType
import org.janelia.saalfeldlab.fx.ObservablePosition
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.extensions.LazyForeignMap
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.invoke
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiPotentiometerEvent
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.NavigationKeys
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.navigation.*
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.util.function.Consumer
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Mode which registers Navigation controls. One [Tool] for all Navigation [Action]
 *
 */
object NavigationControlMode : AbstractToolMode() {

    /**
     * Intentianally empty. [NavigationControlMode] has only one tool, which contains all the Navigation actions.
     * It will always be active when [NavigationControlMode] is the active mode.
     */
    override val modeActions = listOf<ActionSet>()

    override val allowedActions = AllowedActions.NAVIGATION

    override val defaultTool: Tool = NavigationTool

    override val tools: ObservableList<Tool> = FXCollections.observableArrayList(NavigationTool)

    override fun enter() {
        super.enter()
        switchTool(NavigationTool)
    }
}

object NavigationTool : ViewerTool() {

    private const val DEFAULT = 1.0
    private const val FAST = 10.0
    private const val SLOW = 0.1

    internal val keyAndMouseBindings = KeyAndMouseBindings(NavigationKeys.namedCombinationsCopy())

    private val keyBindings = keyAndMouseBindings.keyCombinations

    private val globalTransformManager by lazy { paintera.baseView.manager() }

    private val globalTransform = AffineTransform3D().apply { globalTransformManager.addListener { set(it) } }

    private val zoomSpeed = SimpleDoubleProperty(1.05)

    private val translationSpeed = SimpleDoubleProperty(1.0)

    private val rotationSpeed = SimpleDoubleProperty(1.0)

    val allowRotationsProperty = SimpleBooleanProperty(true)

    private val buttonRotationSpeedConfig = ButtonRotationSpeedConfig()


    override fun activate() {
        with(properties.navigationConfig) {
            allowRotationsProperty.bind(allowRotations)
            buttonRotationSpeedConfig.regular.bind(buttonRotationSpeeds.regular)
            buttonRotationSpeedConfig.slow.bind(buttonRotationSpeeds.slow)
            buttonRotationSpeedConfig.fast.bind(buttonRotationSpeeds.fast)
        }
        super.activate()
    }

    override fun deactivate() {
        super.deactivate()
        allowRotationsProperty.unbind()
        buttonRotationSpeedConfig.apply {
            regular.unbind()
            slow.unbind()
            fast.unbind()
        }
    }

    override val graphic = { FontAwesomeIconView().also { it.styleClass += listOf("toolbar-tool", "navigation-tool") } }

    override val name: String = "Navigation"
    override val keyTrigger = null /* This is typically the default, so no binding to actively switch to it. */

    val worldToSharedViewerSpace by LazyForeignMap({ activeViewerAndTransforms }) { AffineTransform3D() }
    val displayTransform by LazyForeignMap({ activeViewerAndTransforms }) { AffineTransform3D() }
    val globalToViewerTransform by LazyForeignMap({ activeViewerAndTransforms }) { AffineTransform3D() }

    val viewerTransform by LazyForeignValue({ activeViewerAndTransforms }) { viewerAndTransforms ->
        viewerAndTransforms?.run {
            AffineTransform3D().apply {
                viewer().addTransformListener { set(it) }
            }
        }
    }
    val translateXYController by LazyForeignValue({ activeViewerAndTransforms }) { viewerAndTransforms ->
        viewerAndTransforms?.run {
            TranslateWithinPlane(globalTransformManager, displayTransform(), globalToViewerTransform())
        }
    }
    val normalTranslationController by LazyForeignValue({ activeViewerAndTransforms }) { viewerAndTransforms ->
        viewerAndTransforms?.run {

            TranslateAlongNormal(translationSpeed, globalTransformManager, worldToSharedViewerSpace)
        }
    }

    val zoomController by LazyForeignValue({ activeViewerAndTransforms }) {
        Zoom(zoomSpeed, globalTransformManager, viewerTransform)
    }
    val keyRotationAxis by LazyForeignValue({ activeViewerAndTransforms }) {
        SimpleObjectProperty(KeyRotate.Axis.Z)
    }
    val resetRotationController by LazyForeignValue({ activeViewerAndTransforms }) {
        RemoveRotation(viewerTransform, globalTransform, {
            globalTransformManager.setTransform(it, Duration(300.0))
        }, globalTransformManager)
    }

    val targetPositionObservable by LazyForeignValue({ activeViewerAndTransforms }) {
        it?.viewer()?.createMousePositionOrCenterBinding()
    }

    override val actionSets by LazyForeignMap({ activeViewerAndTransforms }) { viewerAndTransforms ->
        viewerAndTransforms?.run {


            displayTransform().addListener {
                displayTransform.set(it)
                globalToViewerTransform().getTransformCopy(worldToSharedViewerSpace)
                worldToSharedViewerSpace.preConcatenate(it)
            }

            globalToViewerTransform().addListener {
                globalToViewerTransform.set(it)
                displayTransform().getTransformCopy(worldToSharedViewerSpace)
                worldToSharedViewerSpace.concatenate(it)
            }


            val actionSets = mutableListOf<ActionSet?>()
            actionSets += translateAlongNormalActions(normalTranslationController!!)
            actionSets += translateInPlaneActions(translateXYController!!)
            actionSets += zoomActions(zoomController, targetPositionObservable!!)
            actionSets += rotationActions(targetPositionObservable!!, keyRotationAxis, displayTransform, globalToViewerTransform, resetRotationController)
            actionSets += goToPositionAction(translateXYController!!)
            actionSets.filterNotNull().toMutableList()
        } ?: mutableListOf()
    }


    private fun translateAlongNormalActions(normalTranslationController: TranslateAlongNormal): List<ActionSet?> {

        fun scrollActions(normalTranslationController: TranslateAlongNormal): ActionSet {
            data class ScrollSpeedStruct(val name: String, val speed: Double, val keysInit: Action<ScrollEvent>.() -> Unit)
            return painteraActionSet("scroll translate along normal", NavigationActionType.Slice) {
                listOf(
                    ScrollSpeedStruct("default", DEFAULT) { keysDown() },
                    ScrollSpeedStruct("fast", FAST) { keysDown(KeyCode.SHIFT) },
                    ScrollSpeedStruct("slow", SLOW) { keysDown(KeyCode.CONTROL) }
                ).map { (actionName, speed, keysInit) ->
                    ScrollEvent.SCROLL {
                        name = actionName
                        onAction { normalTranslationController.translate(-ControlUtils.getBiggestScroll(it), speed) }
                        this.keysInit()
                    }
                }
            }
        }

        fun keyActions(translateAlongNormal: TranslateAlongNormal): ActionSet {
            data class TranslateNormalStruct(val step: Double, val speed: Double, val keyName: String)
            return painteraActionSet("key translate along normal", NavigationActionType.Slice) {
                listOf(
                    TranslateNormalStruct(1.0, DEFAULT, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD),
                    TranslateNormalStruct(1.0, FAST, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST),
                    TranslateNormalStruct(1.0, SLOW, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW),
                    TranslateNormalStruct(-1.0, DEFAULT, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD),
                    TranslateNormalStruct(-1.0, FAST, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST),
                    TranslateNormalStruct(-1.0, SLOW, NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW)
                ).map { (step, speed, keyName) ->
                    KEY_PRESSED {
                        keyMatchesBinding(keyBindings, keyName)
                        onAction { translateAlongNormal.translate(step, speed) }
                    }
                }
            }
        }



        return listOf(
            scrollActions(normalTranslationController),
            keyActions(normalTranslationController),
            midiSliceActions()
        )
    }

    fun midiSliceActions() =
        activeViewer?.let { target ->
            DeviceManager.xTouchMini?.let { device ->
                normalTranslationController?.let { translator ->
                    painteraMidiActionSet("midi translate along normal", device, target, NavigationActionType.Slice) {
                        MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(2) {
                            name = "midi_normal"
                            setDisplayType(DisplayType.TRIM)
                            verifyEventNotNull()
                            onAction {
                                InvokeOnJavaFXApplicationThread { translator.translate(it!!.value.sign * 40.0, FAST) }
                            }
                        }
                    }
                }
            }
        }

    private fun translateInPlaneActions(translateXYController: TranslateWithinPlane): List<ActionSet?> {

        fun dragAction(translateXYController: TranslateWithinPlane) =
            painteraDragActionSet("drag translate xy", NavigationActionType.Pan) {
                verify { it.isSecondaryButtonDown }
                onDragDetected { translateXYController.init() }
                onDrag { translateXYController.translate(it.x - startX, it.y - startY) }
            }

        return listOf(
            dragAction(translateXYController),
            midiPanActions(),
        )

    }

    fun midiPanActions() = activeViewer?.let { target ->
        DeviceManager.xTouchMini?.let { device ->
            translateXYController?.let { translator ->
                painteraMidiActionSet("midi translate xy", device, target, NavigationActionType.Pan) {
                    MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(0) {
                        name = "midi translate x"
                        setDisplayType(DisplayType.TRIM)
                        verifyEventNotNull()
                        onAction {
                            InvokeOnJavaFXApplicationThread {
                                translator.init()
                                translator.translate(it!!.value.toDouble(), 0.0)
                            }
                        }
                    }
                    MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(1) {
                        name = "midi translate y"
                        setDisplayType(DisplayType.TRIM)
                        verifyEventNotNull()
                        onAction {
                            InvokeOnJavaFXApplicationThread {
                                translator.init()
                                translator.translate(0.0, it!!.value.toDouble())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun zoomActions(zoomController: Zoom, targetPositionObservable: ObservablePosition): List<ActionSet?> {


        fun scrollActions(zoomController: Zoom): ActionSet {
            return painteraActionSet("zoom", NavigationActionType.Zoom) {
                listOf(
                    arrayOf(KeyCode.META),
                    arrayOf(KeyCode.CONTROL, KeyCode.SHIFT)
                ).map { keys ->
                    ScrollEvent.SCROLL {
                        verifyEventNotNull()
                        keysDown(*keys)
                        onAction { zoomController.zoomCenteredAt(-ControlUtils.getBiggestScroll(it!!), it.x, it.y) }
                    }
                }
            }
        }

        fun keyActions(zoomController: Zoom, targetPositionObservable: ObservablePosition): ActionSet {
            return painteraActionSet("zoom", NavigationActionType.Zoom) {
                listOf(
                    1.0 to NavigationKeys.BUTTON_ZOOM_OUT,
                    1.0 to NavigationKeys.BUTTON_ZOOM_OUT2,
                    -1.0 to NavigationKeys.BUTTON_ZOOM_IN,
                    -1.0 to NavigationKeys.BUTTON_ZOOM_IN2
                ).map { (delta, key) ->
                    KEY_PRESSED {
                        onAction { zoomController.zoomCenteredAt(delta, targetPositionObservable.x, targetPositionObservable.y) }
                        keyMatchesBinding(keyBindings, key)
                    }
                }
            }
        }

        return listOf(
            scrollActions(zoomController),
            midiZoomActions(),
            keyActions(zoomController, targetPositionObservable)
        )
    }


    fun midiZoomActions() = activeViewer?.let { target ->
        DeviceManager.xTouchMini?.let { device ->
            painteraMidiActionSet("zoom", device, target, NavigationActionType.Zoom) {
                MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(3) {
                    name = "midi_zoom"
                    setDisplayType(DisplayType.TRIM)
                    verifyEventNotNull()
                    onAction {
                        InvokeOnJavaFXApplicationThread { zoomController.zoomCenteredAt(-it!!.value.toDouble(), target.width / 2.0, target.height / 2.0) }
                    }
                }
            }
        }
    }

    private fun rotationActions(
        targetPositionObservable: ObservablePosition,
        keyRotationAxis: SimpleObjectProperty<KeyRotate.Axis>,
        displayTransform: AffineTransform3D,
        globalToViewerTransform: AffineTransform3D,
        resetRotationController: RemoveRotation
    ): List<ActionSet?> {

        val removeRotationActions = let {
            val removeRotation = { resetRotationController.removeRotationCenteredAt(targetPositionObservable.x, targetPositionObservable.y) }
            listOf(
                painteraActionSet(NavigationKeys.REMOVE_ROTATION, NavigationActionType.Rotate) {
                    KEY_PRESSED {
                        keyMatchesBinding(keyBindings, NavigationKeys.REMOVE_ROTATION)
                        onAction { removeRotation() }
                    }
                },
                midiResetRotationAction()
            )
        }

        val setRotationAxis = painteraActionSet("set rotation axis", NavigationActionType.Rotate) {
            arrayOf(
                KeyRotate.Axis.X to NavigationKeys.SET_ROTATION_AXIS_X,
                KeyRotate.Axis.Y to NavigationKeys.SET_ROTATION_AXIS_Y,
                KeyRotate.Axis.Z to NavigationKeys.SET_ROTATION_AXIS_Z
            ).map { (axis, key) ->
                KEY_PRESSED {
                    onAction { keyRotationAxis.set(axis) }
                    keyMatchesBinding(keyBindings, key)
                }
            }
        }

        fun newDragRotationAction(name: String, speed: Double, keyDown: KeyCode? = null) =
            baseRotationAction(
                name,
                allowRotationsProperty,
                rotationSpeed.multiply(speed),
                displayTransform,
                globalToViewerTransform,
                { globalTransformManager.transform = it },
                globalTransformManager
            ).apply {
                keyDown?.let {
                    dragDetectedAction.keysDown(keyDown)
                    dragAction.keysDown(keyDown)
                } ?: let {
                    dragDetectedAction.verifyNoKeysDown()
                    dragAction.verifyNoKeysDown()
                }
            }

        val mouseRotation = newDragRotationAction("rotate", DEFAULT)
        val fastMouseRotation = newDragRotationAction("rotate fast", FAST, KeyCode.SHIFT)
        val slowMouseRotation = newDragRotationAction("rotate slow", SLOW, KeyCode.CONTROL)

        val rotationKeyActions = painteraActionSet("rotate", NavigationActionType.Rotate) {
            mapOf(
                buttonRotationSpeedConfig.regular.multiply(-1) to NavigationKeys.KEY_ROTATE_LEFT,
                buttonRotationSpeedConfig.regular to NavigationKeys.KEY_ROTATE_RIGHT,

                buttonRotationSpeedConfig.fast.multiply(-1) to NavigationKeys.KEY_ROTATE_LEFT_FAST,
                buttonRotationSpeedConfig.fast to NavigationKeys.KEY_ROTATE_RIGHT_FAST,

                buttonRotationSpeedConfig.slow.multiply(-1) to NavigationKeys.KEY_ROTATE_LEFT_SLOW,
                buttonRotationSpeedConfig.slow to NavigationKeys.KEY_ROTATE_RIGHT_SLOW,
            ).forEach { (speed, key) ->
                addKeyRotationHandler(
                    key, keyBindings,
                    targetPositionObservable,
                    allowRotationsProperty,
                    keyRotationAxis,
                    speed.multiply(Math.PI / 180.0),
                    displayTransform,
                    globalToViewerTransform,
                    globalTransform,
                    { globalTransformManager.transform = it },
                    globalTransformManager
                )
            }
        }


        val rotationActions = mutableListOf<ActionSet?>()

        rotationActions += removeRotationActions
        rotationActions += setRotationAxis
        rotationActions += mouseRotation
        rotationActions += fastMouseRotation
        rotationActions += slowMouseRotation
        rotationActions += rotationKeyActions
        midiRotationActions()?.let { rotationActions += it }

        return rotationActions.filterNotNull()
    }

    private fun midiResetRotationAction() : MidiActionSet? {
        return activeViewer?.let { target ->
            DeviceManager.xTouchMini?.let { device ->
                targetPositionObservable?.let { targetPos ->
                    painteraMidiActionSet(NavigationKeys.REMOVE_ROTATION, device, target, NavigationActionType.Rotate) {
                        MidiButtonEvent.BUTTON_PRESED(18) {
                            name = "midi_remove_rotation"
                            verifyEventNotNull()
                            onAction { InvokeOnJavaFXApplicationThread { resetRotationController.removeRotationCenteredAt(targetPos.x, targetPos.y) } }
                        }
                    }
                }
            }
        }
    }

    fun midiRotationActions() = activeViewer?.let { target ->
        DeviceManager.xTouchMini?.let { device ->
            targetPositionObservable?.let { targetPosition ->
                val submitTransform: (AffineTransform3D) -> Unit = { t -> globalTransformManager.transform = t }
                val step = SimpleDoubleProperty(5 * Math.PI / 180.0)
                val rotate = KeyRotate(keyRotationAxis, step, displayTransform, globalToViewerTransform, globalTransform, submitTransform, globalTransform)

                data class MidiRotationStruct(val handle: Int, val axis: KeyRotate.Axis)
                listOf(
                    MidiRotationStruct(5, KeyRotate.Axis.X),
                    MidiRotationStruct(6, KeyRotate.Axis.Y),
                    MidiRotationStruct(7, KeyRotate.Axis.Z),
                ).map { (handle, axis) ->
                    painteraMidiActionSet("rotate", device, target, NavigationActionType.Rotate) {
                        MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(handle) {
                            name = "midi_rotate_${axis.name.lowercase()}"
                            setDisplayType(DisplayType.TRIM)
                            verifyEventNotNull()
                            verify { allowRotationsProperty() }
                            onAction {
                                keyRotationAxis.set(axis)
                                step.set(step.value.absoluteValue * it!!.value.sign)
                                InvokeOnJavaFXApplicationThread { rotate.rotate(targetPosition.x, targetPosition.y) }
                            }
                        }
                    }
                }
            }

        }
    }

    fun midiNavigationActions() = mutableListOf<MidiActionSet>().also {
        midiPanActions()?.let { midiActions -> it.add(midiActions) }
        midiSliceActions()?.let { midiActions -> it.add(midiActions) }
        midiZoomActions()?.let { midiActions -> it.add(midiActions) }
        midiRotationActions()?.let { midiActions -> it.addAll(midiActions) }
        midiResetRotationAction()?.let { midiActions -> it.add(midiActions) }
    }


    private fun goToPositionAction(translateXYController: TranslateWithinPlane) =
        painteraActionSet("center on position", NavigationActionType.Pan) {
            KEY_PRESSED(KeyCode.CONTROL, KeyCode.G) {
                verify { paintera.baseView.sourceInfo().currentSourceProperty().get() != null }
                verify { paintera.baseView.sourceInfo().currentState().get() != null }
                onAction {
                    activeViewer?.let { viewer ->
                        val source = paintera.baseView.sourceInfo().currentSourceProperty().get()!!
                        val sourceToGlobalTransform = AffineTransform3D().also { source.getSourceTransform(viewer.state.timepoint, 0, it) }
                        val currentSourceCoordinate = RealPoint(3).also { viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, it) }

                        val positionField = SpatialField.longField(0, { true }, Region.USE_COMPUTED_SIZE, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
                            x.value = currentSourceCoordinate.getDoublePosition(0)
                            y.value = currentSourceCoordinate.getDoublePosition(1)
                            z.value = currentSourceCoordinate.getDoublePosition(2)
                        }
                        PainteraAlerts.confirmation("Go", "Cancel", true, paintera.pane.scene.window).apply {
                            dialogPane.headerText = "Center On Position?"
                            dialogPane.content = GridPane().apply {
                                mapOf("x" to 1, "y" to 2, "z" to 3).forEach { (axis, col) ->
                                    val label = Label(axis)
                                    add(label, col, 0)
                                    GridPane.setHalignment(label, HPos.CENTER)
                                    GridPane.setHgrow(label, Priority.ALWAYS)
                                }
                                add(Label("Position: "), 0, 1)

                                add(positionField.node, 1, 1, 3, 1)
                            }
                        }.showAndWait().takeIf { it.nullable == ButtonType.OK }?.let {
                            positionField.apply {
                                val sourceDeltaX = x.value.toDouble() - currentSourceCoordinate.getDoublePosition(0).toLong().toDouble()
                                val sourceDeltaY = y.value.toDouble() - currentSourceCoordinate.getDoublePosition(1).toLong().toDouble()
                                val sourceDeltaZ = z.value.toDouble() - currentSourceCoordinate.getDoublePosition(2).toLong().toDouble()

                                val viewerCenterInSource = RealPoint(3)
                                viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, viewerCenterInSource)

                                val newViewerCenter = RealPoint(3)
                                viewer.sourceToDisplayCoordinates(
                                    viewerCenterInSource.getDoublePosition(0) + sourceDeltaX,
                                    viewerCenterInSource.getDoublePosition(1) + sourceDeltaY,
                                    viewerCenterInSource.getDoublePosition(2) + sourceDeltaZ,
                                    sourceToGlobalTransform,
                                    newViewerCenter
                                )


                                val deltaX = viewer.width / 2.0 - newViewerCenter.getDoublePosition(0)
                                val deltaY = viewer.height / 2.0 - newViewerCenter.getDoublePosition(1)
                                val deltaZ = 0 - newViewerCenter.getDoublePosition(2)

                                translateXYController.init()
                                translateXYController.translate(deltaX, deltaY, deltaZ, Duration(300.0))
                            }
                        }
                    }
                }
            }
        }

    private fun baseRotationAction(
        name: String,
        allowRotations: BooleanExpression,
        speed: DoubleExpression,
        displayTransform: AffineTransform3D,
        globalToViewerTransform: AffineTransform3D,
        submitTransform: Consumer<AffineTransform3D>,
        lock: Any
    ): DragActionSet {
        val rotate = Rotate(speed, globalTransform, displayTransform, globalToViewerTransform, submitTransform, lock)

        return painteraDragActionSet(name, NavigationActionType.Rotate) {
            verify { it.isPrimaryButtonDown }
            dragDetectedAction.verify { allowRotations() }
            onDragDetected { rotate.initialize() }
            onDrag { rotate.rotate(it.x, it.y, startX, startY) }
        }
    }

    private fun ActionSet.addKeyRotationHandler(
        name: String,
        keyBindings: NamedKeyCombination.CombinationMap,
        targetPositionObservable: ObservablePosition,
        allowRotations: BooleanExpression,
        axis: ObjectExpression<KeyRotate.Axis>,
        step: DoubleExpression,
        displayTransform: AffineTransform3D,
        globalToViewerTransform: AffineTransform3D,
        globalTransform: AffineTransform3D,
        submitTransform: Consumer<AffineTransform3D>,
        lock: Any
    ) {
        val rotate = KeyRotate(axis, step, displayTransform, globalToViewerTransform, globalTransform, submitTransform, lock)

        KEY_PRESSED {
            verify { allowRotations() }
            onAction { rotate.rotate(targetPositionObservable.x, targetPositionObservable.y) }
            keyMatchesBinding(keyBindings, name)
        }
    }
}



