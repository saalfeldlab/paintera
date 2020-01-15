package org.janelia.saalfeldlab.paintera;

import com.google.gson.JsonObject;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.Label;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.DataTypeNotSupported;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "Paintera", showDefaultValues = true)
public class PainteraCommandLineArgs implements Callable<Boolean>
{

	private static class LongArrayTypeConverter implements CommandLine.ITypeConverter<long[]> {

		@Override
		public long[] convert(String value) {
			return Stream
					.of(value.split(","))
					.mapToLong(Long::parseLong)
					.toArray();
		}
	}

	private enum IdServiceFallback {
		ASK(PainteraAlerts::getN5IdServiceFromData),
		FROM_DATA(PainteraCommandLineArgs::findMaxIdForIdServiceAndWriteToN5),
		NONE((n5, dataset, source) -> new IdService.IdServiceNotProvided());



		private final IdServiceFallbackGenerator idServiceGenerator;

		IdServiceFallback(final IdServiceFallbackGenerator idServiceGenerator) {
			this.idServiceGenerator = idServiceGenerator;
		}

		public IdServiceFallbackGenerator getIdServiceGenerator() {
			LOG.debug("Getting id service generator from {}", this);
			return this.idServiceGenerator;
		}

		private static class TypeConverter implements CommandLine.ITypeConverter<IdServiceFallback> {

			private static class NoMatchFound extends Exception {
				private final String selection;

				private NoMatchFound(String selection, final Throwable e) {
					super(
							String.format(
								"No match found for selection `%s'. Pick any of these options (case insensitive): %s",
								selection,
								Arrays.asList(IdServiceFallback.values())),
							e);
					this.selection = selection;
				}
			}

			@Override
			public IdServiceFallback convert(String s) throws NoMatchFound {
				try {
					return IdServiceFallback.valueOf(s.replace("-", "_").toUpperCase());
				} catch (IllegalArgumentException e) {
					throw new NoMatchFound(s, e);
				}
			}
		}
	}

	private enum LabelBlockLookupFallback {
		ASK(PainteraAlerts::getLabelBlockLookupFromN5DataSource),
		NONE((c, g, s) -> new LabelBlockLookupNoBlocks()),
		COMPLETE((c, g, s) -> LabelBlockLookupAllBlocks.fromSource(s));

		private final LabelBlockLookupFallbackGenerator generator;

		LabelBlockLookupFallback(LabelBlockLookupFallbackGenerator generator) {
			this.generator = generator;
		}

		public LabelBlockLookupFallbackGenerator getGenerator() {
			return this.generator;
		}

		private static class TypeConverter implements CommandLine.ITypeConverter<LabelBlockLookupFallback> {

			private static class NoMatchFound extends Exception {
				private final String selection;

				private NoMatchFound(String selection, final Throwable e) {
					super(
							String.format(
									"No match found for selection `%s'. Pick any of these options (case insensitive): %s",
									selection,
									Arrays.asList(LabelBlockLookupFallback.values())),
							e);
					this.selection = selection;
				}
			}

			@Override
			public LabelBlockLookupFallback convert(String s) throws TypeConverter.NoMatchFound {
				try {
					return LabelBlockLookupFallback.valueOf(s.replace("-", "_").toUpperCase());
				} catch (IllegalArgumentException e) {
					throw new TypeConverter.NoMatchFound(s, e);
				}
			}
		}
	}

	private static final class AddDatasetArgument {

		private static ExecutorService DISCOVERY_EXECUTOR_SERVICE = null;

		private static synchronized ExecutorService getDiscoveryExecutorService() {
			if (DISCOVERY_EXECUTOR_SERVICE == null) {
				DISCOVERY_EXECUTOR_SERVICE = Executors.newFixedThreadPool(
						12,
						new NamedThreadFactory("dataset-discovery-%d", true));
				LOG.debug("Created discovery executor service {}", DISCOVERY_EXECUTOR_SERVICE);
			}
			return DISCOVERY_EXECUTOR_SERVICE;
		};

		private static final class Options {

			@Option(names = {"-d", "--dataset"}, paramLabel = "DATASET", arity = "1..*", required = true, description = "" +
					"Dataset(s) within CONTAINER to be added. " +
					"TODO: If no datasets are specified, all datasets will be added (or use a separate option for this).")
			String[] datasets = null;

