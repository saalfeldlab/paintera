package bdv.bigcat.viewer.panel;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.stage.Stage;
import javafx.stage.Window;

public class OnWindowInitListener implements ChangeListener< Window >
{

	private final Predicate< Window > windowCheck;

	private final Consumer< Window > windowConsumer;

	public OnWindowInitListener( final Consumer< Window > windowConsumer )
	{
		this( window -> window != null, windowConsumer );
	}

	public OnWindowInitListener( final Predicate< Window > windowCheck, final Consumer< Window > windowConsumer )
	{
		super();

		this.windowCheck = windowCheck;

		this.windowConsumer = windowConsumer;
	}

	@Override
	public void changed( final ObservableValue< ? extends Window > observable, final Window oldValue, final Window newValue )
	{
		if ( this.windowCheck.test( newValue ) )
		{
			observable.removeListener( this );
			this.windowConsumer.accept( newValue );
		}
	}

	public static OnSceneInitListener doOnStageInit( final Consumer< Stage > stageConsumer )
	{
		final OnWindowInitListener onWindowInit = new OnWindowInitListener( window -> window != null && window instanceof Stage, window -> stageConsumer.accept( ( Stage ) window ) );
		return new OnSceneInitListener( scene -> scene.windowProperty().addListener( onWindowInit ) );
	}

}
