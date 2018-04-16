package bdv.bigcat.viewer.ortho;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory
{

	private final AtomicInteger threadCount = new AtomicInteger( 0 );

	private final String format;

	public NamedThreadFactory( final String format )
	{
		super();
		this.format = format;
	}

	@Override
	public Thread newThread( final Runnable r )
	{
		final Thread t = new Thread( r );
		t.setName( String.format( format, threadCount.incrementAndGet() ) );
		return t;
	}

}
