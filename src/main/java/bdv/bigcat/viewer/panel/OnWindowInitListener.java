package bdv.bigcat.viewer.panel;

import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.stage.Window;

public class OnWindowInitListener implements ChangeListener< Window >
{

	private final Consumer< Window > windowConsumer;

	public OnWindowInitListener( final Consumer< Window > windowConsumer )
	{
		super();
		this.windowConsumer = windowConsumer;
	}

	@Override
	public void changed( final ObservableValue< ? extends Window > observable, final Window oldValue, final Window newValue )
	{
		if ( newValue != null )
		{
			observable.removeListener( this );
			this.windowConsumer.accept( newValue );
		}
	}

}
