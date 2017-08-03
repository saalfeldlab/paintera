package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.util.AbstractNamedBehaviour;

import bdv.bigcat.viewer.atlas.mode.HandleMultipleIds;
import bdv.bigcat.viewer.state.FragmentSegmentAssignment;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import net.imglib2.RealRandomAccess;

public class IdSelector
{

	private final ViewerPanel viewer;

	private final HashMap< Source< ? >, Function< Object, long[] > > toIdConverters;

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< Source< ? >, RealRandomAccess< ? > > accesses;

	public IdSelector(
			final ViewerPanel viewer,
			final HashMap< Source< ? >, Function< Object, long[] > > toIdConverters,
			final HashMap< Source< ? >, SelectedIds > selectedIds,
			final HashMap< Source< ? >, RealRandomAccess< ? > > accesses )
	{
		super();
		this.viewer = viewer;
		this.toIdConverters = toIdConverters;
		this.selectedIds = selectedIds;
		this.accesses = accesses;
	}

	public SelectSingle selectSingle( final String name, final HandleMultipleIds handleMultipleEntries )
	{
		return new SelectSingle( name, handleMultipleEntries );
	}

	public Append append( final String name, final HandleMultipleIds handleMultipleEntries )
	{
		return new Append( name, handleMultipleEntries );
	}

	public MergeSegments merge( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
	{
		return new MergeSegments( assignments );
	}

	public DetachFragment detach( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
	{
		return new DetachFragment( assignments );
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
				final long[] id = toIdConverters.get( source ).apply( val );
				actOn( id, selectedIds.get( source ) );
			}
		}

		protected abstract void actOn( final long[] id, SelectedIds selectedIds );
	}

	private class SelectSingle extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public SelectSingle( final String name, final HandleMultipleIds handleMultipleEntries )
		{
			super( name );
			this.handleMultipleEntries = handleMultipleEntries;
		}

		@Override
		protected void actOn( final long[] ids, final SelectedIds selectedIds )
		{
			switch ( ids.length )
			{
			case 0:
				return;
			case 1:
				final long id = ids[ 0 ];
				if ( selectedIds.isOnlyActiveId( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activate( id );
				break;
			default:
				final boolean[] isActive = new boolean[ ids.length ];
				final boolean requiresAction = handleMultipleEntries.handle( ids, selectedIds, isActive );
				if ( requiresAction )
				{
					selectedIds.deactivateAll();
					for ( int i = 0; i < ids.length; ++i )
					{
						System.out.println( "ACTIVATE? " + isActive[ i ] + ids[ i ] );
						if ( isActive[ i ] )
							selectedIds.activateAlso( ids[ i ] );
						else
							selectedIds.deactivate( ids[ i ] );
					}
				}
				break;
			}
		}
	}

	private class Append extends Select
	{

		private final HandleMultipleIds handleMultipleEntries;

		public Append( final String name, final HandleMultipleIds handleMultipleEntries )
		{
			super( name );
			this.handleMultipleEntries = handleMultipleEntries;
		}

		@Override
		protected void actOn( final long[] ids, final SelectedIds selectedIds )
		{
			switch ( ids.length )
			{
			case 0:
				return;
			case 1:
				final long id = ids[ 0 ];
				if ( selectedIds.isActive( id ) )
					selectedIds.deactivate( id );
				else
					selectedIds.activateAlso( id );
				break;
			default:
				final boolean[] isActive = new boolean[ ids.length ];
				final boolean requiresAction = handleMultipleEntries.handle( ids, selectedIds, isActive );
				if ( requiresAction )
					for ( int i = 0; i < ids.length; ++i )
						if ( isActive[ i ] )
							selectedIds.activateAlso( ids[ i ] );
						else
							selectedIds.deactivate( ids[ i ] );
				break;
			}
		}

	}

	private class MergeSegments extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments;

		public MergeSegments( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
		{
			super( "merge segments" );
			this.assignments = assignments;
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && accesses.containsKey( source ) && assignments.containsKey( source ) )
			{
				final FragmentSegmentAssignment assignment = assignments.get( source );

				final long[] selIds = selectedIds.get( source ).getActiveIds();

				if ( selIds.length < 1 )
					return;

				final long selectedId = assignment.getSegment( selIds[ 0 ] );

				for ( int i = 1; i < selIds.length; ++i )
					if ( assignment.getSegment( selIds[ i ] ) != selectedId )
					{
						System.out.println( "Ambiguity: Selected multiple active segments -- will not apply merge!" );
						return;
					}

				final RealRandomAccess< ? > access = accesses.get( source );
				viewer.getMouseCoordinates( access );
				access.setPosition( 0l, 2 );
				viewer.displayToGlobalCoordinates( access );
				final Object val = access.get();
				final long[] ids = toIdConverters.get( source ).apply( val );

//				final long firstSegment = assignment.getSegment( ids[ 0 ] );
//				for ( int i = 0; i < ids.length; ++i )
//					if ( assignment.getSegment( ids[ i ] ) != firstSegment )
//					{
//						System.out.println( "Ambiguity: Selected multiple segments -- will not apply merge!" );
//						return;
//					}

//				if ( selectedId == id )
//					return;

				for ( int i = 0; i < ids.length; ++i )
				{
					final long id = assignment.getSegment( ids[ i ] );
					if ( id != selectedId )
						assignment.assignFragments( id, selectedId );
				}

			}
		}

	}

	private class DetachFragment extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments;

		public DetachFragment( final HashMap< Source< ? >, ? extends FragmentSegmentAssignment > assignments )
		{
			super( "detach fragment" );
			this.assignments = assignments;
		}

		@Override
		public void click( final int x, final int y )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( !optionalSource.isPresent() )
				return;
			final Source< ? > source = optionalSource.get();
			if ( toIdConverters.containsKey( source ) && selectedIds.containsKey( source ) && accesses.containsKey( source ) && assignments.containsKey( source ) )
			{

				final FragmentSegmentAssignment assignment = assignments.get( source );

				final long[] selIds = selectedIds.get( source ).getActiveIds();

				if ( selIds.length < 1 )
					return;

				final long selectedId = assignment.getSegment( selIds[ 0 ] );

				for ( int i = 1; i < selIds.length; ++i )
					if ( assignment.getSegment( selIds[ i ] ) != selectedId )
					{
						System.out.println( "Ambiguity: Selected multiple active segments -- will not apply merge!" );
						return;
					}

				final long selectedSegment = assignment.getSegment( selIds[ 0 ] );

				final RealRandomAccess< ? > access = accesses.get( source );
				viewer.getMouseCoordinates( access );
				access.setPosition( 0l, 2 );
				viewer.displayToGlobalCoordinates( access );
				final Object val = access.get();
				final long[] ids = toIdConverters.get( source ).apply( val );

				for ( final long id : ids )
					if ( assignment.getSegment( id ) == selectedSegment )
						assignment.detachFragment( id );

			}
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
