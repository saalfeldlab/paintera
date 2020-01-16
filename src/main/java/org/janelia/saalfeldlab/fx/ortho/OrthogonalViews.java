package org.janelia.saalfeldlab.fx.ortho;

import bdv.cache.CacheControl;
import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.TransformConcatenator;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wrap a {@link ResizableGridPane2x2} with {@link ViewerPanelFX viewer panels} at top left, top right, and bottom left. Bottom right
 * is left generic for flexibility. In practice, this can be populated with a settings tab, a 3D viewer, etc.
 *
 * @param <BR> type of bottom right child
 */
public class OrthogonalViews<BR extends Node>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Utility class that holds {@link ViewerPanelFX} and related objects.
	 */
	public static class ViewerAndTransforms
	{
		private final ViewerPanelFX viewer;

		private final GlobalTransformManager manager;

		private final AffineTransformWithListeners displayTransform;

		private final AffineTransformWithListeners globalToViewerTransform;

		private final TransformConcatenator concatenator;

		/**
		 *
		 * @param viewer viewer
		 * @param manager manages the transform from world coordinates to shared viewer space
		 * @param displayTransform accounts for scale after all other translations are applied
		 * @param globalToViewerTransform transform shared viewer space to the space of {@code viewer}. This typically is an axis permutation
		 *                                only, without scaling or rotation.
		 */
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

		/**
		 *
		 * @return display transform ({@link #ViewerAndTransforms constructor} for details)
		 */
		public AffineTransformWithListeners displayTransform()
		{
			return this.displayTransform;
		}

		/**
		 *
		 * @return global to viewer transform ({@link #ViewerAndTransforms constructor} for details)
		 */
		public AffineTransformWithListeners globalToViewerTransform()
		{
			return this.globalToViewerTransform;
		}
		/**
		 *
		 * @return {@link ViewerPanelFX viewer} associated with this.
		 */
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

	/**
	 *
	 * @param manager manages the transform from world coordinates to shared viewer space and is shared by all {@link ViewerPanelFX viewers}.
	 * @param cacheControl shared between all {@link ViewerPanelFX viewers}
	 * @param optional Options for {@link ViewerPanelFX}
	 * @param bottomRight bottom right child
	 * @param interpolation {@link Interpolation interpolation} lookup for every {@link Source}
	 */
	public OrthogonalViews(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final BR bottomRight,
			final Function<Source<?>, Interpolation> interpolation)
	{
		this.manager = manager;
		this.topLeft = create(this.manager, cacheControl, optional, ViewerAxis.Z, interpolation);
		this.topRight = create(this.manager, cacheControl, optional, ViewerAxis.X, interpolation);
		this.bottomLeft = create(this.manager, cacheControl, optional, ViewerAxis.Y, interpolation);
		this.grid = new ResizableGridPane2x2<>(topLeft.viewer, topRight.viewer, bottomLeft.viewer, bottomRight);
		this.queue = cacheControl;
	}

	/**
	 *
	 * @return underlying {@link ResizableGridPane2x2}
	 */
	public ResizableGridPane2x2<ViewerPanelFX, ViewerPanelFX, ViewerPanelFX, BR> grid()
	{
		return this.grid;
	}

	/**
	 *
	 * @return {@link ResizableGridPane2x2#pane()} for the underlying {@link ResizableGridPane2x2}
	 */
	public GridPane pane()
	{
		return grid().pane();
	}

	/**
	 *
	 * @param apply Apply this to all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 */
	public void applyToAll(final Consumer<ViewerPanelFX> apply)
	{
		apply.accept(topLeft.viewer);
		apply.accept(topRight.viewer);
		apply.accept(bottomLeft.viewer);
	}

	/**
	 * {@link ViewerPanelFX#requestRepaint()}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 */
	public void requestRepaint()
	{
		applyToAll(ViewerPanelFX::requestRepaint);
	}

	/**
	 * {@link ViewerPanelFX#requestRepaint(long[], long[])}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @param min
	 * 		top left corner of interval to be repainted
	 * @param max
	 * 		bottom right corner of interval to be repainted
	 */
	public void requestRepaint(final long[] min, final long[] max)
	{
		applyToAll(vp -> vp.requestRepaint(min, max));
	}

	/**
	 * {@link ViewerPanelFX#setAllSources(Collection)}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @param sources Will replace all current sources.
	 */
	public void setAllSources(final Collection<? extends SourceAndConverter<?>> sources)
	{
		applyToAll(viewer -> viewer.setAllSources(sources));
	}

	/**
	 * {@link ViewerPanelFX#addEventFilter(EventType, EventHandler)}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 * @see Node#addEventFilter(EventType, EventHandler) for details.
	 */
	public <E extends Event> void addEventFilter(final EventType<E> eventType, final EventHandler<E> handler)
	{
		applyToAll(viewer -> viewer.addEventFilter(eventType, handler));
	}

	/**
	 *
	 * @return top left {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms topLeft()
	{
		return this.topLeft;
	}

	/**
	 *
	 * @return top right {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms topRight()
	{
		return this.topRight;
	}

	/**
	 *
	 * @return bottom left {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms bottomLeft()
	{
		return this.bottomLeft;
	}

	/**
	 * Delegates to {@link #setScreenScales(double[], boolean) setScreenScales(screenScales, true)}
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public void setScreenScales(final double[] screenScales)
	{
		this.setScreenScales(screenScales, true);
	}

	/**
	 * {@link ViewerPanelFX#setScreenScales(double[])}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 * @param doRequestRepaint if {@code true}, also run {@link #requestRepaint()}
	 */
	public void setScreenScales(final double[] screenScales, final boolean doRequestRepaint)
	{
		LOG.debug("Setting screen scales to {} for all panels.", screenScales);
		applyToAll(vp -> vp.setScreenScales(screenScales));
		if (doRequestRepaint)
			requestRepaint();
	}

	private static ViewerAndTransforms create(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final ViewerAxis axis,
			final Function<Source<?>, Interpolation> interpolation)
	{
		final AffineTransform3D globalToViewer = ViewerAxis.globalToViewer(axis);
		LOG.debug("Generating viewer, axis={}, globalToViewer={}", axis, globalToViewer);
		final ViewerPanelFX viewer = new ViewerPanelFX(
				1,
				cacheControl,
				optional,
				interpolation
		);
		final AffineTransformWithListeners displayTransform        = new AffineTransformWithListeners();
		final AffineTransformWithListeners globalToViewerTransform = new AffineTransformWithListeners(globalToViewer);

		return new ViewerAndTransforms(viewer, manager, displayTransform, globalToViewerTransform);
	}

}
