package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.type.label.Label;

public class FragmentSegmentAssignmentOnlyLocal extends FragmentSegmentAssignmentState
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	private final BiConsumer< long[], long[] > persister;

	private final IdService idService;

	public FragmentSegmentAssignmentOnlyLocal( final BiConsumer< long[], long[] > persister, final IdService idService )
	{
		this( new long[] {}, new long[] {}, persister, idService );
	}

	public FragmentSegmentAssignmentOnlyLocal( final long[] fragments, final long[] segments, final BiConsumer< long[], long[] > persister, final IdService idService )
	{

		super();

		assert fragments.length == segments.length: "segments and bodies must be of same length";
		for ( int i = 0; i < fragments.length; ++i )
		{
			fragmentToSegmentMap.put( fragments[ i ], segments[ i ] );
		}

		this.persister = persister;
		this.idService = idService;

		syncILut();
		LOG.debug( "Assignment map: {}", fragmentToSegmentMap );
	}

	@Override
	public synchronized void persist()
	{
		this.persister.accept( fragmentToSegmentMap.keys(), fragmentToSegmentMap.values() );
	}

	@Override
	public synchronized long getSegment( final long fragmentId )
	{
		final long id;
		final long segmentId = fragmentToSegmentMap.get( fragmentId );
		if ( segmentId == fragmentToSegmentMap.getNoEntryValue() )
		{
			id = fragmentId;
		}
		else
		{
			id = segmentId;
		}
		LOG.debug( "Returning {} for fragment {}: ", id, fragmentId );
		return id;
	}

	@Override
	public synchronized TLongHashSet getFragments( final long segmentId )
	{
		final TLongHashSet fragments = segmentToFragmentsMap.get( segmentId );
		return fragments == null ? new TLongHashSet( new long[] { segmentId } ) : fragments;
	}

	@Override
	protected synchronized void detachFragmentImpl( final long fragmentId, final long from )
	{

		if ( fragmentId == from )
		{
			LOG.debug( "{} and {} ar the same -- no action necessary", fragmentId, from );
			return;
		}

		final long segmentId = getSegment( fragmentId );
		final long segmentFrom = getSegment( from );
		if ( segmentId != segmentFrom )
		{
			LOG.debug( "{} and {} in different segments: {} {} -- no action necessary", fragmentId, from, segmentId, segmentFrom );
			return;
		}

		this.fragmentToSegmentMap.remove( fragmentId );

		final TLongHashSet fragments = getFragments( segmentFrom );
		fragments.remove( fragmentId );
		if ( fragments.size() == 1 )
		{
			this.fragmentToSegmentMap.remove( from );
			this.segmentToFragmentsMap.remove( segmentFrom );
		}
	}

	@Override
	protected void mergeFragmentsImpl( final long from, final long into )
	{

		if ( from == into )
		{
			LOG.debug( "fragments {} {} are the same -- no action necessary", from, into );
			return;
		}

		if ( getSegment( from ) == getSegment( into ) )
		{
			LOG.debug( "fragments {} {} are in the same segment {} {} -- no action necessary", from, into, getSegment( from ), getSegment( into ) );
			return;
		}

		if ( !fragmentToSegmentMap.contains( into ) )
		{
			fragmentToSegmentMap.put( into, idService.next() );
		}

		final long segmentFrom = fragmentToSegmentMap.get( from );
		final long segmentInto = fragmentToSegmentMap.get( into );

		if ( !segmentToFragmentsMap.contains( segmentInto ) )
		{
			final TLongHashSet fragmentOnly = new TLongHashSet();
			fragmentOnly.add( into );
			segmentToFragmentsMap.put( segmentInto, fragmentOnly );
		}

		final TLongHashSet fragmentsFrom = segmentToFragmentsMap.remove( segmentFrom );

		if ( fragmentsFrom != null )
		{
			segmentToFragmentsMap.get( segmentInto ).addAll( fragmentsFrom );
			Arrays.stream( fragmentsFrom.toArray() ).forEach( id -> fragmentToSegmentMap.put( id, segmentInto ) );
		}
		else
		{
			segmentToFragmentsMap.get( segmentInto ).add( from );
			fragmentToSegmentMap.put( from, segmentInto );
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
				fragments.add( segmentId );
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

	public int size()
	{
		return this.fragmentToSegmentMap.size();
	}

	public void persist( final long[] keys, final long[] values )
	{
		this.fragmentToSegmentMap.keys( keys );
		this.fragmentToSegmentMap.values( values );
	}

}
