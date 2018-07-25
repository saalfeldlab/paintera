package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Optional;
import java.util.function.LongSupplier;

import gnu.trove.set.hash.TLongHashSet;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface FragmentSegmentAssignment
{

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * @param fragmentId
	 *
	 * @return Segment id for a fregment. If fragment is not part of a segment, return fragmentId
	 */
	public long getSegment(final long fragmentId);

	/**
	 * @param segmentId
	 *
	 * @return Set of all fragments contained in a segment
	 */
	public TLongHashSet getFragments(final long segmentId);

	public void apply(AssignmentAction action);

	public void apply(Collection<? extends AssignmentAction> actions);

	// TODO should get<TYPE>Action be part of interface?
	public default Optional<Merge> getMergeAction(
			final long from,
			final long into,
			final LongSupplier newSegmentId)
	{
		return Optional.empty();
	}

	public default Optional<Detach> getDetachAction(
			final long fragmentId,
			final long from)
	{
		return Optional.empty();
	}

	public default Optional<AssignmentAction> getConfirmGroupingAction(
			final long[] fragmentsWithin,
			final long[] fragmentsWihout)
	{
		return Optional.empty();
	}

	public default Optional<AssignmentAction> getConfirmTwoSegmentsAction(
			final long[] segment1,
			final long[] segment2)
	{
		return Optional.empty();
	}

}
