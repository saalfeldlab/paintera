package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.ui.MatchSelection;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.state.metadata.ContainerState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.opendialog.DatasetInfo;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5ReadOnlyException;
import org.janelia.saalfeldlab.util.n5.ij.N5TreeNode;
import org.janelia.saalfeldlab.util.n5.metadata.PainteraBaseMetadata;
import org.janelia.saalfeldlab.util.n5.metadata.PainteraMultiscaleGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GenericBackendDialogN5 implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String EMPTY_STRING = "";

  private static final String ERROR_MESSAGE_PATTERN = "n5? %s -- dataset? %s -- update? %s";

  private final DatasetInfo datasetInfo = new DatasetInfo();

  private final SimpleObjectProperty<ContainerState> containerState = new SimpleObjectProperty<>();

  private final ObjectBinding<N5Writer> sourceWriter = Bindings.createObjectBinding(
		  () -> containerState.isNotNull().get() ? containerState.get().getWriterProperty().getValue() : null,
		  containerState);

  private final BooleanBinding isContainerValid = containerState.isNotNull();

  private final BooleanBinding readOnly = Bindings.createBooleanBinding(() -> containerState.isNotNull().get() && containerState.get().getWriter().isEmpty(), sourceWriter);

  private final ObjectProperty<N5TreeNode> activeN5Node = new SimpleObjectProperty<>();

  private final ObjectBinding<PainteraBaseMetadata> activeMetadata = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(activeN5Node.get())
				  .map(N5TreeNode::getMetadata)
				  .filter(PainteraBaseMetadata.class::isInstance)
				  .map(PainteraBaseMetadata.class::cast)
				  .orElse(null),
		  activeN5Node);

  {
	activeMetadata.addListener((obs, oldv, newv) -> this.updateDatasetInfo(newv));
  }

  private final ObjectBinding<MetadataState> metadataState = Bindings.createObjectBinding(() -> {
	if (containerState.isNotNull().and(activeMetadata.isNotNull()).get())
	  return new MetadataState(containerState.get(), getMetadata());
	return null;
  }, activeMetadata);

  private final StringBinding datasetPath = Bindings.createStringBinding(() -> Optional.ofNullable(this.activeN5Node.get()).map(N5TreeNode::getPath).orElse(null), activeN5Node);

  private final ArrayList<Thread> discoveryThreads = new ArrayList<>();

  private final BooleanProperty discoveryIsActive = new SimpleBooleanProperty();

  private final BooleanBinding isDatasetValid = Bindings.createBooleanBinding(() -> activeN5Node.get() != null && !Objects.equals(getDatasetPath(), EMPTY_STRING), activeN5Node);

  private final SimpleBooleanProperty datasetUpdateFailed = new SimpleBooleanProperty(false);

  private final ObjectBinding<DatasetAttributes> datasetAttributes = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(activeMetadata.get()).map(PainteraBaseMetadata::getAttributes).orElse(null)
		  , activeMetadata
  );

  private final ObjectBinding<long[]> dimensions = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(datasetAttributes.get())
				  .map(DatasetAttributes::getDimensions)
				  .orElse(null),
		  datasetAttributes);

  private final BooleanBinding isReady = isContainerValid.and(isDatasetValid).and(datasetUpdateFailed.not());

  {
	isContainerValid.addListener((obs, oldv, newv) -> datasetUpdateFailed.set(false));
  }

  private final StringBinding errorMessage = Bindings.createStringBinding(
		  () -> isReady.get()
				  ? null
				  : String.format(ERROR_MESSAGE_PATTERN,
				  isContainerValid.get(),
				  isDatasetValid.get(),
				  datasetUpdateFailed.not().get()
		  ),
		  isReady
  );

  private final StringBinding name = Bindings.createStringBinding(() -> {
	final String[] entries = Optional
			.ofNullable(getDatasetPath())
			.map(d -> d.split("/"))
			.filter(a -> a.length > 0)
			.orElse(new String[]{null});
	return entries[entries.length - 1];
  }, activeN5Node);

  private final ObservableMap<String, N5TreeNode> datasetChoices = FXCollections.observableHashMap();

  private final String identifier;

  private final Node node;

  public GenericBackendDialogN5(
		  final Node n5RootNode,
		  final Node browseNode,
		  final String identifier,
		  final ObservableValue<ContainerState> containerState) {

	this("_Dataset", n5RootNode, browseNode, identifier, containerState);
  }

  public GenericBackendDialogN5(
		  final String datasetPrompt,
		  final Node n5RootNode,
		  final Node browseNode,
		  final String identifier,
		  final ObservableValue<ContainerState> containerState) {

	this.identifier = identifier;
	this.node = initializeNode(n5RootNode, datasetPrompt, browseNode);
	this.containerState.bind(containerState);

	containerState.addListener((obs, oldv, newv) -> {
	  if (newv == null) {
		datasetChoices.clear();
		return;
	  }
	  if (oldv == null) {
		LOG.debug("Updated container: obs={} oldv=null newv={}", obs, newv.getUrl());
	  } else {
		LOG.debug("Updated container: obs={} oldv={} newv={}", obs, oldv.getUrl(), newv.getUrl());
	  }
	  /* reset the active node if we are changing containers */
	  if (!newv.equals(oldv))
		this.activeN5Node.set(null);
	  updateDatasetChoices(newv.getReader());
	});

	this.isContainerValid.addListener((obs, oldv, newv) -> cancelDiscovery());

	/* Initial dataset update, if the reader is already set. This is the case when you open a new source for the second time, from the same container.
	 * We pass the same argument to both parameters, since it is not changing.  */
	Optional.ofNullable(containerState.getValue()).map(ContainerState::getReader).ifPresent(this::updateDatasetChoices);
  }

  private void updateDatasetChoices(N5Reader newReader) {

	synchronized (discoveryIsActive) {
	  LOG.debug("Updating dataset choices!");
	  cancelDiscovery();
	  discoveryIsActive.set(true);
	  final Thread discoveryThread = new Thread(() -> {
		final var metadataTree = N5Helpers.parseMetadata(newReader, discoveryIsActive).orElse(null);
		if (metadataTree == null || metadataTree.getMetadata() == null)
		  InvokeOnJavaFXApplicationThread.invoke(() -> this.activeN5Node.set(null));
		final var validChoices = new ArrayList<N5TreeNode>();
		final var potentialDatasets = new ArrayList<N5TreeNode>();
		potentialDatasets.add(metadataTree);
		for (var idx = 0; idx < potentialDatasets.size(); idx++) {
		  final var potential = potentialDatasets.get(idx);
		  if (potential.getMetadata() instanceof PainteraBaseMetadata)
			validChoices.add(potential);
		  else if (!potential.childrenList().isEmpty())
			potentialDatasets.addAll(potential.childrenList());
		}
		final var datasets = validChoices.stream().map(N5TreeNode::getPath).collect(Collectors.toList());
		if (!Thread.currentThread().isInterrupted() && discoveryIsActive.get()) {
		  LOG.debug("Found these datasets: {}", datasets);
		  InvokeOnJavaFXApplicationThread.invoke(() -> {
			datasetChoices.clear();
			validChoices.forEach(n5Node -> datasetChoices.put(n5Node.getPath(), n5Node));
		  });
		}
	  });
	  discoveryThread.setDaemon(true);
	  discoveryThread.start();
	}
  }

  public void cancelDiscovery() {

	LOG.debug("Canceling discovery.");
	synchronized (discoveryIsActive) {
	  discoveryIsActive.set(false);
	  discoveryThreads.forEach(Thread::interrupt);
	  discoveryThreads.clear();

	}
  }

  public BooleanBinding readOnlyBinding() {

	return this.readOnly;
  }

  public ObservableObjectValue<DatasetAttributes> datsetAttributesProperty() {

	return this.datasetAttributes;
  }

  public ObservableObjectValue<long[]> dimensionsProperty() {

	return this.dimensions;
  }

  public void updateDatasetInfo(final PainteraBaseMetadata metadata) {

	if (metadata == null)
	  return;

	final var group = metadata.getPath(); // FIXME Not being used atm
	LOG.debug("Updating dataset info for dataset {}", group);
	setResolution(metadata.getResolution());
	setOffset(metadata.getOffset());

	// TODO handle array case!
	// 	Probably best to always handle min and max as array and populate acoording
	// 	to n5 meta data
	this.datasetInfo.getMinProperty().set(metadata.min());
	this.datasetInfo.getMaxProperty().set(metadata.max());
  }

  public Node getDialogNode() {

	return node;
  }

  public StringBinding errorMessage() {

	return errorMessage;
  }

  public DoubleProperty[] resolution() {

	return this.datasetInfo.getSpatialResolutionProperties();
  }

  public DoubleProperty[] offset() {

	return this.datasetInfo.getSpatialOffsetProperties();
  }

  public DoubleProperty min() {

	return this.datasetInfo.getMinProperty();
  }

  public DoubleProperty max() {

	return this.datasetInfo.getMaxProperty();
  }

  public FragmentSegmentAssignmentState assignments() throws IOException {

	final var writer = getContainer().getWriter().orElseThrow(() -> new N5ReadOnlyException());
	return N5Helpers.assignments(writer, getDatasetPath());
  }

  private Node initializeNode(final Node rootNode, final String datasetPromptText, final Node browseNode) {

	/* Create the grid and add the root node */
	final GridPane grid = new GridPane();
	grid.add(rootNode, 0, 0);
	GridPane.setHgrow(rootNode, Priority.ALWAYS);

	/* create and add the datasertDropdown Menu*/
	final MenuButton datasetDropDown = createDatasetDropdownMenu(datasetPromptText);
	grid.add(datasetDropDown, 0, 1);
	GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);

	grid.add(browseNode, 1, 0);

	return grid;
  }

  private MenuButton createDatasetDropdownMenu(String datasetPromptText) {

	final MenuButton datasetDropDown = new MenuButton();
	String datasetPath = getDatasetPath();
	boolean datasetPathIsValid = datasetPath == null || datasetPath.length() == 0;
	final StringBinding datasetDropDownText = Bindings.createStringBinding(
			() -> datasetPathIsValid ? datasetPromptText : datasetPromptText + ": " + datasetPath,
			activeN5Node);
	final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
			() -> Optional.ofNullable(datasetPath).map(Tooltip::new).orElse(null),
			activeN5Node);
	datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
	datasetDropDown.disableProperty().bind(this.isContainerValid.not());
	datasetDropDown.textProperty().bind(datasetDropDownText);
	/* If the datasetchoices are changed, create new menuItems, and update*/
	datasetChoices.addListener((MapChangeListener<String, N5TreeNode>)change -> {
	  final var choices = List.copyOf(datasetChoices.keySet());
	  final Consumer<String> onMatchFound = s -> {
		activeN5Node.set(datasetChoices.get(s));
		datasetDropDown.hide();
	  };
	  final MatchSelection matcher = MatchSelection.fuzzySorted(choices, onMatchFound);
	  LOG.debug("Updating dataset dropdown to fuzzy matcher with choices: {}", choices);
	  final CustomMenuItem menuItem = new CustomMenuItem(matcher, false);
	  // clear style to avoid weird blue highlight
	  menuItem.getStyleClass().clear();
	  datasetDropDown.getItems().setAll(menuItem);
	  datasetDropDown.setOnAction(e -> {
		datasetDropDown.show();
		matcher.requestFocus();
	  });
	});
	return datasetDropDown;
  }

  public ObservableStringValue nameProperty() {

	return name;
  }

  public String identifier() {

	return identifier;
  }

  public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
  List<? extends SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> getChannels(
		  final String name,
		  final int[] channelSelection,
		  final SharedQueue queue,
		  final int priority) throws Exception {

	final String dataset = getDatasetPath();
	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());
	final long numChannels = datasetAttributes.get().getDimensions()[3];

	LOG.debug("Got channel info: num channels={} channels selection={}", numChannels, channelSelection);
	final N5BackendChannel<T, V> backend = new N5BackendChannel<>(getContainer().getReader(), dataset, channelSelection, 3, metadataState.get());
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

  private ContainerState getContainer() {

	return containerState.get();
  }

  private String getDatasetPath() {

	return this.datasetPath.get();
  }

  public PainteraBaseMetadata getMetadata() {

	return this.activeMetadata.get();
  }

  public N5TreeNode getN5TreeNode() {

	return this.activeN5Node.get();
  }

  public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
  SourceState<T, V> getRaw(
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws Exception {

	LOG.debug("Raw data set requested. Name={}", name);
	final String dataset = getDatasetPath();
	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());
	final N5BackendRaw<T, V> backend = new N5BackendRaw<>(getContainer().getReader(), dataset, metadataState.get());
	final SourceState<T, V> state = new ConnectomicsRawState<>(backend, queue, priority, name, resolution, offset);
	final ARGBColorConverter.InvertingImp0 converter = (ARGBColorConverter.InvertingImp0)state.converter();
	converter.setMin(min().get());
	converter.setMax(max().get());
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

	final String dataset = getDatasetPath();
	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());

	final N5Backend<D, T> backend = N5Backend.createFrom(
			getContainer().getWriter().get(),
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

  public boolean isLabelMultisetType() throws Exception {

	final boolean isLabelMultiset = Optional.ofNullable(activeMetadata.get())
			.filter(PainteraMultiscaleGroup.class::isInstance)
			.map(PainteraMultiscaleGroup.class::cast)
			.map(PainteraMultiscaleGroup::isLabelMultisetType)
			.orElse(false);
	LOG.debug("Getting label multiset attribute: {}", isLabelMultiset);
	return isLabelMultiset;
  }

  public DatasetAttributes getAttributes() {

	final var metadata = getMetadata();
	final var n5Node = getN5TreeNode();
	LOG.debug("Getting attributes for group {} from metadata type: {}", n5Node.getPath(), metadata.getClass().getSimpleName());
	return metadata.getAttributes();
	//TODO meta test with (RAW/LABEL) genericSingle,genericMulti,PainteraData,Cosem
  }

  public static double[] asPrimitiveArray(final DoubleProperty[] data) {

	return Arrays.stream(data).mapToDouble(DoubleProperty::get).toArray();
  }

  public void setResolution(final double[] resolution) {

	final DoubleProperty[] res = resolution();
	for (int i = 0; i < res.length; ++i) {
	  res[i].set(resolution[i]);
	}
  }

  public void setOffset(final double[] offset) {

	final DoubleProperty[] off = offset();
	for (int i = 0; i < off.length; ++i) {
	  off[i].set(offset[i]);
	}
  }

  @Override
  public void close() {

	LOG.debug("Closing {}", this.getClass().getName());
	cancelDiscovery();
  }
}
