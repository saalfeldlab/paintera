package org.janelia.saalfeldlab.util.concurrent;

import java.util.*;
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
 */
public class PriorityThreadPoolExecutor<P extends Comparable<? super P>> extends ThreadPoolExecutor implements PriorityExecutorService<P>
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
		return submit(task, null);
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result)
	{
		return submit(task, result, null);
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task)
	{
		return submit(task, null);
	}

	@Override
	public Future<?> submit(final Runnable task, final P priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<Object> futureTask = new PriorityFutureTask<>(task, null, priority);
		execute(futureTask);
		return futureTask;
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result, final P priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<T> futureTask = new PriorityFutureTask<>(task, result, priority);
		execute(futureTask);
		return futureTask;
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task, final P priority)
	{
		Objects.requireNonNull(task);
		final RunnableFuture<T> futureTask = new PriorityFutureTask<>(task, priority);
		execute(futureTask);
		return futureTask;
	}

	@Override
	public <T> List<Future<T>> submitAll(final List<Runnable> tasks, final List<T> results, final List<P> priorities)
	{
		assert tasks.size() == results.size();
		assert tasks.size() == priorities.size();

		final List<PriorityFutureTask<T, P>> futureTasks = new ArrayList<>();
		for (int i = 0; i < Math.max(tasks.size(), priorities.size()); ++i)
			futureTasks.add(new PriorityFutureTask<>(tasks.get(i), results.get(i), priorities.get(i)));

		return submitAll(futureTasks);
	}

	@Override
	public <T> List<Future<T>> submitAll(final List<Callable<T>> tasks, final List<P> priorities)
	{
		assert tasks.size() == priorities.size();

		final List<PriorityFutureTask<T, P>> futureTasks = new ArrayList<>();
		for (int i = 0; i < Math.max(tasks.size(), priorities.size()); ++i)
			futureTasks.add(new PriorityFutureTask<>(tasks.get(i), priorities.get(i)));

		return submitAll(futureTasks);
	}

	private <T> List<Future<T>> submitAll(final List<PriorityFutureTask<T, P>> futureTasks)
	{
		final List<Future<T>> ret = new ArrayList<>(futureTasks);
		// order future tasks based on their priorities to submit them in the expected order
		Collections.sort(futureTasks, new PriorityFutureTaskComparator<>());
		futureTasks.forEach(this::execute);
		return ret;
	}

	private static class PriorityFutureTaskComparator<P extends Comparable<? super P>> implements Comparator<PriorityFutureTask<?, P>>
	{
		@Override
		public int compare(final PriorityFutureTask<?, P> t1, final PriorityFutureTask<?, P> t2)
		{
			final P p1 = t1.getPriority(), p2 = t2.getPriority();

			if (p1 == null && p2 == null)
				return 0;
			else if (p1 == null)
				return 1;
			else if (p2 == null)
				return -1;

			return p1.compareTo(p2);
		}
	}
}
