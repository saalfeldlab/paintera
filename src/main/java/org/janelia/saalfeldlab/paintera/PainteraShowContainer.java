package org.janelia.saalfeldlab.paintera;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrderNotSupported;
import org.janelia.saalfeldlab.paintera.data.n5.DataTypeNotSupported;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class PainteraShowContainer extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String VALUE_RANGE_KEY = "value_range";

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

		for (String container : clArgs.n5Containers) {
			final N5Reader n5 = N5Helpers.n5Reader(container, 64, 64, 64);
			final N5Reader n5WithChannel = N5Helpers.n5Reader(container, 64, 64, 64, 3);
			List<String> datasets = N5Helpers.discoverDatasets(n5, () -> {
			});
			for (String dataset : datasets) {
				LOG.debug("Inspecting dataset {} in container {}", dataset, container);
				final int nDim = getNumDimensions(n5, dataset);
				if (nDim < 3 || nDim > 4) {
					LOG.info("Ignoring dataset with invalid number of dimensions. Only 3- or 4-dimensional data supported.");
					continue;
				}
				if (nDim == 4) {
					channelDatasets.add(N5Meta.fromReader(n5WithChannel, dataset));
				} else if (isLabelData(n5, dataset)) {
					labelDatasets.add(N5Meta.fromReader(n5, dataset));
				} else {
					rawDatasets.add(N5Meta.fromReader(n5, dataset));
				}

			}
		}

		for (N5Meta rawMeta : rawDatasets) {
			addRawSource(viewer.baseView, rawMeta, clArgs.axisOrder, clArgs.revertArrayAttributes);
		}

		for (N5Meta channelMeta : channelDatasets) {
			addChannelSource(viewer.baseView, channelMeta, clArgs.revertArrayAttributes, clArgs.channelAxis, clArgs.maxNumChannels);
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

		@CommandLine.Option(names = {"--limit-number-of-channels"})
		Integer maxNumChannels = -1;

		@CommandLine.Option(names = {"--width"})
		Integer width = 1600;

		@CommandLine.Option(names = {"--height"})
		Integer height = 900;

		@CommandLine.Option(names = {"--dataset-axis-order"}, description = "Axis order of data. This is not the axis order of array attributes!")
		AxisOrder axisOrder = AxisOrder.XYZ;
	}

	private static boolean isLabelData(N5Reader reader, String group) throws IOException {
		if (N5Helpers.isPainteraDataset(reader, group)) {
			JsonObject painteraInfo = reader.getAttribute(group, N5Helpers.PAINTERA_DATA_KEY, JsonObject.class);
			LOG.debug("Got paintera info {} for group {}", painteraInfo, group);
			return painteraInfo.get("type").getAsString().equals("label");
		}
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
			AxisOrder axisOrder,
			final boolean revertArrayAttributes
	) throws IOException, ReflectionException, AxisOrderNotSupported {
		LOG.info("Adding raw source {}", rawMeta);
		DataSource<T, V> source = N5Data.openRawAsSource(
				rawMeta.reader(),
				rawMeta.dataset(),
				N5Helpers.getTransform(rawMeta.reader(), rawMeta.dataset(), revertArrayAttributes),
				viewer.getGlobalCache(),
				viewer.getGlobalCache().getNumPriorities() - 1,
				rawMeta.dataset());
		ARGBColorConverter.Imp0<V> conv = new ARGBColorConverter.Imp0<>();
		RawSourceState<T, V> state = new RawSourceState<>(source, conv, new CompositeCopy<>(), source.getName());

		Set<String> attrs = rawMeta.reader().listAttributes(rawMeta.dataset()).keySet();
		if (attrs.contains(VALUE_RANGE_KEY)) {
			final double[] valueRange = rawMeta.reader().getAttribute(rawMeta.dataset(), VALUE_RANGE_KEY, double[].class);
			conv.minProperty().set(valueRange[0]);
			conv.maxProperty().set(valueRange[1]);
		} else {
			final T t = source.getDataType();
			if (t instanceof IntegerType<?>) {
				conv.minProperty().set(t.getMinValue());
				conv.maxProperty().set(t.getMaxValue());
			} else {
				LOG.debug("Setting range to [0.0, 1.0] for {}", rawMeta);
				conv.minProperty().set(0.0);
				conv.maxProperty().set(1.0);
			}
		}

		viewer.addState(state);
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void addChannelSource(
			final PainteraBaseView viewer,
			final N5Meta meta,
			final boolean revertArrayAttributes,
			final int channelDimension,
			final int maxNumChannels
	) throws IOException, DataTypeNotSupported {

		LOG.info("Adding channel source {}", meta);

		DatasetAttributes datasetAttributes = meta.datasetAttributes();
		long channelDim = datasetAttributes.getDimensions()[channelDimension];
		long channelMax = channelDim - 1;
		long numChannels = maxNumChannels <= 0 ? channelDim : maxNumChannels;

		for (long cmin = 0; cmin < datasetAttributes.getDimensions()[channelDimension]; cmin += numChannels) {

			final long cmax = Math.min(cmin + numChannels, datasetAttributes.getDimensions()[channelDimension]) - 1;

			N5ChannelDataSource<T, V> source = N5ChannelDataSource.zeroExtended(
					meta,
					N5Helpers.getTransform(meta.reader(), meta.dataset(), revertArrayAttributes),
					viewer.getGlobalCache(),
					cmin == 0 && cmax == channelMax ? meta.dataset() : String.format("%s-channels-[%d,%d]", meta.dataset(), cmin, cmax)	,
					viewer.getGlobalCache().getNumPriorities() - 1,
					channelDimension,
					cmin,
					cmax,
					false);
			ARGBCompositeColorConverter<V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> conv = ARGBCompositeColorConverter.imp0((int) source.numChannels());

			ChannelSourceState<T, V, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state = new ChannelSourceState<>(
					source,
					conv,
					new ARGBCompositeAlphaAdd(),
					source.getName());


			Set<String> attrs = meta.reader().listAttributes(meta.dataset()).keySet();
			if (attrs.contains(VALUE_RANGE_KEY)) {
				final double[] valueRange = meta.reader().getAttribute(meta.dataset(), VALUE_RANGE_KEY, double[].class);
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

}
