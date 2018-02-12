package bdv.bigcat.viewer.atlas.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class Highlights extends AbstractStateMode
{

	private final GlobalTransformManager transformManager;

	private final Group meshesGroup;

	private final SourceInfo sourceInfo;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final KeyTracker keyTracker;

	private final ExecutorService es;

	public Highlights(
			final GlobalTransformManager transformManager,
			final Group meshesGroup,
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker,
			final ExecutorService es )
	{
		this.transformManager = transformManager;
		this.meshesGroup = meshesGroup;
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
		this.es = es;
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
				final IdSelector selector = new IdSelector( t, sourceInfo, this );
				final List< InstallAndRemove< Node > > iars = new ArrayList<>();
				iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && keyTracker.noKeysActive() ) );
				iars.add( selector.appendFragmentWithMaximumCount( "append id", event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) ) );
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
