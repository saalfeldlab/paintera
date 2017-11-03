package bdv.bigcat.viewer.viewer3d;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class LatestTaskExecutor implements Executor
{
	private final AtomicReference< Runnable > lastTask = new AtomicReference<>();

	private final Executor executor;

	private final long delayInNanoSeconds;

	private long lastTime = -1;

	public LatestTaskExecutor()
	{
		this( 0 );
	}

	public LatestTaskExecutor( final long delayInNanoSeconds )
	{
		this( Executors.newSingleThreadExecutor(), delayInNanoSeconds );
	}

	public LatestTaskExecutor( final Executor executor, final long delayInNanoSeconds )
	{
		super();
		this.executor = executor;
		this.delayInNanoSeconds = delayInNanoSeconds;
	}

	@Override
	public void execute( final Runnable command )
	{
		lastTask.set( command );
		executor.execute( () -> {
			final Runnable task = lastTask.getAndSet( null );
			if ( task != null )
				if ( lastTime == -1 || System.nanoTime() - lastTime > delayInNanoSeconds )
				{
					task.run();
					lastTime = System.nanoTime();
				}
		} );

	}
}
