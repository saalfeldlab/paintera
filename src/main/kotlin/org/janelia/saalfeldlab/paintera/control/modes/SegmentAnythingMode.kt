package org.janelia.saalfeldlab.paintera.control.modes

import ai.onnxruntime.OnnxTensor
import bdv.util.Affine3DHelpers
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.value.ChangeListener
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.KeyEvent.KEY_RELEASED
import javafx.scene.input.MouseEvent.MOUSE_PRESSED
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.addWithListener
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.paint.ViewerMask
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.paint.*
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.paintera

class SegmentAnythingMode(val previousMode: ControlMode) : AbstractToolMode() {

	override val defaultTool: Tool? by lazy { samTool }

	private val samTool: SamTool = object : SamTool(activeSourceStateProperty, this@SegmentAnythingMode) {

		private var lastEmbedding: OnnxTensor? = null
		private var globalTransformAtEmbedding = AffineTransform3D()

		init {
			activeViewerProperty.unbind()
			activeViewerProperty.bind(mode!!.activeViewerProperty)
		}

		override fun activate() {
			maskedSource?.resetMasks(false)
			providedEmbedding = if (Affine3DHelpers.equals(paintera.baseView.manager().transform, globalTransformAtEmbedding)) lastEmbedding else null
			super.activate()
		}

		override fun deactivate() {
			super.deactivate()
			lastEmbedding = getImageEmbeddingTask.get()!!
			globalTransformAtEmbedding.set(paintera.baseView.manager().transform)
		}

		override fun setCurrentLabelToSelection() {
			currentLabelToPaint = statePaintContext!!.selectedIds.lastSelection
		}
	}

	private val paintBrushTool = object : PaintBrushTool(activeSourceStateProperty, this@SegmentAnythingMode) {

		override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
			mutableListOf(
				*getBrushActions().filterNot { it.name == CHANGE_BRUSH_DEPTH }.toTypedArray(),
				*getPaintActions().filterNot { it.name == START_BACKGROUND_ERASE }.toTypedArray(),
				segmentAnythingPaintBrushActions(),
				*(midiBrushActions() ?: arrayOf())
			)
		}

		override fun activate() {
			super.activate()
			/* Don't allow painting with depth during shape interpolation */
			brushProperties?.brushDepth = 1.0
			paintClickOrDrag!!.provideMask(samTool.viewerMask!!)
		}

