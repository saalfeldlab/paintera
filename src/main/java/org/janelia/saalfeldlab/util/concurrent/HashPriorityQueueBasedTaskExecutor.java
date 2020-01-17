package org.janelia.saalfeldlab.util.concurrent;

import org.janelia.saalfeldlab.util.HashPriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Igor Pisarev
 *
 * Provides similar functionality to {@link java.util.concurrent.ExecutorService} for prioritized tasks.
 *
 * In contrast to using a standard thread pool executor with a {@link java.util.concurrent.PriorityBlockingQueue},
 * this class allows to efficiently change the priority of already submitted tasks.
 *
 * @param <P> task priority type
 */
public class HashPriorityQueueBasedTaskExecutor<P>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final HashPriorityQueue<P, Runnable> priorityQueue;

	private final Thread[] workers;

	private final Runnable[] runningTasks;

	private final AtomicBoolean isShutdown = new AtomicBoolean(false);

	public HashPriorityQueueBasedTaskExecutor(
			final Comparator<? super P> comparator,
			final int numThreads,
			final ThreadFactory threadFactory)
	{
		priorityQueue = new HashPriorityQueue<>(comparator);
		workers = new Thread[numThreads];
		runningTasks = new Runnable[numThreads];
		Arrays.setAll(workers, i -> threadFactory.newThread(() -> runWorker(i)));
		Arrays.stream(workers).forEach(Thread::start);
	}

	public synchronized void addOrUpdateTask(final Runnable task, final P priority)
	{
		addOrUpdateTasks(Collections.singletonMap(task, priority));
	}

	public synchronized void addOrUpdateTasks(final Map<Runnable, P> tasks)
	{
		if (tasks.isEmpty() || isShutdown.get())
			return;

		for (final Entry<Runnable, P> entry : tasks.entrySet())
			priorityQueue.addOrUpdate(entry.getValue(), entry.getKey());
		notifyAll();
	}

	public synchronized void removeTask(final Runnable task)
	{
		removeTasks(Collections.singleton(task));
	}

	public synchronized void removeTasks(final Set<Runnable> tasks)
	{
		if (tasks.isEmpty() || isShutdown.get())
			return;

		interruptTasks(tasks);
		tasks.forEach(priorityQueue::remove);
	}

	public synchronized boolean containsTask(final Runnable task)
	{
		return priorityQueue.contains(task);
	}

	public synchronized P getPriority(final Runnable task)
	{
		return priorityQueue.getPriority(task);
	}

	public synchronized void removeAllTasks()
	{
		priorityQueue.clear();
		Arrays.stream(workers).forEach(Thread::interrupt);
		Arrays.fill(runningTasks, null);
	}

	public synchronized void shutdown()
	{
		isShutdown.set(true);
		removeAllTasks();
	}

	public synchronized boolean isShutdown()
	{
		return isShutdown.get();
	}

	private synchronized void interruptTasks(final Set<Runnable> tasks)
	{
		for (int i = 0; i < runningTasks.length; ++i)
		{
			if (runningTasks[i] != null && tasks.contains(runningTasks[i]))
			{
				workers[i].interrupt();
				runningTasks[i] = null;
			}
		}
	}

	private void runWorker(final int workerIndex)
	{
		while (!isShutdown.get())
		{
			final Runnable task;
			synchronized (this)
			{
				if (isShutdown.get())
					return;

				runningTasks[workerIndex] = null;
				while (priorityQueue.isEmpty())
				{
					try
					{
						wait();
					}
					catch (final InterruptedException e)
					{
						if (isShutdown.get())
							return;
						else
							continue;
					}
				}
				task = priorityQueue.poll();
				runningTasks[workerIndex] = task;
			}

			if (!isShutdown.get())
				task.run();

			// Reset the interrupted status in case the task has been interrupted
			Thread.interrupted();
		}
	}
}
