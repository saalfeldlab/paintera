package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.input.*
import net.imglib2.Interval
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText
import kotlin.collections.set

open class Fill2DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
	PaintTool(activeSourceStateProperty, mode) {

	override val graphic = { ScaleView().also { it.styleClass += "fill-2d" } }

	override val name = "Fill 2D"
	override val keyTrigger = LabelSourceStateKeys.FILL_2D
	var fillLabel: () -> Long = { statePaintContext?.paintSelection?.invoke() ?: Label.INVALID }

	val fill2D by LazyForeignValue({ statePaintContext }) {
		with(it!!) {
			val floodFill2D = FloodFill2D(
				activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
				dataSource,
			) { MeshSettings.Defaults.Values.isVisible }
			floodFill2D.fillDepthProperty().bindBidirectional(brushProperties.brushDepthProperty)
			floodFill2D
		}
	}

	val fillTaskProperty: SimpleObjectProperty<UtilityTask<*>?> = SimpleObjectProperty(null)
	private var fillTask by fillTaskProperty.nullable()

	private val overlay by lazy {
		Fill2DOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() }).apply {
			brushPropertiesBinding.addListener { _, old, new ->
				old?.brushDepthProperty?.let { brushDepthProperty.unbindBidirectional(it) }
				new?.brushDepthProperty?.let { brushDepthProperty.bindBidirectional(it) }
			}
			brushDepthProperty.bindBidirectional(brushProperties!!.brushDepthProperty)
		}
	}


	override fun activate() {
		super.activate()
		activeViewer?.apply { overlay.setPosition(mouseXProperty.get(), mouseYProperty.get()) }
		overlay.visible = true
	}

	override fun deactivate() {

		fillIsRunningProperty.addTriggeredWithListener { obs, _, isRunning ->
			if (!isRunning) {
				overlay.visible = false
				fill2D.release()
				obs?.removeListener(this)
				super.deactivate()
			}
		}
	}

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*super.actionSets.toTypedArray(),
			painteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
				ScrollEvent.SCROLL {
					keysExclusive = false
					onAction {
						changeBrushDepth(-ControlUtils.getBiggestScroll(it))
						overlay.brushDepthProperty
					}
				}
			},
			painteraActionSet("fill 2d", PaintActionType.Fill) {
				MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
					name = "fill 2d"
					keysExclusive = false
					verifyEventNotNull()
					onAction {
						executeFill2DAction(it!!.x, it.y)
					}
				}
			},
			painteraActionSet(LabelSourceStateKeys.CANCEL, ignoreDisable = true) {
				KeyEvent.KEY_PRESSED(LabelSourceStateKeys.CANCEL) {
					name = "Cancel Fill 2D"
					graphic = { GlyphScaleView(FontAwesomeIconView().apply { styleClass += "reject" }).apply { styleClass += "ignore-disable"} }
					filter = true
					onAction {
						fillTask?.run { if (!isCancelled) cancel() } ?: mode?.switchTool(mode.defaultTool)
					}
				}
			}
		)
	}

	private val fillIsRunningProperty = SimpleBooleanProperty(false, "Fill2D is Running")

	internal fun executeFill2DAction(x: Double, y: Double, afterFill: (Interval) -> Unit = {}): UtilityTask<*>? {

		fillIsRunningProperty.set(true)
		val applyIfMaskNotProvided = fill2D.mask == null
		if (applyIfMaskNotProvided) {
			statePaintContext!!.dataSource.resetMasks(true);
		}
		fillTask = fill2D.fillViewerAt(x, y, fillLabel(), statePaintContext!!.assignment)
		if (fillTask == null) {
			fillIsRunningProperty.set(false)
		}

		return fillTask?.also { task ->
			if (task.isDone) {
				/* If it's already done, do this now*/
				if (!task.isCancelled) {
					val maskFillInterval = fill2D.maskIntervalProperty.value
					afterFill(maskFillInterval)
					if (applyIfMaskNotProvided) {
						/* Then apply when done */
						val source = statePaintContext!!.dataSource
						val mask = source.currentMask as ViewerMask
						val affectedSourceInterval = Intervals.smallestContainingInterval(
							mask.currentMaskToSourceWithDepthTransform.estimateBounds(maskFillInterval))
						source.applyMask(mask, affectedSourceInterval, net.imglib2.type.label.Label::isForeground)
					}

				}
			} else {
				paintera.baseView.disabledPropertyBindings[this] = fillIsRunningProperty
				paintera.baseView.isDisabledProperty.addTriggeredWithListener { obs, _, isBusy ->
					if (isBusy) {
						overlay.cursor = Cursor.WAIT
					} else {
						overlay.cursor = Cursor.CROSSHAIR
						if (!paintera.keyTracker.areKeysDown(*keyTrigger.keyCodes.toTypedArray()) && !enteredWithoutKeyTrigger) {
							InvokeOnJavaFXApplicationThread { mode?.switchTool(mode.defaultTool) }
						}
						obs?.removeListener(this)
					}
				}

				/* Otherwise, do it when it's done */

				task.onEnd(append = true) {
					fillIsRunningProperty.set(false)
					paintera.baseView.disabledPropertyBindings -= this
					fillTask = null
				}

				task.onSuccess(append = true) { _, _ ->
					val maskFillInterval = fill2D.maskIntervalProperty.value
					afterFill(maskFillInterval)
					if (applyIfMaskNotProvided) {
						/* Then apply when done */
						val source = statePaintContext!!.dataSource
						val mask = source.currentMask as ViewerMask
						val affectedSourceInterval = Intervals.smallestContainingInterval(
							mask.currentMaskToSourceWithDepthTransform.estimateBounds(maskFillInterval))
						source.applyMask(mask, affectedSourceInterval, net.imglib2.type.label.Label::isForeground)
					}
				}

			}
		}
	}

	private class Fill2DOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayWithText(viewerProperty) {

		val brushDepthProperty = SimpleDoubleProperty().apply { addListener { _, _, _ -> viewer?.display?.drawOverlays() } }
		private val brushDepth by brushDepthProperty.nonnullVal()

		override val overlayText: String
			get() = when {
				brushDepth > 1 -> "$OVERLAY_TEXT depth= $brushDepth"
				else -> OVERLAY_TEXT
			}

		companion object {
			private const val OVERLAY_TEXT = "Fill 2D"
		}
	}
}
