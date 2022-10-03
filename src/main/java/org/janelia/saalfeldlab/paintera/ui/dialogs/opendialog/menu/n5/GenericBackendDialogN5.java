package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

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
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
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
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.SpatialMetadata;
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
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawBackend;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.DatasetInfo;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final ObjectBinding<SpatialMetadata> activeMetadata = Bindings.createObjectBinding(
		  () -> (SpatialMetadata)Optional.ofNullable(activeN5Node.get())
				  .map(N5TreeNode::getMetadata)
				  .filter(md -> md instanceof SpatialMetadata)
				  .orElse(null),
		  activeN5Node);

  private final BooleanBinding isBusy;

  private final ObjectBinding<MetadataState> metadataState = Bindings.createObjectBinding(() -> {
	if (containerState.isNotNull().and(activeMetadata.isNotNull()).get()) {
	  N5Metadata metadata = activeMetadata.getValue();
	  final MetadataState state = MetadataUtils.createMetadataState(containerState.get(), metadata).orElse(null);
	  if (state != null)
		updateDatasetInfo(state);
	  return state;
	}
	return null;
  }, activeMetadata);

  private final StringBinding datasetPath = Bindings.createStringBinding(() -> Optional.ofNullable(this.activeN5Node.get()).map(N5TreeNode::getPath).orElse(null), activeN5Node);

  private final ArrayList<Thread> discoveryThreads = new ArrayList<>();

  private final BooleanProperty discoveryIsActive = new SimpleBooleanProperty();

  private final BooleanBinding isDatasetValid = Bindings.createBooleanBinding(() -> activeN5Node.get() != null && !Objects.equals(getDatasetPath(), EMPTY_STRING), activeN5Node);

  private final SimpleBooleanProperty datasetUpdateFailed = new SimpleBooleanProperty(false);

  private final ObjectBinding<DatasetAttributes> datasetAttributes = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(metadataState.get()).map(md -> getAttributes()).orElse(null)
		  , metadataState
  );

  private final ObjectBinding<long[]> dimensions = Bindings.createObjectBinding(
		  () -> Optional.ofNullable(datasetAttributes.get())
				  .map(DatasetAttributes::getDimensions)
				  .orElse(null),
		  datasetAttributes);

  private final BooleanBinding isReady = isContainerValid.and(isDatasetValid).and(datasetUpdateFailed.not());

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
			.orElseGet(() -> {
			  if ("/".equals(getDatasetPath())) {
				return new String[]{"root"};
			  } else {
				return new String[]{null};
			  }
			});
	return entries[entries.length - 1];
  }, activeN5Node);

  private final MapProperty<String, N5TreeNode> datasetChoices = new SimpleMapProperty<>();

  private static final HashMap<N5ContainerState, Map<String, N5TreeNode>> previousContainerChoices = new HashMap<>();

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

	this.isContainerValid.addListener((obs, oldv, newv) -> datasetUpdateFailed.set(false));
	this.isContainerValid.addListener((obs, oldv, newv) -> datasetUpdateFailed.set(false));

	this.node = initializeNode(n5RootNode, datasetPrompt, browseNode);

	this.containerState.addListener((obs, oldContainer, newContainer) -> {
	  /* if nothing has changed, do nothing */
	  if (newContainer != null && newContainer.equals(oldContainer))
		return;

	  /* otherwise, clear the existing choices, reset the active node*/
	  invoke(() -> {
		resetDatasetChoices();
		this.activeN5Node.set(null);
	  });

	  /* if we are non-null, update the choices*/
	  if (newContainer != null) {
		/* If we previously parsed this reader, use the cached metadata */
		if (previousContainerChoices.containsKey(newContainer)) {
		  final var previousChoices = previousContainerChoices.get(newContainer);
		  this.updateDatasetChoices(previousChoices);
		} else {
		  final var reader = newContainer.getReader();
		  this.updateDatasetChoices(reader);
		}
	  }

	  final var oldUrl = Optional.ofNullable(oldContainer).map(N5ContainerState::getUrl).orElse(null);
	  final var newUrl = Optional.ofNullable(newContainer).map(N5ContainerState::getUrl).orElse(null);
	  LOG.debug("Updated container: obs={} oldv={} newv={}", obs, oldUrl, newUrl);
	});

	this.isContainerValid.addListener((obs, oldv, newv) -> cancelDiscovery());

	/* If we have a cached containerState and dataset choices, use them. */
	final var previousContainer = Optional.ofNullable(containerState.getValue()).map(previousContainerChoices::get);
	previousContainer.ifPresent(this::updateDatasetChoices);

	/* Otherwise, load new. */
	/* Initial dataset update, if the reader is already set. This is the case when you open a new source for the second time, from the same container.
	 * We pass the same argument to both parameters, since it is not changing.  */
	if (previousContainer.isEmpty()) {
	  Optional.ofNullable(containerState.getValue())
			  .map(N5ContainerState::getReader)
			  .ifPresent(this::updateDatasetChoices);
	}

  }

  public static double[] asPrimitiveArray(final DoubleProperty[] data) {

	return Arrays.stream(data).mapToDouble(DoubleProperty::get).toArray();
  }

  public MetadataState getMetadataState() {

	/* Don't modify the original from the backend, return a copy. */
	return Optional.ofNullable(metadataState.get()).map(MetadataState::copy).orElse(null);
  }

  public ObjectBinding<MetadataState> metadataStateProperty() {

	return metadataState;
  }

  private void updateDatasetChoices(Map<String, N5TreeNode> choices) {

	synchronized (discoveryIsActive) {
	  invoke(() -> {
		cancelDiscovery(); // If discovery is ongoing, cancel it.
		LOG.debug("Updating dataset choices!");
		discoveryIsActive.set(true);
		resetDatasetChoices(); // clean up whatever is currently shown
		datasetChoices.set(FXCollections.synchronizedObservableMap(FXCollections.observableMap(choices)));
		discoveryIsActive.set(false);
	  });
	}
  }

  private void updateDatasetChoices(N5Reader newReader) {

	synchronized (discoveryIsActive) {
	  invoke(() -> {
		cancelDiscovery(); // If discovery is ongoing, cancel it.
		LOG.debug("Updating dataset choices!");
		discoveryIsActive.set(true);
		invoke(this::resetDatasetChoices); // clean up whatever is currently shown
	  });
	  Tasks.<ObservableMap<String, N5TreeNode>>createTask(
					  thisTask -> {
						/* Parse the container's metadata*/
						final ObservableMap<String, N5TreeNode> validDatasetChoices = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap());
						final N5TreeNode metadataTree;
						try {
						  metadataTree = N5Helpers.parseMetadata(newReader, discoveryIsActive).orElse(null);
						} catch (Exception e) {
						  if (!discoveryIsActive.get()) {
							/* if discovery was cancelled ,this is expected*/
							LOG.debug("Metadata Parsing was Canceled");
							thisTask.cancel();
							return null;
						  }
						  throw e;
						}
						Map<String, N5TreeNode> validGroups = N5Helpers.validPainteraGroupMap(metadataTree);
						invoke(() -> validDatasetChoices.putAll(validGroups));

						if (metadataTree == null || metadataTree.getMetadata() == null) {
						  invoke(() -> this.activeN5Node.set(null));
						}
						return validDatasetChoices;
					  })
			  .onSuccess((event, task) -> {
				datasetChoices.set(task.getValue());
				previousContainerChoices.put(getContainer(), Map.copyOf(datasetChoices.getValue()));
			  }) /* set and cache the choices on success*/
			  .onEnd(task -> invoke(() -> discoveryIsActive.set(false))) /* clear the flag when done, regardless */
			  .submit();
	}
  }

  private void resetDatasetChoices() {

	datasetChoices.set(FXCollections.observableHashMap());
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

  public Boolean isReadOnly() {

	return this.readOnly.get();
  }

  public ObservableObjectValue<DatasetAttributes> datasetAttributesProperty() {

	return this.datasetAttributes;
  }

  public ObservableObjectValue<long[]> dimensionsProperty() {

	return this.dimensions;
  }

  private void updateDatasetInfo(MetadataState state) {

	for (int i = 0; i < datasetInfo.getSpatialResolutionProperties().length; i++) {
	  datasetInfo.getSpatialResolutionProperties()[i].set(state.getResolution()[i]);
	}
	for (int i = 0; i < datasetInfo.getSpatialTranslationProperties().length; i++) {
	  datasetInfo.getSpatialTranslationProperties()[i].set(state.getTranslation()[i]);
	}
	datasetInfo.getMinProperty().set(state.getMinIntensity());
	datasetInfo.getMaxProperty().set(state.getMaxIntensity());
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

	return this.datasetInfo.getSpatialTranslationProperties();
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

	  final StringBinding datasetDropDownText = Bindings.createStringBinding(
			  () -> getDatasetPath() == null || getDatasetPath().length() == 0 ? datasetPromptText : datasetPromptText + ": " + getDatasetPath(),
			  activeN5Node);

	  final ObservableList<String> choices = FXCollections.observableArrayList();

	  final Consumer<String> onMatchFound = s -> activeN5Node.set(datasetChoices.get(s));

	  final var datasetDropDown = new MatchSelectionMenuButton(choices, datasetDropDownText.get(), null, onMatchFound);
	  datasetDropDown.setCutoff(50);

	  final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
			  () -> Optional.ofNullable(getDatasetPath()).map(Tooltip::new).orElse(null),
			  activeN5Node);

	  datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
	  /* disable when there are no choices */
	  final var datasetDropDownDisable = Bindings.createBooleanBinding(() -> this.datasetChoices.isEmpty() || this.discoveryIsActive.get(), this.datasetChoices, this.discoveryIsActive);
	  datasetDropDown.disableProperty().bind(datasetDropDownDisable);
	  datasetDropDown.textProperty().bind(datasetDropDownText);
	  /* If the datasetchoices are changed, create new menuItems, and update*/
	  datasetChoices.addListener((obs, oldv, newv) -> {
		  choices.setAll(datasetChoices.keySet());
		  choices.sort(new AlphanumericComparator());
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

	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());
	final long numChannels = datasetAttributes.get().getDimensions()[3];

	LOG.debug("Got channel info: num channels={} channels selection={}", numChannels, channelSelection);
	final N5BackendChannel<T, V> backend = new N5BackendChannel<>(getMetadataState(), channelSelection, 3);
	backend.getMetadataState().updateTransform(resolution, offset);
	final ConnectomicsChannelState<T, V, RealComposite<T>, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state = new ConnectomicsChannelState<>(
			backend,
			queue,
			priority,
			name + "-" + Arrays.toString(channelSelection));
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

  public N5TreeNode getN5TreeNode() {

	return this.activeN5Node.get();
  }

  public <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
  SourceState<T, V> getRaw(
		  final String name,
		  final SharedQueue queue,
		  final int priority) throws Exception {

	LOG.debug("Raw data set requested. Name={}", name);

	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());
	MetadataState metadataState = getMetadataState();

	/* if they are the same, don't change the transform */
	if (!(Arrays.equals(metadataState.getResolution(), resolution) && Arrays.equals(metadataState.getTranslation(), offset))) {
	  metadataState.updateTransform(resolution, offset);
	}

	//	double[] defaultRes = {1, 1, 1};
	//	double[] defaultOff = {0, 0, 0};
	final ConnectomicsRawBackend<T, V> backend = new N5BackendRaw<>(metadataState);
	final SourceState<T, V> state = new ConnectomicsRawState<>(backend, queue, priority, name);
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

	final double[] resolution = asPrimitiveArray(resolution());
	final double[] offset = asPrimitiveArray(offset());
	MetadataState metadataState = getMetadataState();

	/* if they are the same, don't change the transform */
	metadataState.updateTransform(resolution, offset);

	final ConnectomicsLabelBackend<D, T> backend = N5Backend.createFrom(metadataState, projectDirectory, propagationQueue);
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
			null);
  }

  public boolean isLabelMultisetType() throws Exception {

	Boolean isLabelMultiset = Optional.ofNullable(metadataState.getValue()).map(MetadataState::isLabelMultiset).orElse(false);
	LOG.debug("Getting label multiset attribute: {}", isLabelMultiset);
	return isLabelMultiset;
  }

  public DatasetAttributes getAttributes() {

	final MetadataState metadataState = metadataStateProperty().get();
	LOG.debug("Getting attributes for group {} from metadata type: {}", metadataState.getGroup(), metadataState.getMetadata().getClass().getSimpleName());
	return metadataState.getDatasetAttributes();
  }

  public void setResolution(final double[] resolution) {

	setPropertyList(resolution, resolution());
  }

  public void setOffset(final double[] offset) {

	setPropertyList(offset, offset());
  }

  private void setPropertyList(double[] values, DoubleProperty[] properties) {

	for (int i = 0; i < properties.length; ++i) {
	  properties[i].set(values[i]);
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
