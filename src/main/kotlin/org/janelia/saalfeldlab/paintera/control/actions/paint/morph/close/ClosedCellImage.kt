package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.close

import gnu.trove.set.hash.TLongHashSet
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessible
import net.imglib2.RandomAccessibleInterval
import net.imglib2.cache.img.DiskCachedCellImg
import net.imglib2.cache.img.DiskCachedCellImgFactory
import net.imglib2.cache.img.DiskCachedCellImgOptions
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.asRealInterval
import org.janelia.saalfeldlab.paintera.util.IntervalHelpers.Companion.extendBy
import org.janelia.saalfeldlab.util.intersect
import org.janelia.saalfeldlab.util.isEmpty
import kotlin.math.min

data class ClosedCellImage(
	val initLabelsImg: RandomAccessibleInterval<UnsignedLongType>,
	val img: DiskCachedCellImg<UnsignedLongType, *>,
	val closedLabels: LongArray,
	val intervalsToClose: Set<Interval>,
	val gapSize: Int,
	val iterations: Int,
	val fillableLabels: LongArray?,
	val replacementLabel: Long?,
	val fragmentToSegment: ((Long) -> Long)?,
	val padding: IntArray,
) {

	/* the result only depends on these parameters, so a full match can be reused as-is */
	fun canReuse(
		labelsToClose: LongArray,
		intervalsToClose: Set<Interval>,
		gapSize: Int,
		iterations: Int,
		fillableLabels: LongArray?,
		replacementLabel: Long?,
		fragmentToSegment:((Long) -> Long)?,
	): Boolean =
		closedLabels.contentEquals(labelsToClose)
				&& intervalsToClose == this.intervalsToClose
				&& gapSize == this.gapSize
				&& iterations == this.iterations
				&& fillableLabels.contentEquals(this.fillableLabels)
				&& replacementLabel == this.replacementLabel
				&& fragmentToSegment == this.fragmentToSegment

	companion object {
		/**
		 * Creates a new closed cell image
		 *
		 * @param initialLabels
		 * @param labelsToClose
		 * @param gapSize largest voxel gap closed per iteration
		 * @param iterations fills from one pass can flank the next
		 * @param fillableLabels labels that may be overwritten when filling a gap; null lets any regular label fill
		 * @param replacementLabel fill with this label instead of the flanking target label
		 * @param fragmentSegmentLookup fragment -> segment; a gap only fills when both flanks share a group. null groups each label by itself
		 * @param intervalsToClose if not empty, cells that don't intersect with any of these intervals will be left unprocessed.
		 * @param cellDimensions optional override
		 * @return the closed cell image
		 */
		fun createClosedCellImage(
			initialLabels: RandomAccessibleInterval<UnsignedLongType>,
			labelsToClose: LongArray,
			gapSize: Int,
			iterations: Int,
			fillableLabels: LongArray?,
			replacementLabel: Long? = null,
			fragmentSegmentLookup: ((Long) -> Long)? = null,
			intervalsToClose: Set<Interval> = emptySet(),
			cellDimensions: IntArray? = null,
		): ClosedCellImage {

			val numDimensions = initialLabels.numDimensions()
			/* each iteration can fill up to a gap run beyond the previous one's reach */
			val halo = gapSize * iterations
			val padding = IntArray(numDimensions) { halo }
			val cellDimensions = cellDimensions ?: IntArray(numDimensions) { 32 }

			val closeSet = TLongHashSet(labelsToClose)
			val targetSet = TLongHashSet(labelsToClose)
			/* the specified fill label is preserved and flanks itself in later passes */
			replacementLabel?.let { targetSet.add(it) }
			/* reserved labels are negative as signed longs, so null (Any) still excludes INVALID and TRANSPARENT */
			val fillableSet = fillableLabels?.let { TLongHashSet(it) }
			val isFillable: (Long) -> Boolean = fillableSet?.let { set -> set::contains } ?: { it >= 0 }

			/* a fragment is a target if listed directly, or if its segment is listed */
			fun isTarget(value: Long): Boolean =
				targetSet.contains(value) || fragmentSegmentLookup?.let { closeSet.contains(it(value)) } == true

			/**
			 * A gap is closable if bounded by the same fragment, or by fragments of the same segment
			 * when that segment id is itself in [labelsToClose].
			 *
			 * @param flankA one of the values adjacent to the gap
			 * @param flankB the other values adjacent to the gap
			 * @return `true` if the condition holds, `false` otherwise
			 */
			fun sameGroup(flankA: Long, flankB: Long): Boolean {
				if (flankA == flankB)
					return true
				/* the specified fill label bridges with any target, so iterations compose */
				if (replacementLabel != null && (flankA == replacementLabel || flankB == replacementLabel))
					return true
				val lookup = fragmentSegmentLookup ?: return false
				val segment = lookup(flankA)
				return segment == lookup(flankB) && closeSet.contains(segment)
			}

			val closeExtent = intervalsToClose.map { it.extendBy(*padding) }

			/**
			 * Process the block if it intersects the blocks with label (plus padding) OR the blocksWithLabel is empty.
			 *
			 * @param interval to test for intersection
			 * @return if the block should be processed or not
			 */
			fun blockOutOfRange(interval: Interval): Boolean {
				if (closeExtent.isEmpty())
					return false
				return closeExtent.all { (it intersect interval.asRealInterval).isEmpty() }
			}

			val extendedInitialLabels = Views.extendValue(initialLabels, Label.INVALID)

			val closedImg = DiskCachedCellImgFactory(
				UnsignedLongType(Label.TRANSPARENT),
				DiskCachedCellImgOptions.options().cellDimensions(*cellDimensions)
			).create(initialLabels) { target ->

				if (blockOutOfRange(target)) {
					target.forEach { it.set(Label.TRANSPARENT) }
					return@create
				}

				closeCell(target, extendedInitialLabels, ::isTarget, isFillable, gapSize, iterations, replacementLabel, ::sameGroup)
			}

			return ClosedCellImage(
				initialLabels,
				closedImg,
				labelsToClose,
				intervalsToClose,
				gapSize,
				iterations,
				fillableLabels,
				replacementLabel,
				fragmentSegmentLookup,
				padding
			)
		}

		/**
		 * Run all iterations for one output cell on local double buffers. The input is read once into a
		 * buffer padded by `gapSize * iterations`; each pass reads one buffer and writes the other, so
		 * the valid region shrinks by the gap size per pass and the core cell is exact after the last.
		 */
		private fun closeCell(
			target: RandomAccessibleInterval<UnsignedLongType>,
			extendedInput: RandomAccessible<UnsignedLongType>,
			isTarget: (Long) -> Boolean,
			isFillable: (Long) -> Boolean,
			gapSize: Int,
			iterations: Int,
			replacementLabel: Long?,
			sameGroup: (Long, Long) -> Boolean,
		) {
			val numDimensions = target.numDimensions()
			val halo = gapSize * iterations
			val paddedMin = LongArray(numDimensions) { target.min(it) - halo }
			val paddedDims = IntArray(numDimensions) { (target.dimension(it) + 2L * halo).toInt() }
			val strides = IntArray(numDimensions)
			var numElements = 1
			for (d in 0 until numDimensions) {
				strides[d] = numElements
				numElements *= paddedDims[d]
			}

			var readBuffer = LongArray(numElements)
			var writeBuffer = LongArray(numElements)

			val paddedInterval = FinalInterval(paddedMin, LongArray(numDimensions) { paddedMin[it] + paddedDims[it] - 1 })
			var fillIndex = 0
			Views.flatIterable(Views.interval(extendedInput, paddedInterval)).forEach { readBuffer[fillIndex++] = it.get() }

			val position = IntArray(numDimensions)
			repeat(iterations) {
				position.fill(0)
				var flatIndex = 0
				while (flatIndex < numElements) {
					val value = readBuffer[flatIndex]
					var result = value
					if (!isTarget(value) && isFillable(value)) {
						/* fill if along any dimension same-group targets flank a fillable run within the gap size */
						for (d in 0 until numDimensions) {
							val stride = strides[d]

							/* scan negative through fillable voxels for the nearest target label */
							var negDistance = 0
							var negLabel = Label.INVALID
							val maxNegative = min(gapSize, position[d])
							var step = 1
							while (step <= maxNegative) {
								val neighbor = readBuffer[flatIndex - step * stride]
								if (isTarget(neighbor)) {
									negDistance = step
									negLabel = neighbor
									break
								}
								if (!isFillable(neighbor))
									break
								step++
							}
							if (negDistance == 0)
								continue

							/* scan positive with the remaining budget; the whole run must fit in the gap size */
							val maxPositive = min(gapSize + 1 - negDistance, paddedDims[d] - 1 - position[d])
							step = 1
							while (step <= maxPositive) {
								val neighbor = readBuffer[flatIndex + step * stride]
								if (isTarget(neighbor)) {
									if (sameGroup(negLabel, neighbor))
										result = replacementLabel ?: negLabel
									break
								}
								if (!isFillable(neighbor))
									break
								step++
							}
							if (result != value)
								break
						}
					}
					writeBuffer[flatIndex] = result

					flatIndex++
					var d = 0
					while (d < numDimensions) {
						if (++position[d] < paddedDims[d]) break
						position[d] = 0
						d++
					}
				}
				val swap = readBuffer
				readBuffer = writeBuffer
				writeBuffer = swap
			}

			/* copy the exact core back into the cell */
			val targetCursor = Views.flatIterable(target).localizingCursor()
			while (targetCursor.hasNext()) {
				val resultLabel = targetCursor.next()
				var flatIndex = 0
				for (d in 0 until numDimensions)
					flatIndex += (targetCursor.getLongPosition(d) - paddedMin[d]).toInt() * strides[d]
				resultLabel.set(readBuffer[flatIndex])
			}
		}
	}
}
