package bdv.bigcat.viewer.atlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanelFX;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import net.imglib2.ui.InstallAndRemove;

public class AtlasIdSelector
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove > > mouseAndKeyHandlers = new HashMap<>();

	public AtlasIdSelector( final HashMap< Source< ? >, SelectedIds > selectedIds )
	{
		this.selectedIds = selectedIds;
	}

	public void addSource( final Source< ? > source, final Source< ? > dataSource, final ToIdConverter toIdConverter )
	{

		this.dataSources.put( source, dataSource );
		this.toIdConverters.put( source, toIdConverter );
	}

	public Consumer< ViewerPanelFX > onEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
				final List< InstallAndRemove > iars = new ArrayList<>();
				iars.add( selector.selectSingle( "toggle single id", ( ids, selection, isActive ) -> false, me -> me.getButton().equals( MouseButton.PRIMARY ) ) );
				iars.add( selector.append( "append id", ( ids, selection, isActive ) -> false, MouseEvent::isSecondaryButtonDown ) );
				this.mouseAndKeyHandlers.put( t, iars );
//				this.selectedIds.values().forEach( selectedIds -> selectedIds.addListener( () -> t.requestRepaint() ) );
				System.out.println( "Installed handler for " + t );// + " " +
																	// this.mouseAndKeyHandlers
																	// );
				t.getDisplay().addHandler( iars );
			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
//			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

}
