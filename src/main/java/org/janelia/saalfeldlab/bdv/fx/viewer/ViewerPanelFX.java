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
package org.janelia.saalfeldlab.bdv.fx.viewer;

import bdv.cache.CacheControl;
import org.janelia.saalfeldlab.bdv.fx.viewer.render.ViewerRenderUnit;
import bdv.viewer.Interpolation;
import bdv.viewer.RequestRepaint;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TransformListener;
import bdv.viewer.ViewerOptions.Values;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.ObservablePosition;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.ortho.OrthoViewerOptions;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author Philipp Hanslovsky
 * <p>
 * Renders arbitrary cross-sections through a set of multi-resolution data sources with overlays. Overlays are generated
 * independently of cross-sections -- updates of overlays do not trigger re-rendering of the cross-sections.
 */
public class ViewerPanelFX
		extends StackPane
		implements TransformListener<AffineTransform3D>, RequestRepaint {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerRenderUnit renderUnit;

	private final CanvasPane canvasPane = new CanvasPane(1, 1);

	private final OverlayPane overlayPane = new OverlayPane();

	private final ViewerState state;

	private final AffineTransform3D viewerTransform;

	private ThreadGroup threadGroup;

	private final CopyOnWriteArrayList<TransformListener<AffineTransform3D>> transformListeners;

	private final Values options;

	private final MouseCoordinateTracker mouseTracker = new MouseCoordinateTracker();

	private boolean focusable = true;

	public ViewerPanelFX(
			final List<SourceAndConverter<?>> sources,
			final int numTimePoints,
			final CacheControl cacheControl,
			final Function<Source<?>, Interpolation> interpolation,
			final TaskExecutor taskExecutor) {

		this(sources, numTimePoints, cacheControl, OrthoViewerOptions.options(), interpolation, taskExecutor);
	}

	/**
	 * Will create {@link ViewerPanelFX} without any data sources and a single time point.
	 *
	 * @param cacheControl  to control IO budgeting and fetcher queue.
	 * @param options  Viewer parameters. See {@link OrthoViewerOptions#options()}.
	 * @param interpolation Get interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final CacheControl cacheControl,
			final OrthoViewerOptions options,
			final Function<Source<?>, Interpolation> interpolation,
			final TaskExecutor taskExecutor) {

		this(1, cacheControl, options, interpolation, taskExecutor);
	}

	/**
	 * Will create {@link ViewerPanelFX} without any data sources.
	 *
	 * @param numTimepoints number of available timepoints.
	 * @param cacheControl  to control IO budgeting and fetcher queue.
	 * @param options       Viewer parameters. See {@link OrthoViewerOptions#options()}.
	 * @param interpolation Get the interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final int numTimepoints,
			final CacheControl cacheControl,
			final OrthoViewerOptions options,
			final Function<Source<?>, Interpolation> interpolation,
			final TaskExecutor taskExecutor) {

		this(new ArrayList<>(), numTimepoints, cacheControl, options, interpolation, taskExecutor);
	}

	/**
	 * Will create {@link ViewerPanelFX} and populate with {@code sources}.
	 *
	 * @param sources       the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints number of available timepoints.
	 * @param cacheControl  to control IO budgeting and fetcher queue.
	 * @param options      options parameters. See {@link OrthoViewerOptions#options()}.
	 * @param interpolation Get interpolation method for each data source.
	 */
	public ViewerPanelFX(
			final List<SourceAndConverter<?>> sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final OrthoViewerOptions options,
			final Function<Source<?>, Interpolation> interpolation,
			final TaskExecutor taskExecutor) {

		super();
		super.setBackground(Background.fill(Color.BLACK));
		super.getChildren().setAll(canvasPane, overlayPane);
		this.options = options.values;

		threadGroup = new ThreadGroup(this.toString());
		viewerTransform = new AffineTransform3D();

		transformListeners = new CopyOnWriteArrayList<>();

		ActionSet.installActionSet(this, mouseTracker.getActions());

		this.renderUnit = new ViewerRenderUnit(
				threadGroup,
				this::getState,
				interpolation,
				this.options.getAccumulateProjectorFactory(),
				cacheControl,
				this.options.getTargetRenderNanos(),
				taskExecutor
		);

		startRenderAnimator();

		/* initialize the prefWidth and width properties. */
		setPrefWidth(this.options.getWidth());
		setPrefHeight(this.options.getHeight());
		setWidth(this.options.getWidth());
		setHeight(this.options.getHeight());
		widthProperty().subscribe(width -> renderUnit.setDimensions(width.longValue(), (long)getHeight()));
		heightProperty().subscribe(height -> renderUnit.setDimensions((long)getWidth(), height.longValue()));

		visibleProperty().subscribe(visible -> {
			if (visible)
				renderUnit.setDimensions((long)getWidth(), (long)getHeight());
			else
				renderUnit.stopRendering();
		});


		transformListeners.add(tf -> Paintera.whenPaintable(getDisplay()::drawOverlays));

		this.state = new ViewerState(numTimepoints, this);
		state.addListener(obs -> Paintera.whenPaintable(this::requestRepaint));

		Paintera.whenPaintable(() -> getDisplay().drawOverlays());

		setAllSources(sources);
	}

	/**
	 * Set the sources of this {@link ViewerPanelFX}.
	 *
	 * @param sources Will replace all current sources.
	 */
	public void setAllSources(final Collection<? extends SourceAndConverter<?>> sources) {

		this.state.setSources(sources);
	}

	public boolean isFocusable() {

		return focusable;
	}

	public void setFocusable(boolean focusable) {

		this.focusable = focusable;
	}

	@Override
	public void requestFocus() {

		if (this.focusable) {
			super.requestFocus();
		}
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the global coordinate system.
	 *
	 * @param gPos is set to the corresponding global coordinates.
	 */
	public void displayToGlobalCoordinates(final double[] gPos) {

		assert gPos.length >= 3;

		viewerTransform.applyInverse(gPos, gPos);
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the global coordinate system.
	 *
	 * @param gPos is set to the corresponding global coordinates.
	 */
	public <P extends RealLocalizable & RealPositionable> void displayToGlobalCoordinates(final P gPos) {

		assert gPos.numDimensions() >= 3;

		viewerTransform.applyInverse(gPos, gPos);
	}

	/**
	 * Set {@code gPos} to the display coordinates (x,y,0)<sup>T</sup> transformed into the global coordinate system.
	 *
	 * @param gPos is set to the global coordinates at display (x,y,0)<sup>T</sup>.
	 */
	public void displayToGlobalCoordinates(final double x, final double y, final RealPositionable gPos) {

		assert gPos.numDimensions() >= 3;
		final RealPoint lPos = new RealPoint(3);
		lPos.setPosition(x, 0);
		lPos.setPosition(y, 1);
		viewerTransform.applyInverse(gPos, lPos);
	}

	/**
	 * Set {@code pos} to the display coordinates (x,y,0)<sup>T</sup> transformed into the source coordinate system.
	 *
	 * @param pos is set to the source coordinates at display (x,y,0)<sup>T</sup>.
	 */
	public <P extends RealLocalizable & RealPositionable> void displayToSourceCoordinates(
			final double x,
			final double y,
			final AffineTransform3D sourceToGlobal,
			final P pos) {

		pos.setPosition(x, 0);
		pos.setPosition(y, 1);
		pos.setPosition(0, 2);
		displayToGlobalCoordinates(pos);
		sourceToGlobal.applyInverse(pos, pos);
	}


	/**
	 * Set {@code pos} to the source coordinates (x,y,z)<sup>T</sup> transformed into the display coordinate system.
	 *
	 * @param pos is set to the display coordinates at display (x,y,z)<sup>T</sup>.
	 */
	public <P extends RealLocalizable & RealPositionable> void sourceToDisplayCoordinates(
			final double x,
			final double y,
			final double z,
			final AffineTransform3D sourceToGlobal,
			final P pos) {

		pos.setPosition(x, 0);
		pos.setPosition(y, 1);
		pos.setPosition(z, 2);
		sourceToGlobal.apply(pos, pos);
		viewerTransform.apply(pos, pos);
	}

	/**
	 * Set {@code gPos} to the current mouse coordinates transformed into the global coordinate system.
	 *
	 * @param gPos is set to the current global coordinates.
	 */
	public void getGlobalMouseCoordinates(final RealPositionable gPos) {

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
	 * @param p is set to the current mouse coordinates in viewer space.
	 */
	public void getMouseCoordinates(final Positionable p) {

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
	public void requestRepaint() {
		/* If either are 0, that means the user cannot see it. This happens when one of the three are maximized, and onle 1 ViewerPaneFX is shown.
		 * In this case, don't request a repaint for that pane*/
		if (getWidth() != 0 && getHeight() != 0) {
			renderUnit.requestRepaint();
		}
	}

	public void requestRepaint(final RealInterval intervalInGlobalSpace) {

		if (intervalInGlobalSpace == null) {
			requestRepaint();
			return;
		}

		final AffineTransform3D globalToViewerTransform = this.viewerTransform.copy();
		final RealInterval intervalInViewerSpace = globalToViewerTransform.estimateBounds(intervalInGlobalSpace);
		// NOTE: Simply transforming the bounding box will over-estimate the intervalInViewerSpace that
		//  needs to be repainted when not axis aligned but in practice that does not seem to be an issue.
		if (intervalInViewerSpace.realMin(2) <= 0 && intervalInViewerSpace.realMax(2) >= 0) {
			final Interval integerInterval = Intervals.smallestContainingInterval(intervalInViewerSpace);
			final long[] min = new long[2];
			final long[] max = new long[2];
			Arrays.setAll(min, integerInterval::min);
			Arrays.setAll(max, integerInterval::max);
			requestRepaint(min, max);
		}
	}

	/**
	 * Repaint the specified two-dimensional interval as soon as possible.
	 *
	 * @param min top left corner of interval to be repainted
	 * @param max bottom right corner of interval to be repainted
	 */
	public void requestRepaint(final long[] min, final long[] max) {

		assert min.length == 2;
		assert max.length == 2;
		renderUnit.requestRepaint(min, max);
	}

	@Override
	public synchronized void transformChanged(final AffineTransform3D transform) {

		viewerTransform.set(transform);
		state.setViewerTransform(transform);
		for (final TransformListener<AffineTransform3D> l : transformListeners) {
			l.transformChanged(viewerTransform);
		}
	}

	/**
	 * Get the current {@link ViewerState}.
	 *
	 * @return the current {@link ViewerState}.
	 */
	public ViewerState getState() {

		return state;
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified
	 * <em>before</em> calling {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener the transform listener to add.
	 */
	public void addTransformListener(final TransformListener<AffineTransform3D> listener) {

		addTransformListener(listener, Integer.MAX_VALUE);
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified
	 * <em>before</em> calling {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener the transform listener to add.
	 * @param index    position in the list of listeners at which to insert this one.
	 */
	public void addTransformListener(final TransformListener<AffineTransform3D> listener, final int index) {

		synchronized (transformListeners) {
			final int s = transformListeners.size();
			transformListeners.add(index < 0 ? 0 : Math.min(index, s), listener);
			listener.transformChanged(viewerTransform);
		}
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener the transform listener to remove.
	 */
	public void removeTransformListener(final TransformListener<AffineTransform3D> listener) {

		synchronized (transformListeners) {
			transformListeners.remove(listener);
		}
	}

	private static final AtomicInteger panelNumber = new AtomicInteger(1);

	//	TODO: rendering
	protected class RenderThreadFactory implements ThreadFactory {

		private final String threadNameFormat;

		private final AtomicInteger threadNumber = new AtomicInteger(1);

		RenderThreadFactory() {

			this.threadNameFormat = String.format("viewer-panel-fx-%d-thread-%%d", panelNumber.getAndIncrement());
			LOG.debug("Created {} with format {}", getClass().getSimpleName(), threadNameFormat);
		}

		@Override
		public Thread newThread(final Runnable r) {

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

	/**
	 * @return {@link MouseCoordinateTracker#getIsInside()} ()}
	 * @see MouseCoordinateTracker#getIsInside()
	 */
	public boolean isMouseInside() {

		return this.mouseTracker.getIsInside();
	}

	/**
	 * @return {@link MouseCoordinateTracker#isInsideProperty()}
	 * @see MouseCoordinateTracker#isInsideProperty()
	 */
	public ReadOnlyBooleanProperty isMouseInsideProperty() {

		return mouseTracker.isInsideProperty();
	}

	/**
	 * @return {@link MouseCoordinateTracker#getMouseXProperty()}
	 * @see MouseCoordinateTracker#getMouseXProperty()
	 */
	public ReadOnlyDoubleProperty getMouseXProperty() {

		return mouseTracker.getMouseXProperty();
	}

	/**
	 * @return {@link MouseCoordinateTracker#getMouseYProperty()}
	 * @see MouseCoordinateTracker#getMouseYProperty()
	 */
	public ReadOnlyDoubleProperty getMouseYProperty() {

		return mouseTracker.getMouseYProperty();
	}

	/**
	 *
	 * Creates a ObservablePosition which refers to either the location on ViewerPanelFX under the mouse, OR the center
	 *  if the mouse is not on the ViewerPanelFX.
	 *
	 * @return the observable position
	 */
	public ObservablePosition createMousePositionOrCenterBinding() {

		var xBinding = Bindings.createDoubleBinding(
				() -> isMouseInside() ? getMouseXProperty().get() : getWidth() / 2.0,
				isMouseInsideProperty(), getMouseXProperty(), widthProperty());

		var yBinding = Bindings.createDoubleBinding(
				() -> isMouseInside() ? getMouseYProperty().get() : getHeight() / 2.0,
				isMouseInsideProperty(), getMouseYProperty(), heightProperty());

		var pos = new ObservablePosition(mouseTracker.getMouseX(), mouseTracker.getMouseY());
		pos.getXProperty().bind(xBinding);
		pos.getYProperty().bind(yBinding);
		return pos;
	}

	/**
	 * set the screen-scales used for rendering
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public void setScreenScales(final double[] screenScales) {

		LOG.debug("Setting screen scales to {}", screenScales);
		this.renderUnit.setScreenScales(screenScales.clone());
	}

	public double[] getScreenScales() {
		final double[] screenScale = renderUnit.getScreenScalesProperty().get();
		return Arrays.copyOf(screenScale, screenScale.length);
	}

	/**
	 * @return {@link OverlayPane} used for drawing overlays without re-rendering 2D cross-sections
	 */
	public OverlayPane getDisplay() {

		return this.overlayPane;
	}

	public ViewerRenderUnit getRenderUnit() {

		return renderUnit;
	}

	private void startRenderAnimator() {

		new AnimationTimer() {

			private ViewerRenderUnit.RenderResult renderResult = null;

			@Override
			public void handle(long now) {
				final var result = renderUnit.getRenderedImageProperty().get();
				if (result != renderResult) {
					renderResult = result;
				}

				if (renderResult != null) {
					final Image image = renderResult.getImage();
					if (image != null) {
						final Interval screenInterval = renderResult.getScreenInterval();
						final RealInterval renderTargetRealInterval = renderResult.getRenderTargetRealInterval();

						canvasPane.getCanvas().getGraphicsContext2D().clearRect(
								screenInterval.min(0), // dst X
								screenInterval.min(1), // dst Y
								screenInterval.dimension(0), // dst width
								screenInterval.dimension(1)  // dst height
						);
						synchronized (image) {
							canvasPane.getCanvas().getGraphicsContext2D().drawImage(
									image, // src
									renderTargetRealInterval.realMin(0), // src X
									renderTargetRealInterval.realMin(1), // src Y
									renderTargetRealInterval.realMax(0) - renderTargetRealInterval.realMin(0), // src width
									renderTargetRealInterval.realMax(1) - renderTargetRealInterval.realMin(1), // src height
									screenInterval.min(0) - 1, // dst X
									screenInterval.min(1) - 1, // dst Y
									screenInterval.dimension(0) + 1, // dst width
									screenInterval.dimension(1) + 1  // dst height
							);
						}
					}
				}
			}
		}.start();
	}
}
