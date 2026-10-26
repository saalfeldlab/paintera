package org.janelia.saalfeldlab.paintera.data.mask

import net.imglib2.RandomAccessible
import net.imglib2.type.label.Label
import net.imglib2.type.logic.BoolType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.numeric.integer.UnsignedLongType
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments
import org.janelia.saalfeldlab.util.convertWith
import java.util.function.LongPredicate

/**
 * [RandomAccessible<BoolType>]s that are true where an applied mask can write to the canvas
 */
object AcceptLabelMasks {

	/**
     * Predicate that determines which segments to accept to reject modification to
	 */
	sealed class SegmentMask(protected val selectedSegments: SelectedSegments) : LongPredicate {

		protected fun matches(label: Long) = selectedSegments.isSegmentSelected(selectedSegments.assignment.getSegment(label))

		/** Accept label modifications only over labels in the [Whitelist.selectedSegments]*/
		class Whitelist(selectedSegments: SelectedSegments) : SegmentMask(selectedSegments) {

			override fun test(label: Long) = matches(label)
		}

        /** Reject label modification over all labels in [Blacklist.selectedSegments] */
		class Blacklist(selectedSegments: SelectedSegments) : SegmentMask(selectedSegments) {

			override fun test(label: Long) = !matches(label)
		}
	}

    /** Don't allow painting over locked labels */
	fun rejectLockedLabels(isLocked: LongPredicate) = LongPredicate { label -> !isLocked.test(label) }

    /** Combine multiple predicates into a single mask  */
	fun <D : RealType<D>> compose(
        canvas: RandomAccessible<UnsignedLongType>,
        background: RandomAccessible<D>,
        acceptPredicates: List<LongPredicate>
	): RandomAccessible<BoolType>? {
		if (acceptPredicates.isEmpty())
			return null

		val predicates = acceptPredicates.toTypedArray()
		return canvas.convertWith(background, BoolType()) { canvasVal, backgroundVal, out ->
			val label = canvasVal.get().takeUnless { it == Label.INVALID }
				?: (backgroundVal as? IntegerType<*>)?.integerLong
				?: backgroundVal.realDouble.toLong()
			out.set(predicates.all { it.test(label) })
		}
	}
}
