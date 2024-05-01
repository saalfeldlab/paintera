package bdv.fx.viewer.render;

import bdv.cache.CacheControl;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.PainterThread;
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

public abstract class RenderUnit implements PainterThreadFx.Paintable {
	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	protected final ObjectProperty<RenderResult> renderResultProperty = new SimpleObjectProperty<>();
	private final ObjectProperty<double[]> screenScalesProperty = new SimpleObjectProperty<>(ScreenScalesConfig.defaultScreenScalesCopy());
	protected final ThreadGroup threadGroup;
	protected final Function<Source<?>, Interpolation> interpolation;
	protected final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;
	protected final CacheControl cacheControl;
	protected final long targetRenderNanos;
	protected final TaskExecutor renderingTaskExecutor;
	private final long[] dimensions = {0, 0};
	private final List<Runnable> updateListeners = new ArrayList<>();
	protected MultiResolutionRendererFX renderer;
	protected TransformAwareBufferedImageOverlayRendererFX renderTarget;
	protected PainterThreadFx painterThread;

	public RenderUnit(final ThreadGroup threadGroup, final Function<Source<?>, Interpolation> interpolation, final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory, final CacheControl cacheControl, final long targetRenderNanos, final TaskExecutor renderingTaskExecutor) {

		this.threadGroup = threadGroup;
		this.interpolation = interpolation;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		this.targetRenderNanos = targetRenderNanos;
		this.renderingTaskExecutor = renderingTaskExecutor;
	}

	public void stopRendering() {
		var render = renderer;
		if (render != null)
			render.animation.stop();
		painterThread.stopRendering();
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

	/**
	 * Request repaint of the whole screen
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 */
	public synchronized void requestRepaint(final int screenScaleIndex) {

		if (renderer == null)
			return;
		renderer.requestRepaint(new FinalInterval(dimensions), screenScaleIndex);
	}

	/**
	 * Request repaint of the whole screen at highest possible resolution
	 */
	public synchronized void requestRepaint() {

		if (renderer == null)
			return;
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

		if (renderer == null)
			return;
		renderer.requestRepaint(clampRepaintInterval(new FinalInterval(min, max)), screenScaleIndex);
	}

	/**
	 * Request repaint of specified interval at highest possible resolution
	 *
	 * @param min top left corner of interval
	 * @param max bottom right corner of interval
	 */
	public synchronized void requestRepaint(final long[] min, final long[] max) {

		if (renderer == null)
			return;
		renderer.requestRepaint(clampRepaintInterval(new FinalInterval(min, max)));
	}

	private Interval clampRepaintInterval(final Interval interval) {

		return Intervals.intersect(interval, new FinalInterval(dimensions));
	}

	protected synchronized void update() {

		LOG.debug("Updating render unit");

		renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
		renderTarget.setCanvasSize((int)dimensions[0], (int)dimensions[1]);

		if (painterThread == null || !painterThread.isAlive()) {
			painterThread = new PainterThreadFx(threadGroup, "painter-thread", this);
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

	/**
	 * Add listener to updates of {@link RenderUnit}, specifically on calls to {@link RenderUnit#setDimensions(long, long)} and}
	 *
	 * @param listener {@link Runnable#run()} is called on updates and when listener is added.
	 */
	public void addUpdateListener(final Runnable listener) {

		this.updateListeners.add(listener);
		listener.run();
	}

	protected void notifyUpdated() {

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
