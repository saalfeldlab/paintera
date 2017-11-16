package bdv.bigcat.viewer.state;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.set.hash.TLongHashSet;

public class SelectedIds extends AbstractState< SelectedIds >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final TLongHashSet selectedIds;

	public SelectedIds()
	{
		this( new TLongHashSet() );
	}

	public SelectedIds( final TLongHashSet selectedIds )
	{
		super();
		this.selectedIds = selectedIds;
	}

	public boolean isActive( final long id )
	{
		return selectedIds.contains( id );
	}

	public void activate( final long... ids )
	{
		deactivateAll();
		for ( final long id : ids )
			activateAlso( id );
		LOG.debug( "Activated " + Arrays.toString( ids ) + " " + selectedIds );
		stateChanged();
	}

	public void activateAlso( final long... ids )
	{
		for ( final long id : ids )
			selectedIds.add( id );
		stateChanged();
	}

	public void deactivateAll()
	{
		selectedIds.clear();
		stateChanged();
	}

	public void deactivate( final long... ids )
	{
		for ( final long id : ids )
			selectedIds.remove( id );
		LOG.debug( "Deactivated {}, {}", Arrays.toString( ids ), selectedIds );
		stateChanged();
	}

	public boolean isOnlyActiveId( final long id )
	{
		return selectedIds.size() == 1 && isActive( id );
	}

	public long[] getActiveIds()
	{
		return this.selectedIds.toArray();
	}

	@Override
	public String toString()
	{
		return selectedIds.toString();
	}

}
