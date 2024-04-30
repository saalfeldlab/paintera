package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.scene.input.MouseEvent.MOUSE_PRESSED
import net.imglib2.Interval
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.addWithListener
import org.janelia.saalfeldlab.paintera.control.ShapeInterpolationController
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.control.tools.paint.Fill2DTool
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.state.SourceState

internal class ShapeInterpolationFillTool(private val controller : ShapeInterpolationController<*>, activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, val shapeInterpolationMode: ShapeInterpolationMode<*>) : Fill2DTool(activeSourceStateProperty, shapeInterpolationMode) {


	private val controllerPaintOnFill = ChangeListener<Interval?> { _, _, new ->
		new?.let { interval -> shapeInterpolationMode.addSelection(interval)?.also { it.locked = true } }
	}

	override fun activate() {
		super.activate()
		/* Don't allow filling with depth during shape interpolation */
		brushProperties?.brushDepth = 1.0
		fillLabel = { controller.interpolationId }
		fill2D.maskIntervalProperty.addListener(controllerPaintOnFill)
	}

	override fun deactivate() {
		fill2D.maskIntervalProperty.removeListener(controllerPaintOnFill)
		super.deactivate()
	}

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		super.actionSets.also { it += shapeInterpolationMode.extraActions() }
	}



	/**
	 * Additional fill actions for Shape Interpolation
	 *
	 * @return  the additional ActionSet
	 *
	 * */
	private fun ShapeInterpolationMode<*>.extraActions(): ActionSet {
		return painteraActionSet("Shape Interpolation Fill 2D Actions", PaintActionType.ShapeInterpolation) {
			MOUSE_PRESSED {
				name = "provide shape interpolation mask to fill 2d"
				filter = true
				consume = false
				verify { activeTool == this@ShapeInterpolationFillTool }
				onAction {
					/* On click, provide the mask, setup the task listener */
					(activeSourceStateProperty.get()?.dataSource as? MaskedSource<*, *>)?.let { source ->
						source.resetMasks(false)
						val mask = controller.getMask()
						mask.pushNewImageLayer()
						fillTaskProperty.addWithListener { obs, _, task ->
							task?.let {
								task.onCancelled(true) { _, _ ->
									mask.popImageLayer()
									mask.requestRepaint()
								}
								task.onEnd(true) { obs?.removeListener(this) }
							} ?: obs?.removeListener(this)
						}
						fill2D.provideMask(mask)
					}
				}
			}
			switchAndApplyShapeInterpolationActions(this)
		}
	}
}