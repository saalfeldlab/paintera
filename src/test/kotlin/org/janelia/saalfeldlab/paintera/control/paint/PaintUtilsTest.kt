package org.janelia.saalfeldlab.paintera.control.paint

import net.imglib2.realtransform.AffineTransform3D
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.math.cos
import kotlin.math.sin

import kotlin.math.sqrt

class PaintUtilsTest {

	@Test
	fun testLabelAxisCorrespondingToViewerAxis() {
		LOG.warn("Test not implemented yet: {}.{}", PaintUtilsTest::class.java.simpleName, object {}.javaClass.enclosingMethod?.name)
//        val smallestRotationInDegrees = 15.0
//        val smallestRotationInRadians = Math.toRadians(15.0)
//        val largestRotationInDegrees = 45.0
//        val largestRotationInRadians = Math.toRadians(largestRotationInDegrees)
//
//        val tolerance = 1.0 - cos(smallestRotationInRadians)
//
//        val labelToGlobalTransform = AffineTransform3D().also {
//            it.set(42.0, 0, 0)
//            it.set(23.0, 1, 1)
//            it.set(91.0, 2, 2)
//            it.setTranslation(1234.0, 719.12, 11.2)
//        }
//
//        val viewerTransform = AffineTransform3D().also { it.scale(23.0) }
//
//        // check x axis
//        fail()
//        viewerTransform.copy().let {
//            it.rotate(1, smallestRotationInRadians)
//            it.rotate(0, largestRotationInRadians)
//            assertEquals(
//                0,
//                PaintUtils.labelAxisCorrespondingToViewerAxis(labelToGlobalTransform, it, 0, tolerance))
//            assertEquals(
//                -1,
//                PaintUtils.labelAxisCorrespondingToViewerAxis(labelToGlobalTransform, it, 0, tolerance / 2.0))
//
//        }
	}

	@Test
	fun testViewerAxisInLabelCoordinates() {
		val arbitraryTranslation = doubleArrayOf(10.35135, 0.436234, 35926.946)

		val transform = AffineTransform3D().also {
			it.set(1.0, 0, 0)
			it.set(2.0, 1, 1)
			it.set(5.0, 2, 2)
			it.setTranslation(*arbitraryTranslation.reversedArray())
		}

		val length = 3.0
		val scale = 2.5

		// with scaled identity viewer transform (translation should not matter):
		AffineTransform3D()
			.also { it.scale(scale) }
			.also { it.setTranslation(*arbitraryTranslation) }
			.let { viewerTransform ->
				// transform are all forward, i.e. labels -> global space and global space -> viewer. Need to
				// invert both to get correctly scaled vector, i.e. length / scale / labelTransform[i, i]

				// x axis
				assertArrayEquals(
					doubleArrayOf(length / scale / transform[0, 0], 0.0, 0.0),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform, 0, length),
					1e-8
				)

				// y axis
				assertArrayEquals(
					doubleArrayOf(0.0, length / scale / transform[1, 1], 0.0),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform, 1, length),
					1e-8
				)

