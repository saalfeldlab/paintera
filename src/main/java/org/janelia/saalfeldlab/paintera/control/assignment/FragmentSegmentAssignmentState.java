package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FragmentSegmentAssignmentState extends ObservableWithListenersList implements FragmentSegmentAssignment
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public void persist()
	{
		throw new UnsupportedOperationException( "Not implemented yet!" );
	}

	protected abstract void detachFragmentImpl( final long fragmentId, long from );

	protected abstract void mergeFragmentsImpl( final long fragment1, final long fragment2 );

	protected abstract void confirmGroupingImpl( final long[] merge, final long[] detach );

	protected abstract void confirmTwoSegmentsImpl( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 );

	@Override
	public void mergeFragments( final long fragment1, final long fragment2 )
	{
		mergeFragmentsImpl( fragment1, fragment2 );
		LOG.debug( "Merged {} {}", fragment1, fragment2 );
		stateChanged();
	}

	@Override
	public void detachFragment( final long fragmentId, final long from )
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
