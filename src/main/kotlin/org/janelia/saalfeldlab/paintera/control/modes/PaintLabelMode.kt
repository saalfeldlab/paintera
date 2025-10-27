package org.janelia.saalfeldlab.paintera.control.modes

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.control.mcu.MCUButtonControl
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.painteraMidiActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.midi.MidiToggleEvent
import org.janelia.saalfeldlab.fx.midi.ToggleAction
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.DeviceManager
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys.*
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.actions.paint.ReplaceLabel
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.paint.*
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintTool.Companion.createPaintStateContext
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts


open class PaintLabelMode : ViewLabelMode() {

	private val activeSourceToSourceStateContextBinding = activeSourceStateProperty.createNullableValueBinding { binding -> createPaintStateContext<Nothing, Nothing>(binding) }
	internal val statePaintContext by activeSourceToSourceStateContextBinding.nullableVal()

	private val paintBrushTool = PaintBrushTool(activeSourceStateProperty, this)
	private val samTool = SamTool(activeSourceStateProperty, this)
	private val fill2DTool = Fill2DTool(activeSourceStateProperty, this)
	private val fill3DTool = Fill3DTool(activeSourceStateProperty, this)
	private val intersectTool = IntersectPaintWithUnderlyingLabelTool(activeSourceStateProperty, this)

	override val tools: ObservableList<Tool> by lazy {
		FXCollections.observableArrayList(
			NavigationTool,
			paintBrushTool,
			fill2DTool,
			fill3DTool,
			intersectTool,
			samTool
		)
	}

	override val activeViewerActions: List<ActionSet> by lazy {
		listOf(
			*super.activeViewerActions.toTypedArray(),
			escapeToDefault(),
			*getToolTriggers().toTypedArray(),
			enterShapeInterpolationMode,
			getSelectNextIdActions(),
			getDeleteReplaceIdActions(),
			getResetMaskAction(),
		)
	}

	override val allowedActions = AllowedActions.PAINT

	private val toggleFill3D = painteraActionSet("toggle fill 3D overlay", PaintActionType.Fill) {
		KEY_PRESSED(FILL_3D) {
			onAction { switchTool(fill3DTool) }
		}
		KEY_PRESSED {
			/* swallow F down events while filling*/
			filter = true
			consume = true
			verifyEventNotNull()
			verify { it!!.code in FILL_3D.keyCodes && activeTool is Fill3DTool }
		}

		KEY_RELEASED {
			verifyEventNotNull()
			keysReleased(*FILL_3D.keyCodes.toTypedArray())
			verify { activeTool is Fill3DTool }
			onAction {

				val nextTool = when {
					keyTracker()?.areKeysDown(FILL_3D) == true -> return@onAction
					keyTracker()?.areKeysDown(FILL_2D) == true -> fill2DTool
					else -> NavigationTool
				}
				switchTool(nextTool)
			}
		}
	}

	private val enterShapeInterpolationMode = painteraActionSet(SHAPE_INTERPOLATION__TOGGLE_MODE, PaintActionType.ShapeInterpolation) {
		KEY_PRESSED(SHAPE_INTERPOLATION__TOGGLE_MODE) {
			createToolNode = { apply { styleClass += "enter-shape-interpolation" } }
			verify { activeSourceStateProperty.get() is ConnectomicsLabelState<*, *> }
			verify {
				@Suppress("UNCHECKED_CAST")
				activeSourceStateProperty.get()?.dataSource as? MaskedSource<out IntegerType<*>, *> != null
			}
			onAction { event ->
				val switchModes = {
					newShapeInterpolationModeForSource(activeSourceStateProperty.get())?.let {
						paintera.baseView.changeMode(it)
					}
				}
				when {
					event == null && paintera.baseView.currentFocusHolder.value == null -> selectViewerBefore { switchModes() }
					else -> switchModes()
				}
			}
		}
	}

	private val activeSamTool = painteraActionSet(SEGMENT_ANYTHING__TOGGLE_MODE, PaintActionType.Paint) {
		KEY_PRESSED(samTool.keyTrigger) {
			verify { SamEmbeddingLoaderCache.canReachServer }
			verify { activeSourceStateProperty.get() is ConnectomicsLabelState<*, *> }
			verify { activeTool !is SamTool }
			verify {
				@Suppress("UNCHECKED_CAST")
				activeSourceStateProperty.get()?.dataSource as? MaskedSource<out IntegerType<*>, *> != null
			}
			onAction {
				switchTool(samTool)
			}
		}
		KEY_PRESSED(samTool.keyTrigger) {
			verify { activeSourceStateProperty.get() is ConnectomicsLabelState<*, *> }
			verify { activeTool is SamTool }
			onAction {
				switchTool(defaultTool)
			}
		}
		KEY_PRESSED(CANCEL) {
			verify { activeSourceStateProperty.get() is ConnectomicsLabelState<*, *> }
			verify { activeTool is SamTool }
			filter = true
			consume = false
			onAction {
				switchTool(defaultTool)
			}
		}
	}

