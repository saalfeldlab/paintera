package org.janelia.saalfeldlab.paintera;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelUtils;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.DataTypeNotSupported;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PainteraShowContainer extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String VALUE_RANGE_KEY = "value_range";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	@Override
	public void start(Stage primaryStage) throws Exception {

		String[] args = getParameters().getRaw().stream().toArray(String[]::new);
		CommandLineArgs clArgs = new CommandLineArgs();
		CommandLine cl = new CommandLine(clArgs);

		try {
			cl.parse(args);
			if (clArgs.channelAxis < 0 || clArgs.channelAxis > 3) {
				throw new CommandLine.PicocliException("--channel-axis has to be within [0, 3] but is " + clArgs.channelAxis);
			}
		} catch (final CommandLine.PicocliException e) {
			LOG.error(e.getMessage());
			LOG.debug("Stack trace", e);
			cl.usage(System.err);
			Platform.exit();
			return;
		}

		if (cl.isUsageHelpRequested()) {
			cl.usage(System.out);
			Platform.exit();
			return;
		}

		if (cl.isVersionHelpRequested()) {
			System.out.println(Version.VERSION_STRING);
			Platform.exit();
			return;
		}

		final PainteraBaseView.DefaultPainteraBaseView viewer = PainteraBaseView.defaultView();

		List<N5Meta> rawDatasets = new ArrayList<>();

		List<N5Meta> channelDatasets = new ArrayList<>();

		List<N5Meta> labelDatasets = new ArrayList<>();

		final Predicate<String> datasetCheck = clArgs.useDataset();

		for (String container : clArgs.n5Containers) {
			final N5Reader n5 = N5Helpers.n5Writer(container, 64, 64, 64);
			final N5Reader n5WithChannel = N5Helpers.n5Writer(container, 64, 64, 64, 3);
			List<String> datasets = N5Helpers.discoverDatasets(n5, () -> true);
			for (final String dataset : datasets) {

				if (!datasetCheck.test(dataset)) {
					LOG.info("Ignoring dataset {} in container {} based on command line arguments.", dataset, container);
					continue;
				}

				LOG.debug("Inspecting dataset {} in container {}", dataset, container);
				final int nDim = getNumDimensions(n5, dataset);
				if (nDim < 3 || nDim > 4) {
					LOG.info("Ignoring dataset with invalid number of dimensions. Only 3- or 4-dimensional data supported.");
					continue;
				}
				if (nDim == 4) {
					channelDatasets.add(N5Meta.fromReader(n5WithChannel, dataset));
				} else if (isLabelData(n5, dataset)) {
					LOG.debug("Detected label dataset {} in container {}", dataset, container);
					labelDatasets.add(N5Meta.fromReader(n5, dataset));
				} else {
					rawDatasets.add(N5Meta.fromReader(n5, dataset));
				}

			}
		}

		for (N5Meta rawMeta : rawDatasets) {
			addRawSource(viewer.baseView, rawMeta, clArgs.revertArrayAttributes);
		}

		for (N5Meta channelMeta : channelDatasets) {
			if (clArgs.channels == null)
				addChannelSource(viewer.baseView, channelMeta, clArgs.revertArrayAttributes, clArgs.channelAxis, clArgs.maxNumChannels);
			else
				addChannelSource(viewer.baseView, channelMeta, clArgs.revertArrayAttributes, clArgs.channelAxis, clArgs.channels);
		}

		for (final N5Meta labelMeta : labelDatasets) {
			addLabelSource(viewer.baseView, labelMeta, clArgs.revertArrayAttributes);
		}


		final Scene scene = new Scene(viewer.paneWithStatus.getPane(), clArgs.width, clArgs.height);
		viewer.keyTracker.installInto(scene);
		primaryStage.setScene(scene);
		primaryStage.show();
		viewer.baseView.orthogonalViews().requestRepaint();
		Platform.setImplicitExit(true);
		primaryStage.addEventFilter(WindowEvent.WINDOW_HIDDEN, e -> viewer.baseView.stop());
	}

	@CommandLine.Command(name = "paintera-show-container", mixinStandardHelpOptions = true)
	private static final class CommandLineArgs {

		@CommandLine.Parameters(arity = "1..*")
		String[] n5Containers;

		@CommandLine.Option(names = {"--revert-array-attributes"})
		Boolean revertArrayAttributes = false;

		@CommandLine.Option(names = {"--channel-axis"})
		Integer channelAxis = 3;

		@CommandLine.Option(names = {"--limit-number-of-channels"}, paramLabel = "NUM_CHANNELS", description = "" +
				"If specified and larger than 0, " +
				"divide any channel sources into multiple channel sources with at max NUM_CHANNELS channels. " +
				"Ignored if `--channels' option is specified.")
		Integer maxNumChannels = -1;

		@CommandLine.Option(names = {"--width"})
		Integer width = 1600;

		@CommandLine.Option(names = {"--height"})
		Integer height = 900;

		@CommandLine.Option(names = {"--channels"}, paramLabel = "CHANNELS", arity = "1..*", converter = ChannelListConverter.class, description = "" +
				"For each channel data source, display CHANNELS as separate channel source. " +
				"CHANNELS is a comma-separated list of integers. " +
				"This option accepts multiple values separated by space, e.g. --channels 0,3,6 1,4,7")
		List<long[]> channels = null;

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

		public static final class ChannelListConverter implements CommandLine.ITypeConverter<long[]> {

			private final String splitString;

			public ChannelListConverter() {
				this(",");
			}

			public ChannelListConverter(String splitString) {
				this.splitString = splitString;
			}

			@Override
			public long[] convert(String s) throws Exception {
				return Stream.of(s.split(this.splitString)).mapToLong(Long::parseLong).toArray();
			}
		}

		public Predicate<String> isIncluded() {
			LOG.debug("Creating include pattern matcher for patterns {}", (Object) this.include);
			if (this.include == null)
				return s -> false;
			Pattern[] patterns = Stream.of(this.include).map(Pattern::compile).toArray(Pattern[]::new);
			return s -> {
				for (final Pattern p : patterns)
					if (p.matcher(s).matches())
						return true;
				return false;
			};
		}

		public Predicate<String> isExcluded() {
			LOG.debug("Creating exclude pattern matcher for patterns {}", (Object) this.exclude);
			if (this.exclude == null)
				return s -> false;
			Pattern[] patterns = Stream.of(this.exclude).map(Pattern::compile).toArray(Pattern[]::new);
			return s -> {
				for (final Pattern p : patterns)
					if (p.matcher(s).matches()) {
						LOG.debug("Excluded: Pattern {} matched {}", p, s);
						return true;
					}
				return false;
			};
		}

		public Predicate<String> isOnlyExplicitlyIncluded() {
			return s -> {
				LOG.debug("Is only explicitly included? {}", onlyExplicitlyIncluded);
				return onlyExplicitlyIncluded;
			};
		}

		public Predicate<String> useDataset() {
			return isIncluded().or((isExcluded().or(isOnlyExplicitlyIncluded())).negate());
		}

	}

	/* *************************************************** */
	/* private static helper methods for opening data sets */
	/* TODO move to helper class -- which one? N5Helpers?  */
	/* *************************************************** */

	private static boolean isLabelData(N5Reader reader, String group) throws IOException {

		if (N5Helpers.isPainteraDataset(reader, group)) {
			JsonObject painteraInfo = reader.getAttribute(group, N5Helpers.PAINTERA_DATA_KEY, JsonObject.class);
			LOG.debug("Got paintera info {} for group {}", painteraInfo, group);
			return painteraInfo.get("type").getAsString().equals("label");
		}

		if (N5Helpers.isMultiScale(reader, group))
			return N5Types.isLabelMultisetType(reader, group) || isLabelData(reader, N5Helpers.getFinestLevelJoinWithGroup(reader, group));

		return N5Types.isLabelMultisetType(reader, group) || reader.getDatasetAttributes(group).getDataType().equals(DataType.UINT64);
	}

	private static int getNumDimensions(N5Reader n5, String dataset) throws IOException {
		if (N5Helpers.isPainteraDataset(n5, dataset)) {
			return getNumDimensions(n5, dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET);
		}

		if (N5Helpers.isMultiScale(n5, dataset)) {
			return getNumDimensions(n5, dataset + "/" + N5Helpers.listAndSortScaleDatasets(n5, dataset)[0]);
		}

		return n5.getDatasetAttributes(dataset).getNumDimensions();
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void addRawSource(
			final PainteraBaseView viewer,
			final N5Meta rawMeta,
			final boolean revertArrayAttributes
	) throws IOException, ReflectionException {
		LOG.info("Adding raw source {}", rawMeta);
		DataSource<T, V> source = N5Data.openRawAsSource(
				rawMeta.writer(),
				rawMeta.dataset(),
				N5Helpers.getTransform(rawMeta.writer(), rawMeta.dataset(), revertArrayAttributes),
				viewer.getGlobalCache(),
				viewer.getGlobalCache().getNumPriorities() - 1,
				rawMeta.dataset());
		ARGBColorConverter.Imp0<V> conv = new ARGBColorConverter.Imp0<>();
		RawSourceState<T, V> state = new RawSourceState<>(source, conv, new CompositeCopy<>(), source.getName());

		Set<String> attrs = rawMeta.writer().listAttributes(rawMeta.dataset()).keySet();

		final T t = source.getDataType();
		if (t instanceof IntegerType<?>) {
			conv.minProperty().set(t.getMinValue());
			conv.maxProperty().set(t.getMaxValue());
		} else {
			LOG.debug("Setting range to [0.0, 1.0] for {}", rawMeta);
			conv.minProperty().set(0.0);
			conv.maxProperty().set(1.0);
		}

		if (attrs.contains(MIN_KEY)) {
			conv.minProperty().set(rawMeta.writer().getAttribute(rawMeta.dataset(), MIN_KEY, double.class));
		}

		if (attrs.contains(MAX_KEY)) {
			conv.maxProperty().set(rawMeta.writer().getAttribute(rawMeta.dataset(), MAX_KEY, double.class));
		}

		if (attrs.contains(VALUE_RANGE_KEY)) {
			LOG.warn("Using deprecated attribute {}", VALUE_RANGE_KEY);
			final double[] valueRange = rawMeta.writer().getAttribute(rawMeta.dataset(), VALUE_RANGE_KEY, double[].class);
			conv.minProperty().set(valueRange[0]);
			conv.maxProperty().set(valueRange[1]);
		}

		viewer.addState(state);
	}

	private static <T extends IntegerType<T> & NativeType<T>, V extends Volatile<T> & NativeType<V> & IntegerType<V>> void addLabelSource(
			final PainteraBaseView viewer,
			final N5Meta labelMeta,
			final boolean revertArrayAttributes
	) throws IOException, ReflectionException {
		LOG.info("Adding label source {}", labelMeta);
		final N5Writer writer = labelMeta.writer();
		final String dataset  = labelMeta.dataset();
		final double[] resolution = N5Helpers.getResolution(writer, dataset, revertArrayAttributes);
		final double[] offset     = N5Helpers.getOffset(writer, dataset, revertArrayAttributes);
		final AffineTransform3D transform  = N5Helpers.fromResolutionAndOffset(resolution, offset);
		final DataSource<T, V>  source;
		if (N5Types.isLabelMultisetType(writer, dataset))
		{
			source = (DataSource) N5Data.openLabelMultisetAsSource(
					writer,
					dataset,
					transform,
					viewer.getGlobalCache(),
					0,
					dataset
			);
		}
		else
		{
			LOG.debug("Getting scalar data source");
			source = N5Data.openScalarAsSource(
					writer,
					dataset,
					transform,
					viewer.getGlobalCache(),
					0,
					dataset
			);
		}

		final Supplier<String> canvasCacheDirUpdate = Masks.canvasTmpDirDirectorySupplier(null);

		final DataSource<T, V>               masked         = Masks.mask(
				source,
				canvasCacheDirUpdate.get(),
				canvasCacheDirUpdate,
				new CommitCanvasN5(writer, dataset),
				viewer.getPropagationQueue()
		);
		final IdService idService      = idService(writer, dataset);
		final FragmentSegmentAssignmentState assignment = N5Helpers.assignments(writer, dataset);
		final SelectedIds selectedIds    = new SelectedIds();
		final LockedSegmentsOnlyLocal lockedSegments = new LockedSegmentsOnlyLocal(locked -> {
		});
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
				ModalGoldenAngleSaturatedHighlightingARGBStream(
				selectedIds,
				assignment,
				lockedSegments
		);
		final HighlightingStreamConverter<V> converter = HighlightingStreamConverter.forType(stream, masked.getType());

		final LabelBlockLookup lookup =  getLabelBlockLookup(writer, dataset, source);

		IntFunction<InterruptibleFunction<Long, Interval[]>> loaderForLevelFactory = level -> InterruptibleFunction.fromFunction(
				MakeUnchecked.function(
						id -> lookup.read(level, id),
						id -> {LOG.debug("Falling back to empty array"); return new Interval[0];}
				));

		InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
				.range(0, masked.getNumMipmapLevels())
				.mapToObj(loaderForLevelFactory)
				.toArray(InterruptibleFunction[]::new );

		MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
				masked,
				selectedIds,
				assignment,
				stream,
				viewer.viewer3D().meshesGroup(),
				blockLoaders,
				viewer.getGlobalCache()::createNewCache,
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService());

		final LabelSourceState<T, V> state = new LabelSourceState<>(
				masked,
				converter,
				new ARGBCompositeAlphaYCbCr(),
				masked.getName(),
				assignment,
				lockedSegments,
				idService,
				selectedIds,
				meshManager,
				lookup);

		viewer.addState(state);
	}

	private static LabelBlockLookup getLabelBlockLookup(final N5Reader reader, final String dataset, final DataSource<?, ?> fallBack) throws IOException {
		try {
			return N5Helpers.getLabelBlockLookup(reader, dataset);
		} catch (N5Helpers.NotAPainteraDataset e) {
			return PainteraAlerts.getLabelBlockLookupFromDataSource(fallBack);
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void addChannelSource(
			final PainteraBaseView viewer,
			final N5Meta meta,
			final boolean revertArrayAttributes,
			final int channelDimension,
			final int maxNumChannels
	) throws IOException, DataTypeNotSupported {

		LOG.info("Adding channel source {}", meta);

		DatasetAttributes datasetAttributes = N5Helpers.isPainteraDataset(meta.writer(), meta.dataset())
				? meta.writer().getDatasetAttributes(Paths.get(meta.dataset(), "data", "s0").toString())
				: N5Helpers.isMultiScale(meta.writer(), meta.dataset())
					? meta.writer().getDatasetAttributes(N5Helpers.getFinestLevelJoinWithGroup(meta.writer(), meta.dataset()))
					: meta.datasetAttributes();
		long channelDim = datasetAttributes.getDimensions()[channelDimension];
		long channelMax = channelDim - 1;
		long numChannels = maxNumChannels <= 0 ? channelDim : maxNumChannels;

		for (long cmin = 0; cmin < datasetAttributes.getDimensions()[channelDimension]; cmin += numChannels) {

			final long cmax = Math.min(cmin + numChannels, datasetAttributes.getDimensions()[channelDimension]) - 1;

			N5ChannelDataSource<T, V> source = N5ChannelDataSource.valueExtended(
					meta,
					N5Helpers.getTransform(meta.writer(), meta.dataset(), revertArrayAttributes),
					viewer.getGlobalCache(),
					cmin == 0 && cmax == channelMax ? meta.dataset() : String.format("%s-channels-[%d,%d]", meta.dataset(), cmin, cmax)	,
					viewer.getGlobalCache().getNumPriorities() - 1,
					channelDimension,
					cmin,
					cmax,
					false,
					Double.NaN);
			ARGBCompositeColorConverter<V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> conv = ARGBCompositeColorConverter.imp0((int) source.numChannels());

			ChannelSourceState<T, V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state = new ChannelSourceState<>(
					source,
					conv,
					new ARGBCompositeAlphaAdd(),
					source.getName());

			T t = source.getDataType().get(0);
			if (t instanceof IntegerType<?>) {
				for (int channel = 0; channel < conv.numChannels(); ++channel) {
					conv.minProperty(channel).set(t.getMinValue());
					conv.maxProperty(channel).set(t.getMaxValue());
				}
			} else {
				for (int channel = 0; channel < conv.numChannels(); ++channel) {
					conv.minProperty(channel).set(0.0);
					conv.maxProperty(channel).set(1.0);
				}
			}

			Map<String, Class<?>> attrs = meta.writer().listAttributes(meta.dataset());

			final int cminf = (int) cmin;

			if (attrs.containsKey(MIN_KEY)) {
				Object min = meta.writer().getAttribute(meta.dataset(), MIN_KEY, attrs.get(MIN_KEY));

				if (min instanceof Double)
					IntStream.range(0, conv.numChannels()).mapToObj(conv::minProperty).forEach(p -> p.set((Double)min));

				else if (min instanceof double[]) {
					IntStream.range(0, conv.numChannels()).forEach(c -> conv.minProperty(c).set(((double[])min)[cminf + c]));
				}
			}

			if (attrs.containsKey(MAX_KEY)) {
				Object max = meta.writer().getAttribute(meta.dataset(), MAX_KEY, attrs.get(MAX_KEY));

				if (max instanceof Double)
					IntStream.range(0, conv.numChannels()).mapToObj(conv::maxProperty).forEach(p -> p.set((Double)max));

				else if (max instanceof double[]) {
					IntStream.range(0, conv.numChannels()).forEach(c -> conv.maxProperty(c).set(((double[])max)[cminf + c]));
				}
			}

			if (attrs.containsKey(VALUE_RANGE_KEY)) {
				LOG.warn("Using deprecated attribute {}", VALUE_RANGE_KEY);
				final double[] valueRange = meta.writer().getAttribute(meta.dataset(), VALUE_RANGE_KEY, double[].class);
				final double min = valueRange[0];
				final double max = valueRange[1];
				IntStream.range(0, conv.numChannels()).mapToObj(conv::minProperty).forEach(p -> p.set(min));
				IntStream.range(0, conv.numChannels()).mapToObj(conv::maxProperty).forEach(p -> p.set(max));
			}
			viewer.addState(state);
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void addChannelSource(
			final PainteraBaseView viewer,
			final N5Meta meta,
			final boolean revertArrayAttributes,
			final int channelDimension,
			final Collection<? extends long[]> channelLists
	) throws IOException, DataTypeNotSupported {

		LOG.info("Adding channel source {}", meta);

		for (long[] channels : channelLists) {

			N5ChannelDataSource<T, V> source = N5ChannelDataSource.valueExtended(
					meta,
					N5Helpers.getTransform(meta.writer(), meta.dataset(), revertArrayAttributes),
					viewer.getGlobalCache(),
					String.format("%s-channels-%s", meta.dataset(), Arrays.toString(channels)),
					viewer.getGlobalCache().getNumPriorities() - 1,
					channelDimension,
					channels,
					Double.NaN);
			ARGBCompositeColorConverter<V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> conv = ARGBCompositeColorConverter.imp0((int) source.numChannels());

			ChannelSourceState<T, V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state = new ChannelSourceState<>(
					source,
					conv,
					new ARGBCompositeAlphaAdd(),
					source.getName());


			Map<String, Class<?>> attrs = meta.writer().listAttributes(meta.dataset());

			if (attrs.containsKey(MIN_KEY)) {
				Object min = meta.writer().getAttribute(meta.dataset(), MIN_KEY, attrs.get(MIN_KEY));

				if (min instanceof Double)
					IntStream.range(0, conv.numChannels()).mapToObj(conv::minProperty).forEach(p -> p.set((Double)min));

				else if (min instanceof double[]) {
					IntStream.range(0, conv.numChannels()).forEach(c -> conv.minProperty(c).set(((double[])min)[(int) channels[c]]));
				}
			}

			if (attrs.containsKey(MAX_KEY)) {
				Object max = meta.writer().getAttribute(meta.dataset(), MAX_KEY, attrs.get(MAX_KEY));

				if (max instanceof Double)
					IntStream.range(0, conv.numChannels()).mapToObj(conv::maxProperty).forEach(p -> p.set((Double)max));

				else if (max instanceof double[]) {
					IntStream.range(0, conv.numChannels()).forEach(c -> conv.maxProperty(c).set(((double[])max)[(int) channels[c]]));
				}
			}
			if (attrs.containsKey(VALUE_RANGE_KEY)) {
				final double[] valueRange = meta.writer().getAttribute(meta.dataset(), VALUE_RANGE_KEY, double[].class);
				final double min = valueRange[0];
				final double max = valueRange[1];
				IntStream.range(0, conv.numChannels()).mapToObj(conv::minProperty).forEach(p -> p.set(min));
				IntStream.range(0, conv.numChannels()).mapToObj(conv::maxProperty).forEach(p -> p.set(max));
			} else {
				T t = source.getDataType().get(0);
				if (t instanceof IntegerType<?>) {
					for (int channel = 0; channel < conv.numChannels(); ++channel) {
						conv.minProperty(channel).set(t.getMinValue());
						conv.maxProperty(channel).set(t.getMaxValue());
					}
				} else {
					for (int channel = 0; channel < conv.numChannels(); ++channel) {
						conv.minProperty(channel).set(0.0);
						conv.maxProperty(channel).set(1.0);
					}
				}
			}
			viewer.addState(state);
		}
	}

	private static IdService idService(
			final N5Writer n5,
			final String dataset
	) throws IOException
	{
		try {
			LOG.warn("Getting id service for {} -- {}", n5, dataset);
			return N5Helpers.idService(n5, dataset);
		} catch (final N5Helpers.MaxIDNotSpecified e) {
			final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
			alert.setHeaderText("maxId not specified in dataset.");
			final TextArea ta = new TextArea("Could not read maxId attribute from data set. " +
					"You can specify the max id manually, or read it from the data set (this can take a long time if your data is big).\n" +
					"Alternatively, press cancel to load the data set without an id service. " +
					"Fragment-segment-assignments and selecting new (wrt to the data) labels require an id service " +
					"and will not be available if you press cancel.");
			ta.setEditable(false);
			ta.setWrapText(true);
			final NumberField<LongProperty> nextIdField = NumberField.longField(0, v -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
			final Button scanButton = new Button("Scan Data");
			scanButton.setOnAction(event -> {
				event.consume();
				try {
					findMaxId(n5, dataset, nextIdField.valueProperty()::set);
				} catch (IOException e1) {
					throw new RuntimeException(e1);
				}
			});
			final HBox maxIdBox = new HBox(new Label("Max Id:"), nextIdField.textField(), scanButton);
			HBox.setHgrow(nextIdField.textField(), Priority.ALWAYS);
			alert.getDialogPane().setContent(new VBox(ta, maxIdBox));
			final Optional<ButtonType> bt = alert.showAndWait();
			if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
				long maxId = nextIdField.valueProperty().get() + 1;
				n5.setAttribute(dataset, "maxId", maxId);
				return new N5IdService(n5, dataset, maxId);
			}
			else
				return new IdService.IdServiceNotProvided();
		}
	}

	private static <I extends IntegerType<I> & NativeType<I>> void findMaxId(
			final N5Reader reader,
			String group,
			final LongConsumer maxIdTarget
	) throws IOException {
		final int numProcessors = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = Executors.newFixedThreadPool(numProcessors, new NamedThreadFactory("max-id-discovery-%d", true));
		final String dataset = N5Helpers.isPainteraDataset(reader, group)
				? group + "/data/s0"
				: N5Helpers.isMultiScale(reader, group)
					? N5Helpers.getFinestLevelJoinWithGroup(reader, group)
					: group;
		final boolean isLabelMultiset = N5Helpers.getBooleanAttribute(reader, dataset, N5Helpers.IS_LABEL_MULTISET_KEY, false);
		final CachedCellImg<I, ?> img = isLabelMultiset ? (CachedCellImg<I, ?>) (CachedCellImg) LabelUtils.openVolatile(reader, dataset) : (CachedCellImg<I, ?>) N5Utils.open(reader, dataset);
		final int[] blockSize = new  int[img.numDimensions()];
		img.getCellGrid().cellDimensions(blockSize);
		final List<Interval> blocks = Grids.collectAllContainedIntervals(img.getCellGrid().getImgDimensions(), blockSize);
		final IntegerProperty completedTasks = new SimpleIntegerProperty(0);
		final LongProperty maxId = new SimpleLongProperty(0);
		final BooleanProperty wasCanceled = new SimpleBooleanProperty(false);
		LOG.debug("Scanning for max id over {} blocks of size {} (total size {}).", blocks.size(), blockSize, img.getCellGrid().getImgDimensions());
		final Thread t = new Thread(() -> {
			List<Callable<Long>> tasks = new ArrayList<>();
			for (final Interval block : blocks) {
				tasks.add(() -> {
					long localMaxId = 0;
					try {
						final IntervalView<I> interval = Views.interval(img, block);
						final Cursor<I> cursor = interval.cursor();
						while (cursor.hasNext() && !wasCanceled.get()) {
							final long id = cursor.next().getIntegerLong();
							if (id > localMaxId)
								localMaxId = id;
						}
						return localMaxId;
					}
					finally {
						synchronized (completedTasks) {
							if (!wasCanceled.get()) {
								LOG.trace("Incrementing completed tasks ({}/{}) and maxId ({}) with {}", completedTasks, blocks.size(), maxId, localMaxId);
								maxId.setValue(Math.max(maxId.getValue(), localMaxId));
								completedTasks.set(completedTasks.get() + 1);
							}
						}
					}
				});
			}
			try {
				final List<Future<Long>> futures = es.invokeAll(tasks);
				for (final Future<Long> future : futures) {
					final Long id = future.get();
				}
			} catch (final InterruptedException | ExecutionException e) {
				synchronized (completedTasks) {
					completedTasks.set(-1);
					wasCanceled.set(true);
				}
				LOG.error("Was interrupted while finding max id.", e);
			}
		});
		t.setName("max-id-discovery-main-thread");
		t.setDaemon(true);
		t.start();
		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		alert.setHeaderText("Finding max id...");
		final BooleanBinding stillRuning = Bindings.createBooleanBinding(() -> completedTasks.get() < blocks.size(), completedTasks);
		alert.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(stillRuning);
		final StatusBar statusBar = new StatusBar();
		completedTasks.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setProgress(newv.doubleValue() / blocks.size())));
		completedTasks.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setText(String.format("%s/%d", newv, blocks.size()))));
		alert.getDialogPane().setContent(statusBar);
		wasCanceled.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> alert.setHeaderText("Cancelled")));
		completedTasks.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> alert.setHeaderText(newv.intValue() < blocks.size() ? "Finding max id: " + maxId.getValue() : "Found max id: " + maxId.getValue())));
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			LOG.info("Setting max id to {}", maxId.get());
			maxIdTarget.accept(maxId.get());
		} else {
			wasCanceled.set(true);
		}

	}

}
