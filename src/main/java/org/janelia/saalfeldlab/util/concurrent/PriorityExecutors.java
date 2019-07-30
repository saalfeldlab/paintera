package org.janelia.saalfeldlab.util.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PriorityExecutors
{
	public static PriorityExecutorService newPriorityFixedThreadPool(final int nThreads)
	{
		return new PriorityThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS);
	}

	public static PriorityExecutorService newPriorityFixedThreadPool(final int nThreads, final ThreadFactory threadFactory)
	{
		return new PriorityThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, threadFactory);
	}

	public static PriorityExecutorService newPrioritySingleThreadPool()
	{
		return new PriorityThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS);
	}

	public static PriorityExecutorService newPrioritySingleThreadPool(final ThreadFactory threadFactory)
	{
		return new PriorityThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, threadFactory);
	}

	public static PriorityExecutorService newPriorityCachedThreadPool()
	{
		return new PriorityThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS);
	}

	public static PriorityExecutorService newPriorityCachedThreadPool(final ThreadFactory threadFactory)
	{
		return new PriorityThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, threadFactory);
	}
}
