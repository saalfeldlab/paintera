package bdv.bigcat.viewer.state;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.labels.labelset.Label;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

public class FragmentSegmentAssignmentOnlyLocal extends FragmentSegmentAssignmentState< FragmentSegmentAssignmentOnlyLocal >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	public FragmentSegmentAssignmentOnlyLocal()
	{
		this( new long[] {}, new long[] {} );
	}

	public FragmentSegmentAssignmentOnlyLocal( final long[] fragments, final long[] segments )
	{

		super();

		assert fragments.length == segments.length: "segments and bodies must be of same length";
		for ( int i = 0; i < fragments.length; ++i )
			fragmentToSegmentMap.put( fragments[ i ], segments[ i ] );

		syncILut();
	}

	@Override
	public synchronized long getSegment( final long fragmentId )
	{
		final long id;
		final long segmentId = fragmentToSegmentMap.get( fragmentId );
		if ( segmentId == fragmentToSegmentMap.getNoEntryValue() )
			id = fragmentId;
		else
			id = segmentId;
		return id;
	}

	@Override
	public synchronized TLongHashSet getFragments( final long segmentId )
	{
		final TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
		return fragments == null ? new TLongHashSet( new long[] { segmentId } ) : fragments;
	}

	@Override
	protected synchronized void assignFragmentsImpl( final long assignFrom, final long assignTo )
	{
		if ( assignFrom == assignTo )
			return;

		final TLongHashSet fragments1 = getFragments( assignFrom );
		final TLongHashSet fragments2 = getFragments( assignTo );
		final TLongHashSet fragments = new TLongHashSet();

		LOG.debug( "Assigning {} from {} to {} ({}}", fragments1, assignFrom, assignTo, fragments2 );
		fragments.addAll( fragments1 );
		fragments.addAll( fragments2 );

		LOG.debug( "Maps before: {} {}", fragmentToSegmentMap, segmentToFragmentsMap );
		fragments1.forEach( fragmentId -> {
			fragmentToSegmentMap.put( fragmentId, assignTo );
			return true;
		} );
		segmentToFragmentsMap.put( assignTo, fragments );
		segmentToFragmentsMap.remove( assignFrom );

		LOG.debug( "Maps after: {} {}", fragmentToSegmentMap, segmentToFragmentsMap );

	}

	@Override
	protected synchronized void mergeSegmentsImpl( final long segmentId1, final long segmentId2 )
	{
		if ( segmentId1 == segmentId2 )
			return;

		LOG.debug( "Merging segments {} and {}", segmentId1, segmentId2 );
		assignFragmentsImpl( Math.max( segmentId1, segmentId2 ), Math.min( segmentId1, segmentId2 ) );
	}

	@Override
	protected synchronized void detachFragmentImpl( final long fragmentId, final long... from )
	{

		final long segmentId = getSegment( fragmentId );
		final TLongHashSet fragments = getFragments( segmentId );
		if ( fragments != null && fragments.size() > 1 )
		{
			final TLongHashSet fragmentsCopy = new TLongHashSet( fragments );
			fragmentsCopy.remove( fragmentId );

			if ( fragmentId == segmentId )
			{
				final long actualSegmentId = fragmentsCopy.iterator().next();
				for ( final TLongIterator fragmentIt = fragmentsCopy.iterator(); fragmentIt.hasNext(); )
					this.fragmentToSegmentMap.put( fragmentIt.next(), actualSegmentId );
				this.segmentToFragmentsMap.put( actualSegmentId, fragmentsCopy );
			}
			else
				this.segmentToFragmentsMap.put( segmentId, fragmentsCopy );

			final long newSegmentId = fragmentId;
			fragmentToSegmentMap.put( fragmentId, newSegmentId );
			segmentToFragmentsMap.put( newSegmentId, new TLongHashSet( new long[] { fragmentId } ) );
		}
	}

	@Override
	protected void mergeFragmentsImpl( final long... fragments )
	{
		if ( fragments.length < 2 )
			return;
		final long id1 = fragments[ 0 ];
		for ( int k = 1; k < fragments.length; ++k )
		{
			final long id2 = fragments[ k ];
			final long seg1 = getSegment( id1 );
			final long seg2 = getSegment( id2 );
			mergeSegmentsImpl( seg1, seg2 );
		}
	}

	private synchronized void syncILut()
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

	@Override
	protected synchronized void confirmGroupingImpl( final long[] group, final long[] outsideGroup )
	{
		confirmGroupingImpl( group, outsideGroup, true );
	}

	protected synchronized void confirmGroupingImpl( final long[] merge, final long[] detach, final boolean broadcast )
	{}

	@Override
	protected synchronized void confirmTwoSegmentsImpl( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2 )
	{
		confirmTwoSegmentsImpl( fragmentsInSegment1, fragmentsInSegment2, true );
	}

	protected synchronized void confirmTwoSegmentsImpl( final long[] fragmentsInSegment1, final long[] fragmentsInSegment2, final boolean broadcast )
	{}

}
