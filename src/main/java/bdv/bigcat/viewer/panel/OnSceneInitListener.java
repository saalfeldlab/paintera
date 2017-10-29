package bdv.bigcat.viewer.panel;

import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;

public class OnSceneInitListener implements ChangeListener< Scene >
{

	private final Consumer< Scene > sceneConsumer;

	public OnSceneInitListener( final Consumer< Scene > sceneConsumer )
	{
		super();
		this.sceneConsumer = sceneConsumer;
	}

	@Override
	public void changed( final ObservableValue< ? extends Scene > observable, final Scene oldValue, final Scene newValue )
	{
		if ( newValue != null )
		{
			observable.removeListener( this );
			sceneConsumer.accept( newValue );
		}
	}

}