				// z axis
				assertArrayEquals(
					doubleArrayOf(0.0, 0.0, length / scale / transform[2, 2]),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform, 2, length),
					1e-8
				)

			}

		// with scaled and rotated identity viewer transform (translation should not matter):
		AffineTransform3D()
			.also { it.scale(scale) }
			.also { it.setTranslation(*arbitraryTranslation) }
			.let { viewerTransform ->
				// transform are all forward, i.e. labels -> global space and global space -> viewer. Need to
				// invert both to get correctly scaled vector, i.e. length / scale / labelTransform[i, i]
				val angle = Math.PI / 3.0 // 60°

				// rotate around z axis
				assertArrayEquals(
					doubleArrayOf(length / scale / transform[0, 0] * cos(angle), length / scale / transform[1, 1] * -sin(angle), 0.0),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(2, angle) }, 0, length),
					1e-8
				)
				assertArrayEquals(
					doubleArrayOf(0.0, 0.0, length / scale / transform[2, 2]),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(2, angle) }, 2, length),
					1e-8
				)

				// rotate around x axis
				assertArrayEquals(
					doubleArrayOf(0.0, length / scale / transform[1, 1] * cos(angle), length / scale / transform[2, 2] * -sin(angle)),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(0, angle) }, 1, length),
					1e-8
				)
				assertArrayEquals(
					doubleArrayOf(length / scale / transform[0, 0], 0.0, 0.0),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(0, angle) }, 0, length),
					1e-8
				)

				// rotate around y axis
				assertArrayEquals(
					doubleArrayOf(length / scale / transform[0, 0] * -sin(angle), 0.0, length / scale / transform[2, 2] * cos(angle)),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(1, angle) }, 2, length),
					1e-8
				)
				assertArrayEquals(
					doubleArrayOf(0.0, length / scale / transform[1, 1], 0.0),
					PaintUtils.viewerAxisInLabelCoordinates(transform, viewerTransform.copy().also { it.rotate(1, angle) }, 1, length),
					1e-8
				)

			}
	}

	@Test
	fun testMaximumVoxelDiagonalLengthPerDimension() {
		val arbitraryTranslation = doubleArrayOf(10.35135, 0.436234, 35926.946)

		val transform = AffineTransform3D().also {
			it.set(1.0, 0, 0)
			it.set(2.0, 1, 1)
			it.set(5.0, 2, 2)
			it.setTranslation(*arbitraryTranslation.reversedArray())
		}


		val scale = 2.5

		// if axis aligned, just multiply the scale with the voxel size along each dimension
		AffineTransform3D()
			.also { it.scale(scale) }
			.also { it.setTranslation(*arbitraryTranslation) }
			.let { viewerTransform ->
				assertArrayEquals(
					DoubleArray(3) { transform[it, it] * scale },
					PaintUtils.maximumVoxelDiagonalLengthPerDimension(transform, viewerTransform),
					0.0
				)
			}

		val angle = Math.PI / 3.0 // 60°
		AffineTransform3D()
			.also { it.scale(scale) }
			.also { it.setTranslation(*arbitraryTranslation) }
			.let { viewerTransform ->

				// rotate around z axis
				assertArrayEquals(
					// maximumVoxelDiagonalLengthPerDimension is now calculated with L2 norm
					doubleArrayOf(
						scale * sqrt((cos(angle) * transform[0, 0]).square() + (-sin(angle) * transform[1, 1]).square()),
						scale * sqrt((sin(angle) * transform[0, 0]).square() + (cos(angle) * transform[1, 1]).square()),
						scale * transform[2, 2]
					),
					// but was using L1/Manhattan
//                    doubleArrayOf(
//                        scale * ((cos(angle)*transform[0, 0]).abs() + (-sin(angle)*transform[1, 1]).abs()),
//                        scale * ((sin(angle)*transform[0, 0]).abs() + (cos(angle)*transform[1, 1]).abs()),
//                        scale * transform[2, 2]),
					PaintUtils.maximumVoxelDiagonalLengthPerDimension(transform, viewerTransform.copy().also { it.rotate(2, angle) }),
					1e-8
				)

				// rotate around x axis
				assertArrayEquals(
					// maximumVoxelDiagonalLengthPerDimension is now calculated with L2 norm
					doubleArrayOf(
						scale * transform[0, 0],
						scale * sqrt((cos(angle) * transform[1, 1]).square() + (-sin(angle) * transform[2, 2]).square()),
						scale * sqrt((sin(angle) * transform[1, 1]).square() + (cos(angle) * transform[2, 2]).square())
					),
					// but was using L1/Manhattan
//                    doubleArrayOf(
//                        scale * transform[0, 0],
//                        scale * ((cos(angle)*transform[1, 1]).abs() + (-sin(angle)*transform[2, 2]).abs()),
//                        scale * ((sin(angle)*transform[1, 1]).abs() + (cos(angle)*transform[2, 2]).abs())),
					PaintUtils.maximumVoxelDiagonalLengthPerDimension(transform, viewerTransform.copy().also { it.rotate(0, angle) }),
					1e-8
				)

				// rotate around y axis
				assertArrayEquals(
					// maximumVoxelDiagonalLengthPerDimension is now calculated with L2 norm
					doubleArrayOf(
						scale * sqrt((cos(angle) * transform[0, 0]).square() + (-sin(angle) * transform[2, 2]).square()),
						scale * transform[1, 1],
						scale * sqrt((sin(angle) * transform[0, 0]).square() + (cos(angle) * transform[2, 2]).square())
					),
					// but was using L1/Manhattan
//                    doubleArrayOf(
//                        scale * ((cos(angle)*transform[0, 0]).abs() + (-sin(angle)*transform[2, 2]).abs()),
//                        scale * transform[1, 1],
//                        scale * ((sin(angle)*transform[0, 0]).abs() + (cos(angle)*transform[2, 2]).abs())),
					PaintUtils.maximumVoxelDiagonalLengthPerDimension(transform, viewerTransform.copy().also { it.rotate(1, angle) }),
					1e-8
				)
			}
	}

	@Test
	fun testDuplicateWithoutTranslation() {
		val groundTruth = nonSingularMatrixWithoutTranslation()
		val withTranslation = nonSingularMatrixWithTranslation()
		LOG.debug("wihTranslation={}", withTranslation)
		LOG.debug("groundTruth=   {}", groundTruth)
		val withoutTranslation = PaintUtils.duplicateWithoutTranslation(withTranslation)
		assertArrayEquals(groundTruth.rowPackedCopy, withoutTranslation.rowPackedCopy, 0.0)
		assertArrayEquals(groundTruth.rowPackedCopy, PaintUtils.duplicateWithoutTranslation(withoutTranslation).rowPackedCopy, 0.0)
	}

	@Test
	fun testRemoveTranslation() {
		assertArrayEquals(
			nonSingularMatrixWithoutTranslation().rowPackedCopy,
			nonSingularMatrixWithTranslation().also { PaintUtils.removeTranslation(it) }.rowPackedCopy,
			0.0
		)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun nonSingularMatrixWithoutTranslation() = AffineTransform3D().also {
			// Generate a non-singular matrix from a digaonal matrix through rotations.
			it.set(52.46, 0, 0)
			it.set(401.406, 1, 1)
			it.set(0.3151, 2, 2)
			it.rotate(0, Math.PI / 8.0)
			it.rotate(1, Math.PI / 3.0)
			it.rotate(2, Math.PI / 7.0 * 5.0)
		}

		private fun nonSingularMatrixWithTranslation() = nonSingularMatrixWithoutTranslation().also {
			it.setTranslation(1035.1, 87.345, 0.034)
		}

		private fun Double.square() = this * this

		private fun Double.abs() = kotlin.math.abs(this)

	}
}
