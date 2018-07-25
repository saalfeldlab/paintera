package bdv.fx.viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import bdv.viewer.render.VolatileProjector;
import com.sun.javafx.image.PixelUtils;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.cache.iotiming.IoStatistics;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.ui.AbstractInterruptibleProjector;
import net.imglib2.ui.util.StopWatch;
import net.imglib2.view.Views;

/**
 * {@link VolatileProjector} for a hierarchy of {@link Volatile} inputs. After each {@link #map()} call, the projector
 * has a {@link #isValid() state} that signalizes whether all projected pixels were perfect.
 *
 * @author Stephan Saalfeld &lt;saalfeld@mpi-cbg.de&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * @author Philipp Hanslovsky
 */
@SuppressWarnings("restriction")
public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>>
		extends AbstractInterruptibleProjector<A, ARGBType> implements VolatileProjector
{
	protected final ArrayList<RandomAccessible<A>> sources = new ArrayList<>();

	private final byte[] maskArray;

	protected final Img<ByteType> mask;

	protected volatile boolean valid = false;

	protected int numInvalidLevels;

	/**
	 * Extends of the source to be used for mapping.
	 */
	protected final FinalInterval sourceInterval;

	/**
	 * Target width
	 */
	protected final int width;

	/**
	 * Target height
	 */
	protected final int height;

	/**
	 * Steps for carriage return. Typically -{@link #width}
	 */
	protected final int cr;

	/**
	 * A reference to the target image as an iterable. Used for source-less operations such as clearing its content.
	 */
	protected final IterableInterval<ARGBType> iterableTarget;

	/**
	 * Number of threads to use for rendering
	 */
	protected final int numThreads;

	protected final ExecutorService executorService;

	/**
	 * Time needed for rendering the last frame, in nano-seconds. This does not include time spent in blocking IO.
	 */
	protected long lastFrameRenderNanoTime;

	/**
	 * Time spent in blocking IO rendering the last frame, in nano-seconds.
	 */
	protected long lastFrameIoNanoTime; // TODO move to derived implementation
	// for local sources only

	/**
	 * temporary variable to store the number of invalid pixels in the current rendering pass.
	 */
	protected final AtomicInteger numInvalidPixels = new AtomicInteger();

	/**
	 * Flag to indicate that someone is trying to interrupt rendering.
	 */
	protected final AtomicBoolean interrupted = new AtomicBoolean();

	public VolatileHierarchyProjectorPreMultiply(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads,
			final ExecutorService executorService)
	{
		this(
				sources,
				converter,
				target,
				new byte[(int) (target.dimension(0) * target.dimension(1))],
				numThreads,
				executorService
		    );
	}

	public VolatileHierarchyProjectorPreMultiply(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final byte[] maskArray,
			final int numThreads,
			final ExecutorService executorService)
	{
		super(Math.max(2, sources.get(0).numDimensions()), converter, target);

		this.sources.addAll(sources);
		numInvalidLevels = sources.size();

		this.maskArray = maskArray;
		mask = ArrayImgs.bytes(maskArray, target.dimension(0), target.dimension(1));

		iterableTarget = Views.iterable(target);

		for (int d = 2; d < min.length; ++d)
			min[d] = max[d] = 0;

		max[0] = target.max(0);
		max[1] = target.max(1);
		sourceInterval = new FinalInterval(min, max);

		width = (int) target.dimension(0);
		height = (int) target.dimension(1);
		cr = -width;

		this.numThreads = numThreads;
		this.executorService = executorService;

		lastFrameRenderNanoTime = -1;
		clearMask();
	}

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

	public long getLastFrameIoNanoTime()
	{
		return lastFrameIoNanoTime;
	}

	@Override
	public boolean isValid()
	{
		return valid;
	}

	/**
	 * Set all pixels in target to 100% transparent zero, and mask to all Integer.MAX_VALUE.
	 */
	public void clearMask()
	{
		Arrays.fill(maskArray, 0, (int) mask.size(), Byte.MAX_VALUE);
		numInvalidLevels = sources.size();
	}

	/**
	 * Clear target pixels that were never written.
	 */
	protected void clearUntouchedTargetPixels()
	{
		final Cursor<ByteType> maskCursor = mask.cursor();
		for (final ARGBType t : iterableTarget)
			if (maskCursor.next().get() == Byte.MAX_VALUE)
				t.setZero();
	}

	@Override
	public boolean map()
	{
		return map(true);
	}

	@Override
	public boolean map(final boolean clearUntouchedTargetPixels)
	{
		interrupted.set(false);

		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		final IoStatistics iostat                = CacheIoTiming.getIoStatistics();
		final long         startTimeIo           = iostat.getIoNanoTime();
		final long         startTimeIoCumulative = iostat.getCumulativeIoNanoTime();
		//		final long startIoBytes = iostat.getIoBytes();

		final int numTasks;
		if (numThreads > 1)
		{
			numTasks = Math.min(numThreads * 10, height);
		}
		else
			numTasks = 1;
		final double taskHeight = (double) height / numTasks;

		int i;

		valid = false;

		final boolean         createExecutor = executorService == null;
		final ExecutorService ex             = createExecutor
		                                       ? Executors.newFixedThreadPool(numThreads)
		                                       : executorService;
		for (i = 0; i < numInvalidLevels && !valid; ++i)
		{
			final byte iFinal = (byte) i;

			valid = true;
			numInvalidPixels.set(0);

			final ArrayList<Callable<Void>> tasks = new ArrayList<>(numTasks);
			for (int taskNum = 0; taskNum < numTasks; ++taskNum)
			{
				final int  myOffset = width * (int) (taskNum * taskHeight);
				final long myMinY   = min[1] + (int) (taskNum * taskHeight);
				final int  myHeight = (int) ((taskNum == numTasks - 1
				                              ? height
				                              : (int) ((taskNum + 1) * taskHeight)) - myMinY - min[1]);

				final Callable<Void> r = () -> {
					if (interrupted.get())
						return null;

					final RandomAccess<ARGBType> targetRandomAccess = target.randomAccess(target);
					final Cursor<ByteType>       maskCursor         = mask.cursor();
					final RandomAccess<A>        sourceRandomAccess = sources.get(iFinal).randomAccess(sourceInterval);
					int                          myNumInvalidPixels = 0;

					final long[] smin = new long[n];
					System.arraycopy(min, 0, smin, 0, n);
					smin[1] = myMinY;
					sourceRandomAccess.setPosition(smin);

					targetRandomAccess.setPosition(min[0], 0);
					targetRandomAccess.setPosition(myMinY, 1);

					maskCursor.jumpFwd(myOffset);

					for (int y = 0; y < myHeight; ++y)
					{
						if (interrupted.get())
							return null;

						for (int x = 0; x < width; ++x)
						{
							final ByteType m = maskCursor.next();
							if (m.get() > iFinal)
							{
								final A       a = sourceRandomAccess.get();
								final boolean v = a.isValid();
								if (v)
								{
									final ARGBType argb = targetRandomAccess.get();
									converter.convert(a, argb);
									argb.set(PixelUtils.NonPretoPre(argb.get()));
									m.set(iFinal);
								}
								else
									++myNumInvalidPixels;
							}
							sourceRandomAccess.fwd(0);
							targetRandomAccess.fwd(0);
						}
						++smin[1];
						sourceRandomAccess.setPosition(smin);
						targetRandomAccess.move(cr, 0);
						targetRandomAccess.fwd(1);
					}
					numInvalidPixels.addAndGet(myNumInvalidPixels);
					if (myNumInvalidPixels != 0)
						valid = false;
					return null;
				};
				tasks.add(r);
			}
			try
			{
				ex.invokeAll(tasks);
			} catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			if (interrupted.get())
			{
				//				System.out.println( "interrupted" );
				if (createExecutor)
					ex.shutdown();
				return false;
			}
			//			System.out.println( "numInvalidPixels(" + i + ") = " + numInvalidPixels );
		}
		if (createExecutor)
			ex.shutdown();

		if (clearUntouchedTargetPixels && !interrupted.get())
			clearUntouchedTargetPixels();

		final long lastFrameTime = stopWatch.nanoTime();
		//		final long numIoBytes = iostat.getIoBytes() - startIoBytes;
		lastFrameIoNanoTime = iostat.getIoNanoTime() - startTimeIo;
		lastFrameRenderNanoTime = lastFrameTime - (iostat.getCumulativeIoNanoTime() - startTimeIoCumulative) /
				numThreads;

		//		System.out.println( "lastFrameTime = " + lastFrameTime / 1000000 );
		//		System.out.println( "lastFrameRenderNanoTime = " + lastFrameRenderNanoTime / 1000000 );

		if (valid)
			numInvalidLevels = i - 1;
		valid = numInvalidLevels == 0;

		//		System.out.println( "Mapping complete after " + ( s + 1 ) + " levels." );

		return !interrupted.get();
	}
}
