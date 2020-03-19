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
package bdv.fx.viewer;

import bdv.cache.CacheControl;
import bdv.fx.viewer.render.RenderUnit;
import bdv.viewer.Interpolation;
import bdv.viewer.RequestRepaint;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author Philipp Hanslovsky
 *
 * Renders arbitrary cross-sections through a set of multi-resolution data sources with overlays. Overlays are generated
 * independently of cross-sections -- updates of overlays do not trigger re-rendering of the cross-sections.
 *
 */
public class ViewerPanelFX
		extends StackPane
		implements TransformListener<AffineTransform3D>,
		           RequestRepaint
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final RenderUnit renderUnit;

	private final CanvasPane canvasPane = new CanvasPane(1, 1);

	private final OverlayPane<?> overlayPane = new OverlayPane<>();

	private final ViewerState state;

	private final AffineTransform3D viewerTransform;

	private ThreadGroup threadGroup;

	private final ExecutorService renderingExecutorService;

	private final CopyOnWriteArrayList<TransformListener<AffineTransform3D>> transformListeners;

	private final ViewerOptions.Values options;

	private final MouseCoordinateTracker mouseTracker = new MouseCoordinateTracker();

	public ViewerPanelFX(
			final List<SourceAndConverter<?>> sources,
			final int numTimePoints,
			final CacheControl cacheControl,
			final Function<Source<?>, Interpolation> interpolation)
	{
		this(sources, numTimePoints, cacheControl, ViewerOptions.options(), interpolation);
	}

	/**
	 * Will create {@link ViewerPanelFX} without any data sources and a single time point.
	 *
	 * @param cacheControl
	 * 		to control IO budgeting and fetcher queue.
	 * @param optional
	 * 		optional parameters. See {@link ViewerOptions#options()}.
	 * @param interpolation
	 *      Get interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final Function<Source<?>, Interpolation> interpolation)
	{
		this(1, cacheControl, optional, interpolation);
	}

	/**
	 * Will create {@link ViewerPanelFX} without any data sources.
	 *
	 * @param numTimepoints
	 * 		number of available timepoints.
	 * @param cacheControl
	 * 		to control IO budgeting and fetcher queue.
	 * @param optional
	 * 		optional parameters. See {@link ViewerOptions#options()}.
	 * @param interpolation
	 *      Get interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final int numTimepoints,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final Function<Source<?>, Interpolation> interpolation)
	{
		this(new ArrayList<>(), numTimepoints, cacheControl, optional, interpolation);
	}

	/**
	 *
	 * Will create {@link ViewerPanelFX} and populate with {@code sources}.
	 *
	 * @param sources
	 * 		the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 * 		number of available timepoints.
	 * @param cacheControl
	 * 		to control IO budgeting and fetcher queue.
	 * @param optional
	 * 		optional parameters. See {@link ViewerOptions#options()}.
	 * @param interpolation
	 *      Get interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final List<SourceAndConverter<?>> sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final Function<Source<?>, Interpolation> interpolation)
	{
		super();
		super.getChildren().setAll(canvasPane, overlayPane);
		this.renderingExecutorService = Executors.newFixedThreadPool(optional.values.getNumRenderingThreads(), new RenderThreadFactory());
		options = optional.values;

		threadGroup = new ThreadGroup(this.toString());
		viewerTransform = new AffineTransform3D();

		transformListeners = new CopyOnWriteArrayList<>();

		mouseTracker.installInto(this);

		this.renderUnit = new RenderUnit(
			threadGroup,
			this::getState,
			interpolation,
			options.getAccumulateProjectorFactory(),
			cacheControl,
			options.getTargetRenderNanos(),
			options.getNumRenderingThreads(),
			renderingExecutorService
		);

		setRenderedImageListener();
		this.widthProperty().addListener((obs, oldv, newv) -> this.renderUnit.setDimensions((long)getWidth(), (long)getHeight()));
		this.heightProperty().addListener((obs, oldv, newv) -> this.renderUnit.setDimensions((long)getWidth(), (long)getHeight()));
		setWidth(options.getWidth());
		setHeight(options.getHeight());

		// TODO why is this necessary?
		transformListeners.add(tf -> getDisplay().drawOverlays());

		this.state = new ViewerState(numTimepoints);
		state.addListener(obs -> requestRepaint());

		setAllSources(sources);
	}

	/**
	 * Set the sources of this {@link ViewerPanelFX}.
	 * @param sources Will replace all current sources.
	 */
	public void setAllSources(final Collection<? extends SourceAndConverter<?>> sources)
	{
		this.state.setSources(sources);
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the global coordinate system.
	 *
	 * @param gPos
	 * 		is set to the corresponding global coordinates.
	 */
	public void displayToGlobalCoordinates(final double[] gPos)
	{
		assert gPos.length >= 3;

		viewerTransform.applyInverse(gPos, gPos);
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the global coordinate system.
	 *
	 * @param gPos
	 * 		is set to the corresponding global coordinates.
	 */
	public <P extends RealLocalizable & RealPositionable> void displayToGlobalCoordinates(final P gPos)
	{
		assert gPos.numDimensions() >= 3;

		viewerTransform.applyInverse(gPos, gPos);
	}

	/**
	 * Set {@code gPos} to the display coordinates (x,y,0)<sup>T</sup> transformed into the global coordinate system.
	 *
	 * @param gPos
	 * 		is set to the global coordinates at display (x,y,0)<sup>T</sup>.
	 */
	public void displayToGlobalCoordinates(final double x, final double y, final RealPositionable gPos)
	{
		assert gPos.numDimensions() >= 3;
		final RealPoint lPos = new RealPoint(3);
		lPos.setPosition(x, 0);
		lPos.setPosition(y, 1);
		viewerTransform.applyInverse(gPos, lPos);
	}

	/**
	 * Set {@code pos} to the display coordinates (x,y,0)<sup>T</sup> transformed into the source coordinate system.
	 *
	 * @param pos
	 * 		is set to the source coordinates at display (x,y,0)<sup>T</sup>.
	 */
	public <P extends RealLocalizable & RealPositionable> void displayToSourceCoordinates(
			final double x,
			final double y,
			final AffineTransform3D sourceTransform,
			final P pos)
	{
		pos.setPosition(x, 0);
		pos.setPosition(y, 1);
		pos.setPosition(0, 2);
		displayToGlobalCoordinates(pos);
		sourceTransform.applyInverse(pos, pos);
	}

	/**
	 * Set {@code gPos} to the current mouse coordinates transformed into the global coordinate system.
	 *
	 * @param gPos
	 * 		is set to the current global coordinates.
	 */
	public void getGlobalMouseCoordinates(final RealPositionable gPos)
	{
		assert gPos.numDimensions() == 3;
		final RealPoint lPos = new RealPoint(3);
		synchronized (mouseTracker) {
			lPos.setPosition(mouseTracker.getMouseX(), 0);
			lPos.setPosition(mouseTracker.getMouseY(), 1);
		}
		viewerTransform.applyInverse(gPos, lPos);
	}

	/**
	 * Set {@code p} to current mouse coordinates in viewer space.
	 *
	 * @param p
	 * 		is set to the current mouse coordinates in viewer space.
	 */
	public void getMouseCoordinates(final Positionable p)
	{
		assert p.numDimensions() >= 2;
		synchronized (mouseTracker) {
			p.setPosition((long) mouseTracker.getMouseX(), 0);
			p.setPosition((long) mouseTracker.getMouseY(), 1);
		}
	}

	/**
	 * Repaint as soon as possible.
	 */
	@Override
	public void requestRepaint()
	{
		renderUnit.requestRepaint();
	}

	/**
	 * Repaint the specified two-dimensional interval as soon as possible.
	 * @param min
	 * 		top left corner of interval to be repainted
	 * @param max
	 * 		bottom right corner of interval to be repainted
	 */
	public void requestRepaint(final long[] min, final long[] max)
	{
		assert min.length == 2;
		assert max.length == 2;
		renderUnit.requestRepaint(min, max);
	}

	@Override
	public synchronized void transformChanged(final AffineTransform3D transform)
	{
		viewerTransform.set(transform);
		state.setViewerTransform(transform);
		for (final TransformListener<AffineTransform3D> l : transformListeners)
			l.transformChanged(viewerTransform);
	}

	/**
	 * Get the current {@link ViewerState}.
	 *
	 * @return the current {@link ViewerState}.
	 */
	public ViewerState getState()
	{
		return state;
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified
	 * <em>before</em> calling {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 * 		the transform listener to add.
	 */
	public void addTransformListener(final TransformListener<AffineTransform3D> listener)
	{
		addTransformListener(listener, Integer.MAX_VALUE);
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified
	 * <em>before</em> calling {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 * 		the transform listener to add.
	 * @param index
	 * 		position in the list of listeners at which to insert this one.
	 */
	public void addTransformListener(final TransformListener<AffineTransform3D> listener, final int index)
	{
		synchronized (transformListeners)
		{
			final int s = transformListeners.size();
			transformListeners.add(index < 0 ? 0 : index > s ? s : index, listener);
			listener.transformChanged(viewerTransform);
		}
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 * 		the transform listener to remove.
	 */
	public void removeTransformListener(final TransformListener<AffineTransform3D> listener)
	{
		synchronized (transformListeners)
		{
			transformListeners.remove(listener);
		}
	}

	/**
	 * Shutdown the {@link ExecutorService} used for rendering tiles onto the screen.
	 */
	public void stop()
	{
		renderingExecutorService.shutdown();
	}

	private static final AtomicInteger panelNumber = new AtomicInteger(1);

	protected class RenderThreadFactory implements ThreadFactory
	{
		private final String threadNameFormat;

		private final AtomicInteger threadNumber = new AtomicInteger(1);

		RenderThreadFactory()
		{
			this.threadNameFormat = String.format("viewer-panel-fx-%d-thread-%%d", panelNumber.getAndIncrement());
			LOG.debug("Created {} with format {}", getClass().getSimpleName(), threadNameFormat);
		}

		@Override
		public Thread newThread(final Runnable r)
		{
			final Thread t = new Thread(threadGroup, r,
					String.format(threadNameFormat, threadNumber.getAndIncrement()),
					0
			);
			LOG.debug("Creating thread with name {}", t.getName());
			if (!t.isDaemon())
				t.setDaemon(true);
			if (t.getPriority() != Thread.NORM_PRIORITY)
				t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	@Override
	public ObservableList<Node> getChildren()
	{
		return FXCollections.unmodifiableObservableList(super.getChildren());
	}



	/**
	 * @see  MouseCoordinateTracker#getIsInside()
	 * @return {@link MouseCoordinateTracker#getIsInside()} ()}
	 */
	public boolean isMouseInside()
	{
		return this.mouseTracker.getIsInside();
	}

	/**
	 * @see  MouseCoordinateTracker#isInsideProperty()
	 * @return {@link MouseCoordinateTracker#isInsideProperty()}
	 */
	public ReadOnlyBooleanProperty isMouseInsideProperty()
	{
		return mouseTracker.isInsideProperty();
	}

	/**
	 * @see  MouseCoordinateTracker#mouseXProperty()
	 * @return {@link MouseCoordinateTracker#mouseXProperty()}
	 */
	public ReadOnlyDoubleProperty mouseXProperty()
	{
		return mouseTracker.mouseXProperty();
	}


	/**
	 * @see  MouseCoordinateTracker#mouseYProperty()
	 * @return {@link MouseCoordinateTracker#mouseYProperty()}
	 */
	public ReadOnlyDoubleProperty mouseYProperty()
	{
		return mouseTracker.mouseYProperty();
	}

	/**
	 * set the screen-scales used for rendering
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public void setScreenScales(final double[] screenScales)
	{
		LOG.debug("Setting screen scales to {}", screenScales);
		this.renderUnit.setScreenScales(screenScales.clone());
	}

	/**
	 *
	 * @return {@link OverlayPane} used for drawing overlays without re-rendering 2D cross-sections
	 */
	public OverlayPane<?> getDisplay()
	{
		return this.overlayPane;
	}

	public RenderUnit getRenderUnit()
	{
		return renderUnit;
	}

	private void setRenderedImageListener()
	{
		renderUnit.getRenderedImageProperty().addListener((obs, oldv, newv) -> {
			if (newv != null && newv.getImage() != null) {
				final Interval screenInterval = newv.getScreenInterval();
				final RealInterval renderTargetRealInterval = newv.getRenderTargetRealInterval();
				canvasPane.getCanvas().getGraphicsContext2D().drawImage(
					newv.getImage(), // src
					renderTargetRealInterval.realMin(0), // src X
					renderTargetRealInterval.realMin(1), // src Y
					renderTargetRealInterval.realMax(0) - renderTargetRealInterval.realMin(0), // src width
					renderTargetRealInterval.realMax(1) - renderTargetRealInterval.realMin(1), // src height
					screenInterval.min(0), // dst X
					screenInterval.min(1), // dst Y
					screenInterval.dimension(0), // dst width
					screenInterval.dimension(1)  // dst height
				);
			}
		});
	}
}
