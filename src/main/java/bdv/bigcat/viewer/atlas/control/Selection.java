package bdv.bigcat.viewer.atlas.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import javafx.scene.Node;

public class Selection implements ToOnEnterOnExit
{

	private final SourceInfo sourceInfo;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final KeyTracker keyTracker;

	public Selection(
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker )
	{
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
	}

	@Override
	public Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final IdSelector selector = new IdSelector( t, sourceInfo );
				final List< InstallAndRemove< Node > > iars = new ArrayList<>();
				iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && keyTracker.noKeysActive() ) );
				iars.add( selector.appendFragmentWithMaximumCount( "append id", event -> event.isSecondaryButtonDown() && keyTracker.noKeysActive() ) );
				this.mouseAndKeyHandlers.put( t, iars );
			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );
			this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.installInto( t ) );
		};
	}

	@Override
	public Consumer< ViewerPanelFX > getOnExit()
	{
		return t -> {
//			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
			if ( this.mouseAndKeyHandlers.containsKey( t ) )
				this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.removeFrom( t ) );
		};
	}

}
