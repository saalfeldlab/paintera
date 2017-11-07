package bdv.bigcat.viewer.atlas.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import javafx.scene.input.MouseEvent;

public class Highlights extends AbstractStateMode
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove > > mouseAndKeyHandlers = new HashMap<>();

	public Highlights( final HashMap< Source< ? >, SelectedIds > selectedIds )
	{
		this.selectedIds = selectedIds;
	}

	public void addSource( final Source< ? > source, final Source< ? > dataSources, final ToIdConverter toIdConverter )
	{

		if ( !this.dataSources.containsKey( source ) )
			this.dataSources.put( source, dataSources );
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
		return "highlights";
	}

	@Override
	protected Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
				final List< InstallAndRemove > iars = new ArrayList<>();
				iars.add( selector.selectSingle( "toggle single id", new SelectionDialog( "oge1" ), MouseEvent::isPrimaryButtonDown ) );
				iars.add( selector.append( "append id", new SelectionDialog( "oge2" ), MouseEvent::isSecondaryButtonDown ) );
				this.mouseAndKeyHandlers.put( t, iars );
			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );
			this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.installInto( t ) );
		};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
//			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
			if ( this.mouseAndKeyHandlers.containsKey( t ) )
				this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.removeFrom( t ) );
		};
	}

}
