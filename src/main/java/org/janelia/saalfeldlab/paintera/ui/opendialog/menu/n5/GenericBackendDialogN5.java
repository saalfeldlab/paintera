package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
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
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.opendialog.DatasetInfo;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.janelia.saalfeldlab.util.fx.Tasks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5ReadOnlyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sawano.java.text.AlphanumericComparator;

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

import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class GenericBackendDialogN5 implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String EMPTY_STRING = "";

  private static final String ERROR_MESSAGE_PATTERN = "n5? %s -- dataset? %s -- update? %s";

  private final DatasetInfo datasetInfo = new DatasetInfo();

  private final SimpleObjectProperty<N5ContainerState> containerState = new SimpleObjectProperty<>();

  private final ObjectBinding<N5Writer> sourceWriter = Bindings.createObjectBinding(
		  () -> containerState.isNotNull().get() ? containerState.get().getWriterProperty().getValue() : null,
		  containerState);

  private final BooleanBinding isContainerValid = containerState.isNotNull();

  private final BooleanBinding readOnly = Bindings.createBooleanBinding(() -> containerState.isNotNull().get() && containerState.get().getWriter().isEmpty(), sourceWriter);

  private final ObjectProperty<N5TreeNode> activeN5Node = new SimpleObjectProperty<>();

  private final ObjectBinding<N5Metadata> activeMetadata = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(activeN5Node.get())
				  .map(N5TreeNode::getMetadata)
				  .filter(md -> md instanceof MultiscaleMetadata || md instanceof N5SingleScaleMetadata)
				  .orElse(null),
		  activeN5Node);
  private final BooleanBinding isBusy;

  {
	activeMetadata.addListener((obs, oldv, newv) -> this.updateDatasetInfo(newv));
  }

  public MetadataState<?> getMetadataState() {

	return metadataState.get();
  }

  public ObjectBinding<MetadataState<?>> metadataStateProperty() {

	return metadataState;
  }

  private final ObjectBinding<MetadataState<?>> metadataState = Bindings.createObjectBinding(() -> {
	if (containerState.isNotNull().and(activeMetadata.isNotNull()).get()) {
	  N5Metadata metadata = activeMetadata.getValue();
	  return MetadataState.createMetadataState(containerState.get(), metadata).orElse(null);
	}
	return null;
  }, activeMetadata);

  private final StringBinding datasetPath = Bindings.createStringBinding(() -> Optional.ofNullable(this.activeN5Node.get()).map(N5TreeNode::getPath).orElse(null), activeN5Node);

  private final ArrayList<Thread> discoveryThreads = new ArrayList<>();

  private final BooleanProperty discoveryIsActive = new SimpleBooleanProperty();

  private final BooleanBinding isDatasetValid = Bindings.createBooleanBinding(() -> activeN5Node.get() != null && !Objects.equals(getDatasetPath(), EMPTY_STRING), activeN5Node);

  private final SimpleBooleanProperty datasetUpdateFailed = new SimpleBooleanProperty(false);

  private final ObjectBinding<DatasetAttributes> datasetAttributes = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(activeMetadata.get()).map(md -> getAttributes()).orElse(null)
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

  private final MapProperty<String, N5TreeNode> datasetChoices = new SimpleMapProperty<>();

  private final String identifier;

  private final Node node;

  public GenericBackendDialogN5(
		  final Node n5RootNode,
		  final Node browseNode,
		  final String identifier,
		  final ObservableValue<N5ContainerState> containerState,
		  final BooleanProperty isOpeningContainer) {

	this("_Dataset", n5RootNode, browseNode, identifier, containerState, isOpeningContainer);
  }

  public GenericBackendDialogN5(
		  final String datasetPrompt,
		  final Node n5RootNode,
		  final Node browseNode,
		  final String identifier,
		  final ObservableValue<N5ContainerState> containerState,
		  final BooleanProperty isOpeningContainer) {

	this.identifier = identifier;
	this.isBusy = Bindings.createBooleanBinding(() -> isOpeningContainer.get() || discoveryIsActive().get(), isOpeningContainer, discoveryIsActive);
	this.containerState.bind(containerState);
	this.node = initializeNode(n5RootNode, datasetPrompt, browseNode);

	containerState.addListener((obs, oldContainer, newContainer) -> {
	  /* if nothing has changed, do nothing */
	  if (newContainer != null && newContainer.equals(oldContainer))
		return;

	  /* otherwise, clear the existing choices*/
	  datasetChoices.clear();
	  /* reset the active node if we are changing containers */
	  invoke(() -> this.activeN5Node.set(null));

	  /* if we are non-null, update the choices*/
	  if (newContainer != null) {
		final var reader = newContainer.getReader();
		this.updateDatasetChoices(reader);
	  }
	  final var oldUrl = Optional.ofNullable(oldContainer).map(N5ContainerState::getUrl).orElse(null);
	  final var newUrl = Optional.ofNullable(newContainer).map(N5ContainerState::getUrl).orElse(null);
	  LOG.debug("Updated container: obs={} oldv={} newv={}", obs, oldUrl, newUrl);
	});

	this.isContainerValid.addListener((obs, oldv, newv) -> cancelDiscovery());

	/* Initial dataset update, if the reader is already set. This is the case when you open a new source for the second time, from the same container.
	 * We pass the same argument to both parameters, since it is not changing.  */
	Optional.ofNullable(containerState.getValue()).map(N5ContainerState::getReader).ifPresent(this::updateDatasetChoices);
  }

  private void updateDatasetChoices(N5Reader newReader) {

	synchronized (discoveryIsActive) {
	  invoke(() -> {
		cancelDiscovery(); // If discovery is ongoing, cancel it.
		LOG.debug("Updating dataset choices!");
		discoveryIsActive.set(true);
		invoke(datasetChoices::clear); // clean up whatever is currently shown
	  });
	  Tasks.<ObservableMap<String, N5TreeNode>>createTask(
			  thisTask -> {
				/* Parse the container's metadata*/
				final ObservableMap<String, N5TreeNode> validDatasetChoices = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap());
				final var metadataTree = N5Helpers.parseMetadata(newReader, discoveryIsActive).orElse(null);
				if (metadataTree == null || metadataTree.getMetadata() == null)
				  invoke(() -> this.activeN5Node.set(null));
				/* filter the metadata for valid groups/datasets*/
				final var potentialDatasets = new ArrayList<N5TreeNode>();
				potentialDatasets.add(metadataTree);
				for (var idx = 0; idx < potentialDatasets.size(); idx++) {
				  final var potentialChoice = potentialDatasets.get(idx);
				  N5Metadata metadata = potentialChoice.getMetadata();
				  MetadataState.metadataIsValid(metadata).ifPresentOrElse(md -> {
					/* if we are valid, add and update out map. */
					final var validChoicePath = potentialChoice.getPath();
					invoke(() -> validDatasetChoices.put(validChoicePath, potentialChoice));
				  }, () -> {
					if (!potentialChoice.childrenList().isEmpty()) {
					  /* if we aren't valid, but have kids, lets check them later */
					  potentialDatasets.addAll(potentialChoice.childrenList());
					}
				  });
				}
				return validDatasetChoices;
			  })
			  .onSuccess((event, task) -> datasetChoices.set(task.getValue())) /* set the choices on success*/
			  .onEnd(task -> invoke(() -> discoveryIsActive.set(false))) /* clear the flag when done, regardless */
			  .submit();
	}
  }

  public void cancelDiscovery() {

	if (discoveryIsActive.get() || !discoveryThreads.isEmpty()) {
	  LOG.debug("Canceling discovery.");
	  synchronized (discoveryIsActive) {
		discoveryIsActive.set(false);
		discoveryThreads.forEach(Thread::interrupt);
		discoveryThreads.clear();

	  }
	}
  }

  public BooleanBinding readOnlyBinding() {

	return this.readOnly;
  }

  public ObservableObjectValue<DatasetAttributes> datasetAttributesProperty() {

	return this.datasetAttributes;
  }

  public ObservableObjectValue<long[]> dimensionsProperty() {

	return this.dimensions;
  }

  public void updateDatasetInfo(final N5Metadata metadata) {

	if (metadata == null)
	  return;

	final var group = metadata.getPath();
	LOG.debug("Updating dataset info for dataset {}", group);
	final N5SingleScaleMetadata singleScaleMetadata;
	if (metadata instanceof MultiscaleMetadata) {
	  final var multiscaleMd = ((MultiscaleMetadata<?>)metadata);
	  singleScaleMetadata = (N5SingleScaleMetadata)multiscaleMd.getChildrenMetadata()[0];
	} else {
	  singleScaleMetadata = (N5SingleScaleMetadata)metadata;
	}
	setResolution(singleScaleMetadata.getPixelResolution());
	setOffset(singleScaleMetadata.getOffset());

	// TODO handle array case!
	// 	Probably best to always handle min and max as array and populate acoording to n5 meta data
	this.datasetInfo.getMinProperty().set(singleScaleMetadata.minIntensity());
	this.datasetInfo.getMaxProperty().set(singleScaleMetadata.maxIntensity());
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

  public BooleanProperty discoveryIsActive() {

	return discoveryIsActive;
  }

  public FragmentSegmentAssignmentState assignments() throws IOException {

	final var writer = getContainer().getWriter().orElseThrow(N5ReadOnlyException::new);
	return N5Helpers.assignments(writer, getDatasetPath());
  }

  private Node initializeNode(final Node rootNode, final String datasetPromptText, final Node browseNode) {

	/* Create the grid and add the root node */
	final GridPane grid = new GridPane();
	grid.add(rootNode, 0, 0);
	GridPane.setColumnSpan(rootNode, 2);
	GridPane.setHgrow(rootNode, Priority.ALWAYS);

	/* create and add the datasertDropdown Menu*/
	final MenuButton datasetDropDown = createDatasetDropdownMenu(datasetPromptText);
	grid.add(datasetDropDown, 1, 1);
	GridPane.setHgrow(datasetDropDown, Priority.ALWAYS);

	grid.add(browseNode, 2, 0);

	ProgressIndicator progressIndicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
	progressIndicator.setScaleX(.75);
	progressIndicator.setScaleY(.75);

	grid.add(progressIndicator, 2, 1);
	GridPane.setHgrow(progressIndicator, Priority.NEVER);
	GridPane.setVgrow(progressIndicator, Priority.NEVER);
	progressIndicator.visibleProperty().bind(isBusyBinding());

	return grid;
  }

  private MenuButton createDatasetDropdownMenu(String datasetPromptText) {

	final MenuButton datasetDropDown = new MenuButton();
	final StringBinding datasetDropDownText = Bindings.createStringBinding(
			() -> getDatasetPath() == null || getDatasetPath().length() == 0 ? datasetPromptText : datasetPromptText + ": " + getDatasetPath(),
			activeN5Node);
	final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
			() -> Optional.ofNullable(getDatasetPath()).map(Tooltip::new).orElse(null),
			activeN5Node);
	datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
	/* disable when there are no choices */
	final var datasetDropDownDisable = Bindings.createBooleanBinding(this.datasetChoices::isEmpty, this.datasetChoices);
	datasetDropDown.disableProperty().bind(datasetDropDownDisable);
	datasetDropDown.textProperty().bind(datasetDropDownText);
	/* If the datasetchoices are changed, create new menuItems, and update*/
	datasetChoices.addListener((obs, oldv, newv) -> {
	  final var choices = new ArrayList<>(datasetChoices.keySet());
	  choices.sort(new AlphanumericComparator());
	  final Consumer<String> onMatchFound = s -> {
		activeN5Node.set(datasetChoices.get(s));
		datasetDropDown.hide();
	  };
	  final MatchSelection matcher = MatchSelection.fuzzySorted(choices, onMatchFound, 50);
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

  private N5ContainerState getContainer() {

	return containerState.get();
  }

  private String getDatasetPath() {

	return this.datasetPath.get();
  }

  public N5Metadata getMetadata() {

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
	final var converter = (ARGBColorConverter.InvertingImp0<?>)state.converter();
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

	N5Reader n5 = getContainer().getWriter().map(N5Reader.class::cast).orElse(getContainer().getReader());
	final ConnectomicsLabelBackend<D, T> backend = N5Backend.createFrom(n5, dataset, projectDirectory, propagationQueue);
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

	Boolean isLabelMultiset = Optional.ofNullable(metadataState.getValue()).map(MetadataState::isLabelMultiset).orElse(false);
	LOG.debug("Getting label multiset attribute: {}", isLabelMultiset);
	return isLabelMultiset;
  }

  public DatasetAttributes getAttributes() {

	final var metadata = getMetadata();
	final var n5Node = getN5TreeNode();
	LOG.debug("Getting attributes for group {} from metadata type: {}", n5Node.getPath(), metadata.getClass().getSimpleName());
	if (metadata instanceof MultiscaleMetadata)
	  return ((MultiscaleMetadata<?>)metadata).getChildrenMetadata()[0].getAttributes();
	else
	  return ((N5SingleScaleMetadata)metadata).getAttributes();
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

  public BooleanBinding isBusyBinding() {

	return isBusy;
  }
}
