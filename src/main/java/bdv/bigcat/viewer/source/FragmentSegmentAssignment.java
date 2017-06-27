package bdv.bigcat.viewer.source;

public interface FragmentSegmentAssignment
{

	public default long getSegment( final long fragmentId )
	{
		return fragmentId;
	}

	public default long[] getFragments( final long segmentId )
	{
		return new long[] { segmentId };
	}

}
