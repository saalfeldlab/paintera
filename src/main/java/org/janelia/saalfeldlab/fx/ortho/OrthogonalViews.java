package org.janelia.saalfeldlab.fx.ortho;

import bdv.cache.CacheControl;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import net.imglib2.RealInterval;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActionsProperty;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.TransformConcatenator;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wrap a {@link DynamicCellPane} with {@link ViewerPanelFX viewer panels} at top left, top right, and bottom left. Bottom right
 * is left generic for flexibility. In practice, this can be populated with a settings tab, a 3D viewer, etc.
 *
 * @param <BR> type of bottom right child
 */
public class OrthogonalViews<BR extends Node> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final TaskExecutor renderingTaskExecutor;

	public void stop() {

		renderingTaskExecutor.close();
	}

	/**
	 * Utility class that holds {@link ViewerPanelFX} and related objects.
	 */
	public static class ViewerAndTransforms {

		private final ViewerPanelFX viewer;

		private final GlobalTransformManager manager;

		private final AffineTransformWithListeners displayTransform;

		/* transform shared viewer space to the space of {@code viewer}. This typically is an axis permutation */
		private final AffineTransformWithListeners viewerSpaceToViewerTransform;

		private final TransformConcatenator globalToViewerTransformListener;

		/**
		 * @param viewer                       viewer
		 * @param manager                      manages the transform from world coordinates to shared viewer space
		 * @param displayTransform             accounts for scale after all other translations are applied
		 * @param viewerSpaceToViewerTransform transform shared viewer space to the space of {@code viewer}. This typically is an axis permutation
		 *                                     only, without scaling or rotation.
		 */
		public ViewerAndTransforms(
				final ViewerPanelFX viewer,
				final GlobalTransformManager manager,
				final AffineTransformWithListeners displayTransform,
				final AffineTransformWithListeners viewerSpaceToViewerTransform) {

			super();
			this.viewer = viewer;
			this.manager = manager;
			this.displayTransform = displayTransform;
			this.viewerSpaceToViewerTransform = viewerSpaceToViewerTransform;

			this.globalToViewerTransformListener = new TransformConcatenator(
					this.manager,
					displayTransform,
					viewerSpaceToViewerTransform
			);
			this.globalToViewerTransformListener.addListener(viewer);
		}

		public AffineTransformWithListeners getGlobalToViewerTransform() {

			return globalToViewerTransformListener;
		}

		/**
		 * @return display transform ({@link #ViewerAndTransforms constructor} for details)
		 */
		public AffineTransformWithListeners getDisplayTransform() {

			return this.displayTransform;
		}

		/**
		 * @return shared viewer space to viewer transform ({@link #ViewerAndTransforms constructor} for details)
		 */
		public AffineTransformWithListeners getViewerSpaceToViewerTransform() {

			return this.viewerSpaceToViewerTransform;
		}

		/**
		 * @return {@link ViewerPanelFX viewer} associated with this.
		 */
		public ViewerPanelFX viewer() {

			return viewer;
		}
	}

	private final DynamicCellPane pane;

	private final GlobalTransformManager manager;

	private final ViewerAndTransforms topLeft;

	private final ViewerAndTransforms topRight;

	private final ViewerAndTransforms bottomLeft;

	private final BR bottomRight;

	private final CacheControl queue;

	/**
	 * @param manager       manages the transform from world coordinates to shared viewer space and is shared by all {@link ViewerPanelFX viewers}.
	 * @param cacheControl  shared between all {@link ViewerPanelFX viewers}
	 * @param options      Options for {@link ViewerPanelFX}
	 * @param bottomRight   bottom right child
	 * @param interpolation {@link Interpolation interpolation} lookup for every {@link Source}
	 */
	public OrthogonalViews(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final OrthoViewerOptions options,
			final BR bottomRight,
			final Function<Source<?>, Interpolation> interpolation) {

		this.manager = manager;
		final var count = new AtomicInteger();
		final ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
			final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			worker.setDaemon(true);
			worker.setPriority(4);
			worker.setName("render-thread-" + count.getAndIncrement());
			return worker;
		};

		final int maxNumThreads = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);
		var numRenderingThreads = Math.min(options.values.getNumRenderingThreads(), maxNumThreads);
		final ForkJoinPool rendererService = new ForkJoinPool(numRenderingThreads, factory, null, false);
		this.renderingTaskExecutor = TaskExecutors.forExecutorService(rendererService);
		this.topLeft = create(this.manager, cacheControl, options, ViewerAxis.Z, interpolation, renderingTaskExecutor);
		this.topRight = create(this.manager, cacheControl, options, ViewerAxis.X, interpolation, renderingTaskExecutor);
		this.bottomLeft = create(this.manager, cacheControl, options, ViewerAxis.Y, interpolation, renderingTaskExecutor);
		this.bottomRight = bottomRight;
		this.pane = new DynamicCellPane();
		final int orthoViewPrefWidth = options.values.getWidth();
		final int orthoViewPrefHeight = options.values.getHeight();
		this.pane.setPrefSize(orthoViewPrefWidth * 2, orthoViewPrefHeight * 2);
		resetPane();
		Paintera.whenPaintable(() -> {
			listenOnResizePermissions(this.pane);
		});

		this.queue = cacheControl;
	}

	private void listenOnResizePermissions(DynamicCellPane pane) {

		final PainteraBaseView paintera = Paintera.getPaintera().getBaseView();
		final AllowedActionsProperty allowedActions = paintera.allowedActionsProperty();
		final BooleanBinding resizeAllowedBinding = allowedActions.allowedActionBinding(MenuActionType.ResizeViewers);

		/* when the items change, set the resizable status again */
		pane.getItems().addListener((ListChangeListener<Node>)change -> {
			setDividersResizable(pane, resizeAllowedBinding.getValue());
		});

		/* when the permissions change, update the resiable status */
		resizeAllowedBinding.subscribe(resizeable -> setDividersResizable(pane, resizeable));

	}

	private void setDividersResizable(final DynamicCellPane pane, final boolean resizable) {

		for (Node divider : pane.lookupAll(".split-pane-divider")) {
			divider.setDisable(!resizable);
		}
	}

	/**
	 * @return underlying {@link DynamicCellPane}
	 */
	public DynamicCellPane pane() {

		return this.pane;
	}

	/**
	 * reset the DynamicCellPane to match the initial configuration.
	 */
	public void resetPane() {

		this.pane.removeAll();
		this.pane.addRow(topLeft.viewer, topRight.viewer);
		this.pane.addRow(bottomLeft.viewer, bottomRight);

	}

	/**
	 * @param apply Apply this to all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 */
	public void applyToAll(final Consumer<ViewerPanelFX> apply) {

		apply.accept(topLeft.viewer);
		apply.accept(topRight.viewer);
		apply.accept(bottomLeft.viewer);
	}

	/**
	 * {@link ViewerPanelFX#requestRepaint()}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 */
	public void requestRepaint() {

		applyToAll(ViewerPanelFX::requestRepaint);
	}

	public void requestRepaint(final RealInterval intervalInGlobalSpace) {

		this.applyToAll(v -> v.requestRepaint(intervalInGlobalSpace));
	}

	public void drawOverlays() {

		applyToAll(it -> it.getDisplay().drawOverlays());
	}

	/**
	 * {@link ViewerPanelFX#setAllSources(Collection)}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @param sources Will replace all current sources.
	 */
	public void setAllSources(final Collection<? extends SourceAndConverter<?>> sources) {

		applyToAll(viewer -> viewer.setAllSources(sources));
	}

	/**
	 * {@link ViewerPanelFX#addEventFilter(EventType, EventHandler)}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @see Node#addEventFilter(EventType, EventHandler) for details.
	 */
	public <E extends Event> void addEventFilter(final EventType<E> eventType, final EventHandler<E> handler) {

		applyToAll(viewer -> viewer.addEventFilter(eventType, handler));
	}

	public List<ViewerPanelFX> views() {

		return viewerAndTransforms().stream().map(ViewerAndTransforms::viewer).collect(Collectors.toList());
	}

	public List<ViewerAndTransforms> viewerAndTransforms() {

		return List.of(getTopLeft(), getTopRight(), getBottomLeft());
	}

	/**
	 * @return top left {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms getTopLeft() {

		return this.topLeft;
	}

	/**
	 * @return top right {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms getTopRight() {

		return this.topRight;
	}

	/**
	 * @return bottom left {@link ViewerPanelFX viewer}
	 */
	public ViewerAndTransforms getBottomLeft() {

		return this.bottomLeft;
	}

	public BR getBottomRight() {

		return this.bottomRight;
	}

	public void disableView(final ViewerPanelFX viewer) {

		viewer.setFocusable(false);
		final var grayedOut = new ColorAdjust();
		grayedOut.setBrightness(-0.3);
		viewer.setEffect(grayedOut);
	}

	public void enableView(final ViewerPanelFX viewer) {

		viewer.setFocusable(true);
		viewer.setEffect(null);
	}

	/**
	 * Delegates to {@link #setScreenScales(double[], boolean) setScreenScales(screenScales, true)}
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public void setScreenScales(final double[] screenScales) {

		this.setScreenScales(screenScales, true);
	}

	/**
	 * {@link ViewerPanelFX#setScreenScales(double[])}} for all {@link ViewerPanelFX viewer children} (top left, top right, bottom left)
	 *
	 * @param screenScales     subject to following constraints:
	 *                         1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                         2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 * @param doRequestRepaint if {@code true}, also run {@link #requestRepaint()}
	 */
	public void setScreenScales(final double[] screenScales, final boolean doRequestRepaint) {

		LOG.debug("Setting screen scales to {} for all panels.", screenScales);
		applyToAll(vp -> vp.setScreenScales(screenScales));
		if (doRequestRepaint)
			requestRepaint();
	}

	private static ViewerAndTransforms create(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final OrthoViewerOptions options,
			final ViewerAxis axis,
			final Function<Source<?>, Interpolation> interpolation,
			final TaskExecutor taskExecutor) {

		final AffineTransform3D globalToViewer = ViewerAxis.globalToViewer(axis);
		LOG.debug("Generating viewer, axis={}, globalToViewer={}", axis, globalToViewer);
		final ViewerPanelFX viewer = new ViewerPanelFX(
				1,
				cacheControl,
				options,
				interpolation,
				taskExecutor
		);
		final AffineTransformWithListeners displayTransform = new AffineTransformWithListeners();
		final AffineTransformWithListeners globalToViewerTransform = new AffineTransformWithListeners(globalToViewer);

		return new ViewerAndTransforms(viewer, manager, displayTransform, globalToViewerTransform);
	}

}
