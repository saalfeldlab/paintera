/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.fx.viewer.project;

import bdv.fx.viewer.PriorityExecutorService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.ui.InterruptibleProjector;
import net.imglib2.ui.util.StopWatch;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AccumulateProjector< A, B > implements VolatileProjector
{
	protected final ArrayList< VolatileProjector > sourceProjectors;

	protected final ArrayList< IterableInterval< ? extends A > > sources;

	/**
	 * The target interval. Pixels of the target interval should be set by
	 * {@link InterruptibleProjector#map()}
	 */
	protected final RandomAccessibleInterval< B > target;

	/**
	 * A reference to the target image as an iterable.  Used for source-less
	 * operations such as clearing its content.
	 */
	protected final IterableInterval< B > iterableTarget;

	/**
     * Number of threads to use for rendering
     */
    protected final int numThreads;

	protected final PriorityExecutorService executorService;

    /**
     * Time needed for rendering the last frame, in nano-seconds.
     */
    protected long lastFrameRenderNanoTime;

	protected final AtomicBoolean interrupted = new AtomicBoolean();

	protected volatile boolean valid = false;

	public AccumulateProjector(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ? extends A > > sources,
			final RandomAccessibleInterval< B > target,
			final int numThreads,
			final PriorityExecutorService executorService )
	{
		this.sourceProjectors = sourceProjectors;
		this.sources = new ArrayList<>();
		for ( final RandomAccessible< ? extends A > source : sources )
			this.sources.add( Views.flatIterable( Views.interval( source, target ) ) );
		this.target = target;
		this.iterableTarget = Views.flatIterable( target );
		this.numThreads = numThreads;
		this.executorService = executorService;
		lastFrameRenderNanoTime = -1;
	}

	@Override
	public boolean map(final double priority)
	{
		return map(priority,true);
	}

	@Override
	public boolean map(final double priority, final boolean clearUntouchedTargetPixels)
	{
		interrupted.set( false );

		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		valid = true;
		for ( final VolatileProjector p : sourceProjectors )
			if ( !p.isValid() )
				if (!p.map(priority, clearUntouchedTargetPixels))
					return false;
				else
					valid &= p.isValid();

		final int width = ( int ) target.dimension( 0 );
		final int height = ( int ) target.dimension( 1 );
		final int length = width * height;

		final int numTasks = Math.min( numThreads * 10, height );
		final double taskLength = ( double ) length / numTasks;
		final int numSources = sources.size();
		final ArrayList<Runnable> tasks = new ArrayList<>(numTasks);
		final CountDownLatch latch = new CountDownLatch(numTasks);
		for ( int taskNum = 0; taskNum < numTasks; ++taskNum )
		{
			final int myOffset = ( int ) ( taskNum * taskLength );
			final int myLength = ( (taskNum == numTasks - 1 ) ? length : ( int ) ( ( taskNum + 1 ) * taskLength ) ) - myOffset;

			final Runnable r = () -> {
				try {
					if (interrupted.get())
						return;

					final Cursor<? extends A>[] sourceCursors = new Cursor[numSources];
					for (int s = 0; s < numSources; ++s) {
						final Cursor<? extends A> c = sources.get(s).cursor();
						c.jumpFwd(myOffset);
						sourceCursors[s] = c;
					}
					final Cursor<B> targetCursor = iterableTarget.cursor();
					targetCursor.jumpFwd(myOffset);

					for (int i = 0; i < myLength; ++i) {
						for (int s = 0; s < numSources; ++s)
							sourceCursors[s].fwd();
						accumulate(sourceCursors, targetCursor.next());
					}
					return;
				}
				finally {
					latch.countDown();
				}
			};
			tasks.add(r);
		}
		try
		{
			tasks.forEach(t -> executorService.submit(t, priority));
			latch.await();
		}
		catch ( final InterruptedException e )
		{
			Thread.currentThread().interrupt();
		}

		lastFrameRenderNanoTime = stopWatch.nanoTime();

		return !interrupted.get();
	}

	protected abstract void accumulate( final Cursor< ? extends A >[] accesses, final B target );

	@Override
	public void cancel()
	{
		interrupted.set( true );
		for ( final VolatileProjector p : sourceProjectors )
			p.cancel();
	}

	@Override
	public long getLastFrameRenderNanoTime()
	{
		return lastFrameRenderNanoTime;
	}

	@Override
	public boolean isValid()
	{
		return valid;
	}
}
