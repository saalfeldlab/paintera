package org.janelia.saalfeldlab.paintera.control.modes

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
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
import javafx.scene.input.KeyEvent.KEY_RELEASED
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
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.fx.midi.MidiButtonEvent
import org.janelia.saalfeldlab.fx.midi.MidiPotentiometerEvent
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.NavigationKeys
import org.janelia.saalfeldlab.paintera.NavigationKeys.KEY_MODIFIER_FAST
import org.janelia.saalfeldlab.paintera.NavigationKeys.KEY_MODIFIER_SLOW
import org.janelia.saalfeldlab.paintera.NavigationKeys.KEY_ROTATE_LEFT
import org.janelia.saalfeldlab.paintera.NavigationKeys.KEY_ROTATE_RIGHT
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType
import org.janelia.saalfeldlab.paintera.control.navigation.*
import org.janelia.saalfeldlab.paintera.control.navigation.Rotate.Axis
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import kotlin.math.absoluteValue
import kotlin.math.max
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
	override val modeActions = listOf<ActionSet>(

	)

	override val allowedActions = AllowedActions.NAVIGATION

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList(NavigationTool)

}

object NavigationTool : ViewerTool() {

	private const val DEFAULT = 1.0
	private const val FAST = 10.0
	private const val SLOW = 0.1

	internal val keyAndMouseBindings = KeyAndMouseBindings(NavigationKeys.namedCombinationsCopy())

	private val keyBindings = keyAndMouseBindings.keyCombinations

	private val globalTransformManager by LazyForeignValue({ paintera }) {
		paintera.baseView.manager()
	}

	private val globalTransform by LazyForeignValue({ paintera }) {
		AffineTransform3D().apply { globalTransformManager.addListener { set(it) } }
	}


	val allowRotationsProperty = SimpleBooleanProperty(true)

	private val buttonRotationSpeedConfig = ButtonRotationSpeedConfig()

	private val speedProperty = SimpleDoubleProperty(buttonRotationSpeedConfig.regular.value)

	private val speed: Double by speedProperty.nonnull()


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

	override val graphic = { GlyphScaleView(FontAwesomeIconView().also { it.styleClass += "navigation-tool" }) }

	override val name: String = "Navigation"
	override val keyTrigger = null /* This is typically the default, so no binding to actively switch to it. */

	val viewerTransform by LazyForeignValue({ activeViewerAndTransforms }) { viewerAndTransforms ->
		viewerAndTransforms?.run {
			AffineTransform3D().apply {
				viewer().addTransformListener { set(it) }
			}
		}
	}
	val translationController by LazyForeignValue({ activeViewerAndTransforms }) { viewerAndTransforms ->
		viewerAndTransforms?.run {
			TranslationController(globalTransformManager, globalToViewerTransform)
		}
	}

	val zoomController by LazyForeignValue({ activeViewerAndTransforms }) {
		Zoom(globalTransformManager, viewerTransform)
	}

	val keyRotationAxis by LazyForeignValue({ activeViewerAndTransforms }) {
		SimpleObjectProperty(Axis.Z)
	}

