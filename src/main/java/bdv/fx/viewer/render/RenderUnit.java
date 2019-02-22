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
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Render screen as tiles instead of single image.
 */
public class RenderUnit implements PainterThread.Paintable {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final long[] dimensions = {1, 1};

	private final int[] padding = {0, 0};

	private double[] screenScales = ScreenScalesConfig.defaultScreenScalesCopy();

	private MultiResolutionRendererFX renderer;

	private final ObjectProperty<RenderedImage> renderedImage = new SimpleObjectProperty<>();

	private PainterThread painterThread;

	private TransformAwareBufferedImageOverlayRendererFX renderTarget;

	private final ThreadGroup threadGroup;

	private final Supplier<ViewerState> viewerState;

	private final Function<Source<?>, AxisOrder> axisOrder;

	private final Function<Source<?>, Interpolation> interpolation;

	private final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;

	private final CacheControl cacheControl;

	private final long targetRenderNanos;

	private final int numRenderingThreads;

	private final ExecutorService renderingExecutorService;

	private final List<Runnable> updateListeners = new ArrayList<>();

	private final IntSupplier renderedImageTagSupplier;

	public RenderUnit(
			final ThreadGroup threadGroup,
			final Supplier<ViewerState> viewerState,
			final Function<Source<?>, AxisOrder> axisOrder,
			final Function<Source<?>, Interpolation> interpolation,
			final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory,
			final CacheControl cacheControl,
			final long targetRenderNanos,
			final int numRenderingThreads,
			final ExecutorService renderingExecutorService,
			final IntSupplier renderedImageTagSupplier) {
		this.threadGroup = threadGroup;
		this.viewerState = viewerState;
		this.axisOrder = axisOrder;
		this.interpolation = interpolation;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		this.targetRenderNanos = targetRenderNanos;
		this.numRenderingThreads = numRenderingThreads;
		this.renderingExecutorService = renderingExecutorService;
		this.renderedImageTagSupplier = renderedImageTagSupplier;
		update();
	}

	/**
	 * Set size of total screen to be rendered
	 *
	 * @param dimX width of screen
	 * @param dimY height of screen
	 */
	public void setDimensions(final long dimX, final long dimY)
	{
		dimensions[0] = Math.max(dimX, 0);
		dimensions[1] = Math.max(dimY, 0);
		update();
	}

	/**
	 * Request repaint of all tiles
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 */
	public synchronized void requestRepaint(final int screenScaleIndex)
	{
		renderer.requestRepaint(new FinalInterval(dimensions), screenScaleIndex);
	}

	/**
	 * Request repaint of all at highest possible resolution
	 */
	public synchronized void requestRepaint()
	{
		renderer.requestRepaint(new FinalInterval(dimensions));
	}

	/**
	 * Request repaint of all tiles within specified interval
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 * @param min top left corner of interval
	 * @param max bottom right corner of interval
	 */
	public synchronized void requestRepaint(final int screenScaleIndex, final long[] min, final long[] max)
	{
		renderer.requestRepaint(new FinalInterval(min, max), screenScaleIndex);
	}

	/**
	 * Request repaint of all tiles within specified interval at highest possible resolution
	 *
	 * @param min top left corner of interval
	 * @param max bottom right corner of interval
	 */
	public synchronized void requestRepaint(final long[] min, final long[] max)
	{
		renderer.requestRepaint(new FinalInterval(min, max));
	}

	/**
	 * set the screen-scales used for rendering
	 *
	 * @param screenScales subject to following constraints:
	 *                     1. {@code 0 < sceenScales[i] <= 1} for all {@code i}
	 *                     2. {@code screenScales[i] < screenScales[i - 1]} for all {@code i > 0}
	 */
	public synchronized void setScreenScales(final double[] screenScales)
	{
		this.screenScales = screenScales.clone();
		if (renderer != null)
			renderer.setScreenScales(this.screenScales);
	}

