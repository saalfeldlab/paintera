package bdv.bigcat.viewer.atlas.mode;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;

public class Merges extends AbstractStateMode
{

	private final HashMap< Source< ? >, RealRandomAccess< ? > > accesses = new HashMap<>();

	private final HashMap< Source< ? >, Function< Object, long[] > > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanel, MouseAndKeyHandler > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments;

	public Merges( final HashMap< Source< ? >, SelectedIds > selectedIds, final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments )
	{
		this.selectedIds = selectedIds;
		this.assignments = assignments;
	}

	public void addSource( final Source< ? > source, final RealRandomAccess< ? > access, final Function< Object, long[] > toIdConverter )
	{

		this.accesses.put( source, access );
		this.toIdConverters.put( source, toIdConverter );
	}

	public void removeSource( final Source< ? > source )
	{
		this.accesses.remove( source );
		this.toIdConverters.remove( source );
	}

	@Override
	public String getName()
	{
		return "Merge and Split";
	}

	@Override
	protected Consumer< ViewerPanel > getOnEnter()
	{
		return t -> {
			System.out.println( "Entering for merger regardless!" );
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				System.out.println( "Entering for merger!" );
				final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, accesses );
				final Behaviours behaviours = new Behaviours( inputTriggerConfig );
				behaviours.namedBehaviour( selector.selectSingle( "toggle single id" ), "button1" );
				behaviours.namedBehaviour( selector.merge( assignments ), "shift button1" );
				behaviours.namedBehaviour( selector.detach( assignments ), "shift button3" );
				final TriggerBehaviourBindings bindings = new TriggerBehaviourBindings();
				behaviours.install( bindings, "merge and split" );
				final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
				mouseAndKeyHandler.setInputMap( bindings.getConcatenatedInputTriggerMap() );
				mouseAndKeyHandler.setBehaviourMap( bindings.getConcatenatedBehaviourMap() );
				this.mouseAndKeyHandlers.put( t, mouseAndKeyHandler );
			}
			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );

		};
	}

	@Override
	public Consumer< ViewerPanel > onExit()
	{
		return t -> {
			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

}
