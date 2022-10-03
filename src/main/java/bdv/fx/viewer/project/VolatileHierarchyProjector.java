/*
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2022 BigDataViewer developers.
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

import bdv.viewer.render.ProjectorUtils;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.*;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.cache.iotiming.IoStatistics;
import net.imglib2.converter.Converter;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.operators.SetZero;
import net.imglib2.util.Intervals;
import net.imglib2.util.StopWatch;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link VolatileProjector} for a hierarchy of {@link Volatile} inputs.  After each
 * {@link #map()} call, the projector has a {@link #isValid() state} that
 * signalizes whether all projected pixels were perfect.
 *
 * @author Stephan Saalfeld
 * @author Tobias Pietzsch
 */
public class VolatileHierarchyProjector<A extends Volatile<?>, B extends SetZero> implements VolatileProjector {
	/**
	 * A converter from the source pixel type to the target pixel type.
	 */
	protected final Converter<? super A, B> converter;

	/**
	 * The target interval. Pixels of the target interval should be set by
	 * {@link #map}
	 */
	protected final RandomAccessibleInterval<B> target;

	/**
	 * List of source resolutions starting with the optimal resolution at index
	 * 0. During each {@link #map(boolean)}, for every pixel, resolution levels
	 * are successively queried until a valid pixel is found.
	 */
	protected final List<RandomAccessible<A>> sources;

	/**
	 * Records, for every target pixel, the best (smallest index) source
	 * resolution level that has provided a valid value. Only better (lower
	 * index) resolutions are re-tried in successive {@link #map(boolean)}
	 * calls.
	 */
	protected final RandomAccessibleInterval<ByteType> mask;

	/**
	 * {@code true} iff all target pixels were rendered with valid data from the
	 * optimal resolution level (level {@code 0}).
	 */
	private volatile boolean valid = false;

	/**
	 * How many levels (starting from level {@code 0}) have to be re-rendered in
	 * the next rendering pass, i.e., {@code map()} call.
	 */
	private int numInvalidLevels;

	/**
	 * Source interval which will be used for rendering. This is the 2D target
	 * interval expanded to source dimensionality (usually 3D) with
	 * {@code min=max=0} in the additional dimensions.
	 */
	protected final FinalInterval sourceInterval;

	/**
	 * A reference to the target image as an iterable. Used for source-less
	 * operations such as clearing its content.
	 */
	protected final IterableInterval<B> iterableTarget;

	/**
	 * Number of threads to use for rendering
	 */
	private final int numThreads;

	/**
	 * Executor service to be used for rendering
	 */
	private final ExecutorService executorService;

	/**
	 * Time needed for rendering the last frame, in nano-seconds.
	 * This does not include time spent in blocking IO.
	 */
	private long lastFrameRenderNanoTime;

	/**
	 * Time spent in blocking IO rendering the last frame, in nano-seconds.
	 */
	private long lastFrameIoNanoTime; // TODO move to derived implementation for local sources only

	/**
	 * temporary variable to store the number of invalid pixels in the current
	 * rendering pass.
	 */
	protected final AtomicInteger numInvalidPixels = new AtomicInteger();

	/**
	 * Flag to indicate that someone is trying to {@link #cancel()} rendering.
	 */
	protected final AtomicBoolean canceled = new AtomicBoolean();
	protected final Object setTargetLock = new Object();

	public VolatileHierarchyProjector(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, B> converter,
			final RandomAccessibleInterval<B> target,
			final int numThreads,
			final ExecutorService executorService) {
		this(sources, converter, target, ArrayImgs.bytes(Intervals.dimensionsAsLongArray(target)), numThreads, executorService);
	}

	public VolatileHierarchyProjector(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, B> converter,
			final RandomAccessibleInterval<B> target,
			final RandomAccessibleInterval<ByteType> mask,
			final int numThreads,
			final ExecutorService executorService) {
		this.converter = converter;
		this.target = target;
		this.sources = new ArrayList<>(sources);
		numInvalidLevels = sources.size();
		this.mask = mask;

		this.iterableTarget = Views.iterable(target);

		final int n = Math.max(2, sources.get(0).numDimensions());
		final long[] min = new long[n];
		final long[] max = new long[n];
		min[0] = target.min(0);
		max[0] = target.max(0);
		min[1] = target.min(1);
		max[1] = target.max(1);
		sourceInterval = new FinalInterval(min, max);

		this.numThreads = numThreads;
		this.executorService = executorService;

		lastFrameRenderNanoTime = -1;
		clearMask();
	}

	@Override
	public void cancel() {
		canceled.set(true);
	}

	@Override
	public long getLastFrameRenderNanoTime() {
		return lastFrameRenderNanoTime;
	}

