package org.janelia.saalfeldlab.util.n5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.pivovarit.function.ThrowingSupplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.realtransform.Translation3D;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupFromFile;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5DatasetDiscoverer;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5CosemMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5GenericSingleScaleMetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadataParser;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.exception.PainteraException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.janelia.saalfeldlab.paintera.state.raw.n5.Utils;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleMetadata;
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup;
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraRawMultiScaleGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class N5Helpers {

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

  private static final List<N5MetadataParser<?>> GROUP_PARSERS = List.of(
		  new N5PainteraRawMultiScaleGroup.PainteraRawMultiScaleParser(),
		  new N5PainteraLabelMultiScaleGroup.PainteraLabelMultiScaleParser(),
		  new N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
		  new N5MultiScaleMetadata.MultiScaleParser()
  );
  private static final List<N5MetadataParser<?>> METADATA_PARSERS = List.of(
		  new N5PainteraDataMultiScaleMetadata.PainteraDataMultiScaleParser(),
		  new N5CosemMetadataParser(),
		  new N5SingleScaleMetadataParser(),
		  new N5GenericSingleScaleMetadataParser()
  );
  private static final HashMap<String, Optional<N5TreeNode>> N5_METADATA_CACHE = new HashMap<>();

  /**
   * Check if a group is a paintera data set:
   *
   * @param n5    {@link N5Reader} container
   * @param group to be tested for paintera dataset
   * @return {@code true} if {@code group} exists and has attribute {@code painteraData}.
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static boolean isPainteraDataset(final N5Reader n5, final String group) throws IOException {

	final boolean isPainteraDataset = n5.exists(group) && n5.listAttributes(group).containsKey(PAINTERA_DATA_KEY);
	LOG.debug("Is {}/{} Paintera dataset? {}", n5, group, isPainteraDataset);
	return isPainteraDataset;
  }

  /**
   * Determine if a group is multiscale.
   *
   * @param n5    {@link N5Reader} container
   * @param group to be tested for multiscale
   * @return {@code true} if {@code group} exists and is not a dataset and has attribute "multiScale": true,
   * or (legacy) all children are groups following the regex pattern {@code "$s[0-9]+^"}, {@code false} otherwise.
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static boolean isMultiScale(final N5Reader n5, final String group) throws IOException {

	if (!n5.exists(group) || n5.datasetExists(group))
	  return false;

	/* based on attribute */
	boolean isMultiScale = Optional.ofNullable(n5.getAttribute(group, MULTI_SCALE_KEY, Boolean.class)).orElse(false);

	/*
	 * based on groupd content (the old way)
	 * TODO consider removing as multi-scale declaration by attribute becomes part of the N5 spec.
	 */
	if (!isMultiScale && !n5.datasetExists(group)) {
	  final String[] subGroups = n5.list(group);
	  isMultiScale = subGroups.length > 0;
	  for (final String subGroup : subGroups) {
		if (!(subGroup.matches("^s[0-9]+$") && n5.datasetExists(group + "/" + subGroup))) {
		  isMultiScale = false;
		  break;
		}
	  }
	  if (isMultiScale) {
		LOG.debug(
				"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in the future. Please add \"{}\":{} to attributes.json.",
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
   *
   * @param n5    {@link N5Reader} container
   * @param group contains scale directories
   * @return list of all contained scale datasets, relative to {@code group},
   * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static String[] listScaleDatasets(final N5Reader n5, final String group) throws IOException {

	final String[] scaleDirs = Arrays
			.stream(n5.list(group))
			.filter(s -> s.matches("^s\\d+$"))
			.filter(s -> {
			  try {
				return n5.datasetExists(group + "/" + s);
			  } catch (final IOException e) {
				return false;
			  }
			})
			.toArray(String[]::new);

	LOG.debug("Found these scale dirs: {}", Arrays.toString(scaleDirs));
	return scaleDirs;
  }

  /**
   * List and sort all scale datasets within {@code group}
   *
   * @param n5    {@link N5Reader} container
   * @param group contains scale directories
   * @return sorted list of all contained scale datasets, relative to {@code group},
   * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static String[] listAndSortScaleDatasets(final N5Reader n5, final String group) throws IOException {

	final String[] scaleDirs = listScaleDatasets(n5, group);
	sortScaleDatasets(scaleDirs);

	LOG.debug("Sorted scale dirs: {}", Arrays.toString(scaleDirs));
	return scaleDirs;
  }

  /**
   * @param n5    {@link N5Reader} container
   * @param group multi-scale group, dataset, or paintera dataset
   * @return {@link DatasetAttributes} found in appropriate {@code attributes.json}
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static DatasetAttributes getDatasetAttributes(final N5Reader n5, final String group) throws IOException {

	LOG.debug("Getting data type for group/dataset {}", group);
	if (isPainteraDataset(n5, group)) {
	  return getDatasetAttributes(n5, group + "/" + PAINTERA_DATA_DATASET);
	}
	if (isMultiScale(n5, group)) {
	  return getDatasetAttributes(n5, String.join("/", getFinestLevelJoinWithGroup(n5, group)));

	}
	return n5.getDatasetAttributes(group);
  }

  /**
   * @param metadata {@link N5Metadata} metadata to get DatasetAttributes from
   */
  public static DatasetAttributes getDatasetAttributesFromMetadata(N5Metadata metadata) {

	if (metadata instanceof N5DatasetMetadata) {
	  return ((N5DatasetMetadata)metadata).getAttributes();
	} else if (metadata instanceof MultiscaleMetadata) {
	  return ((MultiscaleMetadata<?>)metadata).getChildrenMetadata()[0].getAttributes();
	}
	LOG.warn("DatasetAttributes not found for {}", metadata.getPath());
	return null;
  }

  /**
   * Sort scale datasets numerically by removing all non-number characters during comparison.
   *
   * @param scaleDatasets list of scale datasets
   */
  public static void sortScaleDatasets(final String[] scaleDatasets) {

	Arrays.sort(scaleDatasets, Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^\\d]", ""))));
  }

  /**
   * @param base                  path to directory or h5 file
   * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
   * @return appropriate {@link N5Reader} for file system or h5 access
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static N5Reader n5Reader(final String base, final int... defaultCellDimensions) throws IOException {

	Paintera.getN5Factory().hdf5DefaultBlockSize(defaultCellDimensions);
	return Paintera.getN5Factory().openReader(base);
  }

  /**
   * @param base                  path to directory or h5 file
   * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
   * @return appropriate {@link N5Reader} with custom {@link GsonBuilder} for file system or h5 access
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static N5Reader n5Reader(final String base, final GsonBuilder gsonBuilder, final int... defaultCellDimensions)
		  throws IOException {

	final var factory = Paintera.getN5Factory();
	factory.hdf5DefaultBlockSize(defaultCellDimensions);
	factory.gsonBuilder(gsonBuilder);
	return factory.openReader(base);

  }

  /**
   * @param base                  path to directory or h5 file
   * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
   * @return appropriate {@link N5Writer} for file system or h5 access
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static N5Writer n5Writer(final String base, final int... defaultCellDimensions) throws IOException {

	final var factory = Paintera.getN5Factory();
	factory.hdf5DefaultBlockSize(defaultCellDimensions);
	return factory.openWriter(base);

  }

  /**
   * @param base                  path to directory or h5 file
   * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
   * @return appropriate {@link N5Writer} for file system or h5 access
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static N5Writer n5WriterIfContainerExists(final String base, final int... defaultCellDimensions) throws IOException {

	/* Open a reader first, to see if container exists (otherwise this creates a new container)  */
	final var factory = Paintera.getN5Factory();
	factory.hdf5DefaultBlockSize(defaultCellDimensions);
	factory.openReader(base);
	return factory.openWriter(base);

  }

  /**
   * @param base                  path to directory or h5 file
   * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
   * @return appropriate {@link N5Writer} with custom {@link GsonBuilder} for file system or h5 access
   * @throws IOException if any N5 operation throws {@link IOException}
   */
  public static N5Writer n5Writer(final String base, final GsonBuilder gsonBuilder, final int...
		  defaultCellDimensions)
		  throws IOException {

	final var factory = Paintera.getN5Factory();
	factory.hdf5DefaultBlockSize(defaultCellDimensions);
	factory.gsonBuilder(gsonBuilder);
	return factory.openWriter(base);
  }

  /**
   * @param base path
   * @return {@code true} if {@code base} starts with "h5://" or ends with ".hdf5" or ".h5"
   */
  public static boolean isHDF(final String base) {

	LOG.debug("Checking {} for HDF", base);
	final boolean isHDF = Pattern.matches("^h5://", base) || Pattern.matches("^.*\\.(hdf|h5)$", base);
	LOG.debug("{} is hdf5? {}", base, isHDF);
	return isHDF;
  }

  /**
   * Find all datasets inside an n5 container
   * A dataset is any one of:
   * - N5 dataset
   * - multi-sclae group
   * - paintera dataset
   *
   * @param n5          container
   * @param keepLooking discover datasets while while {@code keepLooking.get() == true}
   * @return List of all contained datasets (paths wrt to the root of the container)
   */
  public static Optional<N5TreeNode> parseMetadata(final N5Reader n5, final BooleanProperty keepLooking) {

	final NamedThreadFactory threadFactory = new NamedThreadFactory("dataset-discovery-%d", true);
	final ExecutorService es;
	if (n5 instanceof N5HDF5Reader) {
	  es = Executors.newFixedThreadPool(1, threadFactory);
	} else {
	  es = Executors.newCachedThreadPool(threadFactory);
	}

	ChangeListener<Boolean> stopDiscovery = (obs, oldv, newv) -> {
	  if (newv != oldv && !newv)
		es.shutdown();
	};
	Optional.ofNullable(keepLooking).ifPresent(kl -> kl.addListener(stopDiscovery));
	final Optional<N5TreeNode> parsedN5Tree = parseMetadata(n5, es);
	LOG.debug("Shutting down discovery ExecutorService.");
	/* we are done, remove our listener */
	Optional.ofNullable(keepLooking).ifPresent(kl -> kl.removeListener(stopDiscovery));
	es.shutdownNow();
	return parsedN5Tree;
  }

  public static Optional<N5TreeNode> parseMetadata(final N5Reader n5) {

	String url = Utils.getUrlRepresentation(n5);
	if (N5_METADATA_CACHE.containsKey(url)) {
	  return N5_METADATA_CACHE.get(url);
	}

	Optional<N5TreeNode> n5TreeNode = parseMetadata(n5, (BooleanProperty)null);
	N5_METADATA_CACHE.put(url, n5TreeNode);
	return n5TreeNode;
  }

  /**
   * Find all datasets inside an n5 container
   * A dataset is any one of:
   * - N5 dataset
   * - multi-sclae group
   * - paintera dataset
   *
   * @param n5 container
   * @param es ExecutorService for parallelization of discovery
   * @return List of all contained datasets (paths wrt to the root of the container)
   */
  public static Optional<N5TreeNode> parseMetadata(
		  final N5Reader n5,
		  final ExecutorService es) {

	final var discoverer = new N5DatasetDiscoverer(n5, es, METADATA_PARSERS, GROUP_PARSERS);
	try {
	  final N5TreeNode rootNode = discoverer.discoverAndParseRecursive("");
	  return Optional.of(rootNode);
	} catch (IOException e) {
	  //FIXME give more info in error, remove stacktrace.
	  LOG.error("Unable to discover datasets");
	  e.printStackTrace();
	  return Optional.empty();
	}
  }

  private static void discoverSubdirectories(
		  final N5Reader n5,
		  final String pathName,
		  final Collection<String> datasets,
		  final ExecutorService exec,
		  final AtomicInteger counter,
		  final BooleanSupplier keepLooking) {

	LOG.trace("Discovering subdirectory {}", pathName);

	try {
	  if (!keepLooking.getAsBoolean() || Thread.currentThread().isInterrupted())
		return;

	  if (isPainteraDataset(n5, pathName)) {
		synchronized (datasets) {
		  datasets.add(pathName);
		}
	  } else if (n5.datasetExists(pathName)) {
		synchronized (datasets) {
		  datasets.add(pathName);
		}
	  } else {

		String[] groups = null;
		/* based on attribute */

		boolean isMipmapGroup = Optional.ofNullable(n5.getAttribute(
				pathName,
				MULTI_SCALE_KEY,
				Boolean.class
		)).orElse(false);

		/* based on groupd content (the old way) */
		if (!isMipmapGroup) {
		  groups = n5.list(pathName);
		  isMipmapGroup = groups.length > 0;
		  for (final String group : groups) {
			if (!(group.matches("^s[0-9]+$") && n5.datasetExists(pathName + "/" + group))) {
			  isMipmapGroup = false;
			  break;
			}
		  }
		  if (isMipmapGroup) {
			LOG.warn(
					"Found multi-scale group without {} tag. Implicit multi-scale detection will be " +
							"removed in the future. Please add \"{}\":{} to attributes.json in group `{}'.",
					MULTI_SCALE_KEY,
					MULTI_SCALE_KEY,
					true,
					pathName
			);
		  }
		}
		if (isMipmapGroup) {
		  synchronized (datasets) {
			LOG.debug("Adding dataset {}", pathName);
			datasets.add(pathName);
		  }
		} else {
		  if (keepLooking.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
			for (final String group : groups) {
			  final String groupPathName = pathName + "/" + group;
			  final int numThreads = counter.incrementAndGet();
			  LOG.debug("Entering {}, {} tasks created", groupPathName, numThreads);
			  exec.submit(() -> discoverSubdirectories(n5, groupPathName, datasets, exec, counter, keepLooking));
			}
		  }
		}
	  }
	} catch (final IOException e) {
	  LOG.debug(e.toString(), e);
	} finally {
	  synchronized (counter) {
		final int numThreads = counter.decrementAndGet();
		counter.notifyAll();
		LOG.debug("Leaving {}, {} tasks remaining", pathName, numThreads);
	  }
	}
  }

  /**
   * Adjust {@link AffineTransform3D} by scaling and translating appropriately.
   *
   * @param transform                  to be adjusted wrt to downsampling factors
   * @param downsamplingFactors        at target level
   * @param initialDownsamplingFactors at source level
   * @return adjusted {@link AffineTransform3D}
   */
  public static AffineTransform3D considerDownsampling(
		  final AffineTransform3D transform,
		  final double[] downsamplingFactors,
		  final double[] initialDownsamplingFactors) {

	final double[] shift = new double[downsamplingFactors.length];
	for (int d = 0; d < downsamplingFactors.length; ++d) {
	  transform.set(transform.get(d, d) * downsamplingFactors[d] / initialDownsamplingFactors[d], d, d);
	  shift[d] = 0.5 / initialDownsamplingFactors[d] - 0.5 / downsamplingFactors[d];
	}
	return transform.concatenate(new Translation3D(shift));
  }

  /**
   * Get appropriate {@link FragmentSegmentAssignmentState} for {@code group} in n5 container {@code writer}
   *
   * @param writer container
   * @param group  group
   * @return {@link FragmentSegmentAssignmentState}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static FragmentSegmentAssignmentOnlyLocal assignments(final N5Writer writer, final String group)
		  throws IOException {

	if (!isPainteraDataset(writer, group)) {
	  final String persistError = "Persisting assignments not supported for non Paintera group/dataset " + group;
	  return new FragmentSegmentAssignmentOnlyLocal(
			  FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
			  FragmentSegmentAssignmentOnlyLocal.doesNotPersist(persistError));
	}

	final String dataset = group + "/" + PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASTE;

	try {
	  return new FragmentSegmentAssignmentOnlyLocal(
			  new N5FragmentSegmentAssignmentInitialLut(writer, dataset),
			  new N5FragmentSegmentAssignmentPersister(writer, dataset));
	} catch (ReflectionException e) {
	  LOG.debug("Unable to create initial lut supplier", e);
	  return new FragmentSegmentAssignmentOnlyLocal(
			  FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
			  new N5FragmentSegmentAssignmentPersister(writer, dataset));
	}
  }

  /**
   * Get id-service for n5 {@code container} and {@code dataset}.
   * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
   *
   * @param n5            container
   * @param dataset       dataset
   * @param maxIdFallback Use this if maxId attribute is not specified in {@code dataset}.
   * @return {@link N5IdService}
   * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
   */
  public static IdService idService(final N5Writer n5, final String dataset, final long maxIdFallback) throws IOException {

	return idService(n5, dataset, () -> maxIdFallback);
  }

  /**
   * Get id-service for n5 {@code container} and {@code dataset}.
   * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
   *
   * @param n5            container
   * @param dataset       dataset
   * @param maxIdFallback Use this if maxId attribute is not specified in {@code dataset}.
   * @return {@link N5IdService}
   * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
   */
  public static IdService idService(final N5Writer n5, final String dataset, final LongSupplier maxIdFallback) throws IOException {

	try {
	  return idService(n5, dataset);
	} catch (final MaxIDNotSpecified e) {
	  n5.setAttribute(dataset, "maxId", maxIdFallback.getAsLong());
	  try {
		return idService(n5, dataset);
	  } catch (final MaxIDNotSpecified e2) {
		throw new IOException(e2);
	  }
	}
  }

  /**
   * Get id-service for n5 {@code container} and {@code dataset}.
   * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
   *
   * @param n5       container
   * @param dataset  dataset
   * @param fallback Use this if maxId attribute is not specified in {@code dataset}.
   * @return {@link N5IdService}
   * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
   */
  public static IdService idService(final N5Writer n5, final String dataset, final Supplier<IdService> fallback) throws IOException {

	try {
	  return idService(n5, dataset);
	} catch (final MaxIDNotSpecified e) {
	  return fallback.get();
	}
  }

  /**
   * Get id-service for n5 {@code container} and {@code dataset}.
   * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
   *
   * @param n5      container
   * @param dataset dataset
   * @return {@link N5IdService}
   * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
   */
  public static IdService idService(final N5Writer n5, final String dataset) throws MaxIDNotSpecified, IOException {

	LOG.debug("Requesting id service for {}:{}", n5, dataset);
	final Long maxId = n5.getAttribute(dataset, "maxId", Long.class);
	LOG.debug("Found maxId={}", maxId);
	if (maxId == null)
	  throw new MaxIDNotSpecified(String.format(
			  "Required attribute `maxId' not specified for dataset `%s' in container `%s'.",
			  dataset,
			  n5));
	return new N5IdService(n5, dataset, maxId);
  }

  /**
   * @param n5    container
   * @param group scale group
   * @return {@code "s0"} if {@code "s0"} is the finest scale level
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getFinestLevel(
		  final N5Reader n5,
		  final String group) throws IOException {

	LOG.debug("Getting finest level for dataset {}", group);
	final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
	return scaleDirs[0];
  }

  /**
   * @param n5    container
   * @param group scale group
   * @return {@code String.format("%s/%s", group, "s0")} if {@code "s0"} is the finest scale level
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getFinestLevelJoinWithGroup(
		  final N5Reader n5,
		  final String group) throws IOException {

	return getFinestLevelJoinWithGroup(n5, group, (g, d) -> String.format("%s/%s", g, d));
  }

  /**
   * @param n5     container
   * @param group  scale group
   * @param joiner join group and finest scale level
   * @return {@code joiner.apply(group, "s0")} if {@code "s0"} is the finest scale level
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getFinestLevelJoinWithGroup(
		  final N5Reader n5,
		  final String group,
		  final BiFunction<String, String, String> joiner) throws IOException {

	return joiner.apply(group, getFinestLevel(n5, group));
  }

  /**
   * @param n5    container
   * @param group scale group
   * @return {@code "sN"} if {@code "sN" is the coarsest scale level}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getCoarsestLevel(
		  final N5Reader n5,
		  final String group) throws IOException {

	final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
	return scaleDirs[scaleDirs.length - 1];
  }

  /**
   * @param n5    container
   * @param group scale group
   * @return {@code String.format("%s/%s", group, "sN")} if {@code "sN"} is the coarsest scale level
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getCoarsestLevelJoinWithGroup(
		  final N5Reader n5,
		  final String group) throws IOException {

	return getCoarsestLevelJoinWithGroup(n5, group, (g, d) -> String.format("%s/%s", g, d));
  }

  /**
   * @param n5     container
   * @param group  scale group
   * @param joiner join group and finest scale level
   * @return {@code joiner.apply(group, "s0")} if {@code "s0"} is the coarsest scale level
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static String getCoarsestLevelJoinWithGroup(
		  final N5Reader n5,
		  final String group,
		  final BiFunction<String, String, String> joiner) throws IOException {

	return joiner.apply(group, getCoarsestLevel(n5, group));
  }

  /**
   * @param n5       container
   * @param group    group
   * @param key      key for array attribute
   * @param fallBack if key not present, return this value instead
   * @return value of attribute at {@code key} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getDoubleArrayAttribute(
		  final N5Reader n5,
		  final String group,
		  final String key,
		  final double... fallBack) throws IOException {

	return getDoubleArrayAttribute(n5, group, key, false, fallBack);
  }

  /**
   * @param n5       container
   * @param group    group
   * @param key      key for array attribute
   * @param reverse  set to {@code true} to reverse order of array entries
   * @param fallBack if key not present, return this value instead
   * @return value of attribute at {@code key} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getDoubleArrayAttribute(
		  final N5Reader n5,
		  final String group,
		  final String key,
		  final boolean reverse,
		  final double... fallBack)
  throws IOException {

	if (reverse) {
	  final double[] toReverse = getDoubleArrayAttribute(n5, group, key, false, fallBack);
	  LOG.debug("Will reverse {}", toReverse);
	  for (int i = 0, k = toReverse.length - 1; i < toReverse.length / 2; ++i, --k) {
		double tmp = toReverse[i];
		toReverse[i] = toReverse[k];
		toReverse[k] = tmp;
	  }
	  LOG.debug("Reversed {}", toReverse);
	  return toReverse;
	}

	if (isPainteraDataset(n5, group)) {
	  //noinspection ConstantConditions
	  return getDoubleArrayAttribute(n5, group + "/" + PAINTERA_DATA_DATASET, key, reverse, fallBack);
	}
	try {
	  return Optional.ofNullable(n5.getAttribute(group, key, double[].class)).orElse(fallBack);
	} catch (final ClassCastException e) {
	  LOG.debug("Caught exception when trying to read double[] attribute. Will try to read as long[] attribute instead.", e);
	  return Optional.of(asDoubleArray(n5.getAttribute(group, key, long[].class))).orElse(fallBack);
	}
  }

  /**
   * @param n5    container
   * @param group group
   * @return value of attribute at {@code "resolution"} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getResolution(final N5Reader n5, final String group) throws IOException {

	return getResolution(n5, group, false);
  }

  /**
   * @param n5      container
   * @param group   group
   * @param reverse set to {@code true} to reverse order of array entries
   * @return value of attribute at {@code "resolution"} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getResolution(final N5Reader n5, final String group, boolean reverse) throws IOException {

	return getDoubleArrayAttribute(n5, group, RESOLUTION_KEY, reverse, 1.0, 1.0, 1.0);
  }

  /**
   * @param n5    container
   * @param group group
   * @return value of attribute at {@code "offset"} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getOffset(final N5Reader n5, final String group) throws IOException {

	return getOffset(n5, group, false);
  }

  /**
   * @param n5      container
   * @param group   group
   * @param reverse set to {@code true} to reverse order of array entries
   * @return value of attribute at {@code "offset"} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getOffset(final N5Reader n5, final String group, boolean reverse) throws IOException {

	return getDoubleArrayAttribute(n5, group, OFFSET_KEY, reverse, 0.0, 0.0, 0.0);
  }

  /**
   * @param n5    container
   * @param group group
   * @return value of attribute at {@code "downsamplingFactors"} as {@code double[]}
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static double[] getDownsamplingFactors(final N5Reader n5, final String group) throws IOException {

	return getDoubleArrayAttribute(n5, group, DOWNSAMPLING_FACTORS_KEY, 1.0, 1.0, 1.0);
  }

  public static boolean getBooleanAttribute(final N5Reader n5, final String group, final String attribute, final boolean fallback) throws IOException {

	return getAttribute(n5, group, attribute, Boolean.class, fallback);
  }

  public static int getIntegerAttribute(final N5Reader n5, final String group, final String attribute, final int fallback) throws IOException {

	return getAttribute(n5, group, attribute, Integer.class, fallback);
  }

  public static <T> T getAttribute(final N5Reader n5, final String group, String attribute, Class<T> clazz, T fallback) throws IOException {

	return getAttribute(n5, group, attribute, clazz, (Supplier<T>)() -> fallback);
  }

  public static <T> T getAttribute(final N5Reader n5, final String group, String attribute, Class<T> clazz, Supplier<T> fallback) throws IOException {

	final T val = n5.getAttribute(group, attribute, clazz);
	return val == null ? fallback.get() : val;
  }

  /**
   * @param resolution voxel-size
   * @param offset     in real-world coordinates
   * @return {@link AffineTransform3D} with {@code resolution} on diagonal and {@code offset} on 4th column.
   */
  public static AffineTransform3D fromResolutionAndOffset(final double[] resolution, final double[] offset) {

	return new AffineTransform3D().concatenate(new ScaleAndTranslation(resolution, offset));
  }

  /**
   * @param n5    container
   * @param group group
   * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static AffineTransform3D getTransform(final N5Reader n5, final String group) throws IOException {

	return getTransform(n5, group, false);
  }

  /**
   * @param n5                       container
   * @param group                    group
   * @param reverseSpatialAttributes reverse offset and resolution attributes if {@code true}
   * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static AffineTransform3D getTransform(final N5Reader n5, final String group, final boolean reverseSpatialAttributes) throws IOException {

	return fromResolutionAndOffset(getResolution(n5, group, reverseSpatialAttributes), getOffset(n5, group, reverseSpatialAttributes));
  }

  /**
   * Return the last segment of a path to a group/dataset in n5 container (absolute or relative)
   *
   * @param group /some/path/to/group
   * @return last segment of {@code group}: {@code "group"}
   */
  public static String lastSegmentOfDatasetPath(final String group) {

	return Paths.get(group).getFileName().toString();
  }

  /**
   * @param reader                      container
   * @param group                       needs to be paitnera dataset to return meaningful lookup
   * @param lookupIfNotAPainteraDataset Use this to generate a fallback lookup if {@code group} is not a Paintera dataset.
   * @return unsupported lookup if {@code is not a paintera dataset}, {@link LabelBlockLookup} otherwise.
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static LabelBlockLookup getLabelBlockLookupWithFallback(
		  final N5Reader reader,
		  final String group,
		  final BiFunction<N5Reader, String, LabelBlockLookup> lookupIfNotAPainteraDataset) throws IOException, NotAPainteraDataset {

	try {
	  return getLabelBlockLookup(MetadataUtils.tmpCreateMetadataState((N5Writer)reader, group));
	} catch (final NotAPainteraDataset e) {
	  return lookupIfNotAPainteraDataset.apply(reader, group);
	}
  }

  /**
   * @param metadataState object containing the metadata information and context for the dataset we are interrogating.
   * @return unsupported lookup if {@code is not a paintera dataset}, {@link LabelBlockLookup} otherwise.
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static LabelBlockLookup getLabelBlockLookup(MetadataState metadataState) throws IOException, NotAPainteraDataset {

	final var group = metadataState.getGroup();
	final var reader = metadataState.getReader();

	// FIXME fix this, we don't always want to return file-based lookup!!!
	//  This also reads from the N5 Container directly, not via the reader
	try {
	  LOG.debug("Getting label block lookup for {}", metadataState.getMetadata().getPath());
	  if (reader instanceof N5FSReader && isPainteraDataset(reader, group)) {
		N5FSMeta n5fs = new N5FSMeta((N5FSReader)reader, group);
		final GsonBuilder gsonBuilder = new GsonBuilder().registerTypeHierarchyAdapter(LabelBlockLookup.class, LabelBlockLookupAdapter.getJsonAdapter());
		final Gson gson = gsonBuilder.create();
		final JsonElement labelBlockLookupJson = reader.getAttribute(group, "labelBlockLookup", JsonElement.class);
		LOG.debug("Got label block lookup json: {}", labelBlockLookupJson);
		final LabelBlockLookup lookup = Optional
				.ofNullable(labelBlockLookupJson)
				.filter(JsonElement::isJsonObject)
				.map(obj -> gson.fromJson(obj, LabelBlockLookup.class))
				.orElseGet(ThrowingSupplier.unchecked(
						() -> new LabelBlockLookupFromFile(Paths.get(n5fs.basePath(), group, "/", "label-to-block-mapping", "s%d", "%d").toString())));
		LOG.debug("Got lookup type: {}", lookup.getClass());
		return lookup;
	  } else
		throw new NotAPainteraDataset(reader, group);
	} catch (final ReflectionException e) {
	  throw new IOException(e);
	}
  }

  /**
   * @param reader  container
   * @param dataset dataset
   * @return {@link CellGrid} that is equivalent to dimensions and block size of dataset
   * @throws IOException if any n5 operation throws {@link IOException}
   */
  public static CellGrid getGrid(N5Reader reader, final String dataset) throws IOException {

	return asCellGrid(reader.getDatasetAttributes(dataset));
  }

  /**
   * @param attributes attributes
   * @return {@link CellGrid} that is equivalent to dimensions and block size of {@code attributes}
   */
  public static CellGrid asCellGrid(DatasetAttributes attributes) {

	return new CellGrid(attributes.getDimensions(), attributes.getBlockSize());
  }

  /**
   * @param group             dataset, multi-scale group, or paintera dataset
   * @param isPainteraDataset set to {@code true} if {@code group} is paintera data set
   * @return multi-scale group or n5 dataset
   */
  public static String volumetricDataGroup(final String group, final boolean isPainteraDataset) {

	return isPainteraDataset
			? group + "/" + N5Helpers.PAINTERA_DATA_DATASET
			: group;
  }

  private static double[] asDoubleArray(long[] array) {

	final double[] doubleArray = new double[array.length];
	Arrays.setAll(doubleArray, d -> array[d]);
	return doubleArray;
  }

  public static Map<String, N5TreeNode> validPainteraGroupMap(N5TreeNode metadataTree) {

	final var validChoices = new HashMap<String, N5TreeNode>();
	/* filter the metadata for valid groups/datasets*/
	final var potentialDatasets = new ArrayList<N5TreeNode>();
	potentialDatasets.add(metadataTree);
	for (var idx = 0; idx < potentialDatasets.size(); idx++) {
	  final var potentialChoice = potentialDatasets.get(idx);
	  N5Metadata metadata = potentialChoice.getMetadata();
	  if (MetadataUtils.metadataIsValid(metadata)) {
		/* if we are valid, add and update out map. */
		final var validChoicePath = potentialChoice.getPath();
		validChoices.put(validChoicePath, potentialChoice);
	  } else {
		if (!potentialChoice.childrenList().isEmpty()) {
		  /* if we aren't valid, but have kids, lets check them later */
		  potentialDatasets.addAll(potentialChoice.childrenList());
		}
	  }
	}
	return validChoices;
  }

  /**
   * Helper exception class, only intented to be used in {@link #idService(N5Writer, String)} if {@code maxId} is not specified.
   */
  public static class MaxIDNotSpecified extends PainteraException {

	private MaxIDNotSpecified(final String message) {

	  super(message);
	}
  }

  public static class NotAPainteraDataset extends PainteraException {

	public final N5Reader container;

	public final String group;

	private NotAPainteraDataset(final N5Reader container, final String group) {

	  super(String.format("Group %s in container %s is not a Paintera dataset.", group, container));
	  this.container = container;
	  this.group = group;
	}

  }
}
