package org.janelia.saalfeldlab.paintera;

import ch.qos.logback.classic.Level;
import com.google.gson.JsonObject;
import com.pivovarit.function.ThrowingConsumer;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.LabelSourceUtils;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.dialogs.DataSourceDialogs;
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.janelia.saalfeldlab.util.n5.DatasetDiscoveryKt.discoverAndParseRecursive;

@Command(name = "Paintera", showDefaultValues = true, resourceBundle = "org.janelia.saalfeldlab.paintera.PainteraCommandLineArgs", usageHelpWidth = 120,
		parameterListHeading = "%n@|bold,underline Parameters|@:%n",
		optionListHeading = "%n@|bold,underline Options|@:%n")
public class PainteraCommandLineArgs implements Callable<Boolean> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final int DEFAULT_NUM_SCREEN_SCALES = 5;
	private static final double DEFAULT_HIGHEST_SCREEN_SCALE = 1.0;
	private static final double DEFAULT_SCREEN_SCALE_FACTOR = 0.5;

	@Option(names = {"--log-level"})
	private final Level logLevel = null;

	@Option(names = {"--log-level-for"}, split = ",")
	private final Map<String, Level> logLevelsByName = null;

	@ArgGroup(exclusive = false, multiplicity = "0..*")
	private final AddDatasetArgument[] n5datasets = null;

	@Option(names = {"--width"}, paramLabel = "WIDTH", showDefaultValue = CommandLine.Help.Visibility.NEVER)
	private int width = -1;

	@Option(names = {"--height"}, paramLabel = "HEIGHT", showDefaultValue = CommandLine.Help.Visibility.NEVER)
	private int height = -1;

	@Option(names = {"-h", "--help"}, usageHelp = true)
	private boolean helpRequested;

	@Option(names = "--num-screen-scales", paramLabel = "NUM_SCREEN_SCALES")
	private Integer numScreenScales;

	@Option(names = "--highest-screen-scale", paramLabel = "HIGHEST_SCREEN_SCALE")
	private Double highestScreenScale;

	@Option(names = "--screen-scale-factor", paramLabel = "SCREEN_SCALE_FACTOR")
	private Double screenScaleFactor;

	@Option(names = "--screen-scales", paramLabel = "SCREEN_SCALES", arity = "1..*", split = ",")
	private double[] screenScales;

	@Parameters(index = "0", paramLabel = "PROJECT", arity = "0..1", descriptionKey = "project")
	private String project;

	@Option(names = "--print-error-codes", paramLabel = "PRINT_ERROR_CODES")
	private Boolean printErrorCodes;

	@Option(names = "--default-to-temp-directory", paramLabel = "DEFAULT_TO_TEMP_DIRECTORY")
	private Boolean defaultToTempDirectory;

	@Option(names = "--version", paramLabel = "PRINT_VERSION_STRING")
	private Boolean printVersionString;

	private boolean screenScalesProvided = false;

	private static double[] createScreenScales(final int numScreenScales, final double highestScreenScale, final
	double screenScaleFactor)
			throws ZeroLengthScreenScales {

		if (numScreenScales <= 1) {
			throw new ZeroLengthScreenScales();
		}

		final double[] screenScales = new double[numScreenScales];
		screenScales[0] = highestScreenScale;
		for (int i = 1; i < screenScales.length; ++i) {
			screenScales[i] = screenScaleFactor * screenScales[i - 1];
		}
		LOG.debug("Returning screen scales {}", screenScales);
		return screenScales;
	}

	private static void checkScreenScales(final double[] screenScales)
			throws ZeroLengthScreenScales, InvalidScreenScaleValue, ScreenScaleNotDecreasing {

		if (screenScales.length == 0) {
			throw new ZeroLengthScreenScales();
		}

		if (screenScales[0] <= 0 || screenScales[0] > 1) {
			throw new InvalidScreenScaleValue(screenScales[0]);
		}

		for (int i = 1; i < screenScales.length; ++i) {
			final double prev = screenScales[i - 1];
			final double curr = screenScales[i];
			// no check for > 1 necessary because already checked for
			// monotonicity
			if (curr <= 0) {
				throw new InvalidScreenScaleValue(curr);
			}
			if (prev <= curr) {
				throw new ScreenScaleNotDecreasing(prev, curr);
			}
		}

	}

	private static <T> T getIfInRange(final T[] array, final int index) {

		return index < array.length ? array[index] : null;
	}

	private static IdService findMaxIdForIdServiceAndWriteToN5(
			final N5Writer n5,
			final String dataset,
			final DataSource<? extends IntegerType<?>, ?> source) throws IOException {

		final long maxId = Math.max(LabelSourceUtils.findMaxId(source), 0);
		n5.setAttribute(dataset, N5Helpers.MAX_ID_KEY, maxId);
		return new N5IdService(n5, dataset, maxId + 1);
	}

	private static void addToViewer(
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory,
			final MetadataState metadataState,
			final boolean reverseArrayAttributes, //TODO meta to be consistent with previous code, we should reverse the axis order if this is true (should we?)
			final int channelDimension,
			long[][] channels,
			final IdServiceFallbackGenerator idServiceFallback,
			final LabelBlockLookupFallbackGenerator labelBlockLookupFallback,
			String name) throws IOException {

		final boolean isLabelData = metadataState.isLabel();
		DatasetAttributes attributes = metadataState.getDatasetAttributes();
		final boolean isChannelData = !isLabelData && attributes.getNumDimensions() == 4;

		if (isLabelData) {
			viewer.addState((SourceState<?, ?>)makeLabelState(viewer, metadataState, name));
		} else if (isChannelData) {
			channels = channels == null ? new long[][]{PainteraCommandLineArgs.range((int)attributes.getDimensions()[channelDimension])} : channels;
			final String fname = name;
			final Function<long[], String> nameBuilder = channels.length == 1
					? c -> fname
					: c -> String.format("%s-%s", fname, Arrays.toString(c));
			for (final long[] channel : channels) {
				viewer.addState(makeChannelSourceState(viewer, metadataState, channelDimension, channel, nameBuilder.apply(channel)));
			}
		} else {
			viewer.addState((SourceState<?, ?>)makeRawSourceState(viewer, metadataState, name));
		}
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> ConnectomicsLabelState<D, T> makeLabelState(
			final PainteraBaseView viewer,
			final MetadataState metadataState,
			final String name) {

		final N5BackendLabel<D, T> backend = N5BackendLabel.createFrom(metadataState, viewer.getPropagationQueue());
		return new ConnectomicsLabelState<>(
				backend,
				viewer.viewer3D().getMeshesGroup(),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getQueue(),
				0, // TODO is this the right priority?
				name,
				null);
	}

	private static <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileRealType<D, T> & NativeType<T>> SourceState<D, T> makeRawSourceState(
			final PainteraBaseView viewer,
			MetadataState metadataState,
			final String name
	) {

		final N5BackendRaw<D, T> backend = new N5BackendRaw<>(metadataState);
		final ConnectomicsRawState<D, T> state = new ConnectomicsRawState<>(
				backend,
				viewer.getQueue(),
				0,
				name);
		state.converter().setMin(metadataState.getMinIntensity());
		state.converter().setMax(metadataState.getMaxIntensity());
		return state;
	}

	private static <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileRealType<D, T> & NativeType<T>> ConnectomicsChannelState<D, T, ?, ?, ?> makeChannelSourceState(
			final PainteraBaseView viewer,
			MetadataState metadataState,
			final int channelDimension,
			final long[] channels,
			final String name
	) {

		final N5BackendChannel<D, T> backend = new N5BackendChannel<>(
				metadataState,
				Arrays.stream(channels).mapToInt(l -> (int)l).toArray(),
				channelDimension
		);
		return new ConnectomicsChannelState<>(
				backend,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				name);
	}

	private static long[] range(final int N) {

		final long[] range = new long[N];
		Arrays.setAll(range, d -> d);
		return range;
	}

	private static String[] datasetsAsRawChannelLabel(final N5Reader n5, final Collection<String> datasets) throws IOException {

		final List<String> rawDatasets = new ArrayList<>();
		final List<String> channelDatasets = new ArrayList<>();
		final List<String> labelDatsets = new ArrayList<>();
		for (final String dataset : datasets) {
			final DatasetAttributes attributes = N5Helpers.getDatasetAttributes(n5, dataset);
			if (attributes.getNumDimensions() == 4)
				channelDatasets.add(dataset);
			else if (attributes.getNumDimensions() == 3) {
				if (
						N5Helpers.isPainteraDataset(n5, dataset) && n5.getAttribute(dataset, N5Helpers.PAINTERA_DATA_KEY, JsonObject.class).get("type")
								.getAsString()
								.equals("label") ||
								N5Types.isLabelData(attributes.getDataType(), N5Types.isLabelMultisetType(n5, dataset)))
					labelDatsets.add(dataset);
				else
					rawDatasets.add(dataset);
			}
		}
		return Stream.of(rawDatasets, channelDatasets, labelDatsets).flatMap(List::stream).toArray(String[]::new);
	}

	@Override
	public Boolean call() throws Exception {

		LogUtils.setRootLoggerLevel(logLevel == null ? Level.INFO : logLevel);

		width = width <= 0 ? -1 : width;
		height = height <= 0 ? -1 : height;

		screenScalesProvided = screenScales != null || numScreenScales != null || highestScreenScale != null || screenScaleFactor != null;

		numScreenScales = Optional.ofNullable(this.numScreenScales).filter(n -> n > 0).orElse(DEFAULT_NUM_SCREEN_SCALES);
		highestScreenScale = Optional.ofNullable(highestScreenScale).filter(s -> s > 0 && s <= 1).orElse(DEFAULT_HIGHEST_SCREEN_SCALE);
		screenScaleFactor = Optional.ofNullable(screenScaleFactor).filter(f -> f > 0 && f < 1).orElse(DEFAULT_SCREEN_SCALE_FACTOR);
		screenScales = screenScales == null
				? createScreenScales(numScreenScales, highestScreenScale, screenScaleFactor)
				: screenScales;

		if (screenScales != null) {
			checkScreenScales(screenScales);
		}

		printErrorCodes = printErrorCodes == null ? false : printErrorCodes;
		if (printErrorCodes) {
			LOG.info("Error codes:");
			for (final Error error : Error.values()) {
				LOG.info("{} -- {}", error.getCode(), error.getDescription());
			}
			return false;
		}

		printVersionString = printVersionString == null ? false : printVersionString;
		if (printVersionString) {
			System.out.println(Version.VERSION_STRING);
			return false;
		}

		if (defaultToTempDirectory != null)
			LOG.warn("The --default-to-temp-directory flag was deprecated and will be removed in a future release.");

		defaultToTempDirectory = defaultToTempDirectory == null ? false : defaultToTempDirectory;

		return true;
	}

	public int width(final int defaultWidth) {

		return width <= 0 ? defaultWidth : width;
	}

	public int height(final int defaultHeight) {

		return height <= 0 ? defaultHeight : height;
	}

	public String project() {

		final String returnedProject = this.project == null ? this.project : new File(project).getAbsolutePath();
		LOG.debug("Return project={}", returnedProject);
		return returnedProject;
	}

	public double[] screenScales() {

		return this.screenScales.clone();
	}

	public boolean defaultToTempDirectory() {

		return this.defaultToTempDirectory;
	}

	public boolean wereScreenScalesProvided() {

		return this.screenScalesProvided;
	}

	public Level getLogLevel() {

		return this.logLevel;
	}

	public Map<String, Level> getLogLevelsByName() {

		return this.logLevelsByName == null ? Collections.emptyMap() : this.logLevelsByName;
	}

	public void addToViewer(final PainteraBaseView viewer, final Supplier<String> projectDirectory) {

		if (this.n5datasets == null)
			return;
		Stream.of(this.n5datasets).forEach(ThrowingConsumer.unchecked(ds -> ds.addToViewer(viewer, projectDirectory)));
	}

	private enum IdServiceFallback {
		ASK(DataSourceDialogs::getN5IdServiceFromData),
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

			@Override
			public IdServiceFallback convert(final String s) throws NoMatchFound {

				try {
					return IdServiceFallback.valueOf(s.replace("-", "_").toUpperCase());
				} catch (final IllegalArgumentException e) {
					throw new NoMatchFound(s, e);
				}
			}

			private static class NoMatchFound extends Exception {

				private final String selection;

				private NoMatchFound(final String selection, final Throwable e) {

					super(
							String.format(
									"No match found for selection `%s'. Pick any of these options (case insensitive): %s",
									selection,
									Arrays.asList(IdServiceFallback.values())),
							e);
					this.selection = selection;
				}
			}
		}
	}

	private enum LabelBlockLookupFallback {
		ASK(DataSourceDialogs::getLabelBlockLookupFromN5DataSource),
		NONE((c, g, s) -> new LabelBlockLookupNoBlocks()),
		COMPLETE((c, g, s) -> LabelBlockLookupAllBlocks.fromSource(s));

		private final LabelBlockLookupFallbackGenerator generator;

		LabelBlockLookupFallback(final LabelBlockLookupFallbackGenerator generator) {

			this.generator = generator;
		}

		public LabelBlockLookupFallbackGenerator getGenerator() {

			return this.generator;
		}

		private static class TypeConverter implements CommandLine.ITypeConverter<LabelBlockLookupFallback> {

			@Override
			public LabelBlockLookupFallback convert(final String s) throws TypeConverter.NoMatchFound {

				try {
					return LabelBlockLookupFallback.valueOf(s.replace("-", "_").toUpperCase());
				} catch (final IllegalArgumentException e) {
					throw new TypeConverter.NoMatchFound(s, e);
				}
			}

			private static class NoMatchFound extends Exception {

				private final String selection;

				private NoMatchFound(final String selection, final Throwable e) {

					super(
							String.format(
									"No match found for selection `%s'. Pick any of these options (case insensitive): %s",
									selection,
									Arrays.asList(LabelBlockLookupFallback.values())),
							e);
					this.selection = selection;
				}
			}
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

	private static class LongArrayTypeConverter implements CommandLine.ITypeConverter<long[]> {

		@Override
		public long[] convert(final String value) {

			return Stream
					.of(value.split(","))
					.mapToLong(Long::parseLong)
					.toArray();
		}
	}

	private static final class AddDatasetArgument {

		private static ExecutorService DISCOVERY_EXECUTOR_SERVICE = null;
		@Option(names = "--add-n5-container", arity = "1..*", required = true)
		private final String[] container = null;
		@ArgGroup(multiplicity = "1", exclusive = false)
		private final Options options = null;

		private static synchronized ExecutorService getDiscoveryExecutorService() {

			if (DISCOVERY_EXECUTOR_SERVICE == null || DISCOVERY_EXECUTOR_SERVICE.isShutdown()) {
				DISCOVERY_EXECUTOR_SERVICE = Executors.newFixedThreadPool(
						12,
						new NamedThreadFactory("dataset-discovery-%d", true));
				LOG.debug("Created discovery executor service {}", DISCOVERY_EXECUTOR_SERVICE);
			}
			return DISCOVERY_EXECUTOR_SERVICE;
		}

		private void addToViewer(final PainteraBaseView viewer, final Supplier<String> projectDirectory) throws IOException {

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

			final String[] containers = container == null
					? new String[]{projectDirectory.get()}
					: container;

			for (final String container : containers) {
				LOG.debug("Adding datasets for container {}", container);

				N5Reader n5Container = Paintera.getN5Factory().openWriterElseOpenReader(container);

				final Predicate<String> datasetFilter = options.useDataset();
				final String[] datasets;
				if (options.addEntireContainer) {
					Optional<N5TreeNode> rootNode = Optional.ofNullable(discoverAndParseRecursive(n5Container));
					if (rootNode.isPresent()) {
						final List<String> validGroups = N5Helpers.validPainteraGroupMap(rootNode.get()).keySet().stream()
								.filter(datasetFilter)
								.collect(Collectors.toList());
						datasets = datasetsAsRawChannelLabel(n5Container, validGroups);
					} else {
						datasets = new String[]{};
					}
				} else {
					datasets = options.datasets;
				}
				final String[] names = options.addEntireContainer
						? null
						: options.name;
				for (int index = 0; index < datasets.length; ++index) {
					final String dataset = datasets[index];

					if (!n5Container.exists(dataset)) {
						LOG.warn("Group {} does not exist in container {}", dataset, n5Container);
						return;
					}

					final var containerState = new N5ContainerState(n5Container);
					final var metadata = discoverAndParseRecursive(n5Container);

					final Stream<N5TreeNode> flatTree = N5TreeNode.flattenN5Tree(metadata);
					final Optional<N5TreeNode> matchingNode = flatTree
							.filter(node -> node.getNodeName().equals(dataset))
							.findFirst();
					final var metadataState = matchingNode
							.map(N5TreeNode::getMetadata)
							.filter(MetadataUtils::metadataIsValid)
							.flatMap(md -> Optional.ofNullable(MetadataUtils.createMetadataState(containerState, md)))
							.get();

					//TODO currenctly this always updates the metadataState with options.resolution and offset.
					//	However, options.resolution and options.offset have default values if not provided.
					//	So, if the metadata specifies resolution for example, and no resolution is specified
					//	via command line to override, it will still be overridden withe default (identity).
					// 	It would be better to either reomve the default, or only override if the default
					//	value is explicitly specified.

					metadataState.updateTransform(options.resolution, options.offset);

					Optional.ofNullable(options.min).ifPresent(metadataState::setMinIntensity);
					Optional.ofNullable(options.max).ifPresent(metadataState::setMinIntensity);

					PainteraCommandLineArgs.addToViewer(
							viewer,
							projectDirectory,
							metadataState,
							options.reverseArrayAttributes,
							options.channelDimension,
							options.channels,
							options.idServiceFallback.getIdServiceGenerator(),
							options.labelBlockLookupFallback.getGenerator(),
							names == null ? metadataState.getGroup() : getIfInRange(names, index));
				}
			}
		}

		private static final class Options {

			@Option(names = {"--min"}, paramLabel = "MIN")
			private final Double min = null;

			@Option(names = {"--max"}, paramLabel = "MAX")
			private final Double max = null;

			@Option(names = {"--channel-dimension"}, defaultValue = "3", paramLabel = "CHANNEL_DIMENSION")
			private final Integer channelDimension = 3;

			@Option(names = {"--channels"}, paramLabel = "CHANNELS", arity = "1..*", converter = LongArrayTypeConverter.class)
			private final long[][] channels = null;

			@Option(names = {"-d", "--dataset"}, paramLabel = "DATASET", arity = "1..*", required = true)
			String[] datasets = null;

			@Option(names = {"-r", "--resolution"}, paramLabel = "RESOLUTION", split = ",")
			double[] resolution = new double[]{1.0, 1.0, 1.0};

			@Option(names = {"-o", "--offset"}, paramLabel = "OFFSET", split = ",")
			double[] offset = new double[]{0.0, 0.0, 0.0};

			@Option(names = {"-R", "--reverse-array-attributes"}, paramLabel = "REVERT")
			Boolean reverseArrayAttributes = false;

			@Option(names = {"--name"}, paramLabel = "NAME")
			String[] name = null;

			@Option(names = {
					"--id-service-fallback"}, paramLabel = "ID_SERVICE_FALLBACK", defaultValue = "ask", converter = IdServiceFallback.TypeConverter.class)
			IdServiceFallback idServiceFallback = null;

			@Option(names = {
					"--label-block-lookup-fallback"}, paramLabel = "LABEL_BLOCK_LOOKUP_FALLBACK", defaultValue = "ask", converter = LabelBlockLookupFallback.TypeConverter.class)
			LabelBlockLookupFallback labelBlockLookupFallback = null;

			@Option(names = {"--entire-container"}, paramLabel = "ENTIRE_CONTAINER", defaultValue = "false")
			Boolean addEntireContainer = null;

			@Option(names = {"--exclude"}, paramLabel = "EXCLUDE", arity = "1..*")
			String[] exclude = null;

			@Option(names = {"--include"}, paramLabel = "INCLUDE", arity = "1..*")
			String[] include = null;

			@Option(names = {"--only-explicitly-included"})
			Boolean onlyExplicitlyIncluded = false;

			private Predicate<String> isIncluded() {

				LOG.debug("Creating include pattern matcher for patterns {}", (Object)this.include);
				if (this.include == null)
					return s -> false;
				final Pattern[] patterns = Stream.of(this.include).map(Pattern::compile).toArray(Pattern[]::new);
				return s -> {
					for (final Pattern p : patterns) {
						if (p.matcher(s).matches())
							return true;
					}
					return false;
				};
			}

			private Predicate<String> isExcluded() {

				LOG.debug("Creating exclude pattern matcher for patterns {}", (Object)this.exclude);
				if (this.exclude == null)
					return s -> false;
				final Pattern[] patterns = Stream.of(this.exclude).map(Pattern::compile).toArray(Pattern[]::new);
				return s -> {
					for (final Pattern p : patterns) {
						if (p.matcher(s).matches()) {
							LOG.debug("Excluded: Pattern {} matched {}", p, s);
							return true;
						}
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
	}

	public static class ZeroLengthScreenScales extends Exception {

	}

	public static class InvalidScreenScaleValue extends Exception {

		InvalidScreenScaleValue(final double scale) {

			super("Screen scale " + scale + " not in legal interval (0,1]");
		}
	}

	public static class ScreenScaleNotDecreasing extends Exception {

		public ScreenScaleNotDecreasing(final double first, final double second) {

			super("Second screen scale " + second + " larger than or equal to first " + first);
		}
	}

}
