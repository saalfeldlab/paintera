package org.janelia.saalfeldlab.util.concurrent;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class LatestTaskExecutor implements Executor {

	private final ScheduledExecutorService executor;

	private Future<?> task;

	private long delayInNanoSeconds;

	private long lastTaskExecutionNanoTime;

	public LatestTaskExecutor(final ThreadFactory factory) {

		this(0, factory);
	}

	public LatestTaskExecutor(final long delayInNanoSeconds, final ThreadFactory factory) {

		super();
		this.executor = Executors.newSingleThreadScheduledExecutor(factory);
		this.delayInNanoSeconds = delayInNanoSeconds;
	}

	@Override
	public synchronized void execute(final Runnable command) {
		submit(command);
	}

	public synchronized Future<?> submit(final Runnable command) {
		if (task == null || task.isDone()) {
			final long nsDelay = Math.max(delayInNanoSeconds - (System.nanoTime() - lastTaskExecutionNanoTime), 0);
			task = executor.schedule(() -> {
						command.run();
						synchronized (this) {
							lastTaskExecutionNanoTime = System.nanoTime();
							task = null;
						}
					},
					nsDelay, TimeUnit.NANOSECONDS
			);
		}
		return task;
	}

	public synchronized boolean busy() {

		return task == null;
	}

	public synchronized void shutDown() {

		this.executor.shutdown();
	}

	public synchronized List<Runnable> shutdownNow() {

		return this.executor.shutdownNow();
	}

	public synchronized void setDelay(final long delayInNanoSeconds) {

		this.delayInNanoSeconds = delayInNanoSeconds;
	}
}
