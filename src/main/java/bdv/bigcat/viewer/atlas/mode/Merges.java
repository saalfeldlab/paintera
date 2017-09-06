package bdv.bigcat.viewer.atlas.mode;

import java.util.HashMap;
import java.util.function.Consumer;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;

public class Merges extends AbstractStateMode
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanel, MouseAndKeyHandler > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments;

	public static String AMBIGUOUS_SELECTION_MESSAGE = "";

	public Merges( final HashMap< Source< ? >, SelectedIds > selectedIds, final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > assignments )
	{
		this.selectedIds = selectedIds;
		this.assignments = assignments;
	}

	public void addSource( final Source< ? > source, final Source< ? > dataSource, final ToIdConverter toIdConverter )
	{

		this.dataSources.put( source, dataSource );
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
	protected Consumer< ViewerPanel > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				System.out.println( "Entering for merger!" );
				final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
				final Behaviours behaviours = new Behaviours( inputTriggerConfig );
				behaviours.namedBehaviour( selector.selectFragmentWithMaximumCount( "toggle single id" ), "button1" );
				behaviours.namedBehaviour( selector.merge( assignments ), "shift button1" );
				behaviours.namedBehaviour( selector.detach( assignments ), "shift button3" );
				behaviours.namedBehaviour( selector.confirm( assignments ), "ctrl shift button1" );
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
