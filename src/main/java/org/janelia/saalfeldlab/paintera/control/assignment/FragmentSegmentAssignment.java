package org.janelia.saalfeldlab.paintera.control.assignment;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

public interface FragmentSegmentAssignment {

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * @param fragmentId
	 * @return Segment id for a fragment. If fragment is not part of a segment, return fragmentId
	 */
	long getSegment(final long fragmentId);

	/**
	 * @param segmentId
	 * @return Set of all fragments contained in a segment
	 */
	TLongHashSet getFragments(final long segmentId);

	void apply(AssignmentAction action);

	void apply(Collection<? extends AssignmentAction> actions);

	/**
	 *  Merge fragment1 and fragment2 into the same segmentId. 3 possible cases
	 *  1. If neither belong to a segment assignment already, create a new one and add both
	 *  2. If both belong to the same segment already, do nothing
	 *  3. If one belongs to a segment already, add the other to the existing segment
	 *  4. If both belong to segment already, merge into the segmentId with the larger numerical value.
	 * @param fragment1 to merge
	 * @param fragment2 to merge
	 * @param newSegmentId generator if a new segment ID is needed
	 * @return the Merge action, or empty
	 */
	// TODO should get<TYPE>Action be part of interface?
	default Optional<Merge> getMergeAction(
			final long fragment1,
			final long fragment2,
			final LongSupplier newSegmentId) {

		return Optional.empty();
	}

	default Optional<Detach> getDetachAction(
			final long fragmentId,
			final long from) {

		return Optional.empty();
	}

	boolean isSegmentConsistent(final long segmentId, final TLongSet containedFragments);

	static void mergeAllSelected(final FragmentSegmentAssignment assignment, final SelectedIds selectedIds, final IdService idService) {

		final long[] ids = selectedIds.getActiveIdsCopyAsArray();
		final long lastSelection = selectedIds.getLastSelection();
		if (ids.length <= 1)
			return;

		final long into;
		if (selectedIds.isLastSelectionValid() && assignment.getSegment(lastSelection) != lastSelection) {
			// last selected fragment belongs to a segment, merge into it to re-use its segment id
			into = lastSelection;
		} else {
			// last selection does not belong to an assignment or is empty, go over all selected fragments to see if there is one that belongs to an assignment
			// 	If multiple fragments are found, use the fragment with the largest associated segmentID
			long maxSegmentId = Label.INVALID;
			long idWithAssignment = Label.INVALID;
			for (final long id : ids) {
				final long segmentId = assignment.getSegment(id);
				if (segmentId != id && segmentId > maxSegmentId) {
					maxSegmentId = segmentId;
					idWithAssignment = id;
				}
			}
			if (idWithAssignment != Label.INVALID) {
				// there is a selected fragment that belongs to an assignment, merge into it to re-use its segment id
				into = idWithAssignment;
			} else {
				// all selected fragments do not belong to any assignment, a new segment id will be created for merging
				into = ids[0];
			}
		}

		final List<Merge> merges = new ArrayList<>();
		for (final long id : ids) {
			final Optional<Merge> action = assignment.getMergeAction(id, into, idService::next);
			if (action.isPresent())
				merges.add(action.get());
		}
		assignment.apply(merges);
	}

}
