package org.janelia.saalfeldlab.util.fx

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.util.fx.Transforms.relativeScale
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TransformsTest {

	@Test
	fun testRelativeScaleIsExactForIntegerFactors() {

		/* anisotropic resolution; going through AffineTransform3D.inverse() gives 1.9999999999999998 on z */
		var previous = transformOf(3.24, 3.24, 40.0)
		repeat(6) {
			val target = transformOf(previous.get(0, 0) * 2, previous.get(1, 1) * 2, previous.get(2, 2) * 2)
			assertArrayEquals(doubleArrayOf(2.0, 2.0, 2.0), relativeScale(previous, target), 0.0)
			previous = target
		}

		/* anisotropic factors, and an offset that only the target level has */
		val s0 = transformOf(4.0, 4.0, 40.0)
		val s1 = transformOf(8.0, 8.0, 40.0, 2.0, 2.0, 0.0)
		assertArrayEquals(doubleArrayOf(2.0, 2.0, 1.0), relativeScale(s0, s1), 0.0)
	}

	@Test
	fun testNonIntegerFactorsAreNotSnapped() {

		assertArrayEquals(
			doubleArrayOf(1.5, 1.5, 1.5),
			relativeScale(transformOf(2.0, 2.0, 2.0), transformOf(3.0, 3.0, 3.0)),
			0.0
		)

		/* a dimension-derived pyramid, 199999 -> 100000; the closest a real factor gets to an integer */
		val nearlyTwo = 199999.0 / 100000.0
		assertArrayEquals(
			doubleArrayOf(nearlyTwo, nearlyTwo, nearlyTwo),
			relativeScale(transformOf(1.0, 1.0, 1.0), transformOf(nearlyTwo, nearlyTwo, nearlyTwo)),
			0.0
		)
	}

	@Test
	fun testRelativeScaleOverScaleFactors() {

		assertArrayEquals(
			doubleArrayOf(2.0, 2.0, 1.0, 1.0),
			relativeScale(doubleArrayOf(1.0, 1.0, 1.0, 1.0), doubleArrayOf(2.0, 2.0, 1.0, 1.0)),
			0.0
		)

		assertArrayEquals(
			doubleArrayOf(1.0, 1.0, 1.0, 2.0),
			relativeScale(doubleArrayOf(4.0, 4.0, 40.0, 1.0), doubleArrayOf(4.0, 4.0, 40.0, 2.0)),
			0.0
		)
	}

	@Test
	fun testMismatchedDimensionalityIsRejected() {

		assertThrows(IllegalArgumentException::class.java) {
			relativeScale(doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(2.0, 2.0, 1.0, 1.0))
		}
	}

	private fun transformOf(x: Double, y: Double, z: Double, tx: Double = 0.0, ty: Double = 0.0, tz: Double = 0.0) =
		AffineTransform3D().apply { set(x, 0.0, 0.0, tx, 0.0, y, 0.0, ty, 0.0, 0.0, z, tz) }
}
