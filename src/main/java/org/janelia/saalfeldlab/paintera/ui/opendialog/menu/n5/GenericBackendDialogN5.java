package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.*;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.ExceptionNode;
import org.janelia.saalfeldlab.fx.ui.MatchSelection;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.paintera.ui.opendialog.DatasetInfo;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public class GenericBackendDialogN5 implements Closeable
{

	private static final String EMPTY_STRING = "";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String ERROR_MESSAGE_PATTERN = "n5? %s -- dataset? %s -- update? %s";

	private final DatasetInfo datasetInfo = new DatasetInfo();

	private final SimpleObjectProperty<Supplier<N5Writer>> n5Supplier = new SimpleObjectProperty<>(() -> null);

	private final ObjectBinding<N5Writer> n5 = Bindings.createObjectBinding(() -> Optional
			.ofNullable(n5Supplier.get())
			.map(Supplier::get)
			.orElse(null), n5Supplier);

	private final StringProperty dataset = new SimpleStringProperty();

	private final ArrayList<Thread> discoveryThreads = new ArrayList<>();

	private final ArrayList<BooleanProperty> discoveryIsActive = new ArrayList<>();

	private final SimpleBooleanProperty isTraversingDirectories = new SimpleBooleanProperty();

	private final BooleanBinding isN5Valid = n5.isNotNull();

	private final BooleanBinding isDatasetValid = dataset.isNotNull().and(dataset.isNotEqualTo(EMPTY_STRING));

	private final SimpleBooleanProperty datasetUpdateFailed = new SimpleBooleanProperty(false);

	private final ExecutorService propagationExecutor;

	private final ObjectProperty<DatasetAttributes> datasetAttributes = new SimpleObjectProperty<>();

	private final ObjectBinding<long[]> dimensions = Bindings.createObjectBinding(() -> Optional.ofNullable(datasetAttributes.get()).map(DatasetAttributes::getDimensions).orElse(null), datasetAttributes);

	private final BooleanBinding isReady = isN5Valid
			.and(isDatasetValid)
			.and(datasetUpdateFailed.not());

	{
		isN5Valid.addListener((obs, oldv, newv) -> datasetUpdateFailed.set(false));
	}

	private final StringBinding errorMessage = Bindings.createStringBinding(
			() -> isReady.get()
			      ? null
			      : String.format(ERROR_MESSAGE_PATTERN,
					      isN5Valid.get(),
					      isDatasetValid.get(),
					      datasetUpdateFailed.not().get()
			                     ),
			isReady
	                                                                       );

	private final StringBinding name = Bindings.createStringBinding(() -> {
		final String[] entries = Optional
				.ofNullable(dataset.get())
				.map(d -> d.split("/"))
				.map(a -> a.length > 0 ? a : new String[] {null})
				.orElse(new String[] {null});
		return entries[entries.length - 1];
	}, dataset);

	private final ObservableList<String> datasetChoices = FXCollections.observableArrayList();

	private final String identifier;

	private final Node node;

	public GenericBackendDialogN5(
			final Node n5RootNode,
			final Node browseNode,
			final String identifier,
			final ObservableValue<Supplier<N5Writer>> writerSupplier,
			final ExecutorService propagationExecutor)
	{
		this("_Dataset", n5RootNode, browseNode, identifier, writerSupplier, propagationExecutor);
	}

	public GenericBackendDialogN5(
			final String datasetPrompt,
			final Node n5RootNode,
			final Node browseNode,
			final String identifier,
			final ObservableValue<Supplier<N5Writer>> writerSupplier,
			final ExecutorService propagationExecutor)
	{
		this.identifier = identifier;
		this.node = initializeNode(n5RootNode, datasetPrompt, browseNode);
		this.propagationExecutor = propagationExecutor;
		n5Supplier.bind(writerSupplier);
		n5.addListener((obs, oldv, newv) -> {
			LOG.debug("Updated n5: obs={} oldv={} newv={}", obs, oldv, newv);
			if (newv == null)
			{
				datasetChoices.clear();
				return;
			}

			LOG.debug("Updating dataset choices!");
			synchronized (discoveryIsActive)
			{
				this.isTraversingDirectories.set(false);
				cancelDiscovery();
				final BooleanProperty keepLooking = new SimpleBooleanProperty(true);
				final Thread discoveryThread = new Thread(() -> {
					this.isTraversingDirectories.set(true);
					final AtomicBoolean discardDatasetList = new AtomicBoolean(false);
					try
					{
						final List<String> datasets = N5Helpers.discoverDatasets(newv, keepLooking::get);
						if (!Thread.currentThread().isInterrupted() && !discardDatasetList.get() && keepLooking.get())
						{
							LOG.debug("Found these datasets: {}", datasets);
							InvokeOnJavaFXApplicationThread.invoke(() -> datasetChoices.setAll(datasets));
							if (!newv.equals(oldv))
							{
								InvokeOnJavaFXApplicationThread.invoke(() -> this.dataset.set(null));
							}
						}
					} finally
					{
						this.isTraversingDirectories.set(false);
					}
				});
				discoveryIsActive.add(keepLooking);
				discoveryThread.setDaemon(true);
				discoveryThread.start();
			}
		});
		dataset.addListener((obs, oldv, newv) -> Optional.ofNullable(newv).filter(v -> v.length() > 0).ifPresent(v ->
				updateDatasetInfo(
				v,
				this.datasetInfo)));

		this.isN5Valid.addListener((obs, oldv, newv) -> cancelDiscovery());

		dataset.set("");
	}

	public void cancelDiscovery() {
		LOG.debug("Canceling discovery.");
		synchronized (discoveryIsActive) {
			discoveryIsActive.forEach(a -> a.set(false));
			discoveryIsActive.clear();
			discoveryThreads.forEach(Thread::interrupt);
			discoveryThreads.clear();

		}
	}

	public ObservableObjectValue<DatasetAttributes> datsetAttributesProperty()
	{
		return this.datasetAttributes;
	}

	public ObservableObjectValue<long[]> dimensionsProperty()
	{
		return this.dimensions;
	}

	public void updateDatasetInfo(final String group, final DatasetInfo info)
	{

		LOG.debug("Updating dataset info for dataset {}", group);
		try
		{
			final N5Reader n5 = this.n5.get();

			setResolution(N5Helpers.getResolution(n5, group));
			setOffset(N5Helpers.getOffset(n5, group));

			this.datasetAttributes.set(N5Helpers.getDatasetAttributes(n5, group));

			final DataType dataType = N5Types.getDataType(n5, group);

			// TODO handle array case! for now just try and set to 0, 1 in case of failure
			// TODO probably best to always handle min and max as array and populate acoording
			// to n5 meta data
			try {
				this.datasetInfo.minProperty().set(Optional.ofNullable(n5.getAttribute(
						group,
						MIN_KEY,
						Double.class)).orElse(N5Types.minForType(dataType)));
				this.datasetInfo.maxProperty().set(Optional.ofNullable(n5.getAttribute(
						group,
						MAX_KEY,
						Double.class)).orElse(N5Types.maxForType(dataType)));
			} catch (final ClassCastException e) {
				this.datasetInfo.minProperty().set(0.0);
				this.datasetInfo.maxProperty().set(1.0);
			}
		} catch (final IOException e)
		{
			ExceptionNode.exceptionDialog(e).show();
		}
	}

	public Node getDialogNode()
	{
		return node;
	}

	public StringBinding errorMessage()
	{
		return errorMessage;
	}

	public DoubleProperty[] resolution()
	{
		return this.datasetInfo.spatialResolutionProperties();
	}

	public DoubleProperty[] offset()
	{
		return this.datasetInfo.spatialOffsetProperties();
	}

	public DoubleProperty min()
	{
		return this.datasetInfo.minProperty();
	}

	public DoubleProperty max()
	{
		return this.datasetInfo.maxProperty();
	}

	public FragmentSegmentAssignmentState assignments() throws IOException
	{
		return N5Helpers.assignments(n5.get(), this.dataset.get());
	}

	public IdService idService() throws IOException
	{
		try {
			LOG.warn("Getting id service for {} -- {}", this.n5.get(), this.dataset.get());
			return N5Helpers.idService(this.n5.get(), this.dataset.get());
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
					findMaxId(this.n5.get(), this.dataset.getValue(), nextIdField.valueProperty()::set);
				} catch (final IOException e1) {
					throw new RuntimeException(e1);
				}
			});
			final HBox maxIdBox = new HBox(new Label("Max Id:"), nextIdField.textField(), scanButton);
			HBox.setHgrow(nextIdField.textField(), Priority.ALWAYS);
			alert.getDialogPane().setContent(new VBox(ta, maxIdBox));
			final Optional<ButtonType> bt = alert.showAndWait();
			if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
				final long maxId = nextIdField.valueProperty().get() + 1;
				this.n5.get().setAttribute(dataset.get(), "maxId", maxId);
				return new N5IdService(this.n5.get(), this.dataset.get(), maxId);
			}
			else
				return new IdService.IdServiceNotProvided();
		}
	}

	private static <I extends IntegerType<I> & NativeType<I>> void findMaxId(
			final N5Reader reader,
			String dataset,
			final LongConsumer maxIdTarget
			) throws IOException {
		final int numProcessors = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = Executors.newFixedThreadPool(numProcessors, new NamedThreadFactory("max-id-discovery-%d", true));
		dataset = N5Helpers.isPainteraDataset(reader, dataset) ? dataset + "/data" : dataset;
		dataset = N5Helpers.isMultiScale(reader, dataset) ? N5Helpers.getFinestLevelJoinWithGroup(reader, dataset) : dataset;
		final boolean isLabelMultiset = N5Helpers.getBooleanAttribute(reader, dataset, N5Helpers.IS_LABEL_MULTISET_KEY, false);
		final CachedCellImg<I, ?> img = isLabelMultiset ? (CachedCellImg<I, ?>) (CachedCellImg) N5LabelMultisets.openLabelMultiset(reader, dataset) : N5Utils.open(reader, dataset);
		final int[] blockSize = new  int[img.numDimensions()];
		img.getCellGrid().cellDimensions(blockSize);
		final List<Interval> blocks = Grids.collectAllContainedIntervals(img.getCellGrid().getImgDimensions(), blockSize);
		final IntegerProperty completedTasks = new SimpleIntegerProperty(0);
		final LongProperty maxId = new SimpleLongProperty(0);
		final BooleanProperty wasCanceled = new SimpleBooleanProperty(false);
		LOG.debug("Scanning for max id over {} blocks of size {} (total size {}).", blocks.size(), blockSize, img.getCellGrid().getImgDimensions());
		final Thread t = new Thread(() -> {
			final List<Callable<Long>> tasks = new ArrayList<>();
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
			LOG.warn("Setting max id to {}", maxId.get());
			maxIdTarget.accept(maxId.get());
		} else {
			wasCanceled.set(true);
		}

	}

	private Node initializeNode(
			final Node rootNode,
			final String datasetPromptText,
			final Node browseNode)
	{
		final MenuButton datasetDropDown = new MenuButton();
		final StringBinding datasetDropDownText = Bindings.createStringBinding(() -> dataset.get() == null || dataset.get().length() == 0 ? datasetPromptText : datasetPromptText + ": " + dataset.get(), dataset);
		final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(() -> Optional.ofNullable(dataset.get()).map(Tooltip::new).orElse(null), dataset);
		datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
		datasetDropDown.disableProperty().bind(this.isN5Valid.not());
		datasetDropDown.textProperty().bind(datasetDropDownText);
		datasetChoices.addListener((ListChangeListener<String>) change -> {
			final MatchSelection matcher = MatchSelection.fuzzySorted(datasetChoices, s -> {
				dataset.set(s);
				datasetDropDown.hide();
			});
			LOG.debug("Updating dataset dropdown to fuzzy matcher with choices: {}", datasetChoices);
			final CustomMenuItem menuItem = new CustomMenuItem(matcher, false);
			// clear style to avoid weird blue highlight
			menuItem.getStyleClass().clear();
			datasetDropDown.getItems().setAll(menuItem);
			datasetDropDown.setOnAction(e -> {datasetDropDown.show(); matcher.requestFocus();});
		});
		final GridPane grid = new GridPane();
		grid.add(rootNode, 0, 0);
		grid.add(datasetDropDown, 0, 1);
		GridPane.setHgrow(rootNode, Priority.ALWAYS);
		GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);
		grid.add(browseNode, 1, 0);

		return grid;
	}

	public ObservableStringValue nameProperty()
	{
		return name;
	}

	public String identifier()
	{
		return identifier;
	}

	public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	List<? extends SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> getChannels(
			final String name,
			final int[] channelSelection,
			final SharedQueue queue,
			final int priority) throws Exception
	{
		final N5Reader                  reader            = n5.get();
		final String                    dataset           = this.dataset.get();
		final N5Meta                    meta              = N5Meta.fromReader(reader, dataset);
		final double[]                  resolution        = asPrimitiveArray(resolution());
		final double[]                  offset            = asPrimitiveArray(offset());
		final AffineTransform3D         transform         = N5Helpers.fromResolutionAndOffset(resolution, offset);
		final long                      numChannels       = datasetAttributes.get().getDimensions()[3];

		LOG.debug("Got channel info: num channels={} channels selection={}", numChannels, channelSelection);
		final N5BackendChannel<T, V> backend = new N5BackendChannel<>(n5.get(), dataset, channelSelection, 3);
		final ConnectomicsChannelState<T, V, RealComposite<T>, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state = new ConnectomicsChannelState<>(
				backend,
				queue,
				priority,
				name + "-" + Arrays.toString(channelSelection),
				resolution,
				offset);
		state.converter().setMins(i -> min().get());
		state.converter().setMaxs(i -> max().get());
		return Collections.singletonList(state);
	}

	public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	SourceState<T, V> getRaw(
			final String name,
			final SharedQueue queue,
			final int priority) throws Exception
	{
		LOG.debug("Raw data set requested. Name={}", name);
		final N5Writer           writer     = n5.get();
		final String             dataset    = this.dataset.get();
		final double[]           resolution = asPrimitiveArray(resolution());
		final double[]           offset     = asPrimitiveArray(offset());
		final N5BackendRaw<T, V> backend    = new N5BackendRaw<>(writer, dataset);
		final SourceState<T, V>  state      = new ConnectomicsRawState<>(backend, queue, priority, name, resolution, offset);
		LOG.debug("Returning raw source state {} {}", name, state);
		return state;
	}

	public <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> SourceState<D, T>
	getLabels(
			final String name,
			final SharedQueue queue,
			final int priority,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService manager,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
			final ExecutorService propagationQueue,
			final Supplier<String> projectDirectory) throws IOException, ReflectionException {
		final N5Writer          reader     = n5.get();
		final String            dataset    = this.dataset.get();
		final double[]          resolution = asPrimitiveArray(resolution());
		final double[]          offset     = asPrimitiveArray(offset());

		final N5Backend<D, T> backend = N5Backend.createFrom(
				reader,
				dataset,
				projectDirectory,
				propagationQueue);
		return new ConnectomicsLabelState<>(
				backend,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				manager,
				workers,
				queue,
				priority,
				name,
				resolution,
				offset,
				null);
	}

	public boolean isLabelMultisetType() throws Exception
	{
		final N5Writer n5      = this.n5.get();
		final String   dataset = this.dataset.get();
		final Boolean attribute = n5.getAttribute(
				N5Helpers.isPainteraDataset(n5, dataset) ? dataset + "/" + N5Helpers.PAINTERA_DATA_DATASET : dataset,
				N5Helpers.LABEL_MULTISETTYPE_KEY,
				Boolean.class
		                                         );
		LOG.debug("Getting label multiset attribute: {}", attribute);
		return Optional.ofNullable(attribute).orElse(false);
	}

	public DatasetAttributes getAttributes() throws IOException
	{
		final N5Reader n5 = this.n5.get();
		final String   ds = this.dataset.get();

		if (n5.datasetExists(ds))
		{
			LOG.debug("Getting attributes for {} and {}", n5, ds);
			return n5.getDatasetAttributes(ds);
		}

		if (n5.listAttributes(ds).containsKey("painteraData")) {
			LOG.debug("Getting attributes for paintera dataset {}", ds);
			return n5.getDatasetAttributes(String.format("%s/data/s0", ds));
		}

		final String[] scaleDirs = N5Helpers.listAndSortScaleDatasets(n5, ds);

		if (scaleDirs.length > 0)
		{
			LOG.debug("Getting attributes for {} and {}", n5, scaleDirs[0]);
			return n5.getDatasetAttributes(String.format("%s/s0", ds));
		}

		throw new RuntimeException(String.format(
				"Cannot read dataset attributes for group %s and dataset %s.",
				n5,
				ds));

	}

	public DataType getDataType() throws IOException
	{
		return getAttributes().getDataType();
	}

	public boolean isIntegerType() throws Exception
	{
		return N5Types.isIntegerType(getDataType());
	}

	public double[] asPrimitiveArray(final DoubleProperty[] data)
	{
		return Arrays.stream(data).mapToDouble(DoubleProperty::get).toArray();
	}

	public void setResolution(final double[] resolution)
	{
		final DoubleProperty[] res = resolution();
		for (int i = 0; i < res.length; ++i)
		{
			res[i].set(resolution[i]);
		}
	}

	public void setOffset(final double[] offset)
	{
		final DoubleProperty[] off = offset();
		for (int i = 0; i < off.length; ++i)
		{
			off[i].set(offset[i]);
		}
	}

	@Override
	public void close() {
		LOG.debug("Closing {}", this.getClass().getName());
		cancelDiscovery();
	}
}
