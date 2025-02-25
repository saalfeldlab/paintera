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
package org.janelia.saalfeldlab.bdv.fx.viewer.project;

import bdv.viewer.render.VolatileProjector;
import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.cache.iotiming.IoStatistics;
import net.imglib2.converter.Converter;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.operators.SetZero;
import net.imglib2.util.Intervals;
import net.imglib2.util.StopWatch;
import org.janelia.saalfeldlab.net.imglib2.view.BundleView;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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

	private static KLogger LOG = KotlinLogging.INSTANCE.logger(() -> null);

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
	protected volatile boolean valid = false;

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
	 * Executor service to be used for rendering
	 */
	protected final TaskExecutor taskExecutor;

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

	public VolatileHierarchyProjector(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, B> converter,
			final RandomAccessibleInterval<B> target,
			final TaskExecutor taskExecutor) {

		this(sources, converter, target, ArrayImgs.bytes(Intervals.dimensionsAsLongArray(target)), taskExecutor);
	}

	public VolatileHierarchyProjector(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, B> converter,
			final RandomAccessibleInterval<B> target,
			final RandomAccessibleInterval<ByteType> mask,
			final TaskExecutor taskExecutor) {

		this.converter = converter;
		this.target = target;
		this.sources = new ArrayList<>(sources);
		numInvalidLevels = sources.size();
		this.mask = mask;

		final int n = Math.max(2, sources.get(0).numDimensions());
		final long[] min = new long[n];
		final long[] max = new long[n];
		min[0] = target.min(0);
		max[0] = target.max(0);
		min[1] = target.min(1);
		max[1] = target.max(1);
		sourceInterval = new FinalInterval(min, max);

		this.taskExecutor = taskExecutor;

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
	 * Set all mask values to Byte.MAX_VALUE. This will ensure all target values are re-render on the next pass.
	 */
	public void clearMask() {

		try {
			LoopBuilder.setImages(mask).multiThreaded(taskExecutor).forEachPixel(val -> val.set(Byte.MAX_VALUE));
		} catch (RejectedExecutionException e) {
			LOG.trace(e, () -> "Clear Mask Rejected");
		} catch (RuntimeException e) {
			if (e.getMessage() != null && e.getMessage().contains("Interrupted"))
				LOG.trace(e, () -> "Clear Mask Interrupted");
			else throw e;
		}
		numInvalidLevels = sources.size();
	}

	/**
	 * Clear target pixels that were never written.
	 */
	private void clearUntouchedTargetPixels() {

		try {
			LoopBuilder.setImages(Views.interval(new BundleView<>(target), target), mask)
					.multiThreaded(taskExecutor)
					.forEachPixel((targetRA, maskVal) -> {
						if (maskVal.get() == Byte.MAX_VALUE) {
							targetRA.get().setZero();
						}
					});
		} catch (RuntimeException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean map(final boolean clearUntouchedTargetPixels) {

		canceled.set(false);
		valid = false;

		final StopWatch stopWatch = StopWatch.createAndStart();
		final IoStatistics iostat = CacheIoTiming.getIoStatistics();
		final long startTimeIo = iostat.getIoNanoTime();
		final long startTimeIoCumulative = iostat.getCumulativeIoNanoTime();

		int resolutionLevel;
		/*
		 * After the for loop, resolutionLevel is the highest (coarsest)
		 * resolution for which all pixels could be filled from valid data. This
		 * means that in the next pass, i.e., map() call, levels up to
		 * resolutionLevel have to be re-rendered.
		 */
		for (resolutionLevel = 0; resolutionLevel < numInvalidLevels && !valid; ++resolutionLevel) {
			final List<Callable<Void>> tasks = new ArrayList<>();
			valid = true;
			numInvalidPixels.set(0);
			final byte idx = (byte) resolutionLevel;
			tasks.add(Executors.callable(() -> map(idx, -1, -1), null));

			try {
				taskExecutor.getExecutorService().invokeAll(tasks);
			} catch (final InterruptedException e) {
				canceled.set(true);
			}
			if (canceled.get())
				return false;
		}

		if (clearUntouchedTargetPixels && !canceled.get())
			clearUntouchedTargetPixels();

		final long lastFrameTime = stopWatch.nanoTime();
		lastFrameIoNanoTime = iostat.getIoNanoTime() - startTimeIo;
		lastFrameRenderNanoTime = lastFrameTime - (iostat.getCumulativeIoNanoTime() - startTimeIoCumulative) / taskExecutor.getParallelism();

		if (valid)
			numInvalidLevels = resolutionLevel - 1;
		valid = numInvalidLevels == 0;

		return !canceled.get();
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

		final AtomicInteger myNumInvalidPixels = new AtomicInteger();

		LoopBuilder.setImages(
						Views.interval(new BundleView<>(target), sourceInterval),
						Views.interval(new BundleView<>(sources.get(resolutionIndex)), sourceInterval),
						Views.interval(mask, sourceInterval)
				).multiThreaded(taskExecutor)
				.forEachChunk(chunk -> {
					if (canceled.get()) {
						Thread.currentThread().interrupt();
						return null;
					}
					chunk.forEachPixel((targetVal, sourceVal, maskVal) -> {
						if (maskVal.get() > resolutionIndex) {
							final A a = sourceVal.get();
							final boolean v = a.isValid();
							if (v) {
								converter.convert(a, targetVal.get());
								maskVal.set(resolutionIndex);
							} else
								myNumInvalidPixels.incrementAndGet();
						}
					});
					return null;
				});
		numInvalidPixels.addAndGet(myNumInvalidPixels.get());
		if (myNumInvalidPixels.get() != 0)
			valid = false;
	}
}
