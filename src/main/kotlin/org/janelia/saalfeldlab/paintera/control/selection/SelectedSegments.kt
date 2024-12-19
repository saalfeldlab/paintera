package org.janelia.saalfeldlab.paintera.control.selection

import gnu.trove.TCollections
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import org.janelia.saalfeldlab.fx.ObservableWithListenersList
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState

/**
 * TODO
 *  The update mechanism for this class regenerates the entire segment set
 *  for each update which is inefficient for small edits on large selections.
 *  Eventually, this should be unified with SelectedIds such that
 *  small updates can be implemented as small updates.
 *
 *
 * Alternatively, SelectedIds can pass both the old state and the new state
 * to its listeners and then a diff can be easily generated from that.
 */
class SelectedSegments(val selectedIds: SelectedIds, val assignment: FragmentSegmentAssignmentState) : ObservableWithListenersList() {
	/**
	 * Package protected for [SelectedSegments] internal use.
	 *
	 * @return
	 */
	val set: TLongHashSet = TLongHashSet()

	init {

		this.selectedIds.addListener { update() }
		this.assignment.addListener { update() }
	}

	fun getSelectedSegments(): TLongSet {
		return TCollections.unmodifiableSet(this.set)
	}

	val selectedSegmentsCopyAsArray: LongArray?
		get() {
			synchronized(this.set) {
				return set.toArray()
			}
		}

	fun isSegmentSelected(id: Long): Boolean {
		return set.contains(id)
	}

	private fun update() {
		synchronized(set) {
			set.clear()
			val selectedIds = selectedIds.set
			synchronized(selectedIds) {
				selectedIds.forEach { id ->
					set.add(assignment.getSegment(id))
					true
				}
			}
		}
		stateChanged()
	}
}