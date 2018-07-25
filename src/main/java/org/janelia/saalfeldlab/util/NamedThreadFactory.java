package org.janelia.saalfeldlab.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory
{

	private final AtomicInteger threadCount = new AtomicInteger(0);

	private final String format;

	private final boolean createDaemonThreads;

	public NamedThreadFactory(final String format)
	{
		this(format, false);
	}

	public NamedThreadFactory(final String format, final boolean createDaemonThreads)
	{
		super();
		this.format = format;
		this.createDaemonThreads = createDaemonThreads;
	}

	@Override
	public Thread newThread(final Runnable r)
	{
		final Thread t = new Thread(r);
		t.setDaemon(createDaemonThreads);
		t.setName(String.format(format, threadCount.incrementAndGet()));
		return t;
	}

}
