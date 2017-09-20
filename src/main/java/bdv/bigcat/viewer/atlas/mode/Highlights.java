package bdv.bigcat.viewer.atlas.mode;

import java.util.HashMap;
import java.util.function.Consumer;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;

public class Highlights extends AbstractStateMode
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< ViewerPanel, MouseAndKeyHandler > mouseAndKeyHandlers = new HashMap<>();

	public Highlights( final HashMap< Source< ? >, SelectedIds > selectedIds )
	{
		this.selectedIds = selectedIds;
	}

	public void addSource( final Source< ? > source, final Source< ? > dataSources, final ToIdConverter toIdConverter )
	{

		this.dataSources.put( source, dataSources );
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
	protected Consumer< ViewerPanel > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();
				final IdSelector selector = new IdSelector( t, toIdConverters, selectedIds, dataSources );
				final Behaviours behaviours = new Behaviours( inputTriggerConfig );
				behaviours.namedBehaviour( selector.selectSingle( "toggle single id", new SelectionDialog( "oge1" ) ), "button1" );
				behaviours.namedBehaviour( selector.append( "append id", new SelectionDialog( "oge2" ) ), "button3" );
				final TriggerBehaviourBindings bindings = new TriggerBehaviourBindings();
				behaviours.install( bindings, "id selection bindings" );
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
