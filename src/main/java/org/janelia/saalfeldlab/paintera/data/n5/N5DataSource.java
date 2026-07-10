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

	/** The mapping reducing the nD backing to a canonical 3D (x, y, z) view at the current slice positions; identity for a 3D source. */
	private SpatialMapping sliceMapping() {

		return new SpatialMapping(
				metadataState.getDatasetAttributes().getNumDimensions(),
				SpatialMapping.xyzSourceAxes(metadataState.getAxes()),
				metadataState.getSlicePositions());
	}

	@Override public RandomAccessibleInterval<T> getSource(int t, int level) {

		final SpatialMapping mapping = sliceMapping();
		final RandomAccessibleInterval<T> source = mapping.isIdentity() ? super.getSource(t, level) : mapping.to3D(super.getSource(t, level));
		final Interval virtualCrop = getVirtualCrop(t, level);
		return virtualCrop == null ? source : Views.interval(source, virtualCrop);
	}

	@Override public RandomAccessibleInterval<D> getDataSource(int t, int level) {

		final SpatialMapping mapping = sliceMapping();
		final RandomAccessibleInterval<D> source = mapping.isIdentity() ? super.getDataSource(t, level) : mapping.to3D(super.getDataSource(t, level));
		final Interval virtualCrop = getVirtualCrop(t, level);
		return virtualCrop == null ? source : Views.interval(source, virtualCrop);
	}

	private Interval getVirtualCrop(int t, int level) {

		var virtualCrop = metadataState.getVirtualCrop();
		if (virtualCrop == null) return null;
		else if (level == 0) return virtualCrop;

		MultiScaleMetadataState state = (MultiScaleMetadataState)metadataState;
		final AffineTransform3D[] transforms = state.getScaleTransforms();

		final FinalRealInterval realVirtualCropForLevel = transforms[0].copy().concatenate(transforms[level].inverse()).estimateBounds(virtualCrop);
		return Intervals.smallestContainingInterval(realVirtualCropForLevel);
	}

	public MetadataState getMetadataState() {

		return metadataState;
	}

	@Override public boolean isSliced() {

		return !sliceMapping().isIdentity();
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
		final int[] ndCellDimensions = new int[ndGrid.numDimensions()];
		ndGrid.cellDimensions(ndCellDimensions);
		final long[] voxelSlice = metadataState.getSlicePositions();
		final long[] cellSlice = new long[voxelSlice.length];
		for (int d = 0; d < cellSlice.length; ++d)
			cellSlice[d] = voxelSlice[d] / ndCellDimensions[d];

		/* the visible footprint at the current slice, at the prefetch priority */
		fetchSliceCells(cellImg, ndGrid, cellSlice, hints, sourceToScreen, cellDimensions, dimensions, screenInterval, interpolation);

		/* warm the same footprint at adjacent non-spatial (timepoint/channel) slices at decaying priority, so a scrub
		 * to the next slice isn't a cold load; the queue dedups and re-prioritises, so this never starves the frame */
		final java.util.List<Integer> nonSpatialAxes = SpatialMapping.nonSpatialAxes(metadataState.getAxes(), ndGrid.numDimensions());
		final long[] ndGridDimensions = ndGrid.getGridDimensions();
		final int basePriority = hints.getQueuePriority();
		int slabsPrefetched = 0;
		for (final int axis : nonSpatialAxes) {
			for (int step = 1; step <= TIME_PREFETCH_DEPTH; ++step) {
				/* offset the CELL coordinate, so a multi-slice-per-block layout warms the adjacent block, not the same one */
				final CacheHints stepHints = new CacheHints(LoadingStrategy.VOLATILE, Math.min(basePriority + step, MAX_QUEUE_PRIORITY), false);
				for (int sign = -1; sign <= 1; sign += 2) {
					final long pos = cellSlice[axis] + (long)sign * step;
					if (pos < 0 || pos >= ndGridDimensions[axis])
						continue;
					if (slabsPrefetched++ >= MAX_PREFETCH_SLICES)
						return;
					final long[] adjacent = cellSlice.clone();
					adjacent[axis] = pos;
					fetchSliceCells(cellImg, ndGrid, adjacent, stepHints, sourceToScreen, cellDimensions, dimensions, screenInterval, interpolation);
				}
			}
		}
	}

	/** Prefetch the screen footprint at one non-spatial [cellSlice] (in cell units), enqueued at [hints]'s priority. */
	private void fetchSliceCells(
			final VolatileCachedCellImg<?, ?> cellImg,
			final CellGrid ndGrid,
			final long[] cellSlice,
			final CacheHints hints,
			final AffineTransform3D sourceToScreen,
			final int[] cellDimensions,
			final long[] dimensions,
			final Dimensions screenInterval,
			final Interpolation interpolation) {

		cellImg.setCacheHints(hints);
		/* slice the nD cells image to 3D at this slice's cell positions, so touching a 3D cell loads the right nD block */
		final SpatialMapping cellMapping = new SpatialMapping(ndGrid.numDimensions(), SpatialMapping.xyzSourceAxes(metadataState.getAxes()), cellSlice);
		@SuppressWarnings({"unchecked", "rawtypes"})
		final RandomAccess<?> cellsRandomAccess = cellMapping.to3D((RandomAccessibleInterval)cellImg.getCells()).randomAccess();
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
