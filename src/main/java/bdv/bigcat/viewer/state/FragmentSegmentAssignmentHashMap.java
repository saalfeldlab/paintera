package bdv.bigcat.viewer.state;

import bdv.labels.labelset.Label;
import bdv.util.IdService;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

public class FragmentSegmentAssignmentHashMap extends FragmentSegmentAssignmentState< FragmentSegmentAssignmentHashMap >
{

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	private IdService idService;

	public FragmentSegmentAssignmentHashMap( final IdService idService )
	{
		this( new long[ 0 ], new long[ 0 ], idService );
	}

	public FragmentSegmentAssignmentHashMap( final long[] fragments, final long[] segments, final IdService idService )
	{

		super();

		assert fragments.length == segments.length: "segments and bodies must be of same length";

		for ( int i = 0; i < fragments.length; ++i )
			fragmentToSegmentMap.put( fragments[ i ], segments[ i ] );

		syncILut();
		this.idService = idService;
	}

	public void setIdService( final IdService idService )
	{
		this.idService = idService;
	}

	@Override
	public synchronized long getSegment( final long fragmentId )
	{
		final long id;
		final long segmentId = fragmentToSegmentMap.get( fragmentId );
		if ( segmentId == segmentToFragmentsMap.getNoEntryValue() )
		{
			id = fragmentId;
			fragmentToSegmentMap.put( fragmentId, id );
			final TLongHashSet set = new TLongHashSet();
			set.add( fragmentId );
			segmentToFragmentsMap.put( id, set );
		}
		else
			id = segmentId;
		return id;
	}

	@Override
	public synchronized TLongHashSet getFragments( final long segmentId )
	{
		final TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
		return fragments;
	}

	@Override
	protected void assignFragmentsImpl( final long assignFrom, final long assignTo )
	{
		if ( assignFrom == assignTo )
			return;

		synchronized ( this )
		{
			final TLongHashSet fragments1 = segmentToFragmentsMap.get( assignFrom );
			final TLongHashSet fragments2 = segmentToFragmentsMap.get( assignTo );
			fragments2.addAll( fragments1 );
			fragments1.forEach( fragmentId -> {
				fragmentToSegmentMap.put( fragmentId, assignTo );
				return true;
			} );
			segmentToFragmentsMap.remove( assignFrom );
		}
	}

	@Override
	protected void mergeSegmentsImpl( final long segmentId1, final long segmentId2 )
	{
		if ( segmentId1 == segmentId2 )
			return;

		final long mergedSegmentId = idService.next();
		synchronized ( this )
		{
			final TLongHashSet fragments1 = segmentToFragmentsMap.get( segmentId1 );
			final TLongHashSet fragments2 = segmentToFragmentsMap.get( segmentId2 );
			final TLongHashSet fragments = fragments1;
			fragments.addAll( fragments2 );

			fragments.forEach( fragmentId -> {
				fragmentToSegmentMap.put( fragmentId, mergedSegmentId );
				return true;
			} );

			segmentToFragmentsMap.put( mergedSegmentId, fragments );
			segmentToFragmentsMap.remove( segmentId1 );
			segmentToFragmentsMap.remove( segmentId2 );
		}
	}

	@Override
	protected synchronized void detachFragmentImpl( final long fragmentId )
	{
		final long segmentId = fragmentToSegmentMap.get( fragmentId );
		final TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
		if ( fragments != null && fragments.size() > 1 )
		{
			fragments.remove( fragmentId );

			final long newSegmentId = fragmentId;
			fragmentToSegmentMap.put( fragmentId, newSegmentId );
			segmentToFragmentsMap.put( newSegmentId, new TLongHashSet( new long[] { fragmentId } ) );
		}
	}

	private void syncILut()
	{
		segmentToFragmentsMap.clear();
		final TLongLongIterator lutIterator = fragmentToSegmentMap.iterator();
		while ( lutIterator.hasNext() )
		{
			lutIterator.advance();
			final long fragmentId = lutIterator.key();
			final long segmentId = lutIterator.value();
			TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
			if ( fragments == null )
			{
				fragments = new TLongHashSet();
				segmentToFragmentsMap.put( segmentId, fragments );
			}
			fragments.add( fragmentId );
		}
	}

}
