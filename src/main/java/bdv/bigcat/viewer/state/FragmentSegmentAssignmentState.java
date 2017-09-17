package bdv.bigcat.viewer.state;

public abstract class FragmentSegmentAssignmentState< T extends FragmentSegmentAssignmentState< T > > extends AbstractState< T > implements FragmentSegmentAssignment
{

	protected abstract void assignFragmentsImpl( final long segmentId1, final long segmentId2 );

	protected abstract void mergeSegmentsImpl( final long segmentId1, final long segmentId2 );

	protected abstract void detachFragmentImpl( final long fragmentId, long... from );

	protected abstract void mergeFragmentsImpl( final long... fragments );

	protected abstract void confirmGroupingImpl( final long[] merge, final long[] detach );

	protected abstract void confirmTwoSegmentsImpl( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 );

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
	public void mergeFragments( final long... fragments )
	{
		mergeFragmentsImpl( fragments );
		stateChanged();
	}

	@Override
	public void detachFragment( final long fragmentId, final long... from )
	{
		detachFragmentImpl( fragmentId, from );
		stateChanged();
	}

	@Override
	public void confirmGrouping( final long[] groupedFragments, final long[] notInGroupFragments )
	{
		confirmGroupingImpl( groupedFragments, notInGroupFragments );
		stateChanged();
	}

	@Override
	public void confirmTwoSegments( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 )
	{
		confirmTwoSegmentsImpl( fragmentsInSegment1, fragmentsInSegment2 );
		stateChanged();
	}

}
