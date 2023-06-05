package org.janelia.saalfeldlab.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

	private final AtomicInteger threadCount = new AtomicInteger(0);

	private final String format;

	private final boolean createDaemonThreads;

	private final Integer threadPriority;

	public NamedThreadFactory(final String format) {

		this(format, false, null);
	}

	public NamedThreadFactory(final String format, final boolean createDaemonThreads) {

		this(format, createDaemonThreads, null);
	}

	public NamedThreadFactory(final String format, final boolean createDaemonThreads, final Integer threadPriority) {

		super();
		this.format = format;
		this.createDaemonThreads = createDaemonThreads;
		this.threadPriority = threadPriority;

		if (threadPriority != null)
			assert threadPriority >= Thread.MIN_PRIORITY && threadPriority <= Thread.MAX_PRIORITY;
	}

	@Override
	public Thread newThread(final Runnable r) {

		final Thread t = new Thread(r);
		t.setDaemon(createDaemonThreads);
		t.setName(String.format(format, threadCount.incrementAndGet()));
		if (threadPriority != null)
			t.setPriority(threadPriority);
		return t;
	}

}