			@Option(names = {"-r", "--resolution"}, paramLabel = "RESOLUTION", required = false, split = ",", description = "" +
					"Spatial resolution for all dataset(s) specified by DATASET. " +
					"Takes meta-data over resolution specified in meta data of DATASET")
			double[] resolution = null;

			@Option(names = {"-o", "--offset"}, paramLabel = "OFFSET", required = false, split = ",", description = "" +
					"Spatial offset for all dataset(s) specified by DATASET. " +
					"Takes meta-data over resolution specified in meta data of DATASET")
			double[] offset = null;

			@Option(names = {"-R", "--revert-array-attributes"}, paramLabel = "REVERT", description = "" +
					"Revert array attributes found in meta data of attributes of DATASET. " +
					"Does not affect any array attributes set explicitly through the RESOLUTION or OFFSET options.")
			Boolean revertArrayAttributes = false;

			@Option(names = { "--min"}, paramLabel = "MIN", description = "" +
					"Minimum value of contrast range for raw and channel data.")
			private Double min = null;

			@Option(names = { "--max"}, paramLabel = "MAX", description = "" +
					"Maximum value of contrast range for raw and channel data.")
			private Double max = null;

			@Option(names = {"--channel-dimension"}, defaultValue = "3", paramLabel = "CHANNEL_DIMENSION", description = "" +
					"Defines the dimension of a 4D dataset to be interpreted as channel axis. " +
					"0 <= CHANNEL_DIMENSION <= 3")
			private Integer channelDimension = 3;

			@Option(names =  {"--channels"}, paramLabel = "CHANNELS", arity = "1..*", converter = LongArrayTypeConverter.class, description = "" +
					"Use only this subset of channels for channel (4D) data. " +
					"Multiple subsets can be specified. " +
					"If no channels are specified, use all channels.")
			private long[][] channels = null;

			@Option(names = {"--name"}, paramLabel = "NAME", description = "" +
					"Specify name for dataset(s). " +
					"The names are assigned to datasets in the same order as specified. " +
					"If more datasets than names are specified, the remaining dataset names " +
					"will default to the last segment of the dataset path.")
			String[] name = null;

			@Option(names = {"--id-service-fallback"}, paramLabel = "ID_SERVICE_FALLBACK", defaultValue = "ask", converter = IdServiceFallback.TypeConverter.class,
					description = "" +
					"Set a fallback id service for scenarios in which an id service is not provided by the data backend, " +
					"e.g. when no `maxId' attribute is specified in an N5 dataset. Valid options are (case insensitive): " +
					"from-data — infer the max id and id service from the dataset (may take a long time for large datasets), " +
					"none — do not use an id service (requesting new ids will not be possible), " +
					"and ask — show a dialog to choose between those two options")
			IdServiceFallback idServiceFallback = null;

			@Option(names = {"--label-block-lookup-fallback"}, paramLabel = "LABEL_BLOCK_LOOKUP_FALLBACK", defaultValue = "ask", converter = LabelBlockLookupFallback.TypeConverter.class,
					description = "" +
							"Set a fallback label block lookup for scenarios in which a label block lookup is not provided by the data backend. " +
							"The label block lookup is used to process only relevant data during on-the-fly mesh generation. " +
							"Valid options are: " +
							"`complete' — always process the entire dataset (slow for large data), " +
							"`none' — do not process at all (no 3D representations/meshes available), " +
							"and `ask' — show a dialog to choose between those two options")
			LabelBlockLookupFallback labelBlockLookupFallback = null;

			@Option(names = {"--entire-container"}, paramLabel = "ENTIRE_CONTAINER", defaultValue = "false", description = "" +
					"If set to true, discover all datasets (Paintera format, multi-scale group, and N5 dataset) inside CONTAINER " +
					"and add to Paintera. The -d, --dataset and --name options will be ignored if ENTIRE_CONTAINER is set. " +
					"Datasets can be excluded through the --exclude option. The --include option overrides any exclusions.")
			Boolean addEntireContainer = null;

			@CommandLine.Option(names = {"--exclude"}, paramLabel = "EXCLUDE", arity = "1..*", description = "" +
					"Exclude any data set that matches any of EXCLUDE regex patterns.")
			String[] exclude = null;

