package org.janelia.saalfeldlab.util.concurrent;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executes tasks with respect to their set priorities.
 * Additionally sets the priority to the worker thread assigned for the task as well.
 * Because of that, the priority is required to be an integer between {@link Thread#MIN_PRIORITY} and {@link Thread#MAX_PRIORITY}.
 *
 * Based on https://funofprograming.wordpress.com/2016/10/08/priorityexecutorservice-for-java/
 */
public class PriorityThreadPoolExecutor extends ThreadPoolExecutor implements PriorityExecutorService
{
	private static final RejectedExecutionHandler defaultHandler = new ThreadPoolExecutor.AbortPolicy();

	public PriorityThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit)
	{
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, Executors.defaultThreadFactory(), defaultHandler);
	}

	public PriorityThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ThreadFactory threadFactory)
	{
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, threadFactory, defaultHandler);
	}

	public PriorityThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final RejectedExecutionHandler handler)
	{
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, Executors.defaultThreadFactory(), handler);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public PriorityThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ThreadFactory threadFactory, final RejectedExecutionHandler handler)
	{
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new PriorityBlockingQueue<>(corePoolSize, new PriorityFutureTaskComparator()), threadFactory, handler);
	}

	@Override
	public Future<?> submit(final Runnable task)
	{
		return this.submit(task, Thread.NORM_PRIORITY);
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result)
	{
		return this.submit(task, result, Thread.NORM_PRIORITY);
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task)
	{
		return this.submit(task, Thread.NORM_PRIORITY);
	}

	@Override
	public Future<?> submit(final Runnable task, final int priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<Object> ftask = newPriorityTaskFor(task, null, priority);
		execute(ftask);
		return ftask;
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result, final int priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<T> ftask = newPriorityTaskFor(task, result, priority);
		execute(ftask);
		return ftask;
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task, final int priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<T> ftask = newPriorityTaskFor(task, priority);
		execute(ftask);
		return ftask;
	}

	protected <T> RunnableFuture<T> newPriorityTaskFor(final Runnable runnable, final T value, final int priority)
	{
		return new PriorityFutureTask<>(runnable, value, priority);
	}

	protected <T> RunnableFuture<T> newPriorityTaskFor(final Callable<T> callable, final int priority)
	{
		return new PriorityFutureTask<>(callable, priority);
	}

	@SuppressWarnings("rawtypes")
	private static class PriorityFutureTaskComparator<T extends PriorityFutureTask> implements Comparator<T>
	{
		@Override
		public int compare(final T t1, final T t2)
		{
			return t2.getPriority() - t1.getPriority();
		}
	}
}
