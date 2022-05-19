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

  // TODO should get<TYPE>Action be part of interface?
  default Optional<Merge> getMergeAction(
		  final long from,
		  final long into,
		  final LongSupplier newSegmentId) {

	return Optional.empty();
  }

  default Optional<Detach> getDetachAction(
		  final long fragmentId,
		  final long from) {

	return Optional.empty();
  }

  default Optional<AssignmentAction> getConfirmGroupingAction(
		  final long[] fragmentsWithin,
		  final long[] fragmentsWithout) {

	return Optional.empty();
  }

  default Optional<AssignmentAction> getConfirmTwoSegmentsAction(
		  final long[] segment1,
		  final long[] segment2) {

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
	  // (it uses the first such found fragment, so if there are several, one of them will be picked arbitrarily because the order of the ids is not defined)
	  long idWithAssignment = Label.INVALID;
	  for (final long id : ids) {
		final long segmentId = assignment.getSegment(id);
		if (segmentId != id) {
		  idWithAssignment = id;
		  break;
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
