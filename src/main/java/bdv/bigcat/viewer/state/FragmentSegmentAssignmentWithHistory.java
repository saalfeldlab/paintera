package bdv.bigcat.viewer.state;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.bigcat.viewer.atlas.solver.action.Detach;
import bdv.bigcat.viewer.atlas.solver.action.Merge;
import bdv.labels.labelset.Label;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

public class FragmentSegmentAssignmentWithHistory extends FragmentSegmentAssignmentState< FragmentSegmentAssignmentWithHistory >
{

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	private final ArrayList< Action > history = new ArrayList<>();

	private final Consumer< Action > broadcaster;

	public FragmentSegmentAssignmentWithHistory( final Consumer< Action > broadcaster, final Supplier< TLongLongHashMap > solutionFetcher )
	{
		this( new long[ 0 ], new long[ 0 ], broadcaster, solutionFetcher );
	}

	public FragmentSegmentAssignmentWithHistory( final long[] fragments, final long[] segments, final Consumer< Action > broadcaster, final Supplier< TLongLongHashMap > solutionFetcher )
	{

		super();

		synchronized ( this )
		{

			assert fragments.length == segments.length: "segments and bodies must be of same length";

			for ( int i = 0; i < fragments.length; ++i )
				fragmentToSegmentMap.put( fragments[ i ], segments[ i ] );

			syncILut();
			this.broadcaster = broadcaster;

			final Thread t = new Thread( () -> {

				while ( true )
				{
					final TLongLongHashMap solution = solutionFetcher.get();
					if ( solution == null )
						continue;
					synchronized ( this )
					{
						synchronized ( history )
						{
							this.fragmentToSegmentMap.clear();
							this.fragmentToSegmentMap.putAll( solution );
							this.syncILut();
							for ( final Action action : history )
								if ( action instanceof Merge )
								{
									final Merge merge = ( Merge ) action;
									final long[] ids = merge.ids();
									for ( int i = 0; i < ids.length; ++i )
										for ( int k = i; k < ids.length; ++k )
											this.mergeSegments( this.fragmentToSegmentMap.get( ids[ i ] ), this.fragmentToSegmentMap.get( ids[ k ] ) );
								}
								else if ( action instanceof Detach )
								{
									final Detach detach = ( Detach ) action;
									final long id = detach.id();
//									long[] from = detach.from();
									this.detachFragment( id );
								}
							this.history.clear();
						}

					}
				}
			} );

			t.start();

		}
	}

	@Override
	public synchronized long getSegment( final long fragmentId )
	{
		final long id;
		final long segmentId = fragmentToSegmentMap.get( fragmentId );
//		System.out.println( "FRAGMENT " + fragmentId + " " + segmentId + " " + segmentToFragmentsMap.getNoEntryValue() + " " + fragmentToSegmentMap.getNoEntryValue() );
		if ( segmentId == fragmentToSegmentMap.getNoEntryValue() )
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
		if ( !segmentToFragmentsMap.contains( segmentId ) )
		{
			segmentToFragmentsMap.put( segmentId, new TLongHashSet( new long[] { segmentId } ) );
			fragmentToSegmentMap.put( segmentId, segmentId );
		}
		final TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
		return fragments;
	}

	@Override
	protected synchronized void assignFragmentsImpl( final long assignFrom, final long assignTo )
	{
		if ( assignFrom == assignTo )
			return;

		final TLongHashSet fragments1 = getFragments( assignFrom );
		final TLongHashSet fragments2 = getFragments( assignTo );
		final TLongHashSet fragments = new TLongHashSet();
		System.out.println( "ASSIGN?" + " " + fragments1 + " " + fragments2 + " " + this.fragmentToSegmentMap + " " + this.segmentToFragmentsMap );

		for ( final long f1 : fragments1.toArray() )
			for ( final long f2 : fragments2.toArray() )
			{
				final Merge merge = new Merge( f1, f2 );
				synchronized ( history )
				{
					history.add( merge );
					broadcaster.accept( merge );
				}
			}

		fragments.addAll( fragments1 );
		fragments.addAll( fragments2 );
		fragments1.forEach( fragmentId -> {
			fragmentToSegmentMap.put( fragmentId, assignTo );
			return true;
		} );
		segmentToFragmentsMap.put( assignTo, fragments );
		segmentToFragmentsMap.remove( assignFrom );
	}

	@Override
	protected synchronized void mergeSegmentsImpl( final long segmentId1, final long segmentId2 )
	{
		System.out.println( "MERGE?" );
		if ( segmentId1 == segmentId2 )
			return;

		assignFragmentsImpl( Math.max( segmentId1, segmentId2 ), Math.min( segmentId1, segmentId2 ) );
	}

	@Override
	protected synchronized void detachFragmentImpl( final long fragmentId )
	{

		final long segmentId = getSegment( fragmentId );
		final TLongHashSet fragments = getFragments( segmentId );
		if ( fragments != null && fragments.size() > 1 )
			synchronized ( history )
			{

				final Detach detach = new Detach( fragmentId );
				synchronized ( history )
				{
					history.add( detach );
					broadcaster.accept( detach );
				}
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
//				}
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

}
