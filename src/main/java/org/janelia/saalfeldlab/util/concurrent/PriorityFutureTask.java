package org.janelia.saalfeldlab.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class PriorityFutureTask<T> extends FutureTask<T>
{
	private int priority;

	public PriorityFutureTask(final Callable<T> callable, final int priority)
	{
		super(callable);
		validatePriority(priority);
		this.priority = priority;
	}

	public PriorityFutureTask(final Runnable runnable, final T result, final int priority)
	{
		super(runnable, result);
		validatePriority(priority);
		this.priority = priority;
	}

	public int getPriority()
	{
		return priority;
	}

	public void setPriority(final int priority)
	{
		validatePriority(priority);
		this.priority = priority;
	}

	private void validatePriority(final int priority)
	{
		if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY)
			throw new IllegalArgumentException(String.format("Priority should be between Thread.MIN_PRIORITY (%d) and Thread.MAX_PRIORITY (%d)", Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
	}

	@Override
	public void run()
	{
		final int originalPriority = Thread.currentThread().getPriority();
		Thread.currentThread().setPriority(priority);
		super.run();
		Thread.currentThread().setPriority(originalPriority);
	}

	@Override
	public String toString()
	{
		return String.format("Priority: %d", priority);
	}
}