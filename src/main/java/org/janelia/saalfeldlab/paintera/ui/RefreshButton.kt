package org.janelia.saalfeldlab.paintera.ui

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.ArcTo
import javafx.scene.shape.ClosePath
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object RefreshButton {

	private val DEFAULT_PATH_SETUP: (Path) -> Unit = {
		it.strokeWidth = 0.0
		it.fill = Color.BLACK
	}

	private val DEFAULT_DEGREES = 160.0

	private val DEFAULT_HEAD_AHEAD = 10.0

	fun create(
			scale: Double = 1.0,
			pathSetup1: (Path) -> Unit = DEFAULT_PATH_SETUP,
			pathSetup2: (Path) -> Unit = pathSetup1,
			angleDegrees: Double = DEFAULT_DEGREES,
			radius: Double = 1.0,
			width: Double = radius / 10.0,
			tailBehind: Double = 0.0,
			headAhead: Double = DEFAULT_HEAD_AHEAD): Group {
		val (p1, p2) = createPair(pathSetup1, pathSetup2, angleDegrees, radius, width, tailBehind, headAhead)

		return Group(p1, p2)
				.also { it.scaleX = scale }
				.also { it.scaleY = scale }
	}

	fun createPair(
			pathSetup1: (Path) -> Unit = DEFAULT_PATH_SETUP,
			pathSetup2: (Path) -> Unit = pathSetup1,
			angleDegrees: Double = DEFAULT_DEGREES,
			radius: Double = 1.0,
			width: Double = radius / 10.0,
			tailBehind: Double = 0.0,
			headAhead: Double = DEFAULT_HEAD_AHEAD): Pair<Path, Path> {
		val p1 = createCircleArrow(0.0, angleDegrees % 180, radius, width, tailBehind, headAhead).also(pathSetup1)
		val p2 = createCircleArrow(180.0, angleDegrees % 180, radius, width, tailBehind, headAhead).also(pathSetup2)
		return Pair(p1, p2)
	}

	fun createCircleArrow(
			startAngleDegrees: Double = 0.0,
			angleDegrees: Double = DEFAULT_DEGREES,
			radius: Double = 1.0,
			width: Double = radius / 10.0,
			tailBehind: Double = 0.0,
			headAhead: Double = DEFAULT_HEAD_AHEAD): Path {
		val innerRadius = radius - width / 2
		val outerRadius = radius + width / 2
		val outerPoint = outerRadius + width
		val innerPoint = innerRadius - width
		val targetAngleDegrees = (startAngleDegrees + angleDegrees)
		val startAngle = startAngleDegrees * PI / 180.0
		val targetAngle = targetAngleDegrees * PI / 180.0
		val tailAngle = (targetAngleDegrees - tailBehind) * PI / 180.0
		val headAngle = (targetAngleDegrees + headAhead) * PI / 180.0
		return Path(
				MoveTo(outerRadius * cos(startAngle), -outerRadius * sin(startAngle)),
				ArcTo(outerRadius, outerRadius, 0.0, outerRadius * cos(targetAngle), -outerRadius * sin(targetAngle), false, false),
				LineTo(outerPoint * cos(tailAngle), -outerPoint * sin(tailAngle)),
				LineTo(radius * cos(headAngle), -radius * sin(headAngle)),
				LineTo(innerPoint * cos(tailAngle), -innerPoint * sin(tailAngle)),
				LineTo(innerRadius * cos(targetAngle), -innerRadius * sin(targetAngle)),
				ArcTo(innerRadius, innerRadius, 0.0, innerRadius * cos(startAngle), -innerRadius * sin(startAngle), false, true),
				ClosePath())
	}

//	@JvmStatic
//	@JvmOverloads
//    fun create(
//			radius: Double,
//			startAngle: Double = 0.0,
//			arcExtent: Double = 360.0,
//			lineWidth: Double = 1.0,
//			foreground: Color = Color.BLACK,
//			background: Color? = null,
//			paddingX: Double = 0.0,
//			paddingY: Double = 0.0): Node {
////        val closeBtn = StackPane().also { p -> background?.let { p.background = it } }
////        closeBtn.children.add(drawSemiRing(innerRadius, outerRadius, fill = foreground))
////        return closeBtn
//		val canvas = Canvas(2 * radius + lineWidth + paddingX, 2 * radius + lineWidth + paddingY)
//		val gc = canvas.graphicsContext2D
//		background?.let { gc.fill = it; gc.fill() }
//		gc.stroke = foreground
//		gc.lineWidth = lineWidth
//		gc.strokeArc(0.0 + lineWidth / 2, 0.0 + lineWidth / 2, 2 * radius,  2 * radius, startAngle, arcExtent, ArcType.OPEN)
//
//		val startX = cos(startAngle) * radius
//		val startY = sin(startAngle) * radius
//		val orthogonalDirection = doubleArrayOf(-cos(startAngle), sin(startAngle))
//		val tangentialDirection = doubleArrayOf()
//		println("$startX $startY")
//
//		gc.strokePolygon(
//				doubleArrayOf(startX - orthogonalDirection[0] * 2 * lineWidth, startX + orthogonalDirection[0] * 2 * lineWidth),
//				doubleArrayOf(startY - orthogonalDirection[1] * 2 * lineWidth, startY + orthogonalDirection[1] * 2 * lineWidth),
//				2)
//
//		return canvas
//    }
//
//	// https://stackoverflow.com/a/11720870/1725687
//	private fun drawSemiRing(
//			innerRadius: Double,
//			outerRadius: Double,
//			centerX: Double = 0.0,
//			centerY: Double = 0.0,
//			fill: Color = Color.BLACK,
//			stroke: Color = fill): Path {
//		val path = Path()
//		path.fill = fill
//		path.stroke = stroke
//		path.fillRule = FillRule.EVEN_ODD
//
//		val moveTo = MoveTo()
//		moveTo.x = centerX + innerRadius
//		moveTo.y = centerY
//
//		val arcToInner = ArcTo()
//		arcToInner.x = centerX - innerRadius
//		arcToInner.y = centerY
//		arcToInner.radiusX = innerRadius
//		arcToInner.radiusY = innerRadius
//
//		val moveTo2 = MoveTo()
//		moveTo2.x = centerX + innerRadius
//		moveTo2.y = centerY
//
//		val hLineToRightLeg = HLineTo()
//		hLineToRightLeg.x = centerX + outerRadius
//
//		val arcTo = ArcTo()
//		arcTo.x = centerX - outerRadius
//		arcTo.y = centerY
//		arcTo.radiusX = outerRadius
//		arcTo.radiusY = outerRadius
//
//		val hLineToLeftLeg = HLineTo()
//		hLineToLeftLeg.x = centerX - innerRadius
//
//		path.elements.addAll(moveTo, arcToInner, moveTo2, hLineToRightLeg, arcTo, hLineToLeftLeg)
//
//		return path
//	}

}
