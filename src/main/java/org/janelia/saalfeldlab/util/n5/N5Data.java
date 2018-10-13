package org.janelia.saalfeldlab.util.n5;

import bdv.viewer.Interpolation;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultiset;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.N5CacheLoader;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5CellLoader;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.cache.global.InvalidAccessException;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.ui.opendialog.VolatileHelpers;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

public class N5Data {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return image data and cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openRaw(
				reader,
				dataset,
				N5Helpers.getResolution(reader, dataset),
				N5Helpers.getOffset(reader, dataset),
				globalCache,
				priority
		              );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T> & RealType<T>, V extends Volatile<T> & NativeType<V> & RealType<V>>
	DataSource<T, V> openRawAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException {
		return openScalarAsSource(
				reader,
				dataset,
				transform,
				globalCache,
				priority,
				i -> i == Interpolation.NLINEAR
				     ? new NLinearInterpolatorFactory<>()
				     : new NearestNeighborInterpolatorFactory<>(),
				i -> i == Interpolation.NLINEAR
				     ? new NLinearInterpolatorFactory<>()
				     : new NearestNeighborInterpolatorFactory<>(),
				name
		                         );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	DataSource<T, V> openScalarAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException {
		return openScalarAsSource(
				reader,
				dataset,
				transform,
				globalCache,
				priority,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name
		                         );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param dataInterpolation interpolator factory for data
	 * @param interpolation interpolator factory for viewer data
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	DataSource<T, V> openScalarAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<V, RandomAccessible<V>>> interpolation,
			final String name) throws IOException, ReflectionException {

		LOG.debug("Creating N5 Data source from {} {}", reader, dataset);
		return new N5DataSource<>(
				Objects.requireNonNull(N5Meta.fromReader(reader, dataset)),
				transform,
				globalCache,
				name,
				priority,
				dataInterpolation,
				interpolation
		);
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openScalar(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return N5Helpers.isMultiScale(reader, dataset)
		       ? openRawMultiscale(reader, dataset, transform, globalCache, priority)
		       : new ImagesWithInvalidate[] {openRaw(reader, dataset, transform, globalCache, priority)};

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openRaw(reader, dataset, transform, globalCache, priority);
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>, A extends ArrayDataAccess<A>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException {

		try {
			final CellGrid grid = N5Helpers.getGrid(reader, dataset);
			final CellLoader<T> loader = new N5CellLoader<>(reader, dataset, reader.getDatasetAttributes(dataset).getBlockSize());
			final T type = N5Types.type(reader.getDatasetAttributes(dataset).getDataType());
			final Pair<CachedCellImg<T, A>, Invalidate<Long>> raw = globalCache.createVolatileImg(grid, loader, type);
			final Triple<RandomAccessibleInterval<V>, VolatileCache<Long, Cell<A>>, Invalidate<Long>> vraw = globalCache.wrapAsVolatile(raw.getA(), raw.getB(), priority);
			return new ImagesWithInvalidate<>(raw.getA(), vraw.getA(), transform, raw.getB(), vraw.getC());
		}
		catch (Exception e)
		{
			throw e instanceof IOException ? (IOException) e : new IOException(e);
		}
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openRawMultiscale(
				reader,
				dataset,
				N5Helpers.getResolution(reader, dataset),
				N5Helpers.getOffset(reader, dataset),
				globalCache,
				priority
		                        );

	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel resolution
	 * @param offset offset in real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openRawMultiscale(reader, dataset, transform, globalCache, priority);
	}

	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(reader, dataset);

		LOG.debug("Opening directories {} as multi-scale in {}: ", Arrays.toString(scaleDatasets), dataset);

		final double[] initialDonwsamplingFactors = N5Helpers.getDownsamplingFactors(
				reader,
				Paths.get(dataset, scaleDatasets[0]).toString()
		                                                                  );
		LOG.debug("Initial transform={}", transform);
		final ExecutorService es = Executors.newFixedThreadPool(
				scaleDatasets.length,
				new NamedThreadFactory("populate-mipmap-scales-%d", true)
		                                                       );
		final ArrayList<Future<Boolean>> futures = new ArrayList<>();
		final ImagesWithInvalidate<T, V>[] imagesWithInvalidate = new ImagesWithInvalidate[scaleDatasets.length];
		for (int scale = 0; scale < scaleDatasets.length; ++scale)
		{
			final int fScale = scale;
			futures.add(es.submit(MakeUnchecked.supplier(() -> {
				LOG.debug("Populating scale level {}", fScale);
				final String scaleDataset = Paths.get(dataset, scaleDatasets[fScale]).toString();
				imagesWithInvalidate[fScale] = openRaw(reader, scaleDataset, transform.copy(), globalCache, priority);
				final double[] downsamplingFactors = N5Helpers.getDownsamplingFactors(reader, scaleDataset);
				LOG.debug("Read downsampling factors: {}", Arrays.toString(downsamplingFactors));
				imagesWithInvalidate[fScale].transform.set(N5Helpers.considerDownsampling(
						imagesWithInvalidate[fScale].transform.copy(),
						downsamplingFactors,
						initialDonwsamplingFactors));
				LOG.debug("Populated scale level {}", fScale);
				return true;
			})::get));
		}
		futures.forEach(MakeUnchecked.unchecked(Future::get));
		es.shutdown();
		return imagesWithInvalidate;
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static DataSource<LabelMultisetType, VolatileLabelMultisetType>
	openLabelMultisetAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException {
		return new N5DataSource<>(
				Objects.requireNonNull(N5Meta.fromReader(reader, dataset)),
				transform,
				globalCache,
				name,
				priority,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>()
		);
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openLabelMultiset(
				reader,
				dataset,
				N5Helpers.getResolution(reader, dataset),
				N5Helpers.getOffset(reader, dataset),
				globalCache,
				priority
		                        );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openLabelMultiset(reader, dataset, transform, globalCache, priority);
	}

	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		try {
			final DatasetAttributes attrs = reader.getDatasetAttributes(dataset);
			final N5CacheLoader loader = new N5CacheLoader(
					reader,
					dataset,
					N5CacheLoader.constantNullReplacement(Label.BACKGROUND)
			);
			final Pair<CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray>, Invalidate<Long>> cachedImg = globalCache.createImg(
					new CellGrid(attrs.getDimensions(), attrs.getBlockSize()),
					loader,
					new LabelMultisetType().getEntitiesPerPixel(),
					new VolatileLabelMultisetArray(0, true, new long[]{Label.INVALID})
			);
			cachedImg.getA().setLinkedType(new LabelMultisetType(cachedImg.getA()));


			@SuppressWarnings("unchecked")
			final Function<NativeImg<VolatileLabelMultisetType, ? extends VolatileLabelMultisetArray>, VolatileLabelMultisetType> linkedTypeFactory =
					img -> new VolatileLabelMultisetType((NativeImg<?, VolatileLabelMultisetArray>) img);

			Triple<RandomAccessibleInterval<VolatileLabelMultisetType>, VolatileCache<Long, Cell<VolatileLabelMultisetArray>>, Invalidate<Long>> vimg = globalCache.wrapAsVolatile(
					cachedImg.getA(),
					cachedImg.getB(),
					linkedTypeFactory,
					new VolatileHelpers.CreateInvalidVolatileLabelMultisetArray(cachedImg.getA().getCellGrid()),
					priority);

			return new ImagesWithInvalidate<>(cachedImg.getA(), vimg.getA(), transform, cachedImg.getB(), vimg.getC());
		}
		catch (InvalidAccessException e)
		{
			throw new IOException(e);
		}

	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openLabelMultisetMultiscale(
				reader,
				dataset,
				N5Helpers.getResolution(reader, dataset),
				N5Helpers.getOffset(reader, dataset),
				globalCache,
				priority
		                                  );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openLabelMultisetMultiscale(
				reader,
				dataset,
				transform,
				globalCache,
				priority
		                                  );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform from voxel space to world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(reader, dataset);

		LOG.debug(
				"Opening directories {} as multi-scale in {} and transform={}: ",
				Arrays.toString(scaleDatasets),
				dataset,
				transform
		         );

		final double[] initialDonwsamplingFactors = N5Helpers.getDownsamplingFactors(
				reader,
				Paths.get(dataset, scaleDatasets[0]).toString()
		                                                                  );
		final ExecutorService es = Executors.newFixedThreadPool(
				scaleDatasets.length,
				new NamedThreadFactory("populate-mipmap-scales-%d", true)
		                                                       );
		final ArrayList<Future<Boolean>> futures = new ArrayList<>();
		final ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] imagesWithInvalidate = new ImagesWithInvalidate[scaleDatasets.length];
		for (int scale = 0; scale < scaleDatasets.length; ++scale)
		{
			final int fScale = scale;
			futures.add(es.submit(MakeUnchecked.supplier(() -> {
				LOG.debug("Populating scale level {}", fScale);
				final String scaleDataset = Paths.get(dataset, scaleDatasets[fScale]).toString();
				imagesWithInvalidate[fScale] = openLabelMultiset(reader, scaleDataset, transform.copy(), globalCache, priority);
				final double[] downsamplingFactors = N5Helpers.getDownsamplingFactors(reader, scaleDataset);
				LOG.debug("Read downsampling factors: {}", Arrays.toString(downsamplingFactors));
				imagesWithInvalidate[fScale].transform.set(N5Helpers.considerDownsampling(
						imagesWithInvalidate[fScale].transform.copy(),
						downsamplingFactors,
						initialDonwsamplingFactors));
				LOG.debug("Populated scale level {}", fScale);
				return true;
			})::get));
		}
		futures.forEach(MakeUnchecked.unchecked(Future::get));
		es.shutdown();
		return imagesWithInvalidate;
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <D> data type
	 * @param <T> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static <D extends NativeType<D>, T extends NativeType<T>> DataSource<D, T>
	openAsLabelSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException {
		return N5Types.isLabelMultisetType(reader, dataset)
		       ? (DataSource<D, T>) openLabelMultisetAsSource(reader, dataset, transform, globalCache, priority, name)
		       : (DataSource<D, T>) openScalarAsSource(reader, dataset, transform, globalCache, priority, name);
	}

	/**
	 *
	 * @param container container
	 * @param group target group in {@code container}
	 * @param dimensions size
	 * @param blockSize chunk size
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param relativeScaleFactors relative scale factors for multi-scale data, e.g.
	 * {@code [2,2,1], [2,2,2]} will result in absolute factors {@code [1,1,1], [2,2,1], [4,4,2]}.
	 * @param maxNumEntries limit number of entries in each {@link LabelMultiset} (set to less than or equal to zero for unbounded)
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static void createEmptyLabeLDataset(
			String container,
			String group,
			long[] dimensions,
			int[] blockSize,
			double[] resolution,
			double[] offset,
			double[][] relativeScaleFactors,
			int[] maxNumEntries) throws IOException
	{
		createEmptyLabeLDataset(container, group, dimensions, blockSize, resolution, offset,relativeScaleFactors, maxNumEntries, false);
	}

	/**
	 *
	 * @param container container
	 * @param group target group in {@code container}
	 * @param dimensions size
	 * @param blockSize chunk size
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param relativeScaleFactors relative scale factors for multi-scale data, e.g.
	 * {@code [2,2,1], [2,2,2]} will result in absolute factors {@code [1,1,1], [2,2,1], [4,4,2]}.
	 * @param maxNumEntries limit number of entries in each {@link LabelMultiset} (set to less than or equal to zero for unbounded)
	 * @param ignoreExisiting overwrite any existing data set
	 * @throws IOException if any n5 operation throws {@link IOException} or {@code group}
	 * already exists and {@code ignorExisting} is {@code false}
	 */
	public static void createEmptyLabeLDataset(
			String container,
			String group,
			long[] dimensions,
			int[] blockSize,
			double[] resolution,
			double[] offset,
			double[][] relativeScaleFactors,
			int[] maxNumEntries,
			boolean ignoreExisiting) throws IOException
	{

		//		{"painteraData":{"type":"label"},
		// "maxId":191985,
		// "labelBlockLookup":{"attributes":{},"root":"/home/phil/local/tmp/sample_a_padded_20160501.n5",
		// "scaleDatasetPattern":"volumes/labels/neuron_ids/oke-test/s%d","type":"n5-filesystem"}}

		final Map<String, String> pd = new HashMap<>();
		pd.put("type", "label");
		final N5FSWriter n5 = new N5FSWriter(container);
		final String uniqueLabelsGroup = String.format("%s/unique-labels", group);

		if (!ignoreExisiting && n5.datasetExists(group))
			throw new IOException(String.format("Dataset `%s' already exists in container `%s'", group, container));

		if (!n5.exists(group))
			n5.createGroup(group);

		if (!ignoreExisiting && n5.listAttributes(group).containsKey(N5Helpers.PAINTERA_DATA_KEY))
			throw new IOException(String.format("Group `%s' exists in container `%s' and is Paintera data set", group, container));

		if (!ignoreExisiting && n5.exists(uniqueLabelsGroup))
			throw new IOException(String.format("Unique labels group `%s' exists in container `%s' -- conflict likely.", uniqueLabelsGroup, container));

		n5.setAttribute(group, N5Helpers.PAINTERA_DATA_KEY, pd);
		n5.setAttribute(group, N5Helpers.MAX_ID_KEY, 1L);

		final String dataGroup = String.format("%s/data", group);
		n5.createGroup(dataGroup);

		// {"maxId":191978,"multiScale":true,"offset":[3644.0,3644.0,1520.0],"resolution":[4.0,4.0,40.0],
		// "isLabelMultiset":true}%
		n5.setAttribute(dataGroup, N5Helpers.MULTI_SCALE_KEY, true);
		n5.setAttribute(dataGroup, N5Helpers.OFFSET_KEY, offset);
		n5.setAttribute(dataGroup, N5Helpers.RESOLUTION_KEY, resolution);
		n5.setAttribute(dataGroup, N5Helpers.IS_LABEL_MULTISET_KEY, true);

		n5.createGroup(uniqueLabelsGroup);
		n5.setAttribute(uniqueLabelsGroup, N5Helpers.MULTI_SCALE_KEY, true);

		final String scaleDatasetPattern      = String.format("%s/s%%d", dataGroup);
		final String scaleUniqueLabelsPattern = String.format("%s/s%%d", uniqueLabelsGroup);
		final long[]       scaledDimensions         = dimensions.clone();
		final double[] accumulatedFactors = new double[] {1.0, 1.0, 1.0};
		for (int scaleLevel = 0, downscaledLevel = -1; downscaledLevel < relativeScaleFactors.length; ++scaleLevel, ++downscaledLevel)
		{
			double[]     scaleFactors       = downscaledLevel < 0 ? null : relativeScaleFactors[downscaledLevel];

			final String dataset            = String.format(scaleDatasetPattern, scaleLevel);
			final String uniqeLabelsDataset = String.format(scaleUniqueLabelsPattern, scaleLevel);
			final int maxNum = downscaledLevel < 0 ? -1 : maxNumEntries[downscaledLevel];
			n5.createDataset(dataset, scaledDimensions, blockSize, DataType.UINT8, new GzipCompression());
			n5.createDataset(uniqeLabelsDataset, scaledDimensions, blockSize, DataType.UINT64, new GzipCompression());

			// {"maxNumEntries":-1,"compression":{"type":"gzip","level":-1},"downsamplingFactors":[2.0,2.0,1.0],"blockSize":[64,64,64],"dataType":"uint8","dimensions":[625,625,125]}%
			n5.setAttribute(dataset, N5Helpers.MAX_NUM_ENTRIES_KEY, maxNum);
			if (scaleLevel == 0)
				n5.setAttribute(dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true);
			else
			{
				n5.setAttribute(dataset, N5Helpers.DOWNSAMPLING_FACTORS_KEY, accumulatedFactors);
				n5.setAttribute(uniqeLabelsDataset, N5Helpers.DOWNSAMPLING_FACTORS_KEY, accumulatedFactors);
			}



			// {"compression":{"type":"gzip","level":-1},"downsamplingFactors":[2.0,2.0,1.0],"blockSize":[64,64,64],"dataType":"uint64","dimensions":[625,625,125]}

			if (scaleFactors != null)
			{
				Arrays.setAll(scaledDimensions, dim -> (long) Math.ceil(scaledDimensions[dim] / scaleFactors[dim]));
				Arrays.setAll(accumulatedFactors, dim -> accumulatedFactors[dim] * scaleFactors[dim]);
			}
		}
	}
}
