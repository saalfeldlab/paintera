package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import bdv.util.volatiles.SharedQueue;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.util.LoaderCacheAsCacheAdapter;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.NativeImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetEntryList;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LongMappedAccessData;
import net.imglib2.type.label.N5CacheLoader;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.util.ValueTriple;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.bdv.img.cache.VolatileCachedCellImg;
import tmp.net.imglib2.cache.ref.WeakRefVolatileCache;

public class VolatileHelpers
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>, A> Pair<VolatileCachedCellImg<V,
			A>, VolatileCache<Long, Cell<A>>> createVolatileCachedCellImg(
			final CachedCellImg<T, A> cachedImg,
			final Function<NativeImg<V, ? extends A>, V> typeFactory,
			final CreateInvalid<Long, Cell<A>> createInvalid,
			final SharedQueue queue,
			final CacheHints hints)
	{
		return createVolatileCachedCellImg(
				cachedImg.getCellGrid(),
				cachedImg.createLinkedType().getEntitiesPerPixel(),
				typeFactory,
				cachedImg.getCache(),
				createInvalid,
				queue,
				hints
		                                  );
	}

	public static <T extends NativeType<T>, A> Pair<VolatileCachedCellImg<T, A>, VolatileCache<Long, Cell<A>>>
	createVolatileCachedCellImg(
			final CellGrid grid,
			final Fraction entitiesPerPixel,
			final Function<NativeImg<T, ? extends A>, T> typeFactory,
			final Cache<Long, Cell<A>> cache,
			final CreateInvalid<Long, Cell<A>> createInvalid,
			final SharedQueue queue,
			final CacheHints hints)
	{
		final VolatileCache<Long, Cell<A>> volatileCache = new WeakRefVolatileCache<>(cache, queue, createInvalid);
		final VolatileCachedCellImg<T, A>  volatileImg   = new VolatileCachedCellImg<>(
				grid,
				entitiesPerPixel,
				typeFactory,
				hints,
				volatileCache.unchecked()::get,
				volatileCache::invalidateAll
		);
		return new ValuePair<>(volatileImg, volatileCache);
	}

	public static class CreateInvalidVolatileLabelMultisetArray
			implements CreateInvalid<Long, Cell<VolatileLabelMultisetArray>>
	{

		private final CellGrid grid;

		public CreateInvalidVolatileLabelMultisetArray(final CellGrid grid)
		{
			super();
			this.grid = grid;
		}

		@Override
		public Cell<VolatileLabelMultisetArray> createInvalid(final Long key) throws Exception
		{
			final long[] cellPosition = new long[grid.numDimensions()];
			grid.getCellGridPositionFlat(key, cellPosition);
			final long[] cellMin  = new long[cellPosition.length];
			final int[]  cellDims = new int[cellPosition.length];
			grid.getCellDimensions(cellPosition, cellMin, cellDims);

			final LabelMultisetEntry e           = new LabelMultisetEntry(Label.INVALID, 1);
			final int                numEntities = (int) Intervals.numElements(cellDims);

			final LongMappedAccessData   listData = LongMappedAccessData.factory.createStorage(32);
			final LabelMultisetEntryList list     = new LabelMultisetEntryList(listData, 0);
			list.createListAt(listData, 0);
			list.add(e);
			final int[]                      data  = new int[numEntities];
			final VolatileLabelMultisetArray array = new VolatileLabelMultisetArray(
					data,
					listData,
					false,
					new long[] {Label.INVALID}
			);
			return new Cell<>(cellDims, cellMin, array);
		}

	}

	@SuppressWarnings("unchecked")
	public static ValueTriple<RandomAccessibleInterval<LabelMultisetType>[],
			RandomAccessibleInterval<VolatileLabelMultisetType>[], AffineTransform3D[]> loadMultiscaleMultisets(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(reader, dataset);

		LOG.debug("Opening directories {} as multi-scale in {}: ", Arrays.toString(scaleDatasets), dataset);

		final RandomAccessibleInterval<LabelMultisetType>[]         raw               = new
				RandomAccessibleInterval[scaleDatasets.length];
		final RandomAccessibleInterval<VolatileLabelMultisetType>[] vraw              = new
				RandomAccessibleInterval[scaleDatasets.length];
		final AffineTransform3D[]                                   transforms        = new
				AffineTransform3D[scaleDatasets.length];
		final double[]                                              initialResolution = resolution;
		final double[] initialDonwsamplingFactors = Optional
				.ofNullable(reader.getAttribute(
						dataset + "/" + scaleDatasets[0],
						"downsamplingFactors",
						double[].class
				                               ))
				.orElse(new double[] {1, 1, 1});
		LOG.debug("Initial resolution={}", Arrays.toString(initialResolution));
		for (int scale = 0; scale < scaleDatasets.length; ++scale)
		{
			LOG.debug("Populating scale level {}", scale);
			final String scaleDataset = dataset + "/" + scaleDatasets[scale];

			final DatasetAttributes                                                 attrs        = reader
					.getDatasetAttributes(
					scaleDataset);
			final N5CacheLoader                                                     loader       = new N5CacheLoader(
					reader,
					scaleDataset,
					N5CacheLoader.constantNullReplacement( Label.BACKGROUND )
			);
			final SoftRefLoaderCache<Long, Cell<VolatileLabelMultisetArray>>        cache        = new
					SoftRefLoaderCache<>();
			final LoaderCacheAsCacheAdapter<Long, Cell<VolatileLabelMultisetArray>> wrappedCache = new
					LoaderCacheAsCacheAdapter<>(
					cache,
					loader
			);
			final CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray> cachedImg = new CachedCellImg<>(
					new CellGrid(attrs.getDimensions(), attrs.getBlockSize()),
					new LabelMultisetType(),
					wrappedCache,
					new VolatileLabelMultisetArray(0, true, new long[] {Label.INVALID})
			);
			raw[scale] = cachedImg;
			// TODO cannot use VolatileViews because VolatileTypeMatches
			// does not know LabelMultisetType
			//				vraw[ scale ] = VolatileViews.wrapAsVolatile( raw[ scale ], sharedQueue, new CacheHints(
			// LoadingStrategy.VOLATILE, priority, true ) );
			final Pair<VolatileCachedCellImg<VolatileLabelMultisetType, VolatileLabelMultisetArray>,
					VolatileCache<Long, Cell<VolatileLabelMultisetArray>>> volatileCachedImg = VolatileHelpers
					.createVolatileCachedCellImg(
					cachedImg,
					(Function<NativeImg<VolatileLabelMultisetType, ? extends VolatileLabelMultisetArray>,
							VolatileLabelMultisetType>) img -> new VolatileLabelMultisetType(
							(NativeImg<?, VolatileLabelMultisetArray>) img),
					new VolatileHelpers.CreateInvalidVolatileLabelMultisetArray(cachedImg.getCellGrid()),
					sharedQueue,
					new CacheHints(LoadingStrategy.VOLATILE, priority, false)
			                                                                                                                                                                                                               );
			vraw[scale] = volatileCachedImg.getA();

			final double[] downsamplingFactors = Optional
					.ofNullable(reader.getAttribute(scaleDataset, "downsamplingFactors", double[].class))
					.orElse(new double[] {1, 1, 1});
			LOG.debug("Read downsampling factors: {}", Arrays.toString(downsamplingFactors));

			final double[] scaledResolution = new double[downsamplingFactors.length];
			final double[] shift            = new double[downsamplingFactors.length];

			for (int d = 0; d < downsamplingFactors.length; ++d)
			{
				scaledResolution[d] = downsamplingFactors[d] * initialResolution[d];
				shift[d] = 0.5 / initialDonwsamplingFactors[d] - 0.5 / downsamplingFactors[d];
			}

			LOG.debug(
					"Downsampling factors={}, scaled resolution={}",
					Arrays.toString(downsamplingFactors),
					Arrays.toString(scaledResolution)
			         );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					scaledResolution[0], 0, 0, offset[0],
					0, scaledResolution[1], 0, offset[1],
					0, 0, scaledResolution[2], offset[2]
			             );
			transforms[scale] = transform.concatenate(new Translation3D(shift));
			LOG.debug("Populated scale level {}", scale);
		}
		return new ValueTriple<>(raw, vraw, transforms);
	}

}
