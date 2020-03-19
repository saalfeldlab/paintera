package org.janelia.saalfeldlab.paintera.ui

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
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

	@Deprecated("Fontawesome is better alternative")
	private val DEFAULT_PATH_SETUP: (Path) -> Unit = {
		it.strokeWidth = 0.0
		it.fill = Color.BLACK
	}

	@Deprecated("Fontawesome is better alternative")
	private val DEFAULT_DEGREES = 160.0

	@Deprecated("Fontawesome is better alternative")
	private val DEFAULT_HEAD_AHEAD = 10.0

	@JvmOverloads
	@JvmStatic
	fun createFontAwesome(scale: Double = 1.0) = FontAwesome[FontAwesomeIcon.REFRESH, scale]

	@Deprecated("Fontawesome is better alternative")
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

	@Deprecated("Fontawesome is better alternative")
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

	@Deprecated("Fontawesome is better alternative")
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

}