		override fun deactivate() {
			paintClickOrDrag?.release()
			super.deactivate()
		}
	}

	private val fill2DTool = object : Fill2DTool(activeSourceStateProperty, this@SegmentAnythingMode) {


		private val samPredictionOnFill = ChangeListener<Interval?> { _, _, new ->
			new?.let {
				switchTool(samTool)
				samTool.requestPrediction()
			}
		}

		override fun activate() {
			super.activate()
			/* Don't allow filling with depth during shape interpolation */
			brushProperties?.brushDepth = 1.0
			fillLabel = { statePaintContext!!.selectedIds.lastSelection }
			brushProperties?.brushDepth = 1.0
			fill2D.provideMask(samTool.viewerMask!!)
			fill2D.maskIntervalProperty.addListener(samPredictionOnFill)
		}

		override fun deactivate() {
			fill2D.maskIntervalProperty.removeListener(samPredictionOnFill)
			super.deactivate()
		}

		override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
			super.actionSets.also { it += segmentAnythingFloodFillActions(this) }
		}

	}

	override val modeActions by lazy { modeActions() }

	override val allowedActions = AllowedActions.AllowedActionsBuilder()
		.add(PaintActionType.Paint, PaintActionType.Erase, PaintActionType.SetBrushSize, PaintActionType.Fill)
		.create()

	private val toolTriggerListener = ChangeListener<OrthogonalViews.ViewerAndTransforms?> { _, old, new ->
		new?.viewer()?.apply { modeActions.forEach { installActionSet(it) } }
		old?.viewer()?.apply { modeActions.forEach { removeActionSet(it) } }
	}

	override val tools: ObservableList<Tool> by lazy { FXCollections.observableArrayList(paintBrushTool, fill2DTool, samTool) }

	override fun enter() {
		activeViewerProperty.addListener(toolTriggerListener)
		super.enter()
		/* unbind the activeViewerProperty, since we disabled other viewers during ShapeInterpolation mode*/
		activeViewerProperty.unbind()
		/* Try to initialize the tool, if state is valid. If not, change back to previous mode. */
		activeViewerProperty.get()?.viewer()?.let {
			disableUnfocusedViewers()
			switchTool(samTool)
		} ?: paintera.baseView.changeMode(previousMode)
	}

	override fun exit() {
		super.exit()
		enableAllViewers()
		activeViewerProperty.removeListener(toolTriggerListener)
	}

	private fun modeActions(): List<ActionSet> {
		val keyCombinations = paintera.baseView.keyAndMouseBindings.getConfigFor(activeSourceStateProperty.value!!).keyCombinations
		return mutableListOf(
			painteraActionSet(LabelSourceStateKeys.EXIT_SEGMENT_ANYTHING_MODE) {

				verifyAll(KEY_PRESSED, "Sam Tool is Active ") { activeTool == samTool }
				KEY_PRESSED {
					graphic = { FontAwesomeIconView().apply { styleClass += listOf("toolbar-tool", "reject", "reject-segment-anything") } }
					keyMatchesBinding(keyCombinations, LabelSourceStateKeys.EXIT_SEGMENT_ANYTHING_MODE)
					onAction {
						paintera.baseView.changeMode(previousMode)
					}
				}
			},
			painteraActionSet("paint during segment anything", PaintActionType.Paint) {
				KEY_PRESSED(*paintBrushTool.keyTrigger.toTypedArray()) {
					name = "switch to paint tool"
					val getViewerMask = { (activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.currentMask as? ViewerMask }
					verify { getViewerMask() != null }
					onAction {
						switchTool(paintBrushTool)
					}
				}

				KEY_RELEASED(*paintBrushTool.keyTrigger.toTypedArray()) {
					name = "switch back to segment anything tool from paint brush"
					filter = true
					verify { activeTool is PaintBrushTool }
					onAction { switchTool(samTool) }
				}

				KEY_PRESSED(*fill2DTool.keyTrigger.toTypedArray()) {
					name = "switch to fill2d tool"
					verify { activeSourceStateProperty.get()?.dataSource is MaskedSource<*, *> }
					onAction { switchTool(fill2DTool) }
				}
				KEY_RELEASED(*fill2DTool.keyTrigger.toTypedArray()) {
					name = "switch to segment anything tool from fill2d"
					filter = true
					verify { activeTool is Fill2DTool }
					onAction {
						switchTool(samTool)
					}
				}
			}
		)
	}

	/**
	 * Additional paint brush actions for Segment Anything
	 *
	 * @receiver the tool to add the actions to
	 * @return the additional action sets
	 */
	private fun PaintBrushTool.segmentAnythingPaintBrushActions(): ActionSet {

		return painteraActionSet("Segment Anything Paint Brush Actions", PaintActionType.SegmentAnything) {
			MOUSE_PRESSED {
				name = "provide SAM tool mask to paint brush"
				filter = true
				consume = false
				verify { activeTool == this@segmentAnythingPaintBrushActions }
				onAction {
					/* On click, generate a new mask, */
					(activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
						paintClickOrDrag!!.let { paintController ->
							source.resetMasks(true)
							paintController.provideMask(samTool.viewerMask!!)
						}
					}
				}
			}
		}
	}

	/**
	 * Additional fill actions for Segment Anything
	 *
	 * @param floodFillTool
	 * @return the additional ActionSet
	 *
	 * */
	private fun segmentAnythingFloodFillActions(floodFillTool: Fill2DTool): ActionSet {
		return painteraActionSet("Segment Anything Fill 2D Actions", PaintActionType.SegmentAnything) {
			MOUSE_PRESSED {
				name = "provide SAM tool mask to fill 2d"
				filter = true
				consume = false
				verify { activeTool == floodFillTool }
				onAction {
					/* On click, provide the mask, setup the task listener */
					(activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
						source.resetMasks(true)
						val mask = samTool.viewerMask!!
						fill2DTool.run {
							fillTaskProperty.addWithListener { obs, _, task ->
								task?.let {
									task.onCancelled(true) { _, _ ->
										source.resetMasks(true)
										mask.requestRepaint()
									}
									task.onEnd(true) { obs?.removeListener(this) }
								} ?: obs?.removeListener(this)
							}
							fill2D.provideMask(mask)
						}
					}
				}
			}
		}
	}
}