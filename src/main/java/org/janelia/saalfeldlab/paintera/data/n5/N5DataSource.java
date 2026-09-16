package org.janelia.saalfeldlab.paintera.data.n5;

import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.viewer.Interpolation;
import bdv.viewer.render.Prefetcher;
import net.imglib2.*;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.XyzView;
import org.janelia.saalfeldlab.paintera.data.SlicedRenderSource;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.util.n5.SpatialMapping;
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState;

import java.io.IOException;
import java.util.function.Function;

public class N5DataSource<D extends NativeType<D>, T extends Volatile<D> & NativeType<T>> extends RandomAccessibleIntervalDataSource<D, T> implements SlicedRenderSource {

	private final MetadataState metadataState;

	/* how many adjacent non-spatial slices to prefetch per axis in each direction */
	private static final int TIME_PREFETCH_DEPTH = 1;
	/* backstop so a high-dimensional source can't flood the fetch queue with adjacent slabs in one pass */
	private static final int MAX_PREFETCH_SLICES = 8;
	/* the SharedQueue has 50 priority levels (PainteraBaseView), so 49 is the least-urgent slot */
	private static final int MAX_QUEUE_PRIORITY = 49;

	public N5DataSource(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority) throws IOException {

		this(
				metadataState,
				name,
				queue,
				priority,
				interpolation(metadataState),
				interpolation(metadataState));
	}

	public N5DataSource(
			final MetadataState metadataState,
			final String name,
			final SharedQueue queue,
			final int priority,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation) {

		super(
				RandomAccessibleIntervalDataSource.asDataWithInvalidate(metadataState.<D, T>getData(queue, priority)),
				dataInterpolation,
				interpolation,
				name);

		this.metadataState = metadataState;
	}

	/** The view this source is presented through. */
	private XyzView xyzView() {

		return metadataState.getXyzView();
	}

	@Override public RandomAccessibleInterval<T> getSource(int t, int level) {

		return getSource(t, level, xyzView().spatialMapping());
	}

	/** The 3D view at {@code level} through {@code mapping} instead of this source's own view */
	public RandomAccessibleInterval<T> getSource(final int t, final int level, final SpatialMapping mapping) {

		return cropToLevel(mapping.toXyz(super.getSource(t, level)), level);
	}

	@Override public RandomAccessibleInterval<D> getDataSource(int t, int level) {

		return getDataSource(t, level, xyzView().spatialMapping());
	}

