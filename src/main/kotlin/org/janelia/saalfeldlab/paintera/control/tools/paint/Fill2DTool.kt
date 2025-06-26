package org.janelia.saalfeldlab.paintera.control.tools.paint

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import kotlinx.coroutines.*
import net.imglib2.Interval
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.*
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill2D
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.smallestContainingInterval
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

private val LOG = KotlinLogging.logger { }

open class Fill2DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) :
	PaintTool(activeSourceStateProperty, mode) {

	override fun newToolBarControl() = super.newToolBarControl().also { item ->
		item.addStyleClass("fill-2d")
	}

	override val name = "Fill 2D"
	override val keyTrigger = LabelSourceStateKeys.FILL_2D
	var fillLabel: () -> Long = { statePaintContext?.paintSelection?.invoke() ?: Label.INVALID }

	val fill2D by LazyForeignValue({ statePaintContext }) {
		with(it!!) {
			val floodFill2D = FloodFill2D(
				activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
				dataSource,
			) { activeSourceStateProperty.value?.isVisibleProperty?.get() ?: false }
			floodFill2D.fillDepthProperty.bindBidirectional(brushProperties.brushDepthProperty)
			floodFill2D
		}
	}

	val fillJobProperty: SimpleObjectProperty<Job> = SimpleObjectProperty(null)
	var fillJob by fillJobProperty.nullable()
		private set

	protected open val afterFill : (Interval) -> Unit = {}


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

		runBlocking {
			fillJob?.join()
			overlay.visible = false
			fill2D.release()
			super.deactivate()
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
						executeFill2DAction(it!!.x, it.y, afterFill = afterFill)
					}
				}
			},
			painteraActionSet(LabelSourceStateKeys.CANCEL, ignoreDisable = true) {
				KeyEvent.KEY_PRESSED(LabelSourceStateKeys.CANCEL) {
					name = "Cancel Fill 2D"
					createToolNode = { apply { addStyleClass(Style.REJECT_ICON)} }
					filter = true
					onAction {
						cancelFloodFill()
						mode?.switchTool(mode.defaultTool)
					}
				}
			}
		)
	}

	fun cancelFloodFill() = fillJob?.cancel(CancellationException("Fill task cancelled by user", null))


	@OptIn(ExperimentalCoroutinesApi::class)
	internal fun executeFill2DAction(x: Double, y: Double, afterFill: (Interval) -> Unit = {}): Job? {

		val applyIfMaskNotProvided = fill2D.viewerMask == null
		if (applyIfMaskNotProvided) {
			statePaintContext!!.dataSource.resetMasks(true)
		}


		val fillIsRunningProperty = SimpleBooleanProperty()
		paintera.baseView.disabledPropertyBindings[this] = fillIsRunningProperty
		fillJob = CoroutineScope(Dispatchers.Default).async {
			fillIsRunningProperty.set(true)
			fill2D.fillViewerAt(x, y, fillLabel(), statePaintContext!!.assignment)
		}.also { job ->
			job.invokeOnCompletion { cause ->
				fillIsRunningProperty.set(false)
				cause?.let {
					if (it is CancellationException) LOG.trace(it) {}
					else LOG.error(it) {"Flood Fill 2D Failed"}
					if (applyIfMaskNotProvided) {
						/* Then apply when done */
						val source = statePaintContext!!.dataSource
						val mask = source.currentMask as ViewerMask
						source.resetMasks(true)
						mask.requestRepaint()
					}
					cleanup()

					return@invokeOnCompletion
				}


				val maskFillInterval = job.getCompleted()
				afterFill(maskFillInterval)
				if (applyIfMaskNotProvided) {
					/* Then apply when done */
					val source = statePaintContext!!.dataSource
					val mask = source.currentMask as ViewerMask
					val affectedSourceInterval = mask.currentMaskToSourceWithDepthTransform.estimateBounds(maskFillInterval).smallestContainingInterval
					source.applyMask(mask, affectedSourceInterval, net.imglib2.type.label.Label::isForeground)
				}
				cleanup()
			}
		}
		return fillJob
	}

	private fun cleanup() {
		paintera.baseView.disabledPropertyBindings -= this
		fillJob = null
	}

	private class Fill2DOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayWithText(viewerProperty) {

		val brushDepthProperty = SimpleDoubleProperty().apply { subscribe { _ -> viewer?.display?.drawOverlays() } }
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
