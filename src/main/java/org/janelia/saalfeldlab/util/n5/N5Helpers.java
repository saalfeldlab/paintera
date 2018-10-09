package org.janelia.saalfeldlab.util.n5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupFromFile;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal.Persister;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.janelia.saalfeldlab.paintera.data.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.data.n5.N5HDF5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class N5Helpers
{

	public static final String MULTI_SCALE_KEY = "multiScale";

	public static final String IS_LABEL_MULTISET_KEY = "isLabelMultiset";

	public static final String MAX_ID_KEY = "maxId";

	public static final String RESOLUTION_KEY = "resolution";

	public static final String OFFSET_KEY = "offset";

	public static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";

	public static final String LABEL_MULTISETTYPE_KEY = "isLabelMultiset";

	public static final String MAX_NUM_ENTRIES_KEY = "maxNumEntries";

	public static final String PAINTERA_DATA_KEY = "painteraData";

	public static final String PAINTERA_DATA_DATASET = "data";

	public static final String PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASTE = "fragment-segment-assignment";

	public static final String LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Check if a group is a paintera data set:
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for paintera dataset
	 * @return {@code true} if {@code group} exists and has attribute {@code painteraData}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isPainteraDataset(final N5Reader n5, final String group) throws IOException
	{
		return n5.exists(group) && n5.listAttributes(group).containsKey(PAINTERA_DATA_KEY);
	}

	/**
	 * Determine if a group is multiscale.
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for multiscale
	 * @return {@code true} if {@code group} exists and is not a dataset and has attribute "multiScale": true,
	 * or (legacy) all children are groups following the regex pattern {@code "$s[0-9]+^"}, {@code false} otherwise.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isMultiScale(final N5Reader n5, final String group) throws IOException
	{

		if (!n5.exists(group) || n5.datasetExists(group))
			return false;

		/* based on attribute */
		boolean isMultiScale = Optional.ofNullable(n5.getAttribute(group, MULTI_SCALE_KEY, Boolean.class)).orElse(false);

		/*
		 * based on groupd content (the old way) TODO conider removing as
		 * multi-scale declaration by attribute becomes part of the N5 spec.
		 */
		if (!isMultiScale && !n5.datasetExists(group))
		{
			final String[] subGroups = n5.list(group);
			isMultiScale = subGroups.length > 0;
			for (final String subGroup : subGroups)
			{
				if (!(subGroup.matches("^s[0-9]+$") && n5.datasetExists(group + "/" + subGroup)))
				{
					isMultiScale = false;
					break;
				}
			}
			if (isMultiScale)
			{
				LOG.debug(
						"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in " +
								"the" +
								" future. Please add \"{}\":{} to attributes.json.",
						MULTI_SCALE_KEY,
						MULTI_SCALE_KEY,
						true
				        );
			}
		}
		return isMultiScale;
	}


	/**
	 * List all scale datasets within {@code group}
	 * @param n5 {@link N5Reader} container
	 * @param group contains scale directories
	 * @return list of all contained scale datasets, relative to {@code group},
	 * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static String[] listScaleDatasets(final N5Reader n5, final String group) throws IOException
	{
		final String[] scaleDirs = Arrays
				.stream(n5.list(group))
				.filter(s -> s.matches("^s\\d+$"))
				.filter(s -> {
					try
					{
						return n5.datasetExists(group + "/" + s);
					} catch (final IOException e)
					{
						return false;
					}
				})
				.toArray(String[]::new);

		LOG.debug("Found these scale dirs: {}", Arrays.toString(scaleDirs));
		return scaleDirs;
	}


	/**
	 * List and sort all scale datasets within {@code group}
	 * @param n5 {@link N5Reader} container
	 * @param group contains scale directories
	 * @return sorted list of all contained scale datasets, relative to {@code group},
	 * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static String[] listAndSortScaleDatasets(final N5Reader n5, final String group) throws IOException
	{
		final String[] scaleDirs = listScaleDatasets(n5, group);
		sortScaleDatasets(scaleDirs);

		LOG.debug("Sorted scale dirs: {}", Arrays.toString(scaleDirs));
		return scaleDirs;
	}

	/**
	 *
	 * @param n5 {@link N5Reader} container
	 * @param group multi-scale group, dataset, or paintera dataset
	 * @return {@link DatasetAttributes} found in appropriate {@code attributes.json}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static DatasetAttributes getDatasetAttributes(final N5Reader n5, final String group) throws IOException
	{
		LOG.debug("Getting data type for group/dataset {}", group);
		if (isPainteraDataset(n5, group)) { return getDatasetAttributes(n5, group + "/" + PAINTERA_DATA_DATASET); }
		if (isMultiScale(n5, group)) { return getDatasetAttributes(n5, getFinestLevel(n5, group)); }
		return n5.getDatasetAttributes(group);
	}

	/**
	 * Sort scale datasets numerically by removing all non-number characters during comparison.
	 * @param scaleDatasets list of scale datasets
	 */
	public static void sortScaleDatasets(final String[] scaleDatasets)
	{
		Arrays.sort(scaleDatasets, Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^\\d]", ""))));
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Reader} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Reader n5Reader(final String base, final int... defaultCellDimensions) throws IOException
	{
		return isHDF(base) ? new N5HDF5Reader(base, defaultCellDimensions) : new N5FSReader(base);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Reader} with custom {@link GsonBuilder} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Reader n5Reader(final String base, final GsonBuilder gsonBuilder, final int... defaultCellDimensions)
	throws IOException
	{
		return isHDF(base) ? new N5HDF5Reader(base, defaultCellDimensions) : new N5FSReader(base, gsonBuilder);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Writer} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Writer n5Writer(final String base, final int... defaultCellDimensions) throws IOException
	{
		return isHDF(base) ? new N5HDF5Writer(base, defaultCellDimensions) : new N5FSWriter(base);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Writer} with custom {@link GsonBuilder} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Writer n5Writer(final String base, final GsonBuilder gsonBuilder, final int...
			defaultCellDimensions)
	throws IOException
	{
		return isHDF(base) ? new N5HDF5Writer(base, defaultCellDimensions) : new N5FSWriter(base, gsonBuilder);
	}

	/**
	 * Generate {@link N5Meta} from base path
	 * @param base base path of n5 container
	 * @param dataset dataset
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Meta} object for file system or h5 access
	 */
	@SuppressWarnings("unused")
	public static N5Meta metaData(final String base, final String dataset, final int... defaultCellDimensions)
	{
		return isHDF(base) ? new N5HDF5Meta(base, dataset, defaultCellDimensions, false) : new N5FSMeta(base, dataset);
	}

	/**
	 *
	 * @param base path
	 * @return {@code true} if {@code base} starts with "h5://" or ends with ".hdf5" or ".h5"
	 */
	public static boolean isHDF(final String base)
	{
		LOG.debug("Checking {} for HDF", base);
		final boolean isHDF = Pattern.matches("^h5://", base) || Pattern.matches("^.*\\.(hdf|h5)$", base);
		LOG.debug("{} is hdf5? {}", base, isHDF);
		return isHDF;
	}

	/**
	 * Find all datasets inside an n5 container
	 * A dataset is any one of:
	 *   - N5 dataset
	 *   - multi-sclae group
	 *   - paintera dataset
	 * @param n5 container
	 * @param onInterruption run this action when interrupted
	 * @return List of all contained datasets (paths wrt to the root of the container)
	 */
	public static List<String> discoverDatasets(final N5Reader n5, final Runnable onInterruption)
	{
		final List<String> datasets = new ArrayList<>();
		final ExecutorService exec = Executors.newFixedThreadPool(
				n5 instanceof N5HDF5Reader ? 1 : 12,
				new NamedThreadFactory("dataset-discovery-%d", true)
		                                                         );
		final AtomicInteger counter = new AtomicInteger(1);
		exec.submit(() -> discoverSubdirectories(n5, "", datasets, exec, counter));
		while (counter.get() > 0 && !Thread.currentThread().isInterrupted())
		{
			try
			{
				Thread.sleep(20);
			} catch (final InterruptedException e)
			{
				exec.shutdownNow();
				onInterruption.run();
			}
		}
		exec.shutdown();
		Collections.sort(datasets);
		return datasets;
	}

	private static void discoverSubdirectories(
			final N5Reader n5,
			final String pathName,
			final Collection<String> datasets,
			final ExecutorService exec,
			final AtomicInteger counter)
	{
		try
		{
			if (isPainteraDataset(n5, pathName))
			{
				synchronized (datasets)
				{
					datasets.add(pathName);
				}
			}
			else if (n5.datasetExists(pathName))
			{
				synchronized (datasets)
				{
					datasets.add(pathName);
				}
			}
			else
			{

				String[] groups = null;
				/* based on attribute */

				boolean isMipmapGroup = Optional.ofNullable(n5.getAttribute(
						pathName,
						MULTI_SCALE_KEY,
						Boolean.class
				                                                           )).orElse(false);

				/* based on groupd content (the old way) */
				if (!isMipmapGroup)
				{
					groups = n5.list(pathName);
					isMipmapGroup = groups.length > 0;
					for (final String group : groups)
					{
						if (!(group.matches("^s[0-9]+$") && n5.datasetExists(pathName + "/" + group)))
						{
							isMipmapGroup = false;
							break;
						}
					}
					if (isMipmapGroup)
					{
						LOG.debug(
								"Found multi-scale group without {} tag. Implicit multi-scale detection will be " +
										"removed in the future. Please add \"{}\":{} to attributes.json.",
								MULTI_SCALE_KEY,
								MULTI_SCALE_KEY,
								true
						        );
					}
				}
				if (isMipmapGroup)
				{
					synchronized (datasets)
					{
						datasets.add(pathName);
					}
				}
				else
				{
					for (final String group : groups)
					{
						final String groupPathName = pathName + "/" + group;
						final int    numThreads    = counter.incrementAndGet();
						LOG.debug("entering {}, {} threads created", groupPathName, numThreads);
						exec.submit(() -> discoverSubdirectories(n5, groupPathName, datasets, exec, counter));
					}
				}
			}
		} catch (final IOException e)
		{
			LOG.debug(e.toString(), e);
		}
		final int numThreads = counter.decrementAndGet();
		LOG.debug("leaving {}, {} threads remaining", pathName, numThreads);
	}


	/**
	 * Adjust {@link AffineTransform3D} by scaling and translating appropriately.
	 * @param transform to be adjusted wrt to downsampling factors
	 * @param downsamplingFactors at target level
	 * @param initialDownsamplingFactors at source level
	 * @return adjusted {@link AffineTransform3D}
	 */
	public static AffineTransform3D considerDownsampling(
			final AffineTransform3D transform,
			final double[] downsamplingFactors,
			final double[] initialDownsamplingFactors)
	{
		final double[] shift = new double[downsamplingFactors.length];
		for (int d = 0; d < downsamplingFactors.length; ++d)
		{
			transform.set(transform.get(d, d) * downsamplingFactors[d] / initialDownsamplingFactors[d], d, d);
			shift[d] = 0.5 / initialDownsamplingFactors[d] - 0.5 / downsamplingFactors[d];
		}
		return transform.concatenate(new Translation3D(shift));
	}

	/**
	 * Get appropriate {@link FragmentSegmentAssignmentState} for {@code group} in n5 container {@code writer}
	 * @param writer container
	 * @param group group
	 * @return {@link FragmentSegmentAssignmentState}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static FragmentSegmentAssignmentState assignments(final N5Writer writer, final String group)
	throws IOException
	{

		if (!isPainteraDataset(writer, group))
		{
			return new FragmentSegmentAssignmentOnlyLocal(
					TLongLongHashMap::new,
					(ks, vs) -> {
						throw new UnableToPersist("Persisting assignments not supported for non Paintera " +
								"group/dataset" +
								" " + group);
					}
			);
		}

		final String dataset = group + "/" + PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASTE;

		final Persister persister = (keys, values) -> {
			// TODO how to handle zero length assignments?
			if (keys.length == 0)
			{
				throw new UnableToPersist("Zero length data, will not persist fragment-segment-assignment.");
			}
			try
			{
				final DatasetAttributes attrs = new DatasetAttributes(
						new long[] {keys.length, 2},
						new int[] {keys.length, 1},
						DataType.UINT64,
						new GzipCompression()
				);
				writer.createDataset(dataset, attrs);
				final DataBlock<long[]> keyBlock = new LongArrayDataBlock(
						new int[] {keys.length, 1},
						new long[] {0, 0},
						keys
				);
				final DataBlock<long[]> valueBlock = new LongArrayDataBlock(
						new int[] {values.length, 1},
						new long[] {0, 1},
						values
				);
				writer.writeBlock(dataset, attrs, keyBlock);
				writer.writeBlock(dataset, attrs, valueBlock);
			} catch (final Exception e)
			{
				throw new UnableToPersist(e);
			}
		};

		final Supplier<TLongLongMap> initialLutSupplier = MakeUnchecked.supplier(() -> {
			final long[] keys;
			final long[] values;
			LOG.debug("Found fragment segment assingment dataset {}? {}", dataset, writer.datasetExists(dataset));
			if (writer.datasetExists(dataset))
			{
				final DatasetAttributes attrs      = writer.getDatasetAttributes(dataset);
				final int               numEntries = (int) attrs.getDimensions()[0];
				keys = new long[numEntries];
				values = new long[numEntries];
				LOG.debug("Found {} assignments", numEntries);
				final RandomAccessibleInterval<UnsignedLongType> data = N5Utils.open(writer, dataset);

				final Cursor<UnsignedLongType> keysCursor = Views.flatIterable(Views.hyperSlice(data, 1, 0L)).cursor();
				for (int i = 0; keysCursor.hasNext(); ++i)
				{
					keys[i] = keysCursor.next().get();
				}

				final Cursor<UnsignedLongType> valuesCursor = Views.flatIterable(Views.hyperSlice(
						data,
						1,
						1L
				                                                                                 )).cursor();
				for (int i = 0; valuesCursor.hasNext(); ++i)
				{
					values[i] = valuesCursor.next().get();
				}
			}
			else
			{
				keys = new long[] {};
				values = new long[] {};
			}
			return new TLongLongHashMap(keys, values);
		});

		return new FragmentSegmentAssignmentOnlyLocal(initialLutSupplier, persister);
	}

	/**
	 * Get id-service for n5 {@code container} and {@code dataset}.
	 * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
	 * @param n5 container
	 * @param dataset dataset
	 * @return {@link N5IdService}
	 * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
	 */
	public static IdService idService(final N5Writer n5, final String dataset) throws IOException
	{

		LOG.debug("Requesting id service for {}:{}", n5, dataset);
		final Long maxId = n5.getAttribute(dataset, "maxId", Long.class);
		LOG.debug("Found maxId={}", maxId);
		if (maxId == null) { throw new IOException("maxId not specified in attributes.json"); }
		return new N5IdService(n5, dataset, maxId);

	}

	/**
	 *
	 * @param n5 container
	 * @param group scale group
	 * @return {@code "group/s0"} if {@code "s0" is the finest scale level}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static String getFinestLevel(
			final N5Reader n5,
			final String group) throws IOException
	{
		LOG.debug("Getting finest level for dataset {}", group);
		final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
		return Paths.get(group, scaleDirs[0]).toString();
	}

	/**
	 *
	 * @param n5 container
	 * @param group scale group
	 * @return {@code "group/sN"} if {@code "sN" is the coarsest scale level}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static String getCoarsestLevel(
			final N5Reader n5,
			final String group) throws IOException
	{
		final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
		return Paths.get(group, scaleDirs[scaleDirs.length - 1]).toString();
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param key key for array attribute
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at {@code key} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDoubleArrayAttribute(
			final N5Reader n5,
			final String group,
			final String key,
			final double... fallBack) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, key, false, fallBack);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param key key for array attribute
	 * @param revert set to {@code true} to revert order of array entries
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at {@code key} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDoubleArrayAttribute(
			final N5Reader n5,
			final String group,
			final String key,
			final boolean revert,
			final double... fallBack)
	throws IOException
	{

		if (revert)
		{
			final double[] toRevert = getDoubleArrayAttribute(n5, group, key, false, fallBack);
			LOG.debug("Will revert {}", toRevert);
			for ( int i = 0, k = toRevert.length - 1; i < toRevert.length / 2; ++i, --k)
			{
				double tmp = toRevert[i];
				toRevert[i] = toRevert[k];
				toRevert[k] = tmp;
			}
			LOG.debug("Reverted {}", toRevert);
			return toRevert;
		}

		if (isPainteraDataset(n5, group))
		{
			//noinspection ConstantConditions
			return getDoubleArrayAttribute(n5, group + "/" + PAINTERA_DATA_DATASET, key, revert, fallBack);
		}
		try {
			return Optional.ofNullable(n5.getAttribute(group, key, double[].class)).orElse(fallBack);
		}
		catch (ClassCastException e)
		{
			LOG.debug("Caught exception when trying to read double[] attribute. Will try to read as long[] attribute instead.", e);
			return Optional.ofNullable(asDoubleArray(n5.getAttribute(group, key, long[].class))).orElse(fallBack);
		}
	}


	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "resolution"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getResolution(final N5Reader n5, final String group) throws IOException
	{
		return getResolution(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revert set to {@code true} to revert order of array entries
	 * @return value of attribute at {@code "resolution"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getResolution(final N5Reader n5, final String group, boolean revert) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, RESOLUTION_KEY, revert, 1.0, 1.0, 1.0);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "offset"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getOffset(final N5Reader n5, final String group) throws IOException
	{
		return getOffset(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revert set to {@code true} to revert order of array entries
	 * @return value of attribute at {@code "offset"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getOffset(final N5Reader n5, final String group, boolean revert) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, OFFSET_KEY, revert,0.0, 0.0, 0.0);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "downsamplingFactors"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDownsamplingFactors(final N5Reader n5, final String group) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, DOWNSAMPLING_FACTORS_KEY, 1.0, 1.0, 1.0);
	}

	public static boolean getBooleanAttribute(final N5Reader n5, final String group, final String attribute, final boolean fallback) throws IOException {
		return getAttribute(n5, group, attribute, Boolean.class, fallback);
	}

	public static int getIntegerAttribute(final N5Reader n5, final String group, final String attribute, final int fallback) throws IOException {
		return getAttribute(n5, group, attribute, Integer.class, fallback);
	}

	public static <T> T getAttribute(final N5Reader n5, final String group, String attribute, Class<T> clazz, T fallback) throws IOException
	{
		return getAttribute(n5, group, attribute, clazz, (Supplier<T>) () -> fallback);
	}

	public static <T> T getAttribute(final N5Reader n5, final String group, String attribute, Class<T> clazz, Supplier<T> fallback) throws IOException
	{
		final T val = n5.getAttribute(group, attribute, clazz);
		return val == null ? fallback.get() : val;
	}

	/**
	 *
	 * @param resolution voxel-size
	 * @param offset in real-world coordinates
	 * @return {@link AffineTransform3D} with {@code resolution} on diagonal and {@code offset} on 4th column.
	 */
	public static AffineTransform3D fromResolutionAndOffset(final double[] resolution, final double[] offset)
	{
		return new AffineTransform3D().concatenate(new ScaleAndTranslation(resolution, offset));
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static AffineTransform3D getTransform(final N5Reader n5, final String group) throws IOException
	{
		return getTransform(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revertSpatialAttributes revert offset and resolution attributes if {@code true}
	 * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static AffineTransform3D getTransform(final N5Reader n5, final String group, boolean revertSpatialAttributes) throws IOException
	{
		return fromResolutionAndOffset(getResolution(n5, group, revertSpatialAttributes), getOffset(n5, group, revertSpatialAttributes));
	}

	/**
	 * Return the last segment of a path to a group/dataset in n5 container (absolute or relative)
	 * @param group /some/path/to/group
	 * @return last segment of {@code group}: {@code "group"}
	 */
	public static String lastSegmentOfDatasetPath(final String group)
	{
		return Paths.get(group).getFileName().toString();
	}

	/**
	 *
	 * @param reader container
	 * @param group needs to be paitnera dataset to return meaningful lookup
	 * @return unsupported lookup if {@code is not a paintera dataset}, {@link LabelBlockLookup} otherwise.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static LabelBlockLookup getLabelBlockLookup(N5Reader reader, String group) throws IOException
	{
		// TODO fix this, we don't always want to return file-based lookup!!!
		try {
			LOG.debug("Getting label block lookup for {}", N5Meta.fromReader(reader, group));
			if (reader instanceof N5FSReader && isPainteraDataset(reader, group)) {
				N5FSMeta n5fs = new N5FSMeta((N5FSReader) reader, group);
				final GsonBuilder gsonBuilder = new GsonBuilder().registerTypeHierarchyAdapter(LabelBlockLookup.class, LabelBlockLookupAdapter.getJsonAdapter());
				final Gson gson = gsonBuilder.create();
				final JsonElement labelBlockLookupJson = reader.getAttribute(group, "labelBlockLookup", JsonElement.class);
				LOG.debug("Got label block lookup json: {}", labelBlockLookupJson);
				final LabelBlockLookup lookup = Optional
						.ofNullable(labelBlockLookupJson)
						.filter(JsonElement::isJsonObject)
						.map(obj -> gson.fromJson(obj, LabelBlockLookup.class))
						.orElseGet(MakeUnchecked.supplier(() -> new LabelBlockLookupFromFile(Paths.get(n5fs.basePath(), group, "/", "label-to-block-mapping", "s%d", "%d").toString())));
				LOG.debug("Got lookup type: {}", lookup.getClass());
				return lookup;
			} else
				return new LabelBlockLookupNotSupportedForNonPainteraDataset();
		}
		catch (final ReflectionException e)
		{
			throw new IOException(e);
		}
	}

	@LabelBlockLookup.LookupType("UNSUPPORTED")
	private static class LabelBlockLookupNotSupportedForNonPainteraDataset implements LabelBlockLookup
	{

		private LabelBlockLookupNotSupportedForNonPainteraDataset()
		{
			LOG.info("3D meshes not supported for non Paintera dataset!");
		}

		@NotNull
		@Override
		public Interval[] read( int level, long id )
		{
			LOG.debug("Reading blocks not supported for non-paintera dataset -- returning empty array");
			return new Interval[ 0 ];
		}

		@Override
		public void write( int level, long id, Interval... intervals )
		{
			LOG.debug("Saving blocks not supported for non-paintera dataset");
		}

		// This is here because annotation interfaces cannot have members in kotlin (currently)
		// https://stackoverflow.com/questions/49661899/java-annotation-implementation-to-kotlin
		@NotNull
		@Override
		public String getType()
		{
			return "UNSUPPORTED";
		}
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @return {@link CellGrid} that is equivalent to dimensions and block size of dataset
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static CellGrid getGrid(N5Reader reader, final String dataset) throws IOException {
		return asCellGrid(reader.getDatasetAttributes(dataset));
	}


	/**
	 *
	 * @param attributes attributes
	 * @return {@link CellGrid} that is equivalent to dimensions and block size of {@code attributes}
	 */
	public static CellGrid asCellGrid(DatasetAttributes attributes)
	{
		return new CellGrid(attributes.getDimensions(), attributes.getBlockSize());
	}

	/**
	 *
	 * @param group dataset, multi-scale group, or paintera dataset
	 * @param isPainteraDataset set to {@code true} if {@code group} is paintera data set
	 * @return multi-scale group or n5 dataset
	 */
	public static String volumetricDataGroup(final String group, final boolean isPainteraDataset)
	{
		return isPainteraDataset
				? group + "/" + N5Helpers.PAINTERA_DATA_DATASET
				: group;
	}

	/**
	 *
	 * @param n5 container
	 * @param group  multi-scale group
	 * @return {@code group} if {@code group} is n5 dataset, {@code group/s0} if {@code group} is multi-scale
	 */
	public static String highestResolutionDataset(final N5Reader n5, final String group) throws IOException {
		return highestResolutionDataset(n5, group, N5Helpers.isMultiScale(n5, group));
	}


	/**
	 *
	 * @param n5 container
	 * @param group  multi-scale group
	 * @param isMultiscale set to true if {@code group} is multi-scale
	 * @return {@code group} if {@code group} is n5 dataset, {@code group/s0} if {@code isMultiscale} is {@code true}
	 */
	public static String highestResolutionDataset(final N5Reader n5, final String group, final boolean isMultiscale) throws IOException {
		return isMultiscale
				? Paths.get(group, N5Helpers.listAndSortScaleDatasets(n5, group)[0]).toString()
				: group;
	}

	private static double[] asDoubleArray(long[] array)
	{
		final double[] doubleArray = new double[array.length];
		Arrays.setAll(doubleArray, d -> array[d]);
		return doubleArray;
	}
}
