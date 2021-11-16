package org.janelia.saalfeldlab.paintera.control

import bdv.fx.viewer.ViewerPanelFX
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.event.EventFX
import org.janelia.saalfeldlab.fx.event.InstallAndRemove
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseDragFX
import org.janelia.saalfeldlab.paintera.NavigationKeys
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.navigation.*
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager
import java.util.Optional
import java.util.function.*
import java.util.function.Function

class Navigation(
    private val bindings: KeyAndMouseBindings,
    private val manager: GlobalTransformManager,
    private val displayTransform: Function<ViewerPanelFX, AffineTransformWithListeners>,
    private val globalToViewerTransform: Function<ViewerPanelFX, AffineTransformWithListeners>,
    private val keyTracker: KeyTracker,
    private val allowedActionsProperty: ObjectProperty<AllowedActions> //FIXME consider making this a lambda instead, so we don't give property access
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
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollDefault.scroll(+1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD, it) }
                )

                iars.add(EventFX.KEY_PRESSED(
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollDefault.scroll(-1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD, it) }
                )

                iars.add(EventFX.KEY_PRESSED(
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollFast.scroll(+1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_FAST, it) }
                )

                iars.add(EventFX.KEY_PRESSED(
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollFast.scroll(-1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_FAST, it) }
                )

                iars.add(EventFX.KEY_PRESSED(
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollSlow.scroll(+1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_BACKWARD_SLOW, it) }
                )

                iars.add(EventFX.KEY_PRESSED(
                    NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW,
                    { this.allowedActionsProperty.get().runIfAllowed(NavigationActionType.Scroll) { it.consume(); scrollSlow.scroll(-1.0) } }
                ) { keyBindings.matches(NavigationKeys.BUTTON_TRANSLATE_ALONG_NORMAL_FORWARD_SLOW, it) }
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

                arrayOf(NavigationKeys.BUTTON_ZOOM_OUT, NavigationKeys.BUTTON_ZOOM_OUT2).forEach { kb ->
                    iars.add(
                        EventFX.KEY_PRESSED(
                            kb,
                            { zoom.zoomCenteredAt(1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) }
                        ) { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Zoom) && keyBindings.matches(kb, it) }
                    )
                }

                arrayOf(NavigationKeys.BUTTON_ZOOM_IN, NavigationKeys.BUTTON_ZOOM_IN2).forEach { kb ->
                    iars.add(
                        EventFX.KEY_PRESSED(
                            kb,
                            { zoom.zoomCenteredAt(-1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) }
                        ) { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Zoom) && keyBindings.matches(kb, it) }
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
                    Pair(KeyRotate.Axis.X, NavigationKeys.SET_ROTATION_AXIS_X),
                    Pair(KeyRotate.Axis.Y, NavigationKeys.SET_ROTATION_AXIS_Y),
                    Pair(KeyRotate.Axis.Z, NavigationKeys.SET_ROTATION_AXIS_Z)
                ).forEach { p ->
                    iars.add(EventFX.KEY_PRESSED(
                        p.second,
                        { keyRotationAxis.set(p.first) }
                    ) { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(p.second, it) }
                    )
                }

                arrayOf(Pair(-1, NavigationKeys.KEY_ROTATE_LEFT), Pair(1, NavigationKeys.KEY_ROTATE_RIGHT)).forEach { pair ->
                    iars.add(
                        keyRotationHandler(
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

                arrayOf(Pair(-1, NavigationKeys.KEY_ROTATE_LEFT_SLOW), Pair(1, NavigationKeys.KEY_ROTATE_RIGHT_SLOW)).forEach { pair ->
                    iars.add(
                        keyRotationHandler(
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

                arrayOf(Pair(-1, NavigationKeys.KEY_ROTATE_LEFT_FAST), Pair(1, NavigationKeys.KEY_ROTATE_RIGHT_FAST)).forEach { pair ->
                    iars.add(
                        keyRotationHandler(
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
                    NavigationKeys.REMOVE_ROTATION,
                    { removeRotation.removeRotationCenteredAt(mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get()) }
                ) { this.allowedActionsProperty.get().isAllowed(NavigationActionType.Rotate) && keyBindings.matches(NavigationKeys.REMOVE_ROTATION, it) }
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

    companion object {

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
                {
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