	public long getLastFrameIoNanoTime() {
		return lastFrameIoNanoTime;
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	/**
	 * Set all pixels in target to 100% transparent zero, and mask to all
	 * Integer.MAX_VALUE.
	 */
	public void clearMask() {

		for (final ByteType val : Views.iterable(mask)) {
			val.set(Byte.MAX_VALUE);
		}
		numInvalidLevels = sources.size();
	}

	/**
	 * Clear target pixels that were never written.
	 */
	private void clearUntouchedTargetPixels() {
		final int[] data = ProjectorUtils.getARGBArrayImgData(target);
		if (data != null) {
			final Cursor<ByteType> maskCursor = Views.iterable(mask).cursor();
			final int size = (int) Intervals.numElements(target);
			for (int i = 0; i < size; ++i) {
				if (maskCursor.next().get() == Byte.MAX_VALUE)
					data[i] = 0;
			}
		} else {
			final Cursor<ByteType> maskCursor = Views.iterable(mask).cursor();
			for (final B t : iterableTarget) {
				if (maskCursor.next().get() == Byte.MAX_VALUE)
					t.setZero();
			}
		}
	}

	@Override
	public boolean map(final boolean clearUntouchedTargetPixels) {
		if (canceled.get())
			return false;

		valid = false;

		final StopWatch stopWatch = StopWatch.createAndStart();
		final IoStatistics iostat = CacheIoTiming.getIoStatistics();
		final long startTimeIo = iostat.getIoNanoTime();
		final long startTimeIoCumulative = iostat.getCumulativeIoNanoTime();

		final int targetHeight = (int) target.dimension(1);
		final int numTasks = numThreads <= 1 ? 1 : Math.min(numThreads * 10, targetHeight);
		final double taskHeight = (double) targetHeight / numTasks;
		final int[] taskStartHeights = new int[numTasks + 1];
		for (int i = 0; i < numTasks; ++i) {
			taskStartHeights[i] = (int) (i * taskHeight);
		}
		taskStartHeights[numTasks] = targetHeight;

		final boolean createExecutor = (executorService == null);
		final ExecutorService ex = createExecutor ? Executors.newFixedThreadPool(numThreads) : executorService;
		try {
			/*
			 * After the for loop, resolutionLevel is the highest (coarsest)
			 * resolution for which all pixels could be filled from valid data. This
			 * means that in the next pass, i.e., map() call, levels up to
			 * resolutionLevel have to be re-rendered.
			 */
			int resolutionLevel;
			for (resolutionLevel = 0; resolutionLevel < numInvalidLevels; ++resolutionLevel) {
				final List<Callable<Void>> tasks = new ArrayList<>(numTasks);
				for (int i = 0; i < numTasks; ++i) {
					tasks.add(createMapTask((byte) resolutionLevel, taskStartHeights[i], taskStartHeights[i + 1]));
				}
				numInvalidPixels.set(0);
				try {
					ex.invokeAll(tasks);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (canceled.get())
					return false;
				if (numInvalidPixels.get() == 0)
					// if this pass was all valid
					numInvalidLevels = resolutionLevel;
			}
		} finally {
			if (createExecutor)
				ex.shutdown();
		}

		if (clearUntouchedTargetPixels && !canceled.get())
			clearUntouchedTargetPixels();

		final long lastFrameTime = stopWatch.nanoTime();
		lastFrameIoNanoTime = iostat.getIoNanoTime() - startTimeIo;
		lastFrameRenderNanoTime = lastFrameTime - (iostat.getCumulativeIoNanoTime() - startTimeIoCumulative) / numThreads;

		valid = numInvalidLevels == 0;

		return !canceled.get();
	}

	/**
	 * @return a {@code Callable} that runs
	 * {@code map(resolutionIndex, startHeight, endHeight)}
	 */
	private Callable<Void> createMapTask(final byte resolutionIndex, final int startHeight, final int endHeight) {
		return Executors.callable(() -> map(resolutionIndex, startHeight, endHeight), null);
	}

	/**
	 * Copy lines from {@code y = startHeight} up to {@code endHeight}
	 * (exclusive) from source {@code resolutionIndex} to target. Check after
	 * each line whether rendering was {@link #cancel() canceled}.
	 * <p>
	 * Only valid source pixels with a current mask value
	 * {@code mask>resolutionIndex} are copied to target, and their mask value
	 * is set to {@code mask=resolutionIndex}. Invalid source pixels are
	 * ignored. Pixels with {@code mask<=resolutionIndex} are ignored, because
	 * they have already been written to target during a previous pass.
	 * <p>
	 *
	 * @param resolutionIndex index of source resolution level
	 * @param startHeight     start of line range to copy (relative to target min coordinate)
	 * @param endHeight       end (exclusive) of line range to copy (relative to target min
	 *                        coordinate)
	 */
	protected void map(final byte resolutionIndex, final int startHeight, final int endHeight) {
		if (canceled.get())
			return;

		final RandomAccess<B> targetRandomAccess = target.randomAccess(target);
		final RandomAccess<A> sourceRandomAccess = sources.get(resolutionIndex).randomAccess(sourceInterval);
		final int width = (int) target.dimension(0);
		final long[] smin = Intervals.minAsLongArray(sourceInterval);
		int myNumInvalidPixels = 0;

		final Cursor<ByteType> maskCursor = Views.iterable(mask).cursor();
		maskCursor.jumpFwd((long) startHeight * width);

		final int targetMin = (int) target.min(1);
		for (int y = startHeight; y < endHeight; ++y) {
			if (canceled.get())
				return;

			smin[1] = y + targetMin;
			sourceRandomAccess.setPosition(smin);
			targetRandomAccess.setPosition(smin);

			for (int x = 0; x < width; ++x) {

				final ByteType maskByte = maskCursor.next();
				if (maskByte.get() > resolutionIndex) {

					//TODO Caleb: This should be temporary, currently necessary to stop the canvas from flickering.
					// Fix underlying issue, so we can remove the lock
					synchronized (setTargetLock) {
						final A a = sourceRandomAccess.get();
						final boolean v = a.isValid();
						if (v) {
							converter.convert(a, targetRandomAccess.get());
							maskByte.set(resolutionIndex);
						} else
							++myNumInvalidPixels;
					}
				}
				sourceRandomAccess.fwd(0);
				targetRandomAccess.fwd(0);
			}
		}

		numInvalidPixels.addAndGet(myNumInvalidPixels);
	}
}
