package bdv.bigcat.viewer.atlas.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanelFX;
import javafx.scene.input.MouseEvent;
import net.imglib2.ui.InstallAndRemove;

public class Merges extends AbstractStateMode
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove > > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments;

	public static String AMBIGUOUS_SELECTION_MESSAGE = "";

	public Merges( final HashMap< Source< ? >, SelectedIds > selectedIds, final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments )
	{
		this.selectedIds = selectedIds;
		this.assignments = assignments;
	}

	public void addSource( final Source< ? > source, final Source< ? > dataSource, final ToIdConverter toIdConverter )
	{

		if ( !this.dataSources.containsKey( source ) )
			this.dataSources.put( source, dataSource );
		if ( !this.toIdConverters.containsKey( source ) )
			this.toIdConverters.put( source, toIdConverter );
	}

	public void removeSource( final Source< ? > source )
	{
		this.dataSources.remove( source );
		this.toIdConverters.remove( source );
	}

	@Override
	public String getName()
	{
		return "Merge and Split";
	}

	@Override
	protected Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
				final List< InstallAndRemove > iars = new ArrayList<>();
				iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && noSpecialKeys( event ) ) );
				iars.add( selector.append( "append id", ( ids, selection, isActive ) -> false, event -> event.isSecondaryButtonDown() && noSpecialKeys( event ) ) );
				iars.add( selector.merge( "merge fragments", assignments, event -> event.isPrimaryButtonDown() && shiftOnly( event ) ) );
				iars.add( selector.detach( "detach", assignments, event -> event.isSecondaryButtonDown() && shiftOnly( event ) ) );
				iars.add( selector.confirm( "confirm assignments", assignments, event -> event.isPrimaryButtonDown() && shiftAndControl( event ) ) );
				this.mouseAndKeyHandlers.put( t, iars );
			}
			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );

		};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			if ( this.mouseAndKeyHandlers.containsKey( t ) )
				t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

	public static boolean noSpecialKeys( final MouseEvent e )
	{
		return !( e.isAltDown() || e.isControlDown() || e.isShiftDown() || e.isMetaDown() );
	}

	public static boolean shiftOnly( final MouseEvent e )
	{
		return e.isShiftDown() && !( e.isAltDown() || e.isControlDown() || e.isMetaDown() );
	}

	public static boolean shiftAndControl( final MouseEvent e )
	{
		return e.isShiftDown() && e.isControlDown() && !( e.isAltDown() || e.isMetaDown() );
	}

}
