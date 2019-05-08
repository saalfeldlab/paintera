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
package bdv.fx.viewer.render;

import bdv.cache.CacheControl;
import bdv.fx.viewer.project.SimpleInterruptibleProjectorPreMultiply;
import bdv.fx.viewer.project.VolatileHierarchyProjector;
import bdv.fx.viewer.project.VolatileHierarchyProjectorPreMultiply;
import bdv.util.MipmapTransforms;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.DefaultMipmapOrdering;
import bdv.viewer.render.EmptyProjector;
import bdv.viewer.render.MipmapOrdering;
import bdv.viewer.render.MipmapOrdering.Level;
import bdv.viewer.render.MipmapOrdering.MipmapHints;
import bdv.viewer.render.Prefetcher;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.bdv.img.cache.VolatileCachedCellImg;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 *
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public class MultiResolutionRendererGeneric<T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public interface ImageGenerator<T>
	{

		T create(int width, int height);

		T create(int width, int height, T other);

	}

	/**
	 * Receiver for the data store that we render.
	 */
	private final TransformAwareRenderTargetGeneric<T> display;

	/**
	 * Thread that triggers repainting of the display. Requests for repainting are send there.
	 */
	private final PainterThread painterThread;

	/**
	 * Currently active projector, used to re-paint the display. It maps the source data to {@link #screenImages}.
	 */
	private VolatileProjector projector;

	/**
	 * The index of the screen scale of the {@link #projector current projector}.
	 */
	private int currentScreenScaleIndex;

	/**
	 * Whether double buffering is used.
	 */
	private final boolean doubleBuffered;

	/**
	 * Double-buffer index of next {@link #screenImages image} to render.
	 */
	private final ArrayDeque<Integer> renderIdQueue;

	/**
	 * Maps from data store to double-buffer index. Needed for double-buffering.
	 */
	private final HashMap<T, Integer> bufferedImageToRenderId;

	/**
	 * Used to render an individual source. One image per screen resolution and visible source. First index is screen
	 * scale, second index is index in list of visible sources.
	 */
	ArrayImg<ARGBType, IntArray>[][] renderImages;

	/**
	 * Storage for mask images of {@link VolatileHierarchyProjector}. One array per visible source. (First) index is
	 * index in list of visible sources.
	 */
	private byte[][] renderMaskArrays;

	/**
	 * Used to render the image for display. Three images per screen resolution if double buffering is enabled. First
	 * index is screen scale, second index is double-buffer.
	 */
	private List<List<T>> screenImages;

	/**
	 * data store wrapping the data in the {@link #screenImages}. First index is screen scale, second index is
	 * double-buffer.
	 */
	private List<List<T>> bufferedImages;

	/**
	 * Scale factors from the {@link #display viewer canvas} to the {@link #screenImages}.
	 * <p>
	 * A scale factor of 1 means 1 pixel in the screen image is displayed as 1 pixel on the canvas, a scale factor of
	 * 0.5 means 1 pixel in the screen image is displayed as 2 pixel on the canvas, etc.
	 */
	private double[] screenScales;

	/**
	 * The scale transformation from viewer to {@link #screenImages screen image}. Each transformation corresponds
	 * to a {@link #screenScales screen scale}.
	 */
	private AffineTransform3D[] screenScaleTransforms;

	/**
	 * Pending repaint requests for each {@link #screenScales screen scale}.
	 */
	private Interval[] pendingRepaintRequests;

	/**
	 * The last rendered interval in screen space.
	 */
	private Interval lastRenderedScreenInterval;

	/**
	 * The last rendered interval in 'render target' space (which is essentially a {@link #lastRenderedScreenInterval} scaled down with respect to the last rendered screen scale).
	 */
	private RealInterval lastRenderTargetRealInterval;

	/**
	 * If the rendering time (in nanoseconds) for the (currently) highest scaled screen image is above this threshold,
	 * increase the {@link #maxScreenScaleIndex index} of the highest screen scale to use. Similarly, if the rendering
	 * time for the (currently) second-highest scaled screen image is below this threshold, decrease the {@link
	 * #maxScreenScaleIndex index} of the highest screen scale to use.
	 */
	private final long targetRenderNanos;

	/**
	 * The index of the (coarsest) screen scale with which to start rendering. Once this level is painted, rendering
	 * proceeds to lower screen scales until index 0 (full resolution) has been reached. While rendering, the
	 * maxScreenScaleIndex is adapted such that it is the highest index for which rendering in {@link
	 * #targetRenderNanos} nanoseconds is still possible.
	 */
	private int maxScreenScaleIndex;

	/**
	 * The index of the screen scale which should be rendered next.
	 */
	private int requestedScreenScaleIndex;

	/**
	 * Whether the current rendering operation may be cancelled (to start a new one). Rendering may be cancelled unless
	 * we are rendering at coarsest screen scale and coarsest mipmap level.
	 */
	private volatile boolean renderingMayBeCancelled;

	/**
	 * How many threads to use for rendering.
	 */
	private final int numRenderingThreads;

	/**
	 * {@link ExecutorService} used for rendering.
	 */
	private final ExecutorService renderingExecutorService;

	/**
	 * TODO
	 */
	private final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;

	/**
	 * Controls IO budgeting and fetcher queue.
	 */
	private final CacheControl cacheControl;

	/**
	 * Whether volatile versions of sources should be used if available.
	 */
	private final boolean useVolatileIfAvailable;

	/**
	 * Whether a repaint was {@link #requestRepaint(Interval) requested}. This will cause {@link
	 * CacheControl#prepareNextFrame()}.
	 */
	private boolean newFrameRequest;

	/**
	 * The timepoint for which last a projector was {@link #createProjector created}.
	 */
	private int previousTimepoint;

	private long[] iobudget = new long[] {100l * 1000000l, 10l * 1000000l};

	private boolean prefetchCells = true;

	private final Function<T, ArrayImg<ARGBType, ? extends IntAccess>> wrapAsArrayImg;

	private final ToIntFunction<T> width;

	private final ToIntFunction<T> height;

	private final ImageGenerator<T> makeImage;

	private final AffineTransform3D currentProjectorTransform = new AffineTransform3D();

	/**
	 * @param display
	 * 		The canvas that will display the images we render.
	 * @param painterThread
	 * 		Thread that triggers repainting of the display. Requests for repainting are send there.
	 * @param screenScales
	 * 		Scale factors from the viewer canvas to screen images of different resolutions. A scale factor of 1 means 1
	 * 		pixel in the screen image is displayed as 1 pixel on the canvas, a scale factor of 0.5 means 1 pixel in the
	 * 		screen image is displayed as 2 pixel on the canvas, etc.
	 * @param targetRenderNanos
	 * 		Target rendering time in nanoseconds. The rendering time for the coarsest rendered scale should be below
	 * 		this
	 * 		threshold.
	 * @param doubleBuffered
	 * 		Whether to use double buffered rendering.
	 * @param numRenderingThreads
	 * 		How many threads to use for rendering.
	 * @param renderingExecutorService
	 * 		if non-null, this is used for rendering. Note, that it is still important to supply the numRenderingThreads
	 * 		parameter, because that is used to determine into how many sub-tasks rendering is split.
	 * @param useVolatileIfAvailable
	 * 		whether volatile versions of sources should be used if available.
	 * @param accumulateProjectorFactory
	 * 		can be used to customize how sources are combined.
	 * @param cacheControl
	 * 		the cache controls IO budgeting and fetcher queue.
	 */
	MultiResolutionRendererGeneric(
			final TransformAwareRenderTargetGeneric<T> display,
			final PainterThread painterThread,
			final double[] screenScales,
			final long targetRenderNanos,
			final boolean doubleBuffered,
			final int numRenderingThreads,
			final ExecutorService renderingExecutorService,
			final boolean useVolatileIfAvailable,
			final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory,
			final CacheControl cacheControl,
			final Function<T, ArrayImg<ARGBType, ? extends IntAccess>> wrapAsArrayImg,
			final ImageGenerator<T> makeImage,
			final ToIntFunction<T> width,
			final ToIntFunction<T> height)
	{
		this.display = display;
		this.painterThread = painterThread;
		projector = null;
		currentScreenScaleIndex = -1;
		this.screenScales = screenScales.clone();
		this.doubleBuffered = doubleBuffered;
		renderIdQueue = new ArrayDeque<>();
		bufferedImageToRenderId = new HashMap<>();
		createVariables();

		this.makeImage = makeImage;

		this.width = width;

		this.height = height;

		this.wrapAsArrayImg = wrapAsArrayImg;

		this.targetRenderNanos = targetRenderNanos;

		renderingMayBeCancelled = true;
		this.numRenderingThreads = numRenderingThreads;
		this.renderingExecutorService = renderingExecutorService;
		this.useVolatileIfAvailable = useVolatileIfAvailable;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		newFrameRequest = false;
		previousTimepoint = -1;
	}

	/**
	 * Check whether the size of the display component was changed and recreate {@link #screenImages} and {@link
	 * #screenScaleTransforms} accordingly.
	 *
	 * @return whether the size was changed.
	 */
	private synchronized boolean checkResize()
	{
		final int componentW = display.getWidth();
		final int componentH = display.getHeight();
		if (screenImages.get(0).get(0) == null
				|| width .applyAsInt(screenImages.get(0).get(0)) != (int) Math.ceil(componentW * screenScales[0])
				|| height.applyAsInt(screenImages.get(0).get(0)) != (int) Math.ceil(componentH * screenScales[0]))
		{
			renderIdQueue.clear();
			renderIdQueue.addAll(Arrays.asList(0, 1, 2));
			bufferedImageToRenderId.clear();
			for (int i = 0; i < screenScales.length; ++i)
			{
				final double screenToViewerScale = screenScales[i];
				final int    w                   = (int) Math.ceil(screenToViewerScale * componentW);
				final int    h                   = (int) Math.ceil(screenToViewerScale * componentH);
				if (doubleBuffered)
				{
					for (int b = 0; b < 3; ++b)
					{
						// reuse storage arrays of level 0 (highest resolution)
						screenImages.get(i).set(b, i == 0
						                     ? makeImage.create(w, h)
						                     : makeImage.create(w, h, screenImages.get(0).get(b)));
						final T bi = screenImages.get(i).get(b);
						// getBufferedImage.apply( screenImages[ i ][ b ] );
						bufferedImages.get(i).set(b, bi);
						bufferedImageToRenderId.put(bi, b);
					}
				}
				else
				{
					screenImages.get(i).set(0, makeImage.create(w, h));
					bufferedImages.get(i).set(0, screenImages.get(i).get(0));
					// getBufferedImage.apply( screenImages[ i ][ 0 ] );
				}
				final AffineTransform3D scale  = new AffineTransform3D();
				final double            xScale = screenToViewerScale;
				final double            yScale = screenToViewerScale;
				scale.set(xScale, 0, 0);
				scale.set(yScale, 1, 1);
				scale.set(0.5 * (xScale - 1), 0, 3);
				scale.set(0.5 * (yScale - 1), 1, 3);
				screenScaleTransforms[i] = scale;
			}

			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private boolean checkRenewRenderImages(final int numVisibleSources)
	{
		final int n = numVisibleSources > 1 ? numVisibleSources : 0;
		if (n != renderImages[0].length ||
				n != 0 &&
						(renderImages[0][0].dimension(0) != width.applyAsInt(screenImages.get(0).get(0)) ||
								renderImages[0][0].dimension(1) != height.applyAsInt(screenImages.get(0).get(0))))
		{
			renderImages = new ArrayImg[screenScales.length][n];
			for (int i = 0; i < screenScales.length; ++i)
			{
				final int w = width.applyAsInt(screenImages.get(i).get(0));
				final int h = height.applyAsInt(screenImages.get(i).get(0));
				for (int j = 0; j < n; ++j)
					renderImages[i][j] = i == 0
					                     ? ArrayImgs.argbs(w, h)
					                     : ArrayImgs.argbs(renderImages[0][j].update(null), w, h);
			}
			return true;
		}
		return false;
	}

	private boolean checkRenewMaskArrays(final int numVisibleSources)
	{
		final int size = width.applyAsInt(screenImages.get(0).get(0)) * height.applyAsInt(screenImages.get(0).get(0));
		if (numVisibleSources != renderMaskArrays.length ||
				numVisibleSources != 0 && renderMaskArrays[0].length < size)
		{
			renderMaskArrays = new byte[numVisibleSources][];
			for (int j = 0; j < numVisibleSources; ++j)
				renderMaskArrays[j] = new byte[size];
			return true;
		}
		return false;
	}

	private int[] getImageSize(final T image)
	{
		return new int[] {this.width.applyAsInt(image), this.height.applyAsInt(image)};
	}

	private static Interval padInterval(final Interval interval, final int[] padding, final int[] imageSize)
	{
		final long[] paddedIntervalMin = new long[2], paddedIntervalMax = new long[2];
		Arrays.setAll(paddedIntervalMin, d -> Math.max(interval.min(d) - padding[d], 0));
		Arrays.setAll(paddedIntervalMax, d -> Math.min(interval.max(d) + padding[d], imageSize[d] - 1));
		return new FinalInterval(paddedIntervalMin, paddedIntervalMax);
	}

	/**
	 * Render image at the {@link #requestedScreenScaleIndex requested screen scale}.
	 *
	 * @return index of the rendered screen scale, or -1 if the rendering was not successful
	 */
	public int paint(
			final List<SourceAndConverter<?>> sources,
			final Function<Source<?>, AxisOrder> axisOrders,
			final int timepoint,
			final AffineTransform3D viewerTransform,
			final Function<Source<?>, Interpolation> interpolationForSource,
			final Object synchronizationLock)
	{
		if (display.getWidth() <= 0 || display.getHeight() <= 0)
			return -1;

		final boolean resized = checkResize();

		// the BufferedImage that is rendered to (to paint to the canvas)
		final T bufferedImage;

		// the projector that paints to the screenImage.
		final VolatileProjector p;

		final boolean clearQueue;

		final boolean createProjector;

		final Interval repaintScreenInterval;

		synchronized (this)
		{
			// FIXME: there is a race condition that sometimes may cause an ArrayIndexOutOfBounds exception:
			// Screen scales are first initialized with the default setting (see RenderUnit),
			// then the project metadata is loaded, and the screen scales are changed to the saved configuration.
			// If the project screen scales are [1.0], sometimes the renderer receives a request to re-render the screen at screen scale 1, which results in the exception.
			if (requestedScreenScaleIndex >= pendingRepaintRequests.length)
				return -1;

			repaintScreenInterval = pendingRepaintRequests[requestedScreenScaleIndex];
			pendingRepaintRequests[requestedScreenScaleIndex] = null;

			if (repaintScreenInterval == null)
				return -1;

			final boolean sameAsLastRenderedInterval = lastRenderedScreenInterval != null && Intervals.equals(repaintScreenInterval, lastRenderedScreenInterval);

			// Rendering may be cancelled unless we are rendering at coarsest screen scale and coarsest mipmap level.
			renderingMayBeCancelled = requestedScreenScaleIndex < maxScreenScaleIndex;

			clearQueue = newFrameRequest;
			if (clearQueue)
				cacheControl.prepareNextFrame();
			createProjector = newFrameRequest || resized || requestedScreenScaleIndex != currentScreenScaleIndex || !sameAsLastRenderedInterval;
			newFrameRequest = false;

			final List<SourceAndConverter<?>> sacs = sources;

			if (createProjector)
			{
				final int renderId = renderIdQueue.peek();
				currentScreenScaleIndex = requestedScreenScaleIndex;
				bufferedImage = bufferedImages.get(currentScreenScaleIndex).get(renderId);
				final T renderTarget = screenImages.get(currentScreenScaleIndex).get(renderId);
				synchronized (Optional.ofNullable(synchronizationLock).orElse(this))
				{
					final int numSources = sacs.size();
					checkRenewRenderImages(numSources);
					checkRenewMaskArrays(numSources);

					// find the scaling ratio between render target pixels and screen pixels
					final double[] renderTargetToScreenPixelRatio = new double[2];
					Arrays.setAll(renderTargetToScreenPixelRatio, d -> screenScaleTransforms[currentScreenScaleIndex].get(d, d));

					// scale the screen repaint request interval into render target coordinates
					final double[] renderTargetRealIntervalMin = new double[2], renderTargetRealIntervalMax = new double[2];
					Arrays.setAll(renderTargetRealIntervalMin, d -> repaintScreenInterval.min(d) * renderTargetToScreenPixelRatio[d]);
					Arrays.setAll(renderTargetRealIntervalMax, d -> repaintScreenInterval.max(d) * renderTargetToScreenPixelRatio[d]);
					final RealInterval renderTargetRealInterval = new FinalRealInterval(renderTargetRealIntervalMin, renderTargetRealIntervalMax);

					// apply 1px padding on each side of the render target repaint interval to avoid interpolation artifacts
					final Interval renderTargetPaddedInterval = padInterval(
						Intervals.smallestContainingInterval(renderTargetRealInterval),
						new int[] {1, 1},
						getImageSize(renderTarget)
					);

					viewerTransform.translate(
						-renderTargetPaddedInterval.min(0) / renderTargetToScreenPixelRatio[0],
						-renderTargetPaddedInterval.min(1) / renderTargetToScreenPixelRatio[1],
						0
					);

					final RandomAccessibleInterval<ARGBType> renderTargetRoi = Views.interval(wrapAsArrayImg.apply(renderTarget), renderTargetPaddedInterval);

					p = createProjector(
						sacs,
						axisOrders,
						timepoint,
						viewerTransform,
						currentScreenScaleIndex,
						renderTargetRoi,
						interpolationForSource
					);

					lastRenderedScreenInterval = repaintScreenInterval;
					lastRenderTargetRealInterval = renderTargetRealInterval;
				}
				projector = p;
			}
			else
			{
				bufferedImage = null;
				p = projector;
			}

			requestedScreenScaleIndex = 0;
		}

		// try rendering
		final boolean success = p.map(createProjector);
//		final long rendertime = p.getLastFrameRenderNanoTime();

		synchronized (this)
		{
			// if rendering was not cancelled...
			if (success)
			{
				if (createProjector)
				{
					final T bi = display.setBufferedImageAndTransform(bufferedImage, currentProjectorTransform);
					if (doubleBuffered)
					{
						renderIdQueue.pop();
						final Integer id = bufferedImageToRenderId.get(bi);
						if (id != null)
							renderIdQueue.add(id);
					}

					/**
					 * TODO: design a better algorithm for adjusting maxScreenScaleIndex or remove and always use all screen scales.
					 *
					 * The current heuristic does not work well in the following cases:
					 *
					 * 1) With vastly different screen scale values such as [1.0, 0.1], it will switch between the two scales every frame.
					 * At screen scale 0.1 rendering is fast, and after a frame is rendered, it switches maxScreenScaleIndex to 0.
					 * However, at screen scale 1.0 rendering is slower than targetRenderNanos, so when the next frame is rendered, it switches maxScreenScaleIndex back to 1.
					 * This causes annoying delays because when maxScreenScaleIndex is switched to 0, renderingMayBeCancelled is in turn set to false, and the algorithm has to wait
					 * until the current high-res frame is fully rendered.
					 *
					 * 2) When the user starts painting, it re-renders only the affected interval which is usually very small compared to the size of the screen.
					 * Usually rendering is very fast in this case, and maxScreenScaleIndex is quickly changed to 0.
					 * When the user finishes painting and starts navigating again, there may be a delay in rendering the first few frames because
					 * it starts from the highest available resolution and then gradually decreases the resolution until the rendertime is within the targetRenderNanos threshold.
					 */
//					if (currentScreenScaleIndex == maxScreenScaleIndex)
//					{
//						if (rendertime > targetRenderNanos && maxScreenScaleIndex < screenScales.length - 1)
//							maxScreenScaleIndex++;
//						else if (rendertime < targetRenderNanos / 3 && maxScreenScaleIndex > 0)
//							maxScreenScaleIndex--;
//					}
//					else if (currentScreenScaleIndex == maxScreenScaleIndex - 1)
//						if (rendertime < targetRenderNanos && maxScreenScaleIndex > 0)
//							maxScreenScaleIndex--;
				}

				if (currentScreenScaleIndex > 0)
					requestRepaint(lastRenderedScreenInterval, currentScreenScaleIndex - 1);
				else if (!p.isValid())
				{
					try
					{
						Thread.sleep(1);
					} catch (final InterruptedException e)
					{
						// restore interrupted state
						Thread.currentThread().interrupt();
					}
					requestRepaint(lastRenderedScreenInterval, currentScreenScaleIndex);
				}
			}
			else
			{
				// Add the requested interval back into the queue if it was not rendered
				if (pendingRepaintRequests[currentScreenScaleIndex] == null)
					pendingRepaintRequests[currentScreenScaleIndex] = repaintScreenInterval;
				else
					pendingRepaintRequests[currentScreenScaleIndex] = Intervals.union(pendingRepaintRequests[currentScreenScaleIndex], repaintScreenInterval);
			}

			return success ? currentScreenScaleIndex : -1;
		}
	}

	public synchronized Interval getLastRenderedScreenInterval()
	{
		return lastRenderedScreenInterval;
	}

	public synchronized RealInterval getLastRenderTargetRealInterval()
	{
		return lastRenderTargetRealInterval;
	}

	/**
	 * Request a repaint of the given display interval from the painter thread, with maximum screen scale index and mipmap level.
	 */
	public synchronized void requestRepaint(final Interval interval)
	{
		newFrameRequest = true;
		requestRepaint(interval, maxScreenScaleIndex);
	}

	/**
	 * Request a repaint of the given display interval from the painter thread. The painter thread will trigger a {@link #paint} as
	 * soon as possible (that is, immediately or after the currently running {@link #paint} has completed).
	 */
	public synchronized void requestRepaint(final Interval interval, final int screenScaleIndex)
	{
		if (Intervals.isEmpty(interval))
			return;

		if (renderingMayBeCancelled && projector != null)
			projector.cancel();

		if (screenScaleIndex > requestedScreenScaleIndex)
			requestedScreenScaleIndex = screenScaleIndex;

		// FIXME: there is a race condition that sometimes may cause an ArrayIndexOutOfBounds exception:
		// Screen scales are first initialized with the default setting (see RenderUnit),
		// then the project metadata is loaded, and the screen scales are changed to the saved configuration.
		// If the project screen scales are [1.0], sometimes the renderer receives a request to re-render the screen at screen scale 1, which results in the exception.
		if (requestedScreenScaleIndex >= pendingRepaintRequests.length)
			return;

		if (pendingRepaintRequests[requestedScreenScaleIndex] == null)
			pendingRepaintRequests[requestedScreenScaleIndex] = interval;
		else
			pendingRepaintRequests[requestedScreenScaleIndex] = Intervals.union(pendingRepaintRequests[requestedScreenScaleIndex], interval);

		painterThread.requestRepaint();
	}

	private VolatileProjector createProjector(
			final List<SourceAndConverter<?>> sacs,
			final Function<Source<?>, AxisOrder> axisOrders,
			final int timepoint,
			final AffineTransform3D viewerTransform,
			final int screenScaleIndex,
			final RandomAccessibleInterval<ARGBType> screenImage,
			final Function<Source<?>, Interpolation> interpolationForSource)
	{
		/*
		 * This shouldn't be necessary, with
		 * CacheHints.LoadingStrategy==VOLATILE
		 */
		//		CacheIoTiming.getIoTimeBudget().clear(); // clear time budget such that prefetching doesn't wait for
		// loading blocks.
		VolatileProjector projector;
		if (sacs.isEmpty())
			projector = new EmptyProjector<>(screenImage);
		else if (sacs.size() == 1)
		{
			LOG.debug("Got only one source, creating pre-multiplying single source projector");
			final SourceAndConverter<?> sac           = sacs.get(0);
			final Interpolation         interpolation = interpolationForSource.apply(sac.getSpimSource());
			final int[] renderTargetSize = getImageSize(this.screenImages.get(currentScreenScaleIndex).get(0));
			projector = createSingleSourceProjector(
					sac,
					axisOrders.apply(sac.getSpimSource()),
					timepoint,
					viewerTransform,
					currentScreenScaleIndex,
					Views.zeroMin(screenImage),
					Views.offsetInterval(ArrayImgs.bytes(renderMaskArrays[0], renderTargetSize[0], renderTargetSize[1]), screenImage),
					interpolation,
					true
			                                       );
		}
		else
		{
			LOG.debug("Got {} sources, creating {} non-pre-multiplying single source projectors", sacs.size());
			final ArrayList<VolatileProjector> sourceProjectors = new ArrayList<>();
			final ArrayList<RandomAccessibleInterval<ARGBType>> sourceImages = new ArrayList<>();
			final ArrayList<Source<?>> sources = new ArrayList<>();
			int j = 0;
			for (final SourceAndConverter<?> sac : sacs)
			{
				final RandomAccessibleInterval<ARGBType> renderImage = Views.interval(renderImages[currentScreenScaleIndex][j], screenImage);
				final byte[] maskArray = renderMaskArrays[j];
				final AxisOrder axisOrder = axisOrders.apply(sac.getSpimSource());
				++j;
				final Interpolation interpolation = interpolationForSource.apply(sac.getSpimSource());
				final int[] renderTargetSize = getImageSize(this.screenImages.get(currentScreenScaleIndex).get(0));
				final VolatileProjector p = createSingleSourceProjector(
						sac,
						axisOrder,
						timepoint,
						viewerTransform,
						currentScreenScaleIndex,
						Views.zeroMin(renderImage),
						Views.offsetInterval(ArrayImgs.bytes(maskArray, renderTargetSize[0], renderTargetSize[1]), screenImage),
						interpolation,
						false
				                                                       );
				sourceProjectors.add(p);
				sources.add(sac.getSpimSource());
				sourceImages.add(renderImage);
			}
			projector = accumulateProjectorFactory.createAccumulateProjector(
					sourceProjectors,
					sources,
					sourceImages,
					screenImage,
					numRenderingThreads,
					renderingExecutorService
			                                                                );
		}
		previousTimepoint = timepoint;
		currentProjectorTransform.set(viewerTransform);
		CacheIoTiming.getIoTimeBudget().reset(iobudget);
		return projector;
	}

	private static class SimpleVolatileProjector<A> extends SimpleInterruptibleProjectorPreMultiply<A>
			implements VolatileProjector
	{
		private boolean valid = false;

		SimpleVolatileProjector(
				final RandomAccessible<A> source,
				final Converter<? super A, ARGBType> converter,
				final RandomAccessibleInterval<ARGBType> target,
				final int numThreads,
				final ExecutorService executorService)
		{
			super(source, converter, target, numThreads, executorService);
		}

		@Override
		public boolean map(final boolean clearUntouchedTargetPixels)
		{
			final boolean success = super.map();
			valid |= success;
			return success;
		}

		@Override
		public boolean isValid()
		{
			return valid;
		}
	}

	private <U> VolatileProjector createSingleSourceProjector(
			final SourceAndConverter<U> source,
			final AxisOrder axisOrder,
			final int timepoint,
			final AffineTransform3D viewerTransform,
			final int screenScaleIndex,
			final RandomAccessibleInterval<ARGBType> screenImage,
			final RandomAccessibleInterval<ByteType> mask,
			final Interpolation interpolation,
			final boolean preMultiply)
	{
		if (useVolatileIfAvailable)
			if (source.asVolatile() != null)
			{
				LOG.debug(
						"Volatile is available for source={} (name={})",
						source.getSpimSource(),
						source.getSpimSource().getName()
				         );
				return createSingleSourceVolatileProjector(
						source.asVolatile(),
						axisOrder,
						timepoint,
						screenScaleIndex,
						viewerTransform,
						screenImage,
						mask,
						interpolation,
						preMultiply
				                                          );
			}
			else if (source.getSpimSource().getType() instanceof Volatile)
			{
				LOG.debug(
						"Casting to volatile source:{} (name={})",
						source.getSpimSource(),
						source.getSpimSource().getName()
				         );
				@SuppressWarnings("unchecked") final SourceAndConverter<? extends Volatile<?>> vsource =
						(SourceAndConverter<? extends Volatile<?>>) source;
				return createSingleSourceVolatileProjector(
						vsource,
						axisOrder,
						timepoint,
						screenScaleIndex,
						viewerTransform,
						screenImage,
						mask,
						interpolation,
						preMultiply
				                                          );
			}

		final AffineTransform3D screenScaleTransform = screenScaleTransforms[currentScreenScaleIndex];
		final AffineTransform3D screenTransform      = viewerTransform.copy();
		screenTransform.preConcatenate(screenScaleTransform);
		final int bestLevel = MipmapTransforms.getBestMipMapLevel(screenTransform, source.getSpimSource(), timepoint);
		LOG.debug("Using bestLevel={}", bestLevel);
		return new SimpleVolatileProjector<>(
				getTransformedSource(
						source.getSpimSource(),
						axisOrder,
						timepoint,
						viewerTransform,
						screenScaleTransform,
						bestLevel,
						null,
						interpolation
				                    ),
				source.getConverter(), screenImage, numRenderingThreads, renderingExecutorService
		);
	}

	private <V extends Volatile<?>> VolatileProjector createSingleSourceVolatileProjector(
			final SourceAndConverter<V> source,
			final AxisOrder axisOrder,
			final int t,
			final int screenScaleIndex,
			final AffineTransform3D viewerTransform,
			final RandomAccessibleInterval<ARGBType> screenImage,
			final RandomAccessibleInterval<ByteType> mask,
			final Interpolation interpolation,
			final boolean preMultiply)
	{
		LOG.debug(
				"Creating single source volatile projector for source={} (name={})",
				source.getSpimSource(),
				source.getSpimSource().getName()
		         );
		final AffineTransform3D              screenScaleTransform = screenScaleTransforms[currentScreenScaleIndex];
		final ArrayList<RandomAccessible<V>> renderList           = new ArrayList<>();
		final Source<V>                      spimSource           = source.getSpimSource();
		LOG.debug("Creating single source volatile projector for type={}", spimSource.getType());

		final MipmapOrdering ordering = MipmapOrdering.class.isInstance(spimSource)
		                                ? (MipmapOrdering) spimSource
		                                : new DefaultMipmapOrdering(spimSource);

		final AffineTransform3D screenTransform = viewerTransform.copy();
		screenTransform.preConcatenate(screenScaleTransform);
		final MipmapHints hints  = ordering.getMipmapHints(screenTransform, t, previousTimepoint);
		final List<Level> levels = hints.getLevels();

		if (prefetchCells)
		{
			Collections.sort(levels, MipmapOrdering.prefetchOrderComparator);
			for (final Level l : levels)
			{
				final CacheHints cacheHints = l.getPrefetchCacheHints();
				if (cacheHints == null || cacheHints.getLoadingStrategy() != LoadingStrategy.DONTLOAD)
					prefetch(
							spimSource,
							t,
							viewerTransform,
							screenScaleTransform,
							l.getMipmapLevel(),
							cacheHints,
							screenImage,
							interpolation
					        );
			}
		}

		Collections.sort(levels, MipmapOrdering.renderOrderComparator);
		for (final Level l : levels)
			renderList.add(getTransformedSource(
					spimSource,
					axisOrder,
					t,
					viewerTransform,
					screenScaleTransform,
					l.getMipmapLevel(),
					l.getRenderCacheHints(),
					interpolation
			                                   ));

		if (hints.renewHintsAfterPaintingOnce())
			newFrameRequest = true;

		LOG.debug("Creating projector. Pre-multiply? {}", preMultiply);

		if (preMultiply)
			return new VolatileHierarchyProjectorPreMultiply<>(
					renderList,
					source.getConverter(),
					screenImage,
					mask,
					numRenderingThreads,
					renderingExecutorService
			);
		else
			return new VolatileHierarchyProjector<>(
					renderList,
					source.getConverter(),
					screenImage,
					mask,
					numRenderingThreads,
					renderingExecutorService
			);
	}

	private static <T> RandomAccessible<T> getTransformedSource(
			final Source<T> source,
			final AxisOrder axisOrder,
			final int timepoint,
			final AffineTransform3D viewerTransform,
			final AffineTransform3D screenScaleTransform,
			final int mipmapIndex,
			final CacheHints cacheHints,
			final Interpolation interpolation)
	{

		final RandomAccessibleInterval<T> img = source.getSource(timepoint, mipmapIndex);
		if (VolatileCachedCellImg.class.isInstance(img))
			((VolatileCachedCellImg<?, ?>) img).setCacheHints(cacheHints);

		final RealRandomAccessible<T> ipimg = source.getInterpolatedSource(timepoint, mipmapIndex, interpolation);

		final AffineTransform3D sourceToScreen  = viewerTransform.copy();
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform(timepoint, mipmapIndex, sourceTransform);
		sourceToScreen.concatenate(axisOrder.asAffineTransform().inverse());
		sourceToScreen.concatenate(sourceTransform);
		sourceToScreen.preConcatenate(screenScaleTransform);

		LOG.debug(
				"Getting transformed source {} (name={}) for t={} level={} transform={} screen-scale={} hints={} " +
						"interpolation={}",
				source,
				source.getName(),
				timepoint,
				mipmapIndex,
				sourceToScreen,
				screenScaleTransform,
				cacheHints,
				interpolation
		         );

		return RealViews.affine(ipimg, sourceToScreen);
	}

	private static <T> void prefetch(
			final Source<T> source,
			final int timepoint,
			final AffineTransform3D viewerTransform,
			final AffineTransform3D screenScaleTransform,
			final int mipmapIndex,
			final CacheHints prefetchCacheHints,
			final Dimensions screenInterval,
			final Interpolation interpolation)
	{
		final RandomAccessibleInterval<T> img = source.getSource(timepoint, mipmapIndex);
		if (VolatileCachedCellImg.class.isInstance(img))
		{
			final VolatileCachedCellImg<?, ?> cellImg = (VolatileCachedCellImg<?, ?>) img;

			CacheHints hints = prefetchCacheHints;
			if (hints == null)
			{
				final CacheHints d = cellImg.getDefaultCacheHints();
				hints = new CacheHints(LoadingStrategy.VOLATILE, d.getQueuePriority(), false);
			}
			cellImg.setCacheHints(hints);
			final int[] cellDimensions = new int[3];
			cellImg.getCellGrid().cellDimensions(cellDimensions);
			final long[] dimensions = new long[3];
			cellImg.dimensions(dimensions);
			final RandomAccess<?> cellsRandomAccess = cellImg.getCells().randomAccess();

			final AffineTransform3D sourceToScreen  = viewerTransform.copy();
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			source.getSourceTransform(timepoint, mipmapIndex, sourceTransform);
			sourceToScreen.concatenate(sourceTransform);
			sourceToScreen.preConcatenate(screenScaleTransform);

			Prefetcher.fetchCells(
					sourceToScreen,
					cellDimensions,
					dimensions,
					screenInterval,
					interpolation,
					cellsRandomAccess
			                     );
		}
	}

	public synchronized void setScreenScales(final double[] screenScales)
	{
		this.screenScales = screenScales.clone();
		createVariables();
	}

	/**
	 * Set {@code screenScaleTransform} to a screen scale transform at a given {@code screenScaleIndex}.
	 *
	 * @param screenScaleIndex
	 * @param screenScaleTransform
	 */
	public synchronized void getScreenScaleTransform(final int screenScaleIndex, final AffineTransform3D screenScaleTransform)
	{
		if (screenScaleIndex < this.screenScaleTransforms.length && this.screenScaleTransforms[screenScaleIndex] != null)
			screenScaleTransform.set(this.screenScaleTransforms[screenScaleIndex]);
	}

	private synchronized void createVariables()
	{
		LOG.debug("Updating images for screen scales {}", screenScales);
		if (renderingMayBeCancelled && projector != null)
			projector.cancel();
		renderImages = new ArrayImg[screenScales.length][0];
		renderMaskArrays = new byte[0][];
		screenImages = new ArrayList<>();
		bufferedImages = new ArrayList<>();
		for (int i = 0; i < screenScales.length; ++i)
		{
			screenImages.add(Arrays.asList(null, null, null));
			bufferedImages.add(Arrays.asList(null, null, null));
		}
		screenScaleTransforms = new AffineTransform3D[screenScales.length];
		pendingRepaintRequests = new Interval[screenScales.length];
		maxScreenScaleIndex = screenScales.length - 1;
		requestedScreenScaleIndex = maxScreenScaleIndex;
	}

}