	/** The 3D data view at {@code level} through {@code mapping} instead of this source's own view */
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level, final SpatialMapping mapping) {

		return cropToLevel(mapping.toXyz(super.getDataSource(t, level)), level);
	}

	/** The cropped active XYZ interval over scale {@code level}, or null when the spatial dimensions are not cropped. */
	private Interval getCroppedInterval(final int level) {

		final Interval s0Interval = metadataState.getVirtualCrop();
		if (s0Interval == null)
			return null;
		if (level == 0)
			return s0Interval;

		final AffineTransform3D[] transforms = ((MultiScaleMetadataState)metadataState).getScaleTransforms();
		final AffineTransform3D s0Transform = transforms[0].copy();
		final AffineTransform3D levelTransform = transforms[level].inverse();
		final AffineTransform3D s0ToLevelTransform = s0Transform.concatenate(levelTransform);
		final FinalRealInterval cropAtLevel = s0ToLevelTransform.estimateBounds(s0Interval);
		return Intervals.smallestContainingInterval(cropAtLevel);
	}

	/** Narrow an XYZ view to the crop at {@code level}; both are XYZ, so this is a plain interval restriction. */
	private <A> RandomAccessibleInterval<A> cropToLevel(final RandomAccessibleInterval<A> s0Xyz, final int level) {

		final Interval crop = getCroppedInterval(level);
		return crop == null ? s0Xyz : Views.interval(s0Xyz, crop);
	}

	public MetadataState getMetadataState() {

		return metadataState;
	}

	@Override public boolean isSliced() {

		return xyzView().isSliced();
	}

	@Override public void setSliceCacheHints(final int level, final CacheHints hints) {

		final RandomAccessibleInterval<T> backing = super.getSource(0, level);
		if (backing instanceof VolatileCachedCellImg)
			((VolatileCachedCellImg<?, ?>)backing).setCacheHints(hints);
	}

	@Override public void prefetchSlice(
			final int level,
			final AffineTransform3D sourceToScreen,
			final Dimensions screenInterval,
			final Interpolation interpolation,
			final CacheHints prefetchHints) {

		final RandomAccessibleInterval<T> backing = super.getSource(0, level);
		if (!(backing instanceof VolatileCachedCellImg))
			return;
		final VolatileCachedCellImg<?, ?> cellImg = (VolatileCachedCellImg<?, ?>)backing;

		CacheHints hints = prefetchHints;
		if (hints == null) {
			final CacheHints defaultHints = cellImg.getDefaultCacheHints();
			hints = new CacheHints(LoadingStrategy.VOLATILE, defaultHints.getQueuePriority(), false);
		}

		/* the carried 3D grid is the spatial projection of the backing's cell grid */
		final CellGrid grid = getGrid(level);
		final int[] cellDimensions = new int[grid.numDimensions()];
		grid.cellDimensions(cellDimensions);
		final long[] dimensions = new long[grid.numDimensions()];
		grid.imgDimensions(dimensions);

		/* the current slice, in cell units */
		final CellGrid ndGrid = cellImg.getCellGrid();
		final Interval cellSlice = xyzView().blockInterval(ndGrid);

		/* the visible footprint at the current slice, at the prefetch priority */
		fetchSliceCells(cellImg, cellSlice, hints, sourceToScreen, cellDimensions, dimensions, screenInterval, interpolation);

		/* warm the same footprint at adjacent non-spatial (timepoint/channel) slices at decaying priority, so a scrub
		 * to the next slice isn't a cold load; the queue dedups and re-prioritises, so this never starves the frame */
		final long[] ndGridDimensions = ndGrid.getGridDimensions();
		final int basePriority = hints.getQueuePriority();
		int slabsPrefetched = 0;
		for (final int axis : xyzView().getNonSpatialAxes()) {
			for (int step = 1; step <= TIME_PREFETCH_DEPTH; ++step) {
				/* offset the CELL coordinate, so a multi-slice-per-block layout warms the adjacent block, not the same one */
				final CacheHints stepHints = new CacheHints(LoadingStrategy.VOLATILE, Math.min(basePriority + step, MAX_QUEUE_PRIORITY), false);
				for (int sign = -1; sign <= 1; sign += 2) {
					final long pos = cellSlice.min(axis) + (long)sign * step;
					if (pos < 0 || pos >= ndGridDimensions[axis])
						continue;
					if (slabsPrefetched++ >= MAX_PREFETCH_SLICES)
						return;
					final long[] adjacentMin = cellSlice.minAsLongArray();
					final long[] adjacentMax = cellSlice.maxAsLongArray();
					adjacentMin[axis] = pos;
					adjacentMax[axis] = pos;
					fetchSliceCells(cellImg, new FinalInterval(adjacentMin, adjacentMax), stepHints, sourceToScreen, cellDimensions, dimensions, screenInterval, interpolation);
				}
			}
		}
	}

	/** Prefetch the screen footprint at one non-spatial [cellSlice] (in cell units), enqueued at [hints]'s priority. */
	private void fetchSliceCells(
			final VolatileCachedCellImg<?, ?> cellImg,
			final Interval cellSlice,
			final CacheHints hints,
			final AffineTransform3D sourceToScreen,
			final int[] cellDimensions,
			final long[] dimensions,
			final Dimensions screenInterval,
			final Interpolation interpolation) {

		cellImg.setCacheHints(hints);
		/* slice the nD cells image to 3D at this slice's cell positions, so touching a 3D cell loads the right nD block;
		 * the mapping only reads the interval's min, so the spatial extent being level-0's does not matter here */
		final SpatialMapping cellMapping = xyzView().spatialMapping(cellSlice);
		@SuppressWarnings({"unchecked", "rawtypes"})
		final RandomAccess<?> cellsRandomAccess = cellMapping.toXyz((RandomAccessibleInterval)cellImg.getCells()).randomAccess();
		Prefetcher.fetchCells(sourceToScreen, cellDimensions, dimensions, screenInterval, interpolation, cellsRandomAccess);
	}

	static <T extends NativeType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	interpolation(MetadataState metadataState) {

		if (metadataState.isLabel() || metadataState.isLabelMultiset() )
			return 	i -> new NearestNeighborInterpolatorFactory<>();
		else
			return (Function)realTypeInterpolation();
	}

	private static <T extends RealType<T>> Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>>
	realTypeInterpolation() {

		return i -> i.equals(Interpolation.NLINEAR)
				? new ClampingNLinearInterpolatorFactory<>()
				: new NearestNeighborInterpolatorFactory<>();
	}
}