	override fun switchTool(tool: Tool?): Job? {
		val switchToolJob = super.switchTool(tool)
		/*SAM Tool restrict the active ViewerPanel, so we don't want it changing on mouseover of the other views, for example */
		(tool as? SamTool)?.let { runBlocking { switchToolJob?.join() } }
		return switchToolJob
	}


	private fun getToolTriggers() = listOf(
		paintBrushTool.createTriggers(this, PaintActionType.Paint),
		fill2DTool.createTriggers(this, PaintActionType.Fill),
		toggleFill3D,
		intersectTool.createTriggers(this, PaintActionType.Intersect),
		activeSamTool

	)

	private fun getSelectNextIdActions() = painteraActionSet("Create New Segment", LabelActionType.CreateNew) {
		KEY_PRESSED(NEXT_ID) {
			name = "create_new_segment"
			verify("Not Painting") { (activeTool as? PaintTool)?.isPainting?.not() ?: true }
			onAction {
				statePaintContext?.nextId(activate = true)
			}
		}
	}

	private fun getDeleteReplaceIdActions() = painteraActionSet("delete_label", LabelActionType.Delete) {
		KEY_PRESSED ( DELETE_ID) {
			onAction { ReplaceLabel.deleteMenu()(it) }
		}
	}

	private fun getResetMaskAction() = painteraActionSet("Force Mask Reset", PaintActionType.Paint, ignoreDisable = true) {
		KEY_PRESSED(KeyCode.SHIFT, KeyCode.ESCAPE) {
			verify("has current mask") { (statePaintContext as? MaskedSource<*, *>)?.currentMask != null }
			onAction {
				InvokeOnJavaFXApplicationThread {
					PainteraAlerts.confirmation("Yes", "No", false).apply {
						headerText = "Force Reset the Active Mask?"
						contentText = """
                            This may result in loss of some of the most recent uncommitted label annotations. This usually is only necessary if the mask is stuck on "busy".

                            Only do this if you suspect an error has occured. You may consider waiting a bit to see if the mask releases on it's own.
                        """.trimIndent()
						val okButton = dialogPane.lookupButton(ButtonType.OK) as Button
						okButton.onAction = EventHandler {
							activeSourceStateProperty.get()?.let { state ->
								(state.dataSource as? MaskedSource<*, *>)?.resetMasks()
							}
						}
						showAndWait()
					}
				}
			}
		}
	}

	private fun newShapeInterpolationModeForSource(sourceState: SourceState<*, *>?): ShapeInterpolationMode<*>? {
		return sourceState?.let { state ->
			(state as? ConnectomicsLabelState<*, *>)?.run {
				(dataSource as? MaskedSource<out IntegerType<*>, *>)?.let { maskedSource ->
					ShapeInterpolationController(
						maskedSource,
						::refreshMeshes,
						selectedIds,
						idService,
						converter(),
						fragmentSegmentAssignment,
					)
				}
			}?.let { ShapeInterpolationMode(it, this) }
		}
	}

	internal fun midiToolTogleActions() = DeviceManager.xTouchMini?.let { device ->
		activeViewerProperty.get()?.viewer()?.let { viewer ->
			painteraMidiActionSet("midi paint tool switch actions", device, viewer, PaintActionType.Paint) {
				val toggleToolActionMap = mutableMapOf<Tool, ToggleAction>()
				activeToolProperty.addListener { obs, old, new ->
					toggleToolActionMap[old]?.updateControlSilently(MCUButtonControl.TOGGLE_OFF)
					toggleToolActionMap[new]?.updateControlSilently(MCUButtonControl.TOGGLE_ON)
				}
				toggleToolActionMap[NavigationTool] = MidiToggleEvent.BUTTON_TOGGLE(0) {
					name = "midi switch back to navigation tool"
					filter = true
					onAction {
						InvokeOnJavaFXApplicationThread {
							if (activeTool is Fill2DTool) {
								fill2DTool.fill2D.release()
							}
							if (activeTool != NavigationTool)
								switchTool(NavigationTool)
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
								switchTool(NavigationTool)
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
								switchTool(NavigationTool)
							} else {
								switchTool(fill2DTool)
							}
						}
					}
				}
			}
		}
	}

}


