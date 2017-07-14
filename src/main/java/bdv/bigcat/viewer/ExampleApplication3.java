package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import javafx.application.Application;
import javafx.stage.Stage;

public class ExampleApplication3 extends Application
{

	public ExampleApplication3()
	{
		super();
	}

	public static HashMap< Long, OrthoView > activeViewers = new HashMap<>();

	public static AtomicLong index = new AtomicLong( 0 );

	public static void main( final String[] args ) throws Exception
	{
		final OrthoView viewer = makeViewer();

	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		final OrthoView viewer = new OrthoView();
		viewer.start( primaryStage );
		System.out.println( getParameters() );
		activeViewers.put( Long.parseLong( getParameters().getRaw().get( 0 ) ), viewer );
	}

	public static OrthoView makeViewer() throws InterruptedException
	{

		synchronized ( index )
		{
			final long idx = index.get();
			final Thread t = new Thread( () -> Application.launch( ExampleApplication3.class, Long.toString( idx ) ) );
			t.start();
			while ( !activeViewers.containsKey( idx ) )
				Thread.sleep( 10 );
			return activeViewers.get( idx );
		}
	}
}