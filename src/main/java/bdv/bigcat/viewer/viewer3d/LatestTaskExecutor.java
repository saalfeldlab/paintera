package bdv.bigcat.viewer.viewer3d;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class LatestTaskExecutor implements Executor
{
	private final AtomicReference< Runnable > lastTask = new AtomicReference<>();

	private final Executor executor;

	public LatestTaskExecutor()
	{
		this( Executors.newSingleThreadExecutor() );
	}

	public LatestTaskExecutor( final Executor executor )
	{
		super();
		this.executor = executor;
	}

	@Override
	public void execute( final Runnable command )
	{
		lastTask.set( command );
		executor.execute( () -> {
			final Runnable task = lastTask.getAndSet( null );
			if ( task != null )
				task.run();
		} );

	}
}
