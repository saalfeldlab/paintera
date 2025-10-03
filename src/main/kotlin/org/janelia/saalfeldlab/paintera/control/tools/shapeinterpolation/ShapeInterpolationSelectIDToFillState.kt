package org.janelia.saalfeldlab.paintera.control.tools.shapeinterpolation

import javafx.scene.input.MouseEvent
import net.imglib2.Point
import net.imglib2.RealPoint
import net.imglib2.Volatile
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import org.janelia.saalfeldlab.fx.actions.verifiable
import org.janelia.saalfeldlab.labels.Label
import org.janelia.saalfeldlab.paintera.control.actions.state.MaskedSourceActionState
import org.janelia.saalfeldlab.paintera.control.modes.ShapeInterpolationMode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.util.extendValue
import org.janelia.saalfeldlab.util.get

class ShapeInterpolationSelectIDToFillState<D, T>() :
	MaskedSourceActionState.FromActiveSource<ConnectomicsLabelState<D, T>, D, T>()
		where D : IntegerType<D>, T : RealType<T>, T : Volatile<D> {

	var shapeInterpolationController by verifiable("Shape Interpolation Mode and Tool must be Active") {
		(paintera.currentMode as? ShapeInterpolationMode<D>)
			?.takeIf { it.activeTool is ShapeInterpolationTool }
			?.controller
	}

	var mask by verifiable {
		maskedSource.resetMasks(false)
		shapeInterpolationController.getMask()
	}

	private fun pointInMask(event : MouseEvent) = pointInMask(event.x, event.y)

	private fun pointInMask(displayX : Double, displayY : Double) = mask.displayPointToMask(displayX, displayY)

	private fun pointInSource(event: MouseEvent) = pointInSource(pointInMask(event))

	private fun pointInSource(pointInMask : Point) = pointInMask.positionAsRealPoint().also { mask.initialMaskToSourceTransform.apply(it, it) }

	fun fillFromViewer(event : MouseEvent) = fillFromViewer(pointInMask(event))

	fun fillFromSource(event : MouseEvent) = fillFromSource(pointInSource(event))

	fun fillFromViewer(pointInMask : Point): Boolean {
		val maskLabel = mask.viewerImg.extendValue(Label.Companion.INVALID)[pointInMask].integerLong
		return maskLabel == shapeInterpolationController.interpolationId
	}

	fun fillFromSource(pointInSource: RealPoint): Boolean {
		val info = mask.info
		val sourceLabel = maskedSource.getInterpolatedDataSource(info.time, info.level, null).getAt(pointInSource).integerLong
		return sourceLabel != Label.Companion.BACKGROUND && sourceLabel.toULong() <= Label.Companion.MAX_ID.toULong()
	}
}