package bdv.bigcat.viewer.state;

public abstract class FragmentSegmentAssignmentState< T extends FragmentSegmentAssignmentState< T > > extends AbstractState< T > implements FragmentSegmentAssignment
{

	protected abstract void assignFragmentsImpl( final long segmentId1, final long segmentId2 );

	protected abstract void mergeSegmentsImpl( final long segmentId1, final long segmentId2 );

	protected abstract void detachFragmentImpl( final long fragmentId );

	@Override
	public void assignFragments( final long segmentId1, final long segmentId2 )
	{
		assignFragmentsImpl( segmentId1, segmentId2 );
		stateChanged();
	}

	@Override
	public void mergeSegments( final long segmentId1, final long segmentId2 )
	{
		mergeSegmentsImpl( segmentId1, segmentId2 );
		stateChanged();
	}

	@Override
	public void mergeFragmentSegments( final long fragmentId1, final long fragmentId2 )
	{
		final long segmentId1, segmentId2;
		segmentId1 = getSegment( fragmentId1 );
		segmentId2 = getSegment( fragmentId2 );
		mergeSegments( segmentId1, segmentId2 );
		stateChanged();
	}

	@Override
	public void detachFragment( final long fragmentId )
	{
		detachFragmentImpl( fragmentId );
		stateChanged();
	}

}
