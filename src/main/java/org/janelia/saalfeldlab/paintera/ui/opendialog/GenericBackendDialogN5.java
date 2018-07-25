package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBColorConverter.InvertingImp1;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.fx.ui.ExceptionNode;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelFromFile;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericBackendDialogN5 implements BackendDialog
{

	private static final String EMPTY_STRING = "";

	private static final String LABEL_MULTISETTYPE_KEY = "isLabelMultiset";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";

	private static final String MAX_NUM_ENTRIES_KEY = "maxNumEntries";

	private static final String ERROR_MESSAGE_PATTERN = "n5? %s -- dataset? %s -- update? %s";

	private final DatasetInfo datasetInfo = new DatasetInfo();

	private final SimpleObjectProperty<Supplier<N5Writer>> n5Supplier = new SimpleObjectProperty<>(() -> null);

	private final ObjectBinding<N5Writer> n5 = Bindings.createObjectBinding(() -> Optional.ofNullable(n5Supplier.get()
	                                                                                                 ).map(
			Supplier::get).orElse(null), n5Supplier);

	private final StringProperty dataset = new SimpleStringProperty();

	private final ArrayList<Thread> directoryTraversalThreads = new ArrayList<>();

	private final SimpleBooleanProperty isTraversingDirectories = new SimpleBooleanProperty();

	private final BooleanBinding isN5Valid = n5.isNotNull();

	private final BooleanBinding isDatasetValid = dataset.isNotNull().and(dataset.isNotEqualTo(EMPTY_STRING));

	private final SimpleBooleanProperty datasetUpdateFailed = new SimpleBooleanProperty(false);

	private final ExecutorService propagationExecutor;

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
			final Consumer<Event> onBrowseClicked,
			final String identifier,
			final ObservableValue<Supplier<N5Writer>> writerSupplier,
			final ExecutorService propagationExecutor)
	{
		this("dataset", n5RootNode, onBrowseClicked, identifier, writerSupplier, propagationExecutor);
	}

	public GenericBackendDialogN5(
			final String datasetPrompt,
			final Node n5RootNode,
			final Consumer<Event> onBrowseClicked,
			final String identifier,
			final ObservableValue<Supplier<N5Writer>> writerSupplier,
			final ExecutorService propagationExecutor)
	{
		this.identifier = identifier;
		this.node = initializeNode(n5RootNode, datasetPrompt, onBrowseClicked);
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
			synchronized (directoryTraversalThreads)
			{
				this.isTraversingDirectories.set(false);
				directoryTraversalThreads.forEach(Thread::interrupt);
				directoryTraversalThreads.clear();
				final Thread t = new Thread(() -> {
					this.isTraversingDirectories.set(true);
					final AtomicBoolean discardDatasetList = new AtomicBoolean(false);
					try
					{
						final List<String> datasets = N5Helpers.discoverDatasets(
								newv,
								() -> discardDatasetList.set(true)
						                                                        );
						if (!Thread.currentThread().isInterrupted() && !discardDatasetList.get())
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
				directoryTraversalThreads.add(t);
				t.start();
			}
		});
		dataset.addListener((obs, oldv, newv) -> Optional.ofNullable(newv).filter(v -> v.length() > 0).ifPresent(v ->
				updateDatasetInfo(
				v,
				this.datasetInfo
		                                                                                                                               )));

		this.isN5Valid.addListener((obs, oldv, newv) -> {
			synchronized (directoryTraversalThreads)
			{
				directoryTraversalThreads.forEach(Thread::interrupt);
				directoryTraversalThreads.clear();
			}
		});

		dataset.set("");
	}

	public void updateDatasetInfo(final String group, final DatasetInfo info)
	{

		LOG.debug("Updating dataset info for dataset {}", group);
		try
		{
			final N5Reader n5 = this.n5.get();

			setResolution(N5Helpers.getResolution(n5, group));
			setOffset(N5Helpers.getOffset(n5, group));

			final DataType dataType = N5Helpers.getDataType(n5, group);

			this.datasetInfo.minProperty().set(Optional.ofNullable(n5.getAttribute(
					group,
					MIN_KEY,
					Double.class
			                                                                      )).orElse(N5Helpers.minForType(
					dataType)));
			this.datasetInfo.maxProperty().set(Optional.ofNullable(n5.getAttribute(
					group,
					MAX_KEY,
					Double.class
			                                                                      )).orElse(N5Helpers.maxForType(
					dataType)));
		} catch (final IOException e)
		{
			ExceptionNode.exceptionDialog(e).show();
		}
	}

	@Override
	public Node getDialogNode()
	{
		return node;
	}

	@Override
	public StringBinding errorMessage()
	{
		return errorMessage;
	}

	@Override
	public DoubleProperty[] resolution()
	{
		return this.datasetInfo.spatialResolutionProperties();
	}

	@Override
	public DoubleProperty[] offset()
	{
		return this.datasetInfo.spatialOffsetProperties();
	}

	@Override
	public DoubleProperty min()
	{
		return this.datasetInfo.minProperty();
	}

	@Override
	public DoubleProperty max()
	{
		return this.datasetInfo.maxProperty();
	}

	public FragmentSegmentAssignmentState assignments() throws IOException
	{
		return N5Helpers.assignments(n5.get(), this.dataset.get());
	}

	public IdService idService()
	{
		try
		{
			return N5Helpers.idService(this.n5.get(), this.dataset.get());
		} catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private Node initializeNode(
			final Node rootNode,
			final String datasetPromptText,
			final Consumer<Event> onBrowseClicked)
	{
		final ComboBox<String> datasetDropDown = new ComboBox<>(datasetChoices);
		datasetDropDown.setPromptText(datasetPromptText);
		datasetDropDown.setEditable(false);
		datasetDropDown.valueProperty().bindBidirectional(dataset);
		datasetDropDown.disableProperty().bind(this.isN5Valid.not());
		final GridPane grid = new GridPane();
		grid.add(rootNode, 0, 0);
		grid.add(datasetDropDown, 0, 1);
		GridPane.setHgrow(rootNode, Priority.ALWAYS);
		GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);
		final Button button = new Button("Browse");
		button.setOnAction(onBrowseClicked::accept);
		grid.add(button, 1, 0);

		return grid;
	}

	@Override
	public ObservableStringValue nameProperty()
	{
		return name;
	}

	@Override
	public String identifier()
	{
		return identifier;
	}

	@Override
	public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	RawSourceState<T, V> getRaw(
			final String name,
			final SharedQueue sharedQueue,
			final int priority) throws Exception
	{
		final N5Reader             reader     = n5.get();
		final String               dataset    = this.dataset.get();
		final double[]             resolution = asPrimitiveArray(resolution());
		final double[]             offset     = asPrimitiveArray(offset());
		final AffineTransform3D    transform  = N5Helpers.fromResolutionAndOffset(resolution, offset);
		final DataSource<T, V>     source     = N5Helpers.openRawAsSource(
				reader,
				dataset,
				transform,
				sharedQueue,
				priority,
				name
		                                                                 );
		final InvertingImp1<V>     converter  = new ARGBColorConverter.InvertingImp1<>(min().get(), max().get());
		final RawSourceState<T, V> state      = new RawSourceState<>(source, converter, new CompositeCopy<>(), name);
		LOG.debug("Returning raw source state {} {}", name, state);
		return state;
	}

	@Override
	public <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> LabelSourceState<D, T>
	getLabels(
			final String name,
			final SharedQueue sharedQueue,
			final int priority,
			final Group meshesGroup,
			final ExecutorService manager,
			final ExecutorService workers,
			final String projectDirectory) throws Exception
	{
		final N5Writer          reader     = n5.get();
		final String            dataset    = this.dataset.get();
		final double[]          resolution = asPrimitiveArray(resolution());
		final double[]          offset     = asPrimitiveArray(offset());
		final AffineTransform3D transform  = N5Helpers.fromResolutionAndOffset(resolution, offset);
		final DataSource<D, T>  source;
		if (N5Helpers.isLabelMultisetType(reader, dataset))
		{
			source = (DataSource) N5Helpers.openLabelMultisetAsSource(
					reader,
					dataset,
					transform,
					sharedQueue,
					priority,
					name
			                                                         );
		}
		else
		{
			source = (DataSource<D, T>) N5Helpers.openScalarAsSource(
					reader,
					dataset,
					transform,
					sharedQueue,
					priority,
					name
			                                                        );
		}

		final Supplier<String> canvasCacheDirUpdate = Masks.canvasTmpDirDirectorySupplier(projectDirectory);

		final DataSource<D, T>               masked         = Masks.mask(
				source,
				canvasCacheDirUpdate.get(),
				canvasCacheDirUpdate,
				commitCanvas(),
				workers
		                                                                );
		final IdService                      idService      = idService();
		final FragmentSegmentAssignmentState assignment     = assignments();
		final SelectedIds                    selectedIds    = new SelectedIds();
		final LockedSegmentsOnlyLocal        lockedSegments = new LockedSegmentsOnlyLocal(locked -> {
		});
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
				ModalGoldenAngleSaturatedHighlightingARGBStream(
				selectedIds,
				assignment,
				lockedSegments
		);
		final HighlightingStreamConverter<T> converter = HighlightingStreamConverter.forType(stream, masked.getType());

		final BlocksForLabelFromFile[] blockLoaders = Arrays
				.stream(N5Helpers.labelMappingFromFileLoaderPattern(reader, dataset))
				.map(BlocksForLabelFromFile::new)
				.toArray(BlocksForLabelFromFile[]::new);

		return new LabelSourceState<>(
				masked,
				converter,
				new ARGBCompositeAlphaYCbCr(),
				name,
				assignment,
				lockedSegments,
				idService,
				selectedIds,
				meshesGroup,
				blockLoaders,
				manager,
				workers
		);
	}

	public boolean isLabelType() throws Exception
	{
		return isIntegerType() || isLabelMultisetType();
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

		final String[] scaleDirs = N5Helpers.listAndSortScaleDatasets(n5, ds);

		if (scaleDirs.length > 0)
		{
			LOG.debug("Getting attributes for {} and {}", n5, scaleDirs[0]);
			return n5.getDatasetAttributes(Paths.get(ds, scaleDirs[0]).toString());
		}

		throw new RuntimeException(String.format(
				"Cannot read dataset attributes for group %s and dataset %s.",
				n5,
				ds
		                                        ));

	}

	public DataType getDataType() throws IOException
	{
		return getAttributes().getDataType();
	}

	public boolean isIntegerType() throws Exception
	{
		return N5Helpers.isIntegerType(getDataType());
	}

	public BiConsumer<CachedCellImg<UnsignedLongType, ?>, long[]> commitCanvas()
	{
		final String   dataset = this.dataset.get();
		final N5Writer writer  = this.n5.get();
		return new CommitCanvasN5(writer, dataset);
	}

	public ExecutorService propagationExecutor()
	{
		return this.propagationExecutor;
	}

	public double[] asPrimitiveArray(final DoubleProperty[] data)
	{
		return Arrays.stream(data).mapToDouble(DoubleProperty::get).toArray();
	}
}
