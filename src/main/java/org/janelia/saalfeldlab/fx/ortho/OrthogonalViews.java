package org.janelia.saalfeldlab.fx.ortho;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.cache.CacheControl;
import bdv.fx.viewer.ViewerPanelFX;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.TransformConcatenator;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrthogonalViews<BR extends Node>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class ViewerAndTransforms
	{
		private final ViewerPanelFX viewer;

		private final GlobalTransformManager manager;

		private final AffineTransformWithListeners displayTransform;

		private final AffineTransformWithListeners globalToViewerTransform;

		private final TransformConcatenator concatenator;

		public ViewerAndTransforms(
				final ViewerPanelFX viewer,
				final GlobalTransformManager manager,
				final AffineTransformWithListeners displayTransform,
				final AffineTransformWithListeners globalToViewerTransform)
		{
			super();
			this.viewer = viewer;
			this.manager = manager;
			this.displayTransform = displayTransform;
			this.globalToViewerTransform = globalToViewerTransform;

			this.concatenator = new TransformConcatenator(
					this.manager,
					displayTransform,
					globalToViewerTransform,
					manager
			);
			this.concatenator.setTransformListener(viewer);
		}

		public AffineTransformWithListeners displayTransform()
		{
			return this.displayTransform;
		}

		public AffineTransformWithListeners globalToViewerTransform()
		{
			return this.globalToViewerTransform;
		}

		public ViewerPanelFX viewer()
		{
			return viewer;
		}
	}

	private final ResizableGridPane2x2<ViewerPanelFX, ViewerPanelFX, ViewerPanelFX, BR> grid;

	private final GlobalTransformManager manager;

	private final ViewerAndTransforms topLeft;

	private final ViewerAndTransforms topRight;

	private final ViewerAndTransforms bottomLeft;

	private final CacheControl queue;

	public OrthogonalViews(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final BR bottomRight,
			final Function<Source<?>, Interpolation> interpolation,
			final Function<Source<?>, AxisOrder> axisOrder)
	{
		this.manager = manager;
		this.topLeft = create(this.manager, cacheControl, optional, ViewerAxis.Z, interpolation, axisOrder);
		this.topRight = create(this.manager, cacheControl, optional, ViewerAxis.X, interpolation, axisOrder);
		this.bottomLeft = create(this.manager, cacheControl, optional, ViewerAxis.Y, interpolation, axisOrder);
		this.grid = new ResizableGridPane2x2<>(topLeft.viewer, topRight.viewer, bottomLeft.viewer, bottomRight);
		this.queue = cacheControl;
	}

	public ResizableGridPane2x2<ViewerPanelFX, ViewerPanelFX, ViewerPanelFX, BR> grid()
	{
		return this.grid;
	}

	public Pane pane()
	{
		return grid().pane();
	}

	private static ViewerAndTransforms create(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final ViewerAxis axis,
			final Function<Source<?>, Interpolation> interpolation,
			final Function<Source<?>, AxisOrder> axisOrder)
	{
		final AffineTransform3D globalToViewer = ViewerAxis.globalToViewer(axis);
		LOG.debug("Generating viewer, axis={}, globalToViewer={}", axis, globalToViewer);
		final ViewerPanelFX                viewer                  = new ViewerPanelFX(
				axisOrder,
				1,
				cacheControl,
				optional,
				interpolation
		);
		final AffineTransformWithListeners displayTransform        = new AffineTransformWithListeners();
		final AffineTransformWithListeners globalToViewerTransform = new AffineTransformWithListeners(globalToViewer);

		return new ViewerAndTransforms(viewer, manager, displayTransform, globalToViewerTransform);
	}

	public void applyToAll(final Consumer<ViewerPanelFX> apply)
	{
		apply.accept(topLeft.viewer);
		apply.accept(topRight.viewer);
		apply.accept(bottomLeft.viewer);
	}

	public void requestRepaint()
	{
		applyToAll(ViewerPanelFX::requestRepaint);
	}

	public void setAllSources(final Collection<? extends SourceAndConverter<?>> sources)
	{
		applyToAll(viewer -> viewer.setAllSources(sources));
	}

	public <E extends Event> void addEventHandler(final EventType<E> eventType, final EventHandler<E> handler)
	{
		applyToAll(viewer -> viewer.addEventHandler(eventType, handler));
	}

	public <E extends Event> void addEventFilter(final EventType<E> eventType, final EventHandler<E> handler)
	{
		applyToAll(viewer -> viewer.addEventFilter(eventType, handler));
	}

	public <E extends Event> void removeEventHandler(final EventType<E> eventType, final EventHandler<E> handler)
	{
		applyToAll(viewer -> viewer.removeEventHandler(eventType, handler));
	}

	public <E extends Event> void removeEventFilter(final EventType<E> eventType, final EventHandler<E> handler)
	{
		applyToAll(viewer -> viewer.removeEventFilter(eventType, handler));
	}

	public ViewerAndTransforms topLeft()
	{
		return this.topLeft;
	}

	public ViewerAndTransforms topRight()
	{
		return this.topRight;
	}

	public ViewerAndTransforms bottomLeft()
	{
		return this.bottomLeft;
	}

	public CacheControl sharedQueue()
	{
		return this.queue;
	}

	public void setScreenScales(final double[] screenScales)
	{
		this.setScreenScales(screenScales, true);
	}

	public void setScreenScales(final double[] screenScales, final boolean doRequestReapint)
	{
		LOG.debug("Setting screen scales to {} for all panels.", screenScales);
		applyToAll(vp -> vp.setScreenScales(screenScales));
		if (doRequestReapint)
			requestRepaint();
	}

}
