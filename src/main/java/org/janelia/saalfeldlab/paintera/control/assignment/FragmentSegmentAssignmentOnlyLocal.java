package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
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

	public interface Persister
	{
		public void persist( long[] keys, long[] values ) throws UnableToPersist;
	}

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	private final TLongObjectHashMap< TLongHashSet > segmentToFragmentsMap = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	private final Persister persister;

	private final long[] initialFragments;

	private final long[] initialSegments;

	public FragmentSegmentAssignmentOnlyLocal( final Persister persister )
	{
		this( new long[] {}, new long[] {}, persister );
	}

	public FragmentSegmentAssignmentOnlyLocal(
			final long[] fragments,
			final long[] segments,
			final Persister persister )
	{

		super();

		assert fragments.length == segments.length: "segments and bodies must be of same length";

		this.initialFragments = fragments.clone();
		this.initialSegments = segments.clone();

		this.persister = persister;
		LOG.debug( "Assignment map: {}", fragmentToSegmentMap );
		// TODO should reset lut also forget about all actions? I think not.
		resetLut();
	}

	@Override
	public synchronized void persist() throws UnableToPersist
	{
		if ( actions.size() == 0 )
		{
			LOG.debug( "No actions to commit." );
			return;
		}

		try
		{
			LOG.debug( "Persisting assignment {}", this.fragmentToSegmentMap );
			LOG.debug( "Committing actions {}", this.actions );
			this.persister.persist( this.fragmentToSegmentMap.keys(), this.fragmentToSegmentMap.values() );
			this.actions.clear();
		}
		catch ( final Exception e )
		{
			throw e instanceof UnableToPersist ? ( UnableToPersist ) e : new UnableToPersist( e );
		}
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
		return fragments == null ? new TLongHashSet( new long[] { segmentId } ) : new TLongHashSet( fragments );
	}

	private void detachFragmentImpl( final Detach detach )
	{
		LOG.debug( "Detach {}", detach );
		final long segmentFrom = fragmentToSegmentMap.get( detach.fragmentId );
		if ( fragmentToSegmentMap.get( detach.fragmentFrom ) != segmentFrom )
		{
			LOG.debug( "{} not in same segment -- return without detach", detach );
			return;
		}

		final long fragmentId = detach.fragmentId;
		final long fragmentFrom = detach.fragmentFrom;

		this.fragmentToSegmentMap.remove( fragmentId );

		LOG.debug( "Removing fragment={} from segment={}", fragmentId, segmentFrom );
		final TLongHashSet fragments = this.segmentToFragmentsMap.get( segmentFrom );
		if ( fragments != null )
		{
			fragments.remove( fragmentId );
			if ( fragments.size() == 1 )
			{
				this.fragmentToSegmentMap.remove( fragmentFrom );
				this.segmentToFragmentsMap.remove( segmentFrom );
			}
		}
	}

	private void mergeFragmentsImpl( final Merge merge )
	{

		LOG.debug( "Merging {}", merge );

		final long into = merge.intoFragmentId;
		final long from = merge.fromFragmentId;
		final long segmentInto = merge.segmentId;

		if ( fragmentToSegmentMap.get( from ) == fragmentToSegmentMap.get( into ) )
		{
			LOG.debug( "Fragments already in same segment -- not merging" );
			return;
		}

		if ( !fragmentToSegmentMap.contains( into ) )
		{
			fragmentToSegmentMap.put( into, segmentInto );
		}

		final long segmentFrom = fragmentToSegmentMap.get( from );

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

	private void resetLut()
	{
		for ( int i = 0; i < this.initialFragments.length; ++i )
		{
			fragmentToSegmentMap.put( this.initialFragments[ i ], this.initialSegments[ i ] );
		}

		syncILut();

		this.actions.forEach( this::apply );

	}

	@Override
	protected void applyImpl( final AssignmentAction action )
	{
		switch ( action.getType() )
		{
		case MERGE:
		{
			mergeFragmentsImpl( ( Merge ) action );
			break;
		}
		case DETACH:
			detachFragmentImpl( ( Detach ) action );
			break;
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

	public int size()
	{
		return this.fragmentToSegmentMap.size();
	}

	public void persist( final long[] keys, final long[] values )
	{
		this.fragmentToSegmentMap.keys( keys );
		this.fragmentToSegmentMap.values( values );
	}

	@Override
	public Optional< Merge > getMergeAction(
			final long from,
			final long into,
			final LongSupplier newSegmentId )
	{
		if ( from == into )
		{
			LOG.debug( "fragments {} {} are the same -- no action necessary", from, into );
			return Optional.empty();
		}

		if ( getSegment( from ) == getSegment( into ) )
		{
			LOG.debug( "fragments {} {} are in the same segment {} {} -- no action necessary", from, into, getSegment( from ), getSegment( into ) );
			return Optional.empty();
		}

		if ( getSegment( into ) == into )
		{
			fragmentToSegmentMap.put( into, newSegmentId.getAsLong() );
		}

		final Merge merge = new Merge( from, into, fragmentToSegmentMap.get( into ) );
		return Optional.of( merge );
	}

	@Override
	public Optional< Detach > getDetachAction( final long fragmentId, final long from )
	{

		if ( fragmentId == from )
		{
			LOG.debug( "{} and {} ar the same -- no action necessary", fragmentId, from );
			return Optional.empty();
		}

		return Optional.ofNullable( new Detach( fragmentId, from ) );

	}

}
