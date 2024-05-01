package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.beans.property.SimpleObjectProperty
import javafx.event.Event
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_PRESSED
import javafx.scene.input.MouseEvent.MOUSE_RELEASED
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.midi.MidiActionSet
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintBrushTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.SourceState

internal class ShapeInterpolationPaintBrushTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, private val shapeInterpolationMode: ShapeInterpolationMode<*>) : PaintBrushTool(activeSourceStateProperty, shapeInterpolationMode) {

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*getBrushActions(),
			*getPaintActions(),
			shapeInterpolationMode.extraActions(),
			*(midiBrushActions() ?: emptyArray()),
			*getMidiNavigationActions().toTypedArray()
		)
	}

	private fun getMidiNavigationActions(): List<MidiActionSet> {
		val midiNavActions = listOfNotNull(
			NavigationTool.midiPanActions(),
			NavigationTool.midiSliceActions(),
			NavigationTool.midiZoomActions()
		)
		midiNavActions.forEach { it.verifyAll(Event.ANY, "Not Currently Painting") { !isPainting && shapeInterpolationMode.activeTool == this } }
		return midiNavActions
	}

	override fun activate() {
		/* Don't allow painting with depth during shape interpolation */
		brushProperties?.brushDepth = 1.0
		super.activate()
	}

	override fun deactivate() {
		paintClickOrDrag?.apply {
			if (isPainting()) {
				finishPaintStroke()
			}
			release()
		}
		super.deactivate()
	}

	fun finishPaintStroke() {
		paintClickOrDrag?.let {
			it.maskInterval?.let { interval ->
				shapeInterpolationMode.addSelection(interval)?.also { slice -> slice.locked = true }
			}
		}
	}



	/**
	 *  Additional paint brush actions for Shape Interpolation.
	 *
	 * @receiver the tool to add the actions to
	 * @return the additional action sets
	 */
	private fun ShapeInterpolationMode<*>.extraActions(): ActionSet {

		return painteraActionSet("Shape Interpolation Paint Brush Actions", PaintActionType.ShapeInterpolation) {
			MOUSE_PRESSED {
				name = "provide shape interpolation mask to paint brush"
				filter = true
				consume = false
				verify { activeTool == this@ShapeInterpolationPaintBrushTool }
				onAction {
					/* On click, generate a new mask, */
					(activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
						paintClickOrDrag!!.let { paintController ->
							source.resetMasks(false)
							paintController.provideMask(controller.getMask())
						}
					}
				}
			}

			MOUSE_PRESSED(MouseButton.PRIMARY) {
				name = "set mask value to label"
				filter = true
				consume = false
				verify { activeTool == this@ShapeInterpolationPaintBrushTool }
				onAction {
					paintClickOrDrag?.apply {
						currentLabelToPaint = controller.interpolationId
					}
				}
			}

			MOUSE_PRESSED(MouseButton.SECONDARY) {
				name = "set mask value to transparent label"
				filter = true
				consume = false
				verify { activeTool == this@ShapeInterpolationPaintBrushTool }
				onAction {
					paintClickOrDrag!!.apply {
						currentLabelToPaint = Label.TRANSPARENT
					}
				}
			}

			MOUSE_RELEASED {
				name = "set mask value to label from paint"
				filter = true
				consume = false
				verify { activeTool == this@ShapeInterpolationPaintBrushTool }
				onAction { finishPaintStroke() }
			}
			switchAndApplyShapeInterpolationActions(this)
		}
	}
}