package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PriorityLatestTaskExecutor
{
	private Runnable topPriorityTask;
	private int topPriority;

	private final ScheduledExecutorService executor;

	private long delayInNanoSeconds;

	private ScheduledFuture<?> scheduledFuture = null;

	public PriorityLatestTaskExecutor(final ThreadFactory factory)
	{
		this(0, factory);
	}

	public PriorityLatestTaskExecutor(final long delayInNanoSeconds, final ThreadFactory factory)
	{
		this.executor = Executors.newSingleThreadScheduledExecutor(factory);
		this.delayInNanoSeconds = delayInNanoSeconds;
	}

	/**
	 * Schedule the task with a given priority for execution.
	 * If the priority is lower than the current task, the new task is ignored.
	 * Otherwise, the current task is replaced by the new one.
	 *
	 * Only the task with the highest priority will be executed after the delay.
	 *
	 * @param task
	 * @param priority
	 * @return true if the task has higher priority than the currently scheduled one
	 */
	public synchronized boolean schedule(final Runnable task, final int priority)
	{
		if (topPriorityTask != null && priority < topPriority)
			return false;

		topPriorityTask = task;
		topPriority = priority;

		if (scheduledFuture == null || scheduledFuture.isDone())
		{
			scheduledFuture = executor.schedule(
				() -> {
					final Runnable currentTask;
					synchronized (this)
					{
						currentTask = topPriorityTask;
						topPriorityTask = null;
					}
					currentTask.run();
				}, delayInNanoSeconds, TimeUnit.NANOSECONDS
			);
		}

		return true;
	}

	public synchronized void cancel()
	{
		if (scheduledFuture != null && !scheduledFuture.isDone())
			scheduledFuture.cancel(false);
		scheduledFuture = null;
		topPriorityTask = null;
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
