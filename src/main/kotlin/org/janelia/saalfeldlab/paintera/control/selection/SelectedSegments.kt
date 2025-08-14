package org.janelia.saalfeldlab.paintera.control.selection

import gnu.trove.TCollections
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import org.janelia.saalfeldlab.fx.ObservableWithListenersList
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import java.util.stream.Collectors

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

	private val set: TLongHashSet = TLongHashSet()
	val segments: TLongSet = TCollections.unmodifiableSet(set)


	init {

		this.selectedIds.addListener { update() }
		this.assignment.addListener { update() }
	}

	val selectedSegmentsCopyAsArray: LongArray?
		get() {
			synchronized(segments) {
				return set.toArray()
			}
		}

	fun isSegmentSelected(id: Long): Boolean {
		return set.contains(id)
	}

	private fun update() {
		val newSegments  = selectedIds.parallelStream().use {
			it
				.mapToObj { id -> assignment.getSegment(id) }
				.collect(Collectors.toSet())

		}
		synchronized(segments) {
			set.clear()
			set.addAll(newSegments)
		}
		stateChanged()
	}
}