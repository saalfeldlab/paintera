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
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;

/**
 * Render screen as tiles instead of single image.
 */
public class RenderUnit {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int NUM_RENDERING_THREADS = 1;

	private final int[] blockSize = {250, 250};

	private final long[] dimensions = {1, 1};

	private final int[] padding = {1, 1};

	private double[] screenScales = ScreenScalesConfig.defaultScreenScalesCopy();

	private CellGrid grid;

	private MultiResolutionRendererFX[] renderers = new MultiResolutionRendererFX[0];

	@SuppressWarnings("unchecked")
	private ObjectProperty<Image>[] images = new ObjectProperty[0];

	private PainterThread[] painterThreads = new PainterThread[0];

	private TransformAwareBufferedImageOverlayRendererFX[] renderTargets = new TransformAwareBufferedImageOverlayRendererFX[0];

	private final ThreadGroup threadGroup;

	private final Supplier<ViewerState> viewerState;

	private final Function<Source<?>, AxisOrder> axisOrder;

	private final Function<Source<?>, Interpolation> interpolation;

	private final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory;

	private final CacheControl cacheControl;

	private final long targetRenderNanos;

	private final ExecutorService renderingExecutorService;

	private final List<Runnable> updateListeners = new ArrayList<>();

	private long[][] offsets = new long[0][];

	public RenderUnit(
			final ThreadGroup threadGroup,
			final Supplier<ViewerState> viewerState,
			final Function<Source<?>, AxisOrder> axisOrder,
			final Function<Source<?>, Interpolation> interpolation,
			final AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory,
			final CacheControl cacheControl,
			final long targetRenderNanos,
			final ExecutorService renderingExecutorService) {
		this.threadGroup = threadGroup;
		this.viewerState = viewerState;
		this.axisOrder = axisOrder;
		this.interpolation = interpolation;
		this.accumulateProjectorFactory = accumulateProjectorFactory;
		this.cacheControl = cacheControl;
		this.targetRenderNanos = targetRenderNanos;
		this.renderingExecutorService = renderingExecutorService;
		update();
	}

	/**
	 * Set size of tiles to be rendered
	 *
	 * @param blockX width of tiles
	 * @param blockY height of tiles
	 */
	public void setBlockSize(final int blockX, final int blockY)
	{
		blockSize[0] = Math.max(blockX, 1);
		blockSize[1] = Math.max(blockY, 1);
		update();
	}

	/**
	 * Set size of total screen to be rendered
	 *
	 * @param dimX width of screen
	 * @param dimY heighto f screen
	 */
	public void setDimensions(final long dimX, final long dimY)
	{
		dimensions[0] = Math.max(dimX, 0);
		dimensions[1] = Math.max(dimY, 0);
		update();
	}

	/**
	 * Request repaint of a single tile
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 * @param tileIndex linear index of tile as defined by {@link CellGrid} that was constructed with block size and dimensions as
	 *                  specified via {@link #setBlockSize(int, int)} and {@link #setDimensions(long, long)}, respectively.
	 */
	public synchronized void requestRepaintSingleTile(final int screenScaleIndex, final int tileIndex)
	{
		renderers[tileIndex].requestRepaint(screenScaleIndex);
	}


	/**
	 * Request repaint of a single tile at highest possible resolution
	 *
	 * @param tileIndex linear index of tile as defined by {@link CellGrid} that was constructed with block size and dimensions as
	 *                  specified via {@link #setBlockSize(int, int)} and {@link #setDimensions(long, long)}, respectively.
	 */
	public synchronized void requestRepaintSingleTile(final int tileIndex)
	{
		renderers[tileIndex].requestRepaint();
	}

	/**
	 * Request repaint of a list of tiles
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 * @param tileIndices list of linear indices of tiles as defined by {@link CellGrid} that was constructed with block size and
	 *                    dimensions as specified via {@link #setBlockSize(int, int)} and {@link #setDimensions(long, long)}, respectively.
	 */
	public synchronized void requestRepaint(final int screenScaleIndex, final int[] tileIndices)
	{
		for (final int b : tileIndices)
			renderers[b].requestRepaint(screenScaleIndex);
	}


	/**
	 * Request repaint of a list of tiles at highest possible resolution
	 *
	 * @param tileIndices list of linear indices of tiles as defined by {@link CellGrid} that was constructed with block size and
	 *                    dimensions as specified via {@link #setBlockSize(int, int)} and {@link #setDimensions(long, long)}, respectively.
	 */
	public synchronized void requestRepaint(final int[] tileIndices)
	{
		for (final int b : tileIndices)
			renderers[b].requestRepaint();
	}

	/**
	 * Request repaint of all tiles
	 *
	 * @param screenScaleIndex request repaint at this target scale
	 */
	public synchronized void requestRepaint(final int screenScaleIndex)
	{
		for (int b = 0; b < renderers.length; ++b)
			renderers[b].requestRepaint(screenScaleIndex);
	}

	/**
	 * Request repaint of all at highest possible resolution
	 */
	public synchronized void requestRepaint()
	{
		for (int b = 0; b < renderers.length; ++b)
			renderers[b].requestRepaint();
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

		long[] relevantBlocks = org.janelia.saalfeldlab.util.grids.Grids.getIntersectingBlocks(min, max, this.grid);
		for (final long b : relevantBlocks)
			renderers[(int)b].requestRepaint(screenScaleIndex);
	}

	/**
	 * Request repaint of all tiles within specified interval at highest possible resolution
	 *
	 * @param min top left corner of interval
	 * @param max bottom right corner of interval
	 */
	public synchronized void requestRepaint(final long[] min, final long[] max)
	{

		long[] relevantBlocks = org.janelia.saalfeldlab.util.grids.Grids.getIntersectingBlocks(min, max, this.grid);
		for (final long b : relevantBlocks)
			renderers[(int)b].requestRepaint();
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
		for (int index = 0; index < renderers.length; ++index)
			if (renderers[index] != null)
				renderers[index].setScreenScales(this.screenScales);
	}

	private synchronized void update()
	{

		for (PainterThread p : painterThreads) {
			if (p == null)
				continue;
			p.stopRendering();
			p.interrupt();
		}

		// Adjust the dimensions to be a greater or equal multiple of the block size
		// to make sure that the border images have the same scaling coefficients
		final long[] adjustedDimensions = new long[dimensions.length];
		Arrays.setAll(adjustedDimensions, d -> dimensions[d] > blockSize[d] ? (int) Math.ceil((double) dimensions[d] / blockSize[d]) * blockSize[d] : dimensions[d]);

		this.grid = new CellGrid(adjustedDimensions, blockSize);

		int numBlocks = (int) LongStream.of(this.grid.getGridDimensions()).reduce(1, (l1, l2) -> l1 * l2);
		renderers = new MultiResolutionRendererFX[numBlocks];
		images = new ObjectProperty[numBlocks];
		renderTargets = new TransformAwareBufferedImageOverlayRendererFX[numBlocks];
		painterThreads = new PainterThread[numBlocks];
		offsets = new long[numBlocks][];
		LOG.debug("Updating render unit");
		final long[] cellPos = new long[2];
		final long[] min = new long[2];
		final long[] max = new long[2];
		final int[] cellDims = new int[2];
		for (int index = 0; index < renderers.length; ++index) {
			this.grid.getCellGridPositionFlat(index, cellPos);
			this.grid.getCellDimensions(cellPos, min, cellDims);
			Arrays.setAll(max, d -> min[d] + cellDims[d] - 1);
			final TransformAwareBufferedImageOverlayRendererFX renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
			final PainterThread.Paintable paintable = new Paintable(index);
			final PainterThread painterThread = new PainterThread(threadGroup, "painter-thread-" + index, paintable);
			final MultiResolutionRendererFX renderer = new MultiResolutionRendererFX(
					renderTarget,
					painterThread,
					this.screenScales,
					targetRenderNanos,
					true,
					NUM_RENDERING_THREADS,
					renderingExecutorService,
					true,
					accumulateProjectorFactory,
					cacheControl,
					padding
			);
			LOG.trace("Creating new renderer for block ({}) ({})", min, max);
			renderTarget.setCanvasSize(cellDims[0], cellDims[1]);
			renderers[index] = renderer;
			images[index] = new SimpleObjectProperty<>(null);
			renderTargets[index] = renderTarget;
			painterThreads[index] = painterThread;
			offsets[index] = min.clone();
			painterThread.setDaemon(true);
			painterThread.start();
		}
		notifyUpdated();
	}

	public synchronized ImagePropertyGrid getImagePropertyGrid()
	{
		return new ImagePropertyGrid(images, grid, dimensions, padding);
	}

	private class Paintable implements PainterThread.Paintable
	{

		final int index;

		private Paintable(int index) {
			this.index = index;
		}

		@Override
		public void paint() {
			MultiResolutionRendererFX renderer;
			ObjectProperty<Image> image;
			TransformAwareBufferedImageOverlayRendererFX renderTarget;
			long[] offset;
			ViewerState viewerState = null;
			final List<SourceAndConverter<?>> sacs = new ArrayList<>();
			synchronized (RenderUnit.this)
			{
				renderer = index < renderers.length ? renderers[index] : null;
				image = index < images.length ? images[index] : null;
				renderTarget = index < renderTargets.length ? renderTargets[index] : null;
				offset = index < offsets.length ? offsets[index] : null;
				if (renderer != null && image != null && renderTarget != null && offset != null) {
					viewerState = RenderUnit.this.viewerState.get().copy();
					sacs.addAll(viewerState.getSources());
				}
			}
			if (renderer == null || image == null || renderTarget == null || offset == null)
				return;

			final AffineTransform3D viewerTransform = new AffineTransform3D();
			viewerState.getViewerTransform(viewerTransform);
			viewerTransform.translate(-offset[0], -offset[1], 0);

			renderer.paint(
					sacs,
					axisOrder,
					viewerState.timepointProperty().get(),
					viewerTransform,
					interpolation,
					null
			);

			renderTarget.drawOverlays(image::set);
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
	 * Utility class to access images via linear index.
	 */
	public static class ImagePropertyGrid
	{
		private final ObjectProperty<Image>[] images;

		private final CellGrid grid;

		private long[] dimensions;

		private final int[] padding;

		private ImagePropertyGrid(final ObjectProperty<Image>[] images, final CellGrid grid, final long[] dimensions, final int[] padding) {
			this.images = images;
			this.grid = grid;
			this.dimensions = dimensions;
			this.padding = padding;
		}

		/**
		 *
		 * @return underlying {@link CellGrid}
		 */
		public CellGrid getGrid()
		{
			return this.grid;
		}

		/**
		 *
		 * @param linearGridIndex linear index wrt to {@link #getGrid()}.
		 * @return {@link ReadOnlyObjectProperty} at {@code linearGridIndex}
		 */
		public ReadOnlyObjectProperty<Image> imagePropertyAt(final int linearGridIndex)
		{
			return images[linearGridIndex];
		}

		/**
		 *
		 * @return number of tiles
		 */
		public int numTiles()
		{
			return images.length;
		}

		/**
		 *
		 * @return dimensions of the displayed image (may be smaller than the grid size)
		 */
		public long[] getDimensions()
		{
			return dimensions;
		}

		/**
		 *
		 * @return padding value by which the rendered images are extended on each side
		 */
		public int[] getPadding()
		{
			return padding;
		}
	}

}
