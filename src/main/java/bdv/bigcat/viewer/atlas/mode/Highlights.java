package bdv.bigcat.viewer.atlas.mode;

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
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class Highlights extends AbstractStateMode
{

	private final Viewer3DControllerFX v3dControl;

	private final GlobalTransformManager transformManager;

	private final SourceInfo sourceInfo;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final KeyTracker keyTracker;

	public Highlights(
			final Viewer3DControllerFX v3dControl,
			final GlobalTransformManager transformManager,
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker )
	{
		this.v3dControl = v3dControl;
		this.transformManager = transformManager;
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
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
				final RenderNeuron show = new RenderNeuron( t, false, sourceInfo, v3dControl, transformManager, this );
				final RenderNeuron append = new RenderNeuron( t, true, sourceInfo, v3dControl, transformManager, this );
				final IdSelector selector = new IdSelector( t, sourceInfo, this );
				final List< InstallAndRemove< Node > > iars = new ArrayList<>();
				iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", show::click, event -> event.isPrimaryButtonDown() && keyTracker.noKeysActive() ) );
				iars.add( selector.appendFragmentWithMaximumCount( "append id", append::click, event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) ) );
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
