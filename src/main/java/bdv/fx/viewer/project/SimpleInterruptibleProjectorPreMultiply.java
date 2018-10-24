package bdv.fx.viewer.project;

import bdv.fx.viewer.PriorityExecutorService;
import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.util.StopWatch;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An {@link InterruptibleProjector}, that renders a target 2D {@link RandomAccessibleInterval} by copying values from a
 * source {@link RandomAccessible}. The source can have more dimensions than the target. Target coordinate
 * <em>(x,y)</em> is copied from source coordinate
 * <em>(x,y,0,...,0)</em>.
 * <p>
 * A specified number of threads is used for rendering.
 *
 * @param <A>
 * 		pixel type of the source {@link RandomAccessible}.
 * @author Tobias Pietzsch
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
@SuppressWarnings("restriction")
public class SimpleInterruptibleProjectorPreMultiply<A> extends AbstractInterruptibleProjector<A, ARGBType>
{
	final protected RandomAccessible<A> source;

	/**
	 * Number of threads to use for rendering
	 */
	final protected int numThreads;

//	final protected ExecutorService executorService;

	private final PriorityExecutorService executorService;

	/**
	 * Time needed for rendering the last frame, in nano-seconds.
	 */
	protected long lastFrameRenderNanoTime;

	/**
	 * Create new projector with the given source and a converter from source to target pixel type.
	 *
	 * @param source
	 * 		source pixels.
	 * @param converter
	 * 		converts from the source pixel type to the target pixel type.
	 * @param target
	 * 		the target interval that this projector maps to
	 * @param numThreads
	 * 		how many threads to use for rendering.
	 */
	public SimpleInterruptibleProjectorPreMultiply(
			final RandomAccessible<A> source,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads)
	{
		this(source, converter, target, numThreads, null);
	}

	public SimpleInterruptibleProjectorPreMultiply(
			final RandomAccessible<A> source,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads,
			final PriorityExecutorService executorService)
	{
		super(source.numDimensions(), converter, target);
		this.source = source;
		this.numThreads = numThreads;
		this.executorService = executorService;
		lastFrameRenderNanoTime = -1;
	}

	/**
	 * Render the 2D target image by copying values from the source. Source can have more dimensions than the target.
	 * Target coordinate <em>(x,y)</em> is copied from source coordinate <em>(x,y,0,...,0)</em>.
	 *
	 * @return true if rendering was completed (all target pixels written). false if rendering was interrupted.
	 */
	@Override
	public boolean map(final double priority)
	{
		interrupted.set(false);

		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		min[0] = target.min(0);
		min[1] = target.min(1);
		max[0] = target.max(0);
		max[1] = target.max(1);

		final long cr = -target.dimension(0);

		final int width  = (int) target.dimension(0);
		final int height = (int) target.dimension(1);
		final int             numTasks;
		if (numThreads > 1)
		{
			numTasks = Math.min(numThreads * 10, height);
		}
		else
			numTasks = 1;
		final double                    taskHeight = (double) height / numTasks;
		final ArrayList<Runnable> tasks      = new ArrayList<>(numTasks);
		final CountDownLatch latch = new CountDownLatch(numTasks);
		for (int taskNum = 0; taskNum < numTasks; ++taskNum)
		{
			final long myMinY   = min[1] + (int) (taskNum * taskHeight);
			final long myHeight = (taskNum == numTasks - 1
			                       ? height
			                       : (int) ((taskNum + 1) * taskHeight)) - myMinY - min[1];

			final Runnable r = () -> {
				try {
					if (interrupted.get())
						return;

					final RandomAccess<A> sourceRandomAccess = source.randomAccess(
							SimpleInterruptibleProjectorPreMultiply.this);
					final RandomAccess<ARGBType> targetRandomAccess = target.randomAccess(target);

					sourceRandomAccess.setPosition(min);
					sourceRandomAccess.setPosition(myMinY, 1);
					targetRandomAccess.setPosition(min[0], 0);
					targetRandomAccess.setPosition(myMinY, 1);
					for (int y = 0; y < myHeight; ++y) {
						if (interrupted.get())
							return;
						for (int x = 0; x < width; ++x) {
							final ARGBType argb = targetRandomAccess.get();
							converter.convert(sourceRandomAccess.get(), argb);
							final int nonpre = argb.get();
							argb.set(PixelUtils.NonPretoPre(nonpre));
							sourceRandomAccess.fwd(0);
							targetRandomAccess.fwd(0);
						}
						sourceRandomAccess.move(cr, 0);
						targetRandomAccess.move(cr, 0);
						sourceRandomAccess.fwd(1);
						targetRandomAccess.fwd(1);
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
		} catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		lastFrameRenderNanoTime = stopWatch.nanoTime();

		return !interrupted.get();
	}

	protected AtomicBoolean interrupted = new AtomicBoolean();

	@Override
	public void cancel()
	{
		interrupted.set(true);
	}

	@Override
	public long getLastFrameRenderNanoTime()
	{
		return lastFrameRenderNanoTime;
	}
}