			@CommandLine.Option(names = {"--include"}, paramLabel = "INCLUDE", arity = "1..*", description = "" +
					"Include any data set that matches any of INCLUDE regex patterns. " +
					"Takes precedence over EXCLUDE.")
			String[] include = null;

			@CommandLine.Option(names = {"--only-explicitly-included"}, description = "" +
					"When this option is set, use only data sets that were explicitly included via INCLUDE. " +
					"Equivalent to --exclude '.*'")
			Boolean onlyExplicitlyIncluded = false;

			private Predicate<String> isIncluded() {
				LOG.debug("Creating include pattern matcher for patterns {}", (Object) this.include);
				if (this.include == null)
					return s -> false;
				final Pattern[] patterns = Stream.of(this.include).map(Pattern::compile).toArray(Pattern[]::new);
				return s -> {
					for (final Pattern p : patterns)
						if (p.matcher(s).matches())
							return true;
					return false;
				};
			}

			private Predicate<String> isExcluded() {
				LOG.debug("Creating exclude pattern matcher for patterns {}", (Object) this.exclude);
				if (this.exclude == null)
					return s -> false;
				final Pattern[] patterns = Stream.of(this.exclude).map(Pattern::compile).toArray(Pattern[]::new);
				return s -> {
					for (final Pattern p : patterns)
						if (p.matcher(s).matches()) {
							LOG.debug("Excluded: Pattern {} matched {}", p, s);
							return true;
						}
					return false;
				};
			}

			private Predicate<String> isOnlyExplicitlyIncluded() {
				return s -> {
					LOG.debug("Is only explicitly included? {}", onlyExplicitlyIncluded);
					return onlyExplicitlyIncluded;
				};
			}

			private Predicate<String> useDataset() {
				return isIncluded().or((isExcluded().or(isOnlyExplicitlyIncluded())).negate());
			}
		}

		@Option(names = "--add-n5-container", arity = "1..*", required = true, description = "" +
				"Container of dataset(s) to be added. " +
				"If none is provided, default to Paintera project (if any). " +
				"Currently N5 file system and HDF5 containers are supported.")
		private File[] container = null;

		@CommandLine.ArgGroup(multiplicity = "1", exclusive = false)
		private Options options = null;

