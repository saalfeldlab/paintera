package org.janelia.saalfeldlab.paintera.control

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.scene.Node
import javafx.scene.input.*
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.event.EventFX
import org.janelia.saalfeldlab.fx.event.InstallAndRemove
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseDragFX
import org.janelia.saalfeldlab.paintera.NamedKeyCombination
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.navigation.*
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager
import java.util.ArrayList
import java.util.HashMap
import java.util.Optional
import java.util.concurrent.Callable
import java.util.function.*
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.DoubleSupplier
import java.util.function.Function

class Navigation(
    private val bindings: KeyAndMouseBindings,
    private val manager: GlobalTransformManager,
    private val displayTransform: Function<ViewerPanelFX, AffineTransformWithListeners>,
    private val globalToViewerTransform: Function<ViewerPanelFX, AffineTransformWithListeners>,
    private val keyTracker: KeyTracker,
    private val allowedActionsProperty: ObjectProperty<AllowedActions>
) : ToOnEnterOnExit {

    private val zoomSpeed = SimpleDoubleProperty(1.05)

    private val translationSpeed = SimpleDoubleProperty(1.0)

    private val rotationSpeed = SimpleDoubleProperty(1.0)

    private val allowRotations = SimpleBooleanProperty(true)

    private val buttonRotationSpeedConfig = ButtonRotationSpeedConfig()

    private val mouseAndKeyHandlers = HashMap<ViewerPanelFX, Collection<InstallAndRemove<Node>>>()

    private val keyBindings = bindings.keyCombinations

    override fun getOnEnter(): Consumer<ViewerPanelFX> {
        return Consumer { t ->
            if (!this.mouseAndKeyHandlers.containsKey(t)) {

                val viewerTransform = AffineTransform3D()
                t.addTransformListener { viewerTransform.set(it) }

                val mouseX = t.mouseXProperty()
                val mouseY = t.mouseYProperty()
                val isInside = t.isMouseInsideProperty

                val mouseXIfInsideElseCenterX = Bindings.createDoubleBinding(
                    {
                        if (isInside.get())
                            mouseX.get()
                        else
                            t.width / 2
                    },
                    isInside,
                    mouseX
                )
                val mouseYIfInsideElseCenterY = Bindings.createDoubleBinding(
                    {
                        if (isInside.get())
                            mouseY.get()
                        else
                            t.height / 2
                    },
                    isInside,
                    mouseY
                )

                val worldToSharedViewerSpace = AffineTransform3D()
                val globalTransform = AffineTransform3D()
                val displayTransform = AffineTransform3D()
                val globalToViewerTransform = AffineTransform3D()

                manager.addListener { globalTransform.set(it) }

                this.displayTransform.apply(t).addListener { tf ->
                    displayTransform.set(tf)
                    this.globalToViewerTransform.apply(t).getTransformCopy(worldToSharedViewerSpace)
                    worldToSharedViewerSpace.preConcatenate(tf)
                }

                this.globalToViewerTransform.apply(t).addListener { tf ->
                    globalToViewerTransform.set(tf)
                    this.displayTransform.apply(t).getTransformCopy(worldToSharedViewerSpace)
                    worldToSharedViewerSpace.concatenate(tf)
                }

                val translateXY = TranslateWithinPlane(
                    manager,
                    this.displayTransform.apply(t),
                    this.globalToViewerTransform.apply(t),
                    manager
                )

                val iars = ArrayList<InstallAndRemove<Node>>()

                val scrollDefault = TranslateAlongNormal(
                    { translationSpeed.multiply(FACTORS[0]).get() },
                    manager,
                    worldToSharedViewerSpace,
                    manager
                )
                val scrollFast = TranslateAlongNormal(
                    { translationSpeed.multiply(FACTORS[1]).get() },
                    manager,
                    worldToSharedViewerSpace,
                    manager
                )
                val scrollSlow = TranslateAlongNormal(
                    { translationSpeed.multiply(FACTORS[2]).get() },
                    manager,
                    worldToSharedViewerSpace,
                    manager
                )

                iars.add(EventFX.SCROLL(
                    "translate along normal",
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { scrollDefault.scroll(-ControlUtils.getBiggestScroll(it)) } },
                    { keyTracker.noKeysActive() })
                )
                iars.add(EventFX.SCROLL(
                    "translate along normal fast",
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { scrollFast.scroll(-ControlUtils.getBiggestScroll(it)) } },
                    { keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT) })
                )
                iars.add(EventFX.SCROLL(
                    "translate along normal slow",
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { scrollSlow.scroll(-ControlUtils.getBiggestScroll(it)) } },
                    { keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollDefault.scroll(+1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD, it) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollDefault.scroll(-1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD, it) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollFast.scroll(+1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST, it) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollFast.scroll(-1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST, it) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollSlow.scroll(+1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW, it) })
                )

                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollSlow.scroll(-1.0) } },
                    { keyBindings.matches(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW, it) })
                )

                iars.add(
                    MouseDragFX.createDrag(
                        "translate xy",
                        { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Drag) && it.isSecondaryButtonDown && keyTracker.noKeysActive() },
                        true,
                        manager,
                        { translateXY.init() },
                        { dX, dY -> translateXY.drag(dX, dY) },
                        false
                    )
                )

                val zoom = Zoom({ zoomSpeed.get() }, manager, viewerTransform, manager)
                iars.add(EventFX.SCROLL(
                    "zoom",
                    { zoom.zoomCenteredAt(-ControlUtils.getBiggestScroll(it), it.x, it.y) },
                    {
                        this.allowedActionsProperty.get()
                            .isAllowed(NavigationActionType.Zoom) && (keyTracker.areOnlyTheseKeysDown(KeyCode.META) || keyTracker.areOnlyTheseKeysDown(
                            KeyCode.CONTROL,
                            KeyCode.SHIFT
                        ))
                    })
                )

                arrayOf(BindingKeys.BUTTON_ZOOM_OUT, BindingKeys.BUTTON_ZOOM_OUT2).forEach { kb ->
                    iars.add(
                        EventFX.KEY_PRESSED(
                            kb,
                            { zoom.zoomCenteredAt(1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) },
                            { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Zoom) && keyBindings.matches(kb, it) })
                    )
                }

                arrayOf(BindingKeys.BUTTON_ZOOM_IN, BindingKeys.BUTTON_ZOOM_IN2).forEach { kb ->
                    iars.add(
                        EventFX.KEY_PRESSED(
                            kb,
                            { zoom.zoomCenteredAt(-1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) },
                            { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Zoom) && keyBindings.matches(kb, it) })
                    )
                }

                iars.add(rotationHandler(
                    "rotate",
                    { allowRotations.get() },
                    { rotationSpeed.multiply(FACTORS[0]).get() },
                    globalTransform,
                    displayTransform,
                    globalToViewerTransform,
                    { manager.setTransform(it) },
                    manager,
                    { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyTracker.noKeysActive() && it.button == MouseButton.PRIMARY }
                ))

                iars.add(rotationHandler(
                    "rotate fast",
                    { allowRotations.get() },
                    { rotationSpeed.multiply(FACTORS[1]).get() },
                    globalTransform,
                    displayTransform,
                    globalToViewerTransform,
                    { manager.setTransform(it) },
                    manager,
                    {
                        this.allowedActionsProperty.get()
                            .isAllowed(NavigationActionType.Rotate) && keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT) && it.button == MouseButton.PRIMARY
                    }
                ))

                iars.add(rotationHandler(
                    "rotate slow",
                    { allowRotations.get() },
                    { rotationSpeed.multiply(FACTORS[2]).get() },
                    globalTransform,
                    displayTransform,
                    globalToViewerTransform,
                    { manager.setTransform(it) },
                    manager,
                    {
                        this.allowedActionsProperty.get()
                            .isAllowed(NavigationActionType.Rotate) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL) && it.button == MouseButton.PRIMARY
                    }
                ))

                val keyRotationAxis = SimpleObjectProperty(KeyRotate.Axis.Z)
                listOf(
                    Pair(KeyRotate.Axis.X, BindingKeys.SET_ROTATION_AXIS_X),
                    Pair(KeyRotate.Axis.Y, BindingKeys.SET_ROTATION_AXIS_Y),
                    Pair(KeyRotate.Axis.Z, BindingKeys.SET_ROTATION_AXIS_Z)
                ).forEach { p ->
                    iars.add(EventFX.KEY_PRESSED(
                        p.second,
                        { keyRotationAxis.set(p.first) },
                        { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(p.second, it) })
                    )
                }

                arrayOf(Pair(-1, BindingKeys.KEY_ROTATE_LEFT), Pair(1, BindingKeys.KEY_ROTATE_RIGHT)).forEach { pair ->
                    iars.add(keyRotationHandler(
                        pair.second,
                        { mouseXIfInsideElseCenterX.get() },
                        { mouseYIfInsideElseCenterY.get() },
                        { allowRotations.get() },
                        { keyRotationAxis.get() },
                        { this.buttonRotationSpeedConfig.regular.multiply(pair.first * Math.PI / 180.0).get() },
                        displayTransform,
                        globalToViewerTransform,
                        globalTransform,
                        { manager.setTransform(it) },
                        manager,
                        { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(pair.second, it) })
                    )
                }

                arrayOf(Pair(-1, BindingKeys.KEY_ROTATE_LEFT_SLOW), Pair(1, BindingKeys.KEY_ROTATE_RIGHT_SLOW)).forEach { pair ->
                    iars.add(keyRotationHandler(
                        pair.second,
                        { mouseXIfInsideElseCenterX.get() },
                        { mouseYIfInsideElseCenterY.get() },
                        { allowRotations.get() },
                        { keyRotationAxis.get() },
                        { this.buttonRotationSpeedConfig.slow.multiply(pair.first * Math.PI / 180.0).get() },
                        displayTransform,
                        globalToViewerTransform,
                        globalTransform,
                        { manager.setTransform(it) },
                        manager,
                        { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(pair.second, it) })
                    )
                }

                arrayOf(Pair(-1, BindingKeys.KEY_ROTATE_LEFT_FAST), Pair(1, BindingKeys.KEY_ROTATE_RIGHT_FAST)).forEach { pair ->
                    iars.add(keyRotationHandler(
                        pair.second,
                        { mouseXIfInsideElseCenterX.get() },
                        { mouseYIfInsideElseCenterY.get() },
                        { allowRotations.get() },
                        { keyRotationAxis.get() },
                        { this.buttonRotationSpeedConfig.fast.multiply(pair.first * Math.PI / 180.0).get() },
                        displayTransform,
                        globalToViewerTransform,
                        globalTransform,
                        { manager.setTransform(it) },
                        manager,
                        { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(pair.second, it) })
                    )
                }
                val removeRotation = RemoveRotation(
                    viewerTransform,
                    globalTransform,
                    { manager.setTransform(it) },
                    manager
                )
                iars.add(EventFX.KEY_PRESSED(
                    BindingKeys.REMOVE_ROTATION,
                    { removeRotation.removeRotationCenteredAt(mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) },
                    { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(BindingKeys.REMOVE_ROTATION, it) }
                )
                )

                this.mouseAndKeyHandlers[t] = iars

            }
            this.mouseAndKeyHandlers[t]!!.forEach { it.installInto(t) }
        }
    }

    override fun getOnExit(): Consumer<ViewerPanelFX> {
        return Consumer { t ->
            Optional
                .ofNullable(this.mouseAndKeyHandlers[t])
                .ifPresent { hs -> hs.forEach { h -> h.removeFrom(t) } }
        }
    }

    fun allowRotationsProperty(): BooleanProperty {
        return this.allowRotations
    }

    fun bindTo(config: ButtonRotationSpeedConfig) {
        this.buttonRotationSpeedConfig.regular.bind(config.regular)
        this.buttonRotationSpeedConfig.slow.bind(config.slow)
        this.buttonRotationSpeedConfig.fast.bind(config.fast)
    }

    object BindingKeys {
        const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD = "translate along normal forward"
        const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST = "translate along normal forward fast"
        const val BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW = "translate along normal forward slow"
        const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD = "translate along normal backward"
        const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST = "translate along normal backward fast"
        const val BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW = "translate along normal backward slow"
        const val BUTTON_ZOOM_OUT = "zoom out"
        const val BUTTON_ZOOM_OUT2 = "zoom out (alternative)"
        const val BUTTON_ZOOM_IN = "zoom in"
        const val BUTTON_ZOOM_IN2 = "zoom in (alternative)"
        const val SET_ROTATION_AXIS_X = "set rotation axis x"
        const val SET_ROTATION_AXIS_Y = "set rotation axis y"
        const val SET_ROTATION_AXIS_Z = "set rotation axis z"
        const val KEY_ROTATE_LEFT = "rotate left"
        const val KEY_ROTATE_LEFT_FAST = "rotate left fast"
        const val KEY_ROTATE_LEFT_SLOW = "rotate left slow"
        const val KEY_ROTATE_RIGHT = "rotate right"
        const val KEY_ROTATE_RIGHT_FAST = "rotate right fast"
        const val KEY_ROTATE_RIGHT_SLOW = "rotate right slow"
        const val REMOVE_ROTATION = "remove rotation"
    }

    companion object {

        fun createNamedKeyCombinations() = NamedKeyCombination.CombinationMap().also {
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD, KeyCodeCombination(KeyCode.COMMA)))
            it.addCombination(
                NamedKeyCombination(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST,
                    KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHIFT_DOWN)
                )
            )
            it.addCombination(
                NamedKeyCombination(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW,
                    KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN)
                )
            )
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD, KeyCodeCombination(KeyCode.PERIOD)))
            it.addCombination(
                NamedKeyCombination(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST,
                    KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHIFT_DOWN)
                )
            )
            it.addCombination(
                NamedKeyCombination(
                    BindingKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW,
                    KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN)
                )
            )
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_ZOOM_OUT, KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHIFT_ANY)))
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_ZOOM_OUT2, KeyCodeCombination(KeyCode.DOWN)))
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_ZOOM_IN, KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHIFT_ANY)))
            it.addCombination(NamedKeyCombination(BindingKeys.BUTTON_ZOOM_IN2, KeyCodeCombination(KeyCode.UP)))
            it.addCombination(NamedKeyCombination(BindingKeys.SET_ROTATION_AXIS_X, KeyCodeCombination(KeyCode.X)))
            it.addCombination(NamedKeyCombination(BindingKeys.SET_ROTATION_AXIS_Y, KeyCodeCombination(KeyCode.Y)))
            it.addCombination(NamedKeyCombination(BindingKeys.SET_ROTATION_AXIS_Z, KeyCodeCombination(KeyCode.Z)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_LEFT, KeyCodeCombination(KeyCode.LEFT)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_LEFT_FAST, KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHIFT_DOWN)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_LEFT_SLOW, KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_RIGHT, KeyCodeCombination(KeyCode.RIGHT)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_RIGHT_FAST, KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHIFT_DOWN)))
            it.addCombination(NamedKeyCombination(BindingKeys.KEY_ROTATE_RIGHT_SLOW, KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN)))
            it.addCombination(NamedKeyCombination(BindingKeys.REMOVE_ROTATION, KeyCodeCombination(KeyCode.Z, KeyCombination.SHIFT_DOWN)))
        }

        private val FACTORS = doubleArrayOf(1.0, 10.0, 0.1)

        private fun rotationHandler(
            name: String,
            allowRotations: BooleanSupplier,
            speed: DoubleSupplier,
            globalTransform: AffineTransform3D,
            displayTransform: AffineTransform3D,
            globalToViewerTransform: AffineTransform3D,
            submitTransform: Consumer<AffineTransform3D>,
            lock: Any,
            predicate: Predicate<MouseEvent>
        ): MouseDragFX {
            val rotate = Rotate(
                speed,
                globalTransform,
                displayTransform,
                globalToViewerTransform,
                submitTransform,
                lock
            )

            return object : MouseDragFX(name, predicate, true, lock, false) {

                override fun initDrag(event: MouseEvent) {
                    if (allowRotations.asBoolean) {
                        rotate.initialize()
                    } else {
                        abortDrag()
                    }
                }

                override fun drag(event: MouseEvent) {
                    rotate.rotate(event.x, event.y, startX, startY)
                }
            }

        }

        private fun keyRotationHandler(
            name: String,
            rotationCenterX: DoubleSupplier,
            rotationCenterY: DoubleSupplier,
            allowRotations: BooleanSupplier,
            axis: Supplier<KeyRotate.Axis>,
            step: DoubleSupplier,
            displayTransform: AffineTransform3D,
            globalToViewerTransform: AffineTransform3D,
            globalTransform: AffineTransform3D,
            submitTransform: Consumer<AffineTransform3D>,
            lock: Any,
            predicate: Predicate<KeyEvent>
        ): EventFX<KeyEvent> {
            val rotate = KeyRotate(
                axis,
                step,
                displayTransform,
                globalToViewerTransform,
                globalTransform,
                submitTransform,
                lock
            )

            return EventFX.KEY_PRESSED(
                name,
                Consumer {
                    if (allowRotations.asBoolean) {
                        it.consume()
                        rotate.rotate(rotationCenterX.asDouble, rotationCenterY.asDouble)
                    }
                },
                predicate
            )

        }
    }

}
