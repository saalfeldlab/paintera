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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import bdv.viewer.Source;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.canvas.GraphicsContext;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render multibox overlay corresponding to a {@link ViewerState} into a {@link GraphicsContext}.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class MultiBoxOverlayRendererFX implements OverlayRendererGeneric<GraphicsContext>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Navigation wire-frame cube.
	 */
	protected final MultiBoxOverlayFX box;

	/**
	 * Screen interval in which to display navigation wire-frame cube.
	 */
	protected Interval boxInterval;

	/**
	 * scaled screenImage interval for {@link #box} rendering
	 */
	protected Interval virtualScreenInterval;

	protected final ArrayList<IntervalAndTransform> boxSources;

	private final Supplier<ViewerState> viewerState;

	private final List<Source<?>> allSources;

	private final List<Source<?>> visibleSources;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	public MultiBoxOverlayRendererFX(
			final Supplier<ViewerState> viewerState,
			final List<Source<?>> allSources,
			final List<Source<?>> visibleSources)
	{
		this(viewerState, allSources, visibleSources, 800, 600);
	}

	public MultiBoxOverlayRendererFX(
			final Supplier<ViewerState> viewerState,
			final List<Source<?>> allSources,
			final List<Source<?>> visibleSources,
			final int screenWidth,
			final int screenHeight)
	{
		this.viewerState = viewerState;
		this.allSources = allSources;
		this.visibleSources = visibleSources;
		box = new MultiBoxOverlayFX();
		boxInterval = Intervals.createMinSize(10, 10, 160, 120);
		virtualScreenInterval = Intervals.createMinSize(0, 0, screenWidth, screenHeight);
		boxSources = new ArrayList<>();
	}

	public synchronized void paint(final GraphicsContext g)
	{
		box.paint(g, boxSources, virtualScreenInterval, boxInterval);
	}

	// TODO
	public boolean isHighlightInProgress()
	{
		return box.isHighlightInProgress();
	}

	// TODO
	public void highlight(final int sourceIndex)
	{
		box.highlight(sourceIndex);
	}

	/**
	 * Update the screen interval. This is the target 2D interval into which pixels are rendered. (In the box
	 * overlay it
	 * is shown as a filled grey rectangle.)
	 */
	public synchronized void updateVirtualScreenSize(final int screenWidth, final int screenHeight)
	{
		final long oldW = virtualScreenInterval.dimension(0);
		final long oldH = virtualScreenInterval.dimension(1);
		if (screenWidth != oldW || screenHeight != oldH)
		{
			virtualScreenInterval = Intervals.createMinSize(0, 0, screenWidth, screenHeight);
		}
	}

	/**
	 * Update the box interval. This is the screen interval in which to display navigation wire-frame cube.
	 */
	public synchronized void setBoxInterval(final Interval interval)
	{
		boxInterval = interval;
	}

	/**
	 * Update data to show in the box overlay.
	 */
	public synchronized void setViewerState(final ViewerState viewerState)
	{
		synchronized (viewerState)
		{
			final int timepoint = viewerState.timepointProperty().get();

			final int numSources        = this.allSources.size();
			final int numPresentSources = (int) IntStream.range(
					0,
					numSources
			                                                   ).mapToObj(allSources::get).filter(s -> s.isPresent(
					timepoint)).count();

			LOG.debug(
					"numSources={} numPresentSources={} boxSources.size={}",
					numSources,
					numPresentSources,
					boxSources.size()
			         );

			if (boxSources.size() != numPresentSources)
			{
				while (boxSources.size() < numPresentSources)
				{
					boxSources.add(new IntervalAndTransform());
				}
				while (boxSources.size() > numPresentSources)
				{
					boxSources.remove(boxSources.size() - 1);
				}
			}

			final AffineTransform3D sourceToViewer  = new AffineTransform3D();
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			for (int i = 0, j = 0; i < numSources; ++i)
			{
				final Source<?> source = this.allSources.get(i);
				if (source.isPresent(timepoint))
				{
					LOG.debug("Setting box for source i={}, j={} boxSources.size={}", i, j, boxSources.size());
					final IntervalAndTransform boxsource = boxSources.get(j++);
					viewerState.getViewerTransform(sourceToViewer);
					source.getSourceTransform(timepoint, 0, sourceTransform);
					sourceToViewer.concatenate(sourceTransform);
					boxsource.setSourceToViewer(sourceToViewer);
					boxsource.setSourceInterval(source.getSource(timepoint, 0));
					boxsource.setVisible(this.visibleSources.contains(source));
				}
			}
		}
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	@Override
	public void drawOverlays(final GraphicsContext g)
	{
		if (this.isVisible.get())
		{
			this.setViewerState(viewerState.get());
			this.paint(g);
		}
	}

	@Override
	public void setCanvasSize(final int width, final int height)
	{
		this.updateVirtualScreenSize(width, height);
	}
}