		private void addToViewer(
				final PainteraBaseView viewer,
				final Supplier<String> projectDirectory) throws IOException {

			if (options == null)
				return;

			if (options.datasets == null && !options.addEntireContainer) {
				LOG.warn("" +
						"No datasets will be added: " +
						"--add-n5-container was specified but no dataset was provided through the -d, --dataset option. " +
						"To add all datasets of a container, please set the --entire-container option and use the " +
						"--exclude and --include options.");
				return;
			}

			if (container == null && projectDirectory == null) {
				LOG.warn("Will not add any datasets: " +
						"No container or project directory specified.");
				return;
			}

			final File[] containers = container == null
					? new File[] {new File(projectDirectory.get())}
					: container;

			for (final File container : containers) {
				LOG.debug("Adding datasets for container {}", container);
				final String containerPath = container.getAbsolutePath();
				final N5Writer n5 = N5Helpers.n5Writer(containerPath);
				final Predicate<String> datasetFilter = options.useDataset();
				final ExecutorService es = getDiscoveryExecutorService();
				final String[] datasets = options.addEntireContainer
						? datasetsAsRawChannelLabel(n5, N5Helpers.discoverDatasets(n5, () -> true, es).stream().filter(datasetFilter).collect(Collectors.toList()))
						: options.datasets;
				final String[] names = options.addEntireContainer
						? null
						: options.name;
				for (int index = 0; index < datasets.length; ++index) {
					final String dataset = datasets[index];
					PainteraCommandLineArgs.addToViewer(
							viewer,
							projectDirectory,
							containerPath,
							dataset,
							options.revertArrayAttributes,
							options.resolution,
							options.offset,
							options.min,
							options.max,
							options.channelDimension,
							options.channels,
							options.idServiceFallback.getIdServiceGenerator(),
							options.labelBlockLookupFallback.getGenerator(),
							names == null ? null : getIfInRange(names, index));
				}
			}
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int DEFAULT_NUM_SCREEN_SCALES = 5;

	private static final double DEFAULT_HIGHEST_SCREEN_SCALE = 1.0;

	private static final double DEFAULT_SCREEN_SCALE_FACTOR = 0.5;

	@Option(names = {"--width"}, paramLabel = "WIDTH", required = false, description = "Initial width of viewer. " +
			"Defaults to 800. Overrides width stored in project.")
	private int width = -1;

	@Option(names = {"--height"}, paramLabel = "HEIGHT", required = false, description = "Initial height of viewer. " +
			"Defaults to 600. Overrides height stored in project.")
	private int height = -1;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
	private boolean helpRequested;

	@Option(names = "--num-screen-scales", paramLabel = "NUM_SCREEN_SCALES", required = false, description = "Number " +
			"of screen scales, defaults to 3. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Integer numScreenScales;

	@Option(names = "--highest-screen-scale", paramLabel = "HIGHEST_SCREEN_SCALE", required = false, description =
			"Highest screen scale, restricted to the interval (0,1], defaults to 1. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Double highestScreenScale;

	@Option(names = "--screen-scale-factor", paramLabel = "SCREEN_SCALE_FACTOR", required = false, description =
			"Scalar value from the open interval (0,1) that defines how screen scales diminish in each dimension. " +
					"Defaults to 0.5. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].")
	private Double screenScaleFactor;

	@Option(names = "--screen-scales", paramLabel = "SCREEN_SCALES", required = false, description = "Explicitly set " +
			"screen scales. Must be strictly monotonically decreasing values in from the interval (0,1]. Overrides " +
			"all other screen scale options. If no scale option is specified, scales default to [1.0, 0.5, 0.25, 0.125, 0.0625].", arity = "1..*", split = ",")
	private double[] screenScales;

	@Parameters(index = "0", paramLabel = "PROJECT", arity = "0..1", description = "Optional project N5 root (N5 or " +
			"FileSystem).")
	private String project;

	@Option(names = "--print-error-codes", paramLabel = "PRINT_ERROR_CODES", required = false, description = "List all" +
			" error codes and exit.")
	private Boolean printErrorCodes;

	@Option(names = "--default-to-temp-directory", paramLabel = "DEFAULT_TO_TEMP_DIRECTORY", required = false,
			description = "Default to temporary directory instead of showing dialog when PROJECT is not specified. " +
					"DEPRECATED: This flag will have no effect and will be removed in a future release.")
	private Boolean defaultToTempDirectory;

	@Option(names = "--version", paramLabel = "PRINT_VERSION_STRING", required = false, description = "Print version string and exit")
	private Boolean printVersionString;

	@CommandLine.ArgGroup(exclusive = false, multiplicity = "0..*")
	private AddDatasetArgument[] n5datasets = null;

	private boolean screenScalesProvided = false;

	@Override
	public Boolean call() throws Exception
	{
		width = width <= 0 ? -1 : width;
		height = height <= 0 ? -1 : height;

		screenScalesProvided = screenScales != null || numScreenScales != null || highestScreenScale != null || screenScaleFactor != null;

		numScreenScales = Optional.ofNullable(this.numScreenScales).filter(n -> n > 0).orElse(DEFAULT_NUM_SCREEN_SCALES);
		highestScreenScale = Optional.ofNullable(highestScreenScale).filter(s -> s > 0 && s <= 1).orElse(DEFAULT_HIGHEST_SCREEN_SCALE);
		screenScaleFactor = Optional.ofNullable(screenScaleFactor).filter(f -> f > 0 && f < 1).orElse(DEFAULT_SCREEN_SCALE_FACTOR);
		screenScales = screenScales == null
		               ? createScreenScales(numScreenScales, highestScreenScale, screenScaleFactor)
		               : screenScales;

		if (screenScales != null)
		{
			checkScreenScales(screenScales);
		}

		printErrorCodes = printErrorCodes == null ? false : printErrorCodes;
		if (printErrorCodes)
		{
			LOG.info("Error codes:");
			for (final Paintera2.Error error : Paintera2.Error.values())
			{
				LOG.info("{} -- {}", error.getCode(), error.getDescription());
			}
			return false;
		}

		printVersionString = printVersionString == null ? false : printVersionString;
		if (printVersionString)
		{
			LOG.info("Paintera version: {}", Version.VERSION_STRING);
			return false;
		}

		if (defaultToTempDirectory != null)
			LOG.warn("The --default-to-temp-directory flag was deprecated and will be removed in a future release.");

		defaultToTempDirectory = defaultToTempDirectory == null ? false : defaultToTempDirectory;

		return true;
	}

	public int width(final int defaultWidth)
	{
		return width <= 0 ? defaultWidth : width;
	}

	public int height(final int defaultHeight)
	{
		return height <= 0 ? defaultHeight : height;
	}

	public String project()
	{
		final String returnedProject = this.project == null ? this.project : new File(project).getAbsolutePath();
		LOG.debug("Return project={}", returnedProject);
		return returnedProject;
	}

	public double[] screenScales()
	{
		return this.screenScales.clone();
	}

	public boolean defaultToTempDirectory()
	{
		return this.defaultToTempDirectory;
	}

	public boolean wereScreenScalesProvided()
	{
		return this.screenScalesProvided;
	}

	public void addToViewer(final PainteraBaseView viewer, final Supplier<String> projectDirectory) {
		if (this.n5datasets == null)
			return;
		Stream.of(this.n5datasets).forEach(ThrowingConsumer.unchecked(ds -> ds.addToViewer(viewer, projectDirectory)));
	}

	private static double[] createScreenScales(final int numScreenScales, final double highestScreenScale, final
	double screenScaleFactor)
	throws ZeroLengthScreenScales
	{
		if (numScreenScales <= 1) { throw new ZeroLengthScreenScales(); }

		final double[] screenScales = new double[numScreenScales];
		screenScales[0] = highestScreenScale;
		for (int i = 1; i < screenScales.length; ++i)
		{
			screenScales[i] = screenScaleFactor * screenScales[i - 1];
		}
		LOG.debug("Returning screen scales {}", screenScales);
		return screenScales;
	}

	private static void checkScreenScales(final double[] screenScales)
	throws ZeroLengthScreenScales, InvalidScreenScaleValue, ScreenScaleNotDecreasing
	{
		if (screenScales.length == 0) { throw new ZeroLengthScreenScales(); }

		if (screenScales[0] <= 0 || screenScales[0] > 1) { throw new InvalidScreenScaleValue(screenScales[0]); }

		for (int i = 1; i < screenScales.length; ++i)
		{
			final double prev = screenScales[i - 1];
			final double curr = screenScales[i];
			// no check for > 1 necessary because already checked for
			// monotonicity
			if (curr <= 0) { throw new InvalidScreenScaleValue(curr); }
			if (prev <= curr) { throw new ScreenScaleNotDecreasing(prev, curr); }
		}

	}

	public static class ZeroLengthScreenScales extends Exception
	{

	}

	public static class InvalidScreenScaleValue extends Exception
	{
		InvalidScreenScaleValue(final double scale)
		{
			super("Screen scale " + scale + " not in legal interval (0,1]");
		}
	}

	public static class ScreenScaleNotDecreasing extends Exception
	{
		public ScreenScaleNotDecreasing(final double first, final double second)
		{
			super("Second screen scale " + second + " larger than or equal to first " + first);
		}
	}

	private static <T> T getIfInRange(T[] array, final int index) {
		return index < array.length ? array[index] : null;
	}

	private static IdService findMaxIdForIdServiceAndWriteToN5(
			final N5Writer n5,
			final String dataset,
			final DataSource<? extends IntegerType<?>, ?> source) throws IOException {
		final long maxId = Math.max(findMaxId(source), 0);
		n5.setAttribute(dataset, N5Helpers.MAX_ID_KEY, maxId);
		return new N5IdService(n5, dataset, maxId + 1);
	}

	private static long findMaxId(final DataSource<? extends IntegerType<?>, ?> source) {
		final RandomAccessibleInterval<? extends IntegerType<?>> rai = source.getDataSource(0, 0);

		final int[] blockSize = blockSizeFromRai(rai);
		final List<Interval> intervals = Grids.collectAllContainedIntervals(Intervals.minAsLongArray(rai), Intervals.maxAsLongArray(rai), blockSize);

		final ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		final List<Future<Long>> futures = new ArrayList<>();
		for (final Interval interval : intervals)
			futures.add(es.submit(() -> findMaxId(Views.interval(rai, interval))));
		es.shutdown();
		final long maxId = futures
				.stream()
				.map(ThrowingFunction.unchecked(Future::get))
				.mapToLong(Long::longValue)
				.max()
				.orElse(Label.getINVALID());
		LOG.debug("Found max id {}", maxId);
		return maxId;
	}

	private static long findMaxId(final RandomAccessibleInterval<? extends IntegerType<?>> rai) {
		long maxId = org.janelia.saalfeldlab.labels.Label.getINVALID();
		for (final IntegerType<?> t : Views.iterable(rai)) {
			final long id = t.getIntegerLong();
			if (id > maxId)
				maxId = id;
		}
		return maxId;
	}

	private static int[] blockSizeFromRai(final RandomAccessibleInterval<?> rai) {
		if (rai instanceof AbstractCellImg<?, ?, ?, ?>) {
			final CellGrid cellGrid = ((AbstractCellImg<?, ?, ?, ?>) rai).getCellGrid();
			final int[] blockSize = new int[cellGrid.numDimensions()];
			cellGrid.cellDimensions(blockSize);
			LOG.debug("{} is a cell img with block size {}", rai, blockSize);
			return blockSize;
		}
		int argMaxDim = argMaxDim(rai);
		final int[] blockSize = Intervals.dimensionsAsIntArray(rai);
		blockSize[argMaxDim] = 1;
		return blockSize;
	}

	private static int argMaxDim(final Dimensions dims) {
		long max = -1;
		int argMax = -1;
		for (int d = 0; d < dims.numDimensions(); ++d) {
			if (dims.dimension(d) > max) {
				max = dims.dimension(d);
				argMax = d;
			}
		}
		return argMax;
	}

	private static void addToViewer(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory,
			final String containerPath,
			final String group,
			final boolean revertArrayAttributes,
			double[] resolution,
			double[] offset,
			Double min,
			Double max,
			final int  channelDimension,
			long[][] channels,
			final IdServiceFallbackGenerator idServiceFallback,
			final LabelBlockLookupFallbackGenerator labelBlockLookupFallback,
			String name) throws IOException {
		final N5Writer container = N5Helpers.n5Writer(containerPath, 64, 64, 64);

		if (!container.exists(group)) {
			LOG.info("Group {} does not exist in container {}", group, container);
			return;
		}

		final boolean isPainteraDataset = N5Helpers.isPainteraDataset(container, group);
		final String dataGroup = isPainteraDataset ? String.format("%s/data", group) : group;
		resolution = resolution == null ? N5Helpers.getResolution(container, dataGroup, revertArrayAttributes) : resolution;
		offset = offset == null ? N5Helpers.getOffset(container, isPainteraDataset ? String.format("%s/data", group) : group, revertArrayAttributes) : offset;
		name = name == null ? getLastEntry(group.split("/")) : name;
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0.0, 0.0, offset[0],
				0.0, resolution[1], 0.0, offset[1],
				0.0, 0.0, resolution[2], offset[2]);
		final boolean isLabelMultisetType = N5Types.isLabelMultisetType(container, dataGroup);
		final DatasetAttributes attributes = N5Helpers.getDatasetAttributes(container, group);
		final DataType dataType = attributes.getDataType();
		final boolean isLabelData = N5Types.isLabelData(dataType, isLabelMultisetType);
		final boolean isChannelData = !isLabelData && attributes.getNumDimensions() == 4;

		min = min == null ? N5Types.minForType(dataType) : min;
		max = max == null ? N5Types.maxForType(dataType) : max;

		if (isLabelData)
			viewer.addState((SourceState<?, ?>) makeLabelState(viewer, projectDirectory, container, group, name, resolution, offset));
		else if (isChannelData) {
			channels = channels == null ? new long[][] { PainteraCommandLineArgs.range((int) attributes.getDimensions()[channelDimension]) } : channels;
			final String fname = name;
			final Function<long[], String> nameBuilder = channels.length == 1
					? c -> fname
					: c -> String.format("%s-%s", fname, Arrays.toString(c));
			for (long[] channel : channels) {
				viewer.addState(makeChannelSourceState(viewer, container, group, transform, channelDimension, channel, min, max, nameBuilder.apply(channel)));
			}
		} else {
			viewer.addState((SourceState<?, ?>) makeRawSourceState(viewer, container, group, resolution, offset, min, max, name));
		}
	}

	private interface IdServiceFallbackGenerator {
		IdService get(
				final N5Writer n5,
				final String dataset,
				final DataSource<? extends IntegerType<?>, ?> source) throws IOException;
	}

	private interface LabelBlockLookupFallbackGenerator {
		LabelBlockLookup get(
				final N5Reader n5,
				final String group,
				final DataSource<?, ?> source);
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> ConnectomicsLabelState<D, T> makeLabelState(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory,
			final N5Writer container,
			final String dataset,
			final String name,
			final double[] resolution,
			final double[] offset) {

		final N5Backend<D, T> backend = N5Backend.createFrom(
				container,
				dataset,
				projectDirectory,
				viewer.getPropagationQueue());
		return new ConnectomicsLabelState<D, T>(
				backend,
				viewer.viewer3D().meshesGroup(),
				viewer.viewer3D().viewFrustumProperty(),
				viewer.viewer3D().eyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getQueue(),
				0, // TODO is this the right priority?
				name,
				resolution,
				offset,
				null);
	}

	private static <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileRealType<D, T> & NativeType<T>> SourceState<D, T> makeRawSourceState(
			final PainteraBaseView viewer,
			final N5Reader container,
			final String group,
			final double[] resolution,
			final double[] offset,
			final double min,
			final double max,
			final String name
	) throws IOException {
		try {
			final N5BackendRaw<D, T> backend = new N5BackendRaw<>(
					N5Meta.fromReader(container, group).getWriter(),
					group);
			final ConnectomicsRawState<D, T> state =  new ConnectomicsRawState<>(
					backend,
					viewer.getQueue(),
					0,
					name,
					resolution,
					offset);
			state.converter().setMin(min);
			state.converter().setMax(max);
			return state;
		} catch (final ReflectionException e) {
			throw new IOException(e);
		}
	}

	private static <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileRealType<D, T> & NativeType<T>> ChannelSourceState<D, T, ?, ?> makeChannelSourceState(
			final PainteraBaseView viewer,
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final int channelDimension,
			final long[] channels,
			final double min,
			final double max,
			final String name
	) throws IOException {
		try {
			final N5ChannelDataSource<D, T> channelSource = N5ChannelDataSource.zeroExtended(
					N5Meta.fromReader(reader, dataset),
					transform,
					name,
					viewer.getQueue(),
					0,
					channelDimension,
					channels);
			return new ChannelSourceState<>(
					channelSource,
					new ARGBCompositeColorConverter.InvertingImp0<>(channels.length, min, max),
					new ARGBCompositeAlphaAdd(),
					name);
		} catch (final ReflectionException | DataTypeNotSupported e) {
			throw new IOException(e);
		}
	}

	private static <T> T getLastEntry(T[] array) {
		return array.length > 0 ? array[array.length-1] : null;
	}

	private static long[] range(final int N) {
		final long[] range = new long[N];
		Arrays.setAll(range, d -> d);
		return range;
	}

	private static String[] datasetsAsRawChannelLabel(final N5Reader n5, final Collection<String> datasets) throws IOException {
		final List<String> rawDatasets = new ArrayList<>();
		final List<String> channelDatasets= new ArrayList<>();
		final List<String> labelDatsets = new ArrayList<>();
		for (final String dataset : datasets) {
			final DatasetAttributes attributes = N5Helpers.getDatasetAttributes(n5, dataset);
			if (attributes.getNumDimensions() == 4)
				channelDatasets.add(dataset);
			else if (attributes.getNumDimensions() == 3) {
				if (
						N5Helpers.isPainteraDataset(n5, dataset) && n5.getAttribute(dataset, N5Helpers.PAINTERA_DATA_KEY, JsonObject.class).get("type").getAsString().equals("label") ||
						N5Types.isLabelData(attributes.getDataType(), N5Types.isLabelMultisetType(n5, dataset)))
					labelDatsets.add(dataset);
				else
					rawDatasets.add(dataset);
			}
		}
		return Stream.of(rawDatasets, channelDatasets, labelDatsets).flatMap(List::stream).toArray(String[]::new);
	}

}
