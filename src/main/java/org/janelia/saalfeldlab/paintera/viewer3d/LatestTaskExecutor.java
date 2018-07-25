package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class LatestTaskExecutor implements Executor
{
	private Runnable task = null;

	private final ScheduledExecutorService executor;

	private long delayInNanoSeconds;

	public LatestTaskExecutor(final ThreadFactory factory)
	{
		this(0, factory);
	}

	public LatestTaskExecutor(final long delayInNanoSeconds, final ThreadFactory factory)
	{
		super();
		this.executor = Executors.newSingleThreadScheduledExecutor(factory);
		this.delayInNanoSeconds = delayInNanoSeconds;
	}

	@Override
	public void execute(final Runnable command)
	{
		synchronized (this)
		{
			final Runnable pendingTask = task;
			task = command;
			if (pendingTask == null)
			{
				executor.schedule(
						() -> {
							final Runnable currentTask;
							synchronized (LatestTaskExecutor.this)
							{
								currentTask = task;
								task = null;
							}
							currentTask.run();
						}, delayInNanoSeconds, TimeUnit.NANOSECONDS);
			}
		}
	}

	public boolean busy()
	{
		return task == null;
	}

	public void shutDown()
	{
		this.executor.shutdown();
	}

	public List<Runnable> shutdownNow()
	{
		return this.executor.shutdownNow();
	}

	public void setDelay(final long delayInNanoSeconds)
	{
		this.delayInNanoSeconds = delayInNanoSeconds;
	}
}