	val rotationController by LazyForeignValue({ activeViewerAndTransforms }) {
		Rotate(globalTransformManager, it!!.globalToViewerTransform)
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
			val actionSets = mutableListOf<ActionSet?>()
			actionSets += speedModifierActions()
			actionSets += translateAlongNormalActions(translationController!!)
			actionSets += translateInPlaneActions(translationController!!)
			actionSets += zoomActions(zoomController, targetPositionObservable!!)

			actionSets += rotationActions(targetPositionObservable!!, keyRotationAxis, resetRotationController)
			actionSets += goToPositionAction(translationController!!)
			actionSets.filterNotNull().toMutableList()
		} ?: mutableListOf()
	}

	private fun speedModifierActions() = painteraActionSet("speed-modifier", ignoreDisable = true) {
		mapOf(
			KEY_MODIFIER_FAST to buttonRotationSpeedConfig.fast,
			KEY_MODIFIER_SLOW to buttonRotationSpeedConfig.slow
		).forEach { (keys, speed) ->
			KEY_PRESSED(keyBindings, keys, keysExclusive = false) {
				consume = false
				onAction {
					speedProperty.unbind()
					speedProperty.bind(speed)
				}
			}
			KEY_RELEASED(keyBindings, keys, keysExclusive = false) {
				consume = false
				onAction {
					speedProperty.unbind()
					speedProperty.bind(buttonRotationSpeedConfig.regular)
				}
			}
		}
	}


	private fun translateAlongNormalActions(translationController: TranslationController): List<ActionSet?> {

		fun scrollActions(translationController: TranslationController): ActionSet {
			data class ScrollSpeedStruct(val name: String, val speed: Double, val keysInit: Action<ScrollEvent>.() -> Unit)
			return painteraActionSet("scroll-translate-along-normal", NavigationActionType.Slice) {
				listOf(
					ScrollSpeedStruct("default", DEFAULT) { keysDown() },
					ScrollSpeedStruct("fast", FAST) { keysDown(KeyCode.SHIFT) },
					ScrollSpeedStruct("slow", SLOW) { keysDown(KeyCode.CONTROL) }
				).map { (actionName, speed, keysInit) ->
					ScrollEvent.SCROLL {
						name = "${this@painteraActionSet.name}.$actionName"
						onAction {
							val delta = -ControlUtils.getBiggestScroll(it).sign * speed
							translationController.translate(0.0, 0.0, delta)
						}
						this.keysInit()
					}
				}
			}
		}

		fun keyActions(translationController: TranslationController): ActionSet {
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
						onAction {
							translationController.translate(0.0, 0.0, step * speed)
						}
					}
				}
			}
		}



		return listOf(
			scrollActions(translationController),
			keyActions(translationController),
			midiSliceActions()
		)
	}

	fun midiSliceActions() =
		activeViewer?.let { target ->
			DeviceManager.xTouchMini?.let { device ->
				translationController?.let { translator ->
					painteraMidiActionSet("midi translate along normal", device, target, NavigationActionType.Slice) {
						MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(2) {
							name = "midi_normal"
							displayType = DisplayType.TRIM
							verifyEventNotNull()
							onAction {
								InvokeOnJavaFXApplicationThread {
									translator.translate(0.0, 0.0, it!!.value.toInt().sign * FAST)
								}
							}
						}
					}
				}
			}
		}

	private fun translateInPlaneActions(translateXYController: TranslationController): List<ActionSet?> {

		fun dragAction(translateXYController: TranslationController) =
			painteraDragActionSet("drag translate xy", NavigationActionType.Pan) {
				relative = true
				verify { it.isSecondaryButtonDown }
				onDrag { translateXYController.translate(it.x - startX, it.y - startY) }
			}

		return listOf(
			dragAction(translateXYController),
			midiPanActions(),
		)

	}

	fun midiPanActions() = activeViewer?.let { target ->
		DeviceManager.xTouchMini?.let { device ->
			translationController?.let { translator ->
				painteraMidiActionSet("midi translate xy", device, target, NavigationActionType.Pan) {
					MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(0) {
						name = "midi translate x"
						displayType = DisplayType.TRIM
						verifyEventNotNull()
						onAction {
							InvokeOnJavaFXApplicationThread {
								translator.translate(it!!.value.toDouble(), 0.0)
							}
						}
					}
					MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(1) {
						name = "midi translate y"
						displayType = DisplayType.TRIM
						verifyEventNotNull()
						onAction {
							InvokeOnJavaFXApplicationThread {
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
						verify("scroll size at least 1 pixel") { max(it!!.deltaX.absoluteValue, it.deltaY.absoluteValue) > 1.0 }
						keysDown(*keys)
						onAction {
							val scale = 1 + ControlUtils.getBiggestScroll(it!!) / 1_000
							zoomController.zoomCenteredAt(scale, it.x, it.y)
						}
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
				).map { (direction, key) ->
					KEY_PRESSED(keyBindings, key, keysExclusive = true) {
						onAction {
							val delta = speed / 100
							val scale = 1 - direction * delta
							zoomController.zoomCenteredAt(scale, targetPositionObservable.x, targetPositionObservable.y)
						}
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
					displayType = DisplayType.TRIM
					verifyEventNotNull()
					onAction {
						InvokeOnJavaFXApplicationThread {
							val delta = speed / 100
							val potVal = it!!.value.toDouble()
							val scale = 1 - delta * potVal
							val (x, y) = targetPositionObservable!!.let { it.x to it.y }
							zoomController.zoomCenteredAt(scale, x, y)
						}
					}
				}
			}
		}
	}

	private fun rotationActions(
		targetPositionObservable: ObservablePosition,
		keyRotationAxis: SimpleObjectProperty<Axis>,
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
			KEY_PRESSED(keyBindings, NavigationKeys.SET_ROTATION_AXIS_X) { onAction { keyRotationAxis.set(Axis.X) } }
			KEY_PRESSED(keyBindings, NavigationKeys.SET_ROTATION_AXIS_Y) { onAction { keyRotationAxis.set(Axis.Y) } }
			KEY_PRESSED(keyBindings, NavigationKeys.SET_ROTATION_AXIS_Z) { onAction { keyRotationAxis.set(Axis.Z) } }
		}

		val mouseRotation = painteraDragActionSet("mouse-drag-rotate", NavigationActionType.Rotate) {
			verify { it.isPrimaryButtonDown }
			dragDetectedAction.verify { NavigationTool.allowRotationsProperty() }
			onDragDetected {
				rotationController.initialize(targetPositionObservable.x, targetPositionObservable.y)
			}
			onDrag {
				rotationController.setSpeed(speed / buttonRotationSpeedConfig.regular.value)
				rotationController.rotate3D(it.x, it.y, startX, startY)
			}
		}


		val keyRotation = painteraActionSet("key-rotate", NavigationActionType.Rotate) {
			mapOf(-1 to KEY_ROTATE_LEFT, 1 to KEY_ROTATE_RIGHT).forEach { (direction, key) ->

				KEY_PRESSED(keyBindings, key, keysExclusive = false) {
					verify { allowRotationsProperty() }
					onAction {
						rotationController.setSpeed(direction * speed)
						rotationController.rotateAroundAxis(targetPositionObservable.x, targetPositionObservable.y, keyRotationAxis.get())
					}
				}
			}
		}


		val rotationActions = mutableListOf<ActionSet?>()

		rotationActions += removeRotationActions
		rotationActions += setRotationAxis
		rotationActions += mouseRotation
		rotationActions += keyRotation
		midiRotationActions()?.let { rotationActions += it }

		return rotationActions.filterNotNull()
	}

	private fun midiResetRotationAction(): MidiActionSet? {
		return activeViewer?.let { target ->
			DeviceManager.xTouchMini?.let { device ->
				targetPositionObservable?.let { targetPos ->
					painteraMidiActionSet(NavigationKeys.REMOVE_ROTATION, device, target, NavigationActionType.Rotate) {
						MidiButtonEvent.BUTTON_PRESSED(18) {
							name = "midi_remove_rotation"
							verifyEventNotNull()
							onAction { InvokeOnJavaFXApplicationThread { resetRotationController.removeRotationCenteredAt(targetPos.x, targetPos.y) } }
						}
					}
				}
			}
		}
	}

	fun midiRotationActions() = activeViewerAndTransforms?.let { vat ->
		DeviceManager.xTouchMini?.let { device ->
			targetPositionObservable?.let { targetPosition ->
				val target = vat.viewer()

				data class MidiRotationStruct(val handle: Int, val axis: Axis)
				listOf(
					MidiRotationStruct(5, Axis.X),
					MidiRotationStruct(6, Axis.Y),
					MidiRotationStruct(7, Axis.Z),
				).map { (handle, axis) ->
					painteraMidiActionSet("rotate", device, target, NavigationActionType.Rotate) {
						MidiPotentiometerEvent.POTENTIOMETER_RELATIVE(handle) {
							name = "midi_rotate_${axis.name.lowercase()}"
							displayType = DisplayType.TRIM
							verifyEventNotNull()
							verify { allowRotationsProperty() }
							onAction {
								InvokeOnJavaFXApplicationThread {
									val direction = it!!.value.toInt().sign
									rotationController.setSpeed(direction * speed)
									rotationController.rotateAroundAxis(targetPosition.x, targetPosition.y, axis)
								}
							}
						}
					}
				}
			}

		}
	}

	fun midiNavigationActions(
		pan: Boolean = true,
		slice: Boolean = true,
		zoom: Boolean = true,
		rotation: Boolean = true,
		resetRotation: Boolean = true
	) = mutableListOf<MidiActionSet>().also {
		if (pan) midiPanActions()?.let { midiActions -> it.add(midiActions) }
		if (slice) midiSliceActions()?.let { midiActions -> it.add(midiActions) }
		if (zoom) midiZoomActions()?.let { midiActions -> it.add(midiActions) }
		if (rotation) midiRotationActions()?.let { midiActions -> it.addAll(midiActions) }
		if (resetRotation) midiResetRotationAction()?.let { midiActions -> it.add(midiActions) }
	}

	private fun goToPositionAction(translateXYController: TranslationController) =
		painteraActionSet("center on position", NavigationActionType.Pan) {
			KEY_PRESSED(KeyCode.CONTROL, KeyCode.G) {
				verify { paintera.baseView.sourceInfo().currentSourceProperty().get() != null }
				verify { paintera.baseView.sourceInfo().currentState().get() != null }
				onAction {
					activeViewer?.let { viewer ->
						val source = paintera.baseView.sourceInfo().currentSourceProperty().get()!!
						val sourceToGlobalTransform = AffineTransform3D().also { source.getSourceTransform(viewer.state.timepoint, 0, it) }
						val currentSourceCoordinate =
							RealPoint(3).also { viewer.displayToSourceCoordinates(viewer.width / 2.0, viewer.height / 2.0, sourceToGlobalTransform, it) }

						val positionField =
							SpatialField.doubleField(0.0, { true }, Region.USE_COMPUTED_SIZE, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
								x.value = currentSourceCoordinate.getDoublePosition(0)
								y.value = currentSourceCoordinate.getDoublePosition(1)
								z.value = currentSourceCoordinate.getDoublePosition(2)
							}
						PainteraAlerts.confirmation("Go", "Cancel", true).apply {
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
								val sourceDeltaX = x.value.toDouble() - currentSourceCoordinate.getDoublePosition(0)
								val sourceDeltaY = y.value.toDouble() - currentSourceCoordinate.getDoublePosition(1)
								val sourceDeltaZ = z.value.toDouble() - currentSourceCoordinate.getDoublePosition(2)

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

								translateXYController.translate(deltaX, deltaY, deltaZ, Duration(300.0))
							}
						}
					}
				}
			}
		}
}
