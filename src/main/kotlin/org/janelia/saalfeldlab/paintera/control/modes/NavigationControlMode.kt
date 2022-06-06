package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.binding.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.HPos
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.actions.*
import org.janelia.saalfeldlab.fx.extensions.LazyForeignMap
import org.janelia.saalfeldlab.fx.extensions.UtilityExtensions.Companion.nullable
import org.janelia.saalfeldlab.fx.extensions.invoke
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
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

    override val actionSets by LazyForeignMap({ activeViewerAndTransforms }) { viewerAndTransforms ->
        viewerAndTransforms?.run {
            val viewerTransform = AffineTransform3D().apply {
                viewer().addTransformListener { set(it) }
            }

            val mouseXProperty = viewer().mouseXProperty
            val mouseYProperty = viewer().mouseYProperty
            val isInsideProp = viewer().isMouseInsideProperty

            val mouseX by mouseXProperty.nonnullVal()
            val mouseY by mouseYProperty.nonnullVal()
            val isInside by isInsideProp.nonnullVal()

            val mouseXIfInsideElseCenterX = Bindings.createDoubleBinding(
                { if (isInside) mouseX else viewer().width / 2 },
                isInsideProp,
                mouseXProperty
            )

            val mouseYIfInsideElseCenterY = Bindings.createDoubleBinding(
                { if (isInside) mouseY else viewer().height / 2 },
                isInsideProp,
                mouseYProperty
            )


            val worldToSharedViewerSpace = AffineTransform3D()
            val displayTransform = AffineTransform3D()
            val globalToViewerTransform = AffineTransform3D()



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


            val translateXYController = TranslateWithinPlane(globalTransformManager, displayTransform(), globalToViewerTransform())
            val normalTranslationController = TranslateAlongNormal(translationSpeed, globalTransformManager, worldToSharedViewerSpace)
            val zoomController = Zoom(zoomSpeed, globalTransformManager, viewerTransform)
            val keyRotationAxis = SimpleObjectProperty(KeyRotate.Axis.Z)
            val resetRotationController = RemoveRotation(viewerTransform, globalTransform, { globalTransformManager.setTransform(it) }, globalTransformManager)


            arrayListOf(
                getTranslateAlongNormalScrollActions(normalTranslationController),
                getTranslateAlongNormalKeyActions(normalTranslationController),
                getTranslateInPlaneDragAction(translateXYController),
                getZoomScrollActions(zoomController),
                getZoomKeyActions(zoomController, mouseXIfInsideElseCenterX, mouseYIfInsideElseCenterY),
                getRotationMouseAction(displayTransform, globalToViewerTransform),
                getFastRotationMouseAction(displayTransform, globalToViewerTransform),
                getSlowRotationMouseAction(displayTransform, globalToViewerTransform),
                getSetRotationAxisActions(keyRotationAxis),
                getRotationKeyActions(mouseXIfInsideElseCenterX, mouseYIfInsideElseCenterY, keyRotationAxis, displayTransform, globalToViewerTransform),
                getRemoveRotationAction(resetRotationController, mouseXIfInsideElseCenterX, mouseYIfInsideElseCenterY),
                getCenterOnPositionAction(translateXYController)
            )
        } ?: arrayListOf()
    }

    private fun getTranslateAlongNormalScrollActions(normalTranslationController: TranslateAlongNormal): ActionSet {
        data class ScrollSpeedStruct(val name: String, val speed: Double, val keysInit: Action<ScrollEvent>.() -> Unit)
        return PainteraActionSet("translate along normal", NavigationActionType.Slice) {
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

    private fun getRemoveRotationAction(removeRotationController: RemoveRotation, mouseXIfInsideElseCenterX: DoubleBinding, mouseYIfInsideElseCenterY: DoubleBinding): ActionSet {
        return PainteraActionSet(NavigationKeys.REMOVE_ROTATION, NavigationActionType.Rotate) {
            KEY_PRESSED {
                keyMatchesBinding(keyBindings, NavigationKeys.REMOVE_ROTATION)
                onAction { removeRotationController.removeRotationCenteredAt(mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) }
            }
        }
    }

    private fun getTranslateAlongNormalKeyActions(translateAlongNormal: TranslateAlongNormal): ActionSet {
        data class TranslateNormalStruct(val step: Double, val speed: Double, val keyName: String)
        return PainteraActionSet("translate along normal", NavigationActionType.Slice) {
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

    private fun getTranslateInPlaneDragAction(translateXYController: TranslateWithinPlane) =
        PainteraDragActionSet(NavigationActionType.Pan, "translate xy") {
            verify { it.isSecondaryButtonDown }
            onDragDetected { translateXYController.init() }
            onDrag { translateXYController.translate(it.x - startX, it.y - startY) }
        }

    private fun getZoomScrollActions(zoomController: Zoom): ActionSet {
        return PainteraActionSet("zoom", NavigationActionType.Zoom) {
            listOf(
                arrayOf(KeyCode.META),
                arrayOf(KeyCode.CONTROL, KeyCode.SHIFT)
            ).map { keys ->
                ScrollEvent.SCROLL {
                    keysDown(*keys)
                    onAction { zoomController.zoomCenteredAt(-ControlUtils.getBiggestScroll(it), it.x, it.y) }
                }
            }
        }
    }


    private fun getZoomKeyActions(zoomController: Zoom, mouseXIfInsideElseCenterX: DoubleBinding, mouseYIfInsideElseCenterY: DoubleBinding): ActionSet {
        return PainteraActionSet("zoom", NavigationActionType.Zoom) {
            listOf(
                1.0 to NavigationKeys.BUTTON_ZOOM_OUT,
                1.0 to NavigationKeys.BUTTON_ZOOM_OUT2,
                -1.0 to NavigationKeys.BUTTON_ZOOM_IN,
                -1.0 to NavigationKeys.BUTTON_ZOOM_IN2
            ).map { (delta, key) ->
                KEY_PRESSED {
                    onAction { zoomController.zoomCenteredAt(delta, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) }
                    keyMatchesBinding(keyBindings, key)
                }
            }
        }

    }

    private fun getRotationMouseAction(displayTransform: AffineTransform3D, globalToViewerTransform: AffineTransform3D) =
        baseRotationAction(
            "rotate",
            allowRotationsProperty,
            rotationSpeed.multiply(DEFAULT),
            globalTransform,
            displayTransform,
            globalToViewerTransform,
            { globalTransformManager.setTransform(it) },
            globalTransformManager
        ).apply {
            dragDetectedAction.verifyNoKeysDown()
            dragAction.verifyNoKeysDown()
        }

    private fun getFastRotationMouseAction(displayTransform: AffineTransform3D, globalToViewerTransform: AffineTransform3D) =
        baseRotationAction(
            "rotate fast",
            allowRotationsProperty,
            rotationSpeed.multiply(FAST),
            globalTransform,
            displayTransform,
            globalToViewerTransform,
            { globalTransformManager.setTransform(it) },
            globalTransformManager
        ).apply {
            dragDetectedAction.keysDown(KeyCode.SHIFT)
            dragAction.keysDown(KeyCode.SHIFT)
        }

    private fun getSlowRotationMouseAction(displayTransform: AffineTransform3D, globalToViewerTransform: AffineTransform3D) =
        baseRotationAction(
            "rotate slow",
            allowRotationsProperty,
            rotationSpeed.multiply(SLOW),
            globalTransform,
            displayTransform,
            globalToViewerTransform,
            { globalTransformManager.setTransform(it) },
            globalTransformManager
        ).apply {
            dragDetectedAction.keysDown(KeyCode.CONTROL)
            dragAction.keysDown(KeyCode.CONTROL)
        }

    private fun getRotationKeyActions(
        mouseXIfInsideElseCenterX: DoubleBinding,
        mouseYIfInsideElseCenterY: DoubleBinding,
        keyRotationAxis: SimpleObjectProperty<KeyRotate.Axis>,
        displayTransform: AffineTransform3D,
        globalToViewerTransform: AffineTransform3D
    ): ActionSet {
        return PainteraActionSet("rotate", NavigationActionType.Rotate) {
            arrayOf(
                buttonRotationSpeedConfig.regular to mapOf(
                    -1 to NavigationKeys.KEY_ROTATE_LEFT,
                    1 to NavigationKeys.KEY_ROTATE_RIGHT,
                ),
                buttonRotationSpeedConfig.fast to mapOf(
                    -1 to NavigationKeys.KEY_ROTATE_LEFT_FAST,
                    1 to NavigationKeys.KEY_ROTATE_RIGHT_FAST,
                ),
                buttonRotationSpeedConfig.slow to mapOf(
                    -1 to NavigationKeys.KEY_ROTATE_LEFT_SLOW,
                    1 to NavigationKeys.KEY_ROTATE_RIGHT_SLOW,
                ),
            ).forEach { (speed, dirKeyMap) ->
                dirKeyMap.forEach { (direction, key) ->
                    addKeyRotationHandler(
                        key, keyBindings,
                        mouseXIfInsideElseCenterX,
                        mouseYIfInsideElseCenterY,
                        allowRotationsProperty,
                        keyRotationAxis,
                        speed.multiply(direction * Math.PI / 180.0),
                        displayTransform,
                        globalToViewerTransform,
                        globalTransform,
                        { globalTransformManager.setTransform(it) },
                        globalTransformManager
                    )
                }
            }
        }
    }


    private fun getSetRotationAxisActions(keyRotationAxis: SimpleObjectProperty<KeyRotate.Axis>) =
        PainteraActionSet("set rotation axis", NavigationActionType.Rotate) {
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

    private fun getCenterOnPositionAction(translateXYController: TranslateWithinPlane) =
        PainteraActionSet("center on position", NavigationActionType.Pan) {
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
                                translateXYController.translate(deltaX, deltaY, deltaZ)
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
        globalTransform: AffineTransform3D,
        displayTransform: AffineTransform3D,
        globalToViewerTransform: AffineTransform3D,
        submitTransform: Consumer<AffineTransform3D>,
        lock: Any
    ): DragActionSet {
        val rotate = Rotate(speed, globalTransform, displayTransform, globalToViewerTransform, submitTransform, lock)

        return PainteraDragActionSet(NavigationActionType.Rotate, name) {
            verify { it.isPrimaryButtonDown }
            dragDetectedAction.verify { allowRotations() }
            onDragDetected { rotate.initialize() }
            onDrag { rotate.rotate(it.x, it.y, startX, startY) }
        }
    }

    private fun ActionSet.addKeyRotationHandler(
        name: String,
        keyBindings: NamedKeyCombination.CombinationMap,
        rotationCenterX: DoubleExpression,
        rotationCenterY: DoubleExpression,
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
            onAction { rotate.rotate(rotationCenterX(), rotationCenterY()) }
            keyMatchesBinding(keyBindings, name)
        }
    }
}



