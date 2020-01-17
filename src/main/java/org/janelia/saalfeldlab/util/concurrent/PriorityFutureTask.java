package org.janelia.saalfeldlab.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class PriorityFutureTask<T, P extends Comparable<? super P>> extends FutureTask<T>
{
	private final P priority;

	public PriorityFutureTask(final Callable<T> callable, final P priority)
	{
		super(callable);
		this.priority = priority;
	}

	public PriorityFutureTask(final Runnable runnable, final T result, final P priority)
	{
		super(runnable, result);
		this.priority = priority;
	}

	public P getPriority()
	{
		return priority;
	}
}
