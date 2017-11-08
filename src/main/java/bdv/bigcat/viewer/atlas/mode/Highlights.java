package bdv.bigcat.viewer.atlas.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.viewer.Source;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class Highlights extends AbstractStateMode
{

	private final Viewer3DControllerFX v3dControl;

	private final GlobalTransformManager transformManager;

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, Function< ?, ForegroundCheck< ? > > > foregroundChecks = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState > frags = new HashMap<>();

	private final HashMap< Source< ? >, ARGBStream > streams = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final KeyTracker keyTracker;

	public Highlights(
			final Viewer3DControllerFX v3dControl,
			final GlobalTransformManager transformManager,
			final HashMap< Source< ? >, SelectedIds > selectedIds,
			final KeyTracker keyTracker )
	{
		this.v3dControl = v3dControl;
		this.transformManager = transformManager;
		this.selectedIds = selectedIds;
		this.keyTracker = keyTracker;
	}

	public void addSource(
			final Source< ? > source,
			final Source< ? > dataSources,
			final ToIdConverter toIdConverter,
			final Function< ?, ForegroundCheck< ? > > foregroundCheck,
			final FragmentSegmentAssignmentState< ? > frag,
			final ARGBStream stream )
	{

		this.dataSources.put( source, dataSources );
		this.toIdConverters.put( source, toIdConverter );
		this.foregroundChecks.put( source, foregroundCheck );
		this.frags.put( source, frag );
		this.streams.put( source, stream );
	}

	public void removeSource( final Source< ? > source )
	{
		this.dataSources.remove( source );
		this.toIdConverters.remove( source );
		this.foregroundChecks.remove( source );
		this.frags.remove( source );
		this.streams.remove( source );
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
				final RenderNeuron show = new RenderNeuron( t, false, dataSources, foregroundChecks, toIdConverters, selectedIds, frags, v3dControl, streams, transformManager );
				final RenderNeuron append = new RenderNeuron( t, true, dataSources, foregroundChecks, toIdConverters, selectedIds, frags, v3dControl, streams, transformManager );
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
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
