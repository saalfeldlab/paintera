package bdv.fx.viewer.render;

import bdv.cache.CacheControl;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjectorFactory;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages rendering of arbitrary intervals of the screen.
 */
public class RenderUnit implements PainterThread.Paintable {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final long[] dimensions = {0, 0};

	private final ObjectProperty<double[]> screenScalesProperty = new SimpleObjectProperty<>(ScreenScalesConfig.defaultScreenScalesCopy());

	protected MultiResolutionRendererFX renderer;

	protected final ObjectProperty<RenderResult> renderResultProperty = new SimpleObjectProperty<>();

	private PainterThread painterThread;

	protected TransformAwareBufferedImageOverlayRendererFX renderTarget;

	private final ThreadGroup threadGroup;

	protected final Supplier<ViewerState> viewerStateSupplier;

	protected final Function<Source<?>, Interpolation> interpolation;

	private final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;

	private final CacheControl cacheControl;

	private final long targetRenderNanos;

	private final TaskExecutor renderingTaskExecutor;

	private final List<Runnable> updateListeners = new ArrayList<>();

	public RenderUnit(
			final ThreadGroup threadGroup,
			final Supplier<ViewerState> viewerStateSupplier,
			final Function<Source<?>, Interpolation> interpolation,
			final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory,
			final CacheControl cacheControl,
			final long targetRenderNanos,
			final TaskExecutor renderingTaskExecutor) {

		this.threadGroup = threadGroup;
		this.viewerStateSupplier = viewerStateSupplier;
		this.interpolation = interpolation;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		this.targetRenderNanos = targetRenderNanos;
		this.renderingTaskExecutor = renderingTaskExecutor;
	}

	/**
	 * Set size of total screen to be rendered
	 *
	 * @param dimX width of screen
	 * @param dimY height of screen
	 */
	public void setDimensions(final long dimX, final long dimY) {

		dimensions[0] = Math.max(dimX, 0);
		dimensions[1] = Math.max(dimY, 0);
		update();
	}

	/**
	 * Request repaint of the whole screen
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 */
	public synchronized void requestRepaint(final int screenScaleIndex) {

		if (renderer == null) return;
		renderer.requestRepaint(new FinalInterval(dimensions), screenScaleIndex);
	}

	/**
	 * Request repaint of the whole screen at highest possible resolution
	 */
	public synchronized void requestRepaint() {
		if (renderer == null) return;
		renderer.requestRepaint(new FinalInterval(dimensions));
	}

	/**
	 * Request repaint of specified interval
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 * @param min              top left corner of interval
	 * @param max              bottom right corner of interval
	 */
	public synchronized void requestRepaint(final int screenScaleIndex, final long[] min, final long[] max) {

		if (renderer == null) return;
		renderer.requestRepaint(clampRepaintInterval(new FinalInterval(min, max)), screenScaleIndex);
	}

	/**
	 * Request repaint of specified interval at highest possible resolution
	 *
	 * @param min top left corner of interval
	 * @param max bottom right corner of interval
	 */
	public synchronized void requestRepaint(final long[] min, final long[] max) {

		if (renderer == null) return;
		renderer.requestRepaint(clampRepaintInterval(new FinalInterval(min, max)));
	}

	private Interval clampRepaintInterval(final Interval interval) {

		return Intervals.intersect(interval, new FinalInterval(dimensions));
	}

	/**
	 * set the screen-scales used for rendering
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public synchronized void setScreenScales(final double[] screenScales) {

		this.screenScalesProperty.set(screenScales.clone());
		if (renderer != null)
			renderer.setScreenScales(screenScales);
	}

	private synchronized void update() {

		LOG.debug("Updating render unit");

		renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
		renderTarget.setCanvasSize((int) dimensions[0], (int) dimensions[1]);

		if (painterThread == null || !painterThread.isAlive()) {
			painterThread = new PainterThread(threadGroup, "painter-thread", this);
			painterThread.start();
		}

		renderer = new MultiResolutionRendererFX(
				renderTarget,
				painterThread,
				screenScalesProperty.get(),
				targetRenderNanos,
				true,
				renderingTaskExecutor,
				true,
				accumulateProjectorFactory,
				cacheControl
		);

		notifyUpdated();
	}

	public synchronized ReadOnlyObjectProperty<RenderResult> getRenderedImageProperty() {

		return renderResultProperty;
	}

	public synchronized long[] getDimensions() {

		return dimensions;
	}

	public synchronized ObjectProperty<double[]> getScreenScalesProperty() {

		return screenScalesProperty;
	}

	/**
	 * Set {@code screenScaleTransform} to a screen scale transform at a given {@code screenScaleIndex}.
	 *
	 * @param screenScaleIndex
	 * @param screenScaleTransform
	 */
	public synchronized void getScreenScaleTransform(final int screenScaleIndex, final AffineTransform3D screenScaleTransform) {

		renderer.getScreenScaleTransform(screenScaleIndex, screenScaleTransform);
	}

	private AffineTransform3D viewerTransform = new AffineTransform3D();

	@Override
	public void paint() {

		final List<SourceAndConverter<?>> sacs = new ArrayList<>();
		final int timepoint;
		synchronized (RenderUnit.this) {
			final ViewerState viewerState = this.viewerStateSupplier.get();
			if (renderer != null && renderTarget != null && viewerState != null && viewerState.isVisible()) {
				synchronized (viewerState) {
					viewerState.getViewerTransform(viewerTransform);
					timepoint = viewerState.getTimepoint();
					sacs.addAll(viewerState.getSources());
				}
			} else {
				return;
			}
		}

		final int renderedScreenScaleIndex = renderer.paint(
				sacs,
				timepoint,
				viewerTransform,
				interpolation,
				null
		);

		if (renderedScreenScaleIndex != -1) {
			final Interval screenInterval = renderer.getLastRenderedScreenInterval();
			final RealInterval renderTargetRealInterval = renderer.getLastRenderTargetRealInterval();

			renderTarget.drawOverlays(img -> renderResultProperty.set(new RenderResult(
					img,
					screenInterval,
					renderTargetRealInterval,
					renderedScreenScaleIndex
			)));
		}
	}

	/**
	 * Add listener to updates of {@link RenderUnit}, specifically on calls to {@link RenderUnit#setDimensions(long, long)} and}
	 *
	 * @param listener {@link Runnable#run()} is called on udpates and when listener is added.
	 */
	public void addUpdateListener(final Runnable listener) {

		this.updateListeners.add(listener);
		listener.run();
	}

	private void notifyUpdated() {

		this.updateListeners.forEach(Runnable::run);
	}

	/**
	 * Utility class for representing render results.
	 */
	public static class RenderResult {

		private final Image image;
		private final Interval screenInterval;
		private final RealInterval renderTargetRealInterval;
		private final int screenScaleIndex;

		public RenderResult(
				final Image image,
				final Interval screenInterval,
				final RealInterval renderTargetRealInterval,
				final int screenScaleIndex) {

			this.image = image;
			this.screenInterval = screenInterval;
			this.renderTargetRealInterval = renderTargetRealInterval;
			this.screenScaleIndex = screenScaleIndex;
		}

		public Image getImage() {

			return image;
		}

		public Interval getScreenInterval() {

			return screenInterval;
		}

		public RealInterval getRenderTargetRealInterval() {

			return renderTargetRealInterval;
		}

		public int getScreenScaleIndex() {

			return screenScaleIndex;
		}
	}
}
