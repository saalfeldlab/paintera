package org.janelia.saalfeldlab.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface PriorityExecutorService extends ExecutorService
{
	Future<?> submit(Runnable task, int priority);

	<T> Future<T> submit(Runnable task, T result, int priority);

	<T> Future<T> submit(Callable<T> task, int priority);
}