	private synchronized void update()
	{
		LOG.debug("Updating render unit");

		if (painterThread != null) {
			painterThread.stopRendering();
			painterThread.interrupt();
		}

		renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
		renderTarget.setCanvasSize((int) dimensions[0], (int) dimensions[1]);

		painterThread = new PainterThread(threadGroup, "painter-thread", this);
		painterThread.setDaemon(true);
		painterThread.start();

		renderer = new MultiResolutionRendererFX(
				renderTarget,
				painterThread,
				screenScales,
				targetRenderNanos,
				true,
				numRenderingThreads,
				renderingExecutorService,
				true,
				accumulateProjectorFactory,
				cacheControl,
				padding
		);

		notifyUpdated();
	}

	public synchronized ReadOnlyObjectProperty<RenderedImage> getRenderedImageProperty()
	{
		return renderedImage;
	}

	public synchronized long[] getDimensions()
	{
		return dimensions;
	}

	public synchronized int[] getPadding()
	{
		return padding;
	}

	@Override
	public void paint(Interval interval)
	{
		final List<SourceAndConverter<?>> sacs = new ArrayList<>();
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final int timepoint;
		final int renderedImageTag;
		synchronized (RenderUnit.this)
		{
			if (renderer != null && renderedImage != null && renderTarget != null)
			{
				final ViewerState viewerState = RenderUnit.this.viewerState.get();
				synchronized (viewerState)
				{
					viewerState.getViewerTransform(viewerTransform);
					timepoint = viewerState.timepointProperty().get();
					renderedImageTag = renderedImageTagSupplier != null ? renderedImageTagSupplier.getAsInt() : -1;
					sacs.addAll(viewerState.getSources());
				}
			}
			else
			{
				return;
			}
		}

//		viewerTransform.translate(-interval.min(0), -interval.min(1), 0);

		final int renderedScreenScaleIndex = renderer.paint(
			sacs,
			axisOrder,
			timepoint,
			viewerTransform,
			interpolation,
			interval,
			null
		);

		if (renderedScreenScaleIndex != -1)
		{
			final Interval renderedInterval = renderer.getLastRenderedInterval();
			final Interval renderedScaledInterval = renderer.getLastRenderedScaledInterval();

			renderTarget.drawOverlays(img -> renderedImage.set(new RenderedImage(
				img, 
				renderedInterval, 
				renderedScaledInterval, 
				renderedImageTag, 
				renderedScreenScaleIndex
			)));
		}
	}

	/**
	 * Add listener to updates of {@link RenderUnit}, specifically on calls to {@link RenderUnit#setDimensions(long, long)} and
	 * {@link RenderUnit#setBlockSize(int, int)}
	 *
	 * @param listener {@link Runnable#run()} is called on udpates and when listener is added.
	 */
	public void addUpdateListener(final Runnable listener)
	{
		this.updateListeners.add(listener);
		listener.run();
	}

	private void notifyUpdated()
	{
		this.updateListeners.forEach(Runnable::run);
	}

	/**
	 * Utility class to represent rendering results that contains a rendered image, an assigned tag, and an index of the screen scale used for rendering.
	 */
	public static class RenderedImage
	{
		private final Image image;
		private final Interval interval, scaledInterval;
		private final int tag;
		private final int screenScaleIndex;

		public RenderedImage(final Image image, final Interval interval, final Interval scaledInterval, final int tag, final int screenScaleIndex) {
			this.image = image;
			this.interval = interval;
			this.scaledInterval = scaledInterval;
			this.tag = tag;
			this.screenScaleIndex = screenScaleIndex;
		}

		public Image getImage() {
			return image;
		}

		public Interval getInterval() {
			return interval;
		}

		public Interval getScaledInterval() {
			return scaledInterval;
		}

		public int getTag() {
			return tag;
		}

		public int getScreenScaleIndex() {
			return screenScaleIndex;
		}
	}
}
