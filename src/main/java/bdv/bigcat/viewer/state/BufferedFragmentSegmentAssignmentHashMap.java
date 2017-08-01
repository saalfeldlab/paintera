package bdv.bigcat.viewer.state;

import java.util.function.Consumer;

import bdv.labels.labelset.Label;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

public class BufferedFragmentSegmentAssignmentHashMap extends FragmentSegmentAssignmentState< BufferedFragmentSegmentAssignmentHashMap >
{

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	private final FragmentSegmentAssignment sourceAssignment;

	private final Consumer< TLongLongHashMap > writeBufferToSource;

	private boolean showBufferedChanges = true;

	public BufferedFragmentSegmentAssignmentHashMap( final FragmentSegmentAssignment sourceAssignment, final Consumer< TLongLongHashMap > writeBufferToSource )
	{
		this( new long[ 0 ], new long[ 0 ], sourceAssignment, writeBufferToSource );
	}

	public BufferedFragmentSegmentAssignmentHashMap( final long[] fragments, final long[] segments, final FragmentSegmentAssignment sourceAssignment, final Consumer< TLongLongHashMap > writeBufferToSource )
	{

		super();

		assert fragments.length == segments.length: "segments and bodies must be of same length";

		for ( int i = 0; i < fragments.length; ++i )
			fragmentToSegmentMap.put( fragments[ i ], segments[ i ] );

		syncILut();
		this.sourceAssignment = sourceAssignment;
		this.writeBufferToSource = writeBufferToSource;
	}

	public void showBufferedChanges( final boolean showBufferedChanges )
	{
		this.showBufferedChanges = showBufferedChanges;
	}

	public synchronized void clearBuffer( final boolean writeToSource )
	{
		if ( writeToSource )
		{
			writeBufferToSource.accept( fragmentToSegmentMap );
			if ( sourceAssignment instanceof FragmentSegmentAssignmentFromSource )
				( ( FragmentSegmentAssignmentFromSource ) sourceAssignment ).reload();
		}
		fragmentToSegmentMap.clear();
		segmentToFragmentsMap.clear();
	}

	@Override
	public synchronized long getSegment( final long fragmentId )
	{
		if ( showBufferedChanges && fragmentToSegmentMap.contains( fragmentId ) )
			return fragmentToSegmentMap.get( fragmentId );
		else
			return sourceAssignment.getSegment( fragmentId );
	}

	@Override
	public synchronized TLongHashSet getFragments( final long segmentId )
	{
		return segmentToFragmentsMap.contains( segmentId ) ? segmentToFragmentsMap.get( segmentId ) : sourceAssignment.getFragments( segmentId );
	}

	@Override
	protected void assignFragmentsImpl( final long assignFrom, final long assignTo )
	{
		if ( assignFrom == assignTo )
			return;

		synchronized ( this )
		{
			final TLongHashSet fragments1 = getFragments( assignFrom );
			final TLongHashSet fragments2 = getFragments( assignTo );
			final TLongHashSet fragments = new TLongHashSet();
			fragments.addAll( fragments1 );
			fragments.addAll( fragments2 );
			fragments1.forEach( fragmentId -> {
				fragmentToSegmentMap.put( fragmentId, assignTo );
				return true;
			} );
			segmentToFragmentsMap.put( assignTo, fragments );
			segmentToFragmentsMap.remove( assignFrom );
		}
	}

	@Override
	protected void mergeSegmentsImpl( final long segmentId1, final long segmentId2 )
	{
		if ( segmentId1 == segmentId2 )
			return;

		// TODO fix this!!!
		final long mergedSegmentId = segmentId2; // idService.next();
		synchronized ( this )
		{
			final TLongHashSet fragments1 = getFragments( segmentId1 );
			final TLongHashSet fragments2 = getFragments( segmentId2 );
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
		synchronized ( sourceAssignment )
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

//				final TLongHashSet original = sourceAssignment.getFragments( segmentId );
//				if ( false && original != null && fragmentsCopy.equals( original ) )
//					this.segmentToFragmentsMap.remove( segmentId );
//				else

				final long newSegmentId = fragmentId;
//				if ( false && sourceAssignment.getSegment( fragmentId ) == newSegmentId )
//				{
//					fragmentToSegmentMap.remove( fragmentId );
//					segmentToFragmentsMap.remove( newSegmentId );
//				}
//				else
//				{
				fragmentToSegmentMap.put( fragmentId, newSegmentId );
				segmentToFragmentsMap.put( newSegmentId, new TLongHashSet( new long[] { fragmentId } ) );
//				}
			}
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
