package bdv.bigcat.viewer.state;

import gnu.trove.set.hash.TLongHashSet;

public interface FragmentSegmentAssignment
{

	public long getSegment( final long fragmentId );

	public TLongHashSet getFragments( final long segmentId );

	public void assignFragments( final long segmentId1, final long segmentId2 );

	public void mergeSegments( final long segmentId1, final long segmentId2 );

	public void mergeFragments( final long... fragments );

	public void detachFragment( final long fragmentId, long... from );

	public void confirmGrouping( final long[] groupedFragments, final long[] notInGroupFragments );

	public void confirmTwoSegments( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 );

}
