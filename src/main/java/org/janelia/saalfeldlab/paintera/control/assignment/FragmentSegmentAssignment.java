package org.janelia.saalfeldlab.paintera.control.assignment;

import java.util.Optional;

import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;

import gnu.trove.set.hash.TLongHashSet;

public interface FragmentSegmentAssignment
{

	/**
	 *
	 * @param fragmentId
	 * @return Segment id for a fregment. If fragment is not part of a segment,
	 *         return fragmentId
	 */
	public long getSegment( final long fragmentId );

	/**
	 *
	 *
	 * @param segmentId
	 * @return Set of all fragments contained in a segment
	 */
	public TLongHashSet getFragments( final long segmentId );

	/**
	 * Merge fragments
	 *
	 * @param fragment1
	 * @param fragment2
	 */
	public Optional< Merge > mergeFragments( final long fragment1, long fragment2 );

	/**
	 * TODO should this have only one argument (always detach fragment) or two
	 * (only detach if correct segment is selected already)?
	 *
	 * @param fragmentId
	 * @param from
	 */
	public Optional< Detach > detachFragment( final long fragmentId, long from );

	/**
	 *
	 * @param groupedFragments
	 * @param notInGroupFragments
	 */
	public void confirmGrouping( final long[] groupedFragments, final long[] notInGroupFragments );

	/**
	 *
	 * @param fragmentsInSegment1
	 * @param fragmentsInSegment2
	 */
	public void confirmTwoSegments( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 );

}
