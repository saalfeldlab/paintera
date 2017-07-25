package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.util.AbstractNamedBehaviour;

import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import net.imglib2.RealRandomAccess;

public class IdSelector
{

	private final ViewerPanel viewer;

	private final HashMap< Source< ? >, ToLongFunction > toIdConverters;

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< Source< ? >, RealRandomAccess< ? > > accesses;

	public IdSelector(
			final ViewerPanel viewer,
			final HashMap< Source< ? >, ToLongFunction > toIdConverters,
			final HashMap< Source< ? >, SelectedIds > selectedIds,
			final HashMap< Source< ? >, RealRandomAccess< ? > > accesses )
	{
		super();
		this.viewer = viewer;
		this.toIdConverters = toIdConverters;
		this.selectedIds = selectedIds;
		this.accesses = accesses;
	}

	public SelectSingle selectSingle( final String name )
	{
		return new SelectSingle( name );
	}

	public Append append( final String name )
	{
		return new Append( name );
	}

	private abstract class Select extends AbstractNamedBehaviour implements ClickBehaviour
	{
		public Select( final String name )
		{
			super( name );
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && accesses.containsKey( source ) )
			{

				final RealRandomAccess< ? > access = accesses.get( source );
				viewer.getMouseCoordinates( access );
				access.setPosition( 0l, 2 );
				viewer.displayToGlobalCoordinates( access );
				final Object val = access.get();
				final long id = toIdConverters.get( source ).applyAsLong( val );
				actOn( id, selectedIds.get( source ) );
				System.out.println( "Ran act on for " + name() );
			}
		}

		protected abstract void actOn( final long id, SelectedIds selectedIds );
	}

	private class SelectSingle extends Select
	{

		public SelectSingle( final String name )
		{
			super( name );
		}

		@Override
		protected void actOn( final long id, final SelectedIds selectedIds )
		{
			if ( selectedIds.isOnlyActiveId( id ) )
				selectedIds.deactivate( id );
			else
				selectedIds.activate( id );
		}
	}

	private class Append extends Select
	{

		public Append( final String name )
		{
			super( name );
			// TODO Auto-generated constructor stub
		}

		@Override
		protected void actOn( final long id, final SelectedIds selectedIds )
		{
			if ( selectedIds.isActive( id ) )
				selectedIds.deactivate( id );
			else
				selectedIds.activateAlso( id );
		}

	}

	private Optional< Source< ? > > getSource()
	{
		final int currentSource = viewer.getState().getCurrentSource();
		final List< SourceState< ? > > sources = viewer.getState().getSources();
		if ( sources.size() <= currentSource || currentSource < 0 )
			return Optional.empty();
		final Source< ? > activeSource = sources.get( currentSource ).getSpimSource();
		return Optional.of( activeSource );
	}

}
