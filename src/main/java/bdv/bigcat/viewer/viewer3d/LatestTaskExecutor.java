package bdv.bigcat.viewer.viewer3d;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LatestTaskExecutor implements Executor
{
	private Runnable task = null;

	private final ScheduledExecutorService executor;

	private final long delayInNanoSeconds;

	public LatestTaskExecutor()
	{
		this( 0 );
	}

	public LatestTaskExecutor( final long delayInNanoSeconds )
	{
		super();
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.delayInNanoSeconds = delayInNanoSeconds;
	}

	@Override
	public void execute( final Runnable command )
	{
		synchronized ( this )
		{
			final Runnable pendingTask = task;
			task = command;
			if ( pendingTask == null )
			{
				executor.schedule(
						() -> {
							final Runnable currentTask;
							synchronized ( LatestTaskExecutor.this )
							{
								currentTask = task;
								task = null;
							}
							currentTask.run();
						}, delayInNanoSeconds, TimeUnit.NANOSECONDS );
			}
		}
	}

	public boolean busy()
	{
		return task == null;
	}
}
