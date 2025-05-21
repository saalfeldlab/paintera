package org.janelia.saalfeldlab.util.concurrent;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class LatestTaskExecutor implements Executor {

	private final ExecutorService executor;

	private Future<?> task;

	public LatestTaskExecutor(final ThreadFactory factory) {

		super();
		this.executor = Executors.newSingleThreadExecutor(factory);
	}

	@Override
	public synchronized void execute(final Runnable command) {
		submit(command);
	}

	public synchronized Future<?> submit(final Runnable command) {
		if (task != null) {
			/* cancel if it hasn't started, let it run if not */
			task.cancel(false);
		}
		final Future<?> future = executor.submit(command);
		task = future;
		return future;
	}

	public synchronized boolean busy() {

		return task == null || !(task.isDone() || task.isCancelled()) ;
	}

	public synchronized void shutDown() {

		this.executor.shutdown();
	}

	public synchronized List<Runnable> shutdownNow() {

		return this.executor.shutdownNow();
	}

	@Deprecated
	public synchronized void setDelay(final long delayInNanoSeconds) {

		//no-op; class should be removed in the future
	}
}
