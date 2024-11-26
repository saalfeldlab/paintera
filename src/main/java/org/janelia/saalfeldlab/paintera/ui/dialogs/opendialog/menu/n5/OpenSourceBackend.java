package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

import bdv.cache.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import kotlin.Pair;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelBackend;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawBackend;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.DatasetInfo;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sawano.java.text.AlphanumericComparator;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OpenSourceBackend {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String DATASET_PROMPT = "_Dataset";

	private final Node node;
	final OpenSourceState state;

	BooleanProperty isBusyProperty = new SimpleBooleanProperty(false);

	public OpenSourceBackend(
			final OpenSourceState openSourceState,
			final Node containerLocationNode,
			final Node browseNode,
			final BooleanProperty isOpeningContainer) {

		state = openSourceState;
		node = initializeNode(containerLocationNode, DATASET_PROMPT, browseNode);
		isBusyProperty.bind(isOpeningContainer);
	}

	public ObservableValue<Boolean> getVisibleProperty() {

		return node.visibleProperty();
	}

	public static double[] asPrimitiveArray(final DoubleProperty[] data) {

		return Arrays.stream(data).mapToDouble(DoubleProperty::get).toArray();
	}

	public ObservableObjectValue<long[]> dimensionsProperty() {

		return Bindings.createObjectBinding(state::getDimensions, state.getMetadataStateBinding());
	}

	public Node getDialogNode() {

		return node;
	}

	private DatasetInfo getDatasetInfo() {

		return state.getDatasetInfo();
	}

	private DoubleProperty[] resolution() {

		return getDatasetInfo().getSpatialResolutionProperties();
	}

	private DoubleProperty[] offset() {

		return getDatasetInfo().getSpatialTranslationProperties();
	}

	private DoubleProperty min() {

		return getDatasetInfo().getMinProperty();
	}

	private DoubleProperty max() {

		return getDatasetInfo().getMaxProperty();
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
		progressIndicator.visibleProperty().bind(isBusyProperty);

		return grid;
	}

	private MenuButton createDatasetDropdownMenu(String datasetPromptText) {

		final SimpleObjectProperty<N5TreeNode> activeN5Node = state.getActiveNodeProperty();
		final StringBinding datasetDropDownText = Bindings.createStringBinding(
				() -> state.getDatasetPath() == null || state.getDatasetPath().isEmpty() ? datasetPromptText : datasetPromptText + ": " + state.getDatasetPath(),
				activeN5Node);

		final ObservableList<String> choices = FXCollections.observableArrayList();

		final Consumer<String> onMatchFound = s -> activeN5Node.set(state.getValidDatasets().get().get(s));

		final var datasetDropDown = new MatchSelectionMenuButton(choices, datasetDropDownText.get(), null, onMatchFound);
		datasetDropDown.setCutoff(50);

		final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
				() -> Optional.ofNullable(state.getDatasetPath()).map(Tooltip::new).orElse(null),
				activeN5Node);

		datasetDropDown.tooltipProperty().bind(datasetDropDownTooltip);
		/* disable when there are no choices */
		final var datasetDropDownDisable = Bindings.createBooleanBinding(() -> state.getValidDatasets().get().isEmpty(),
				state.getValidDatasets());
		datasetDropDown.disableProperty().bind(datasetDropDownDisable);
		datasetDropDown.textProperty().bind(datasetDropDownText);
		/* If the datasetchoices are changed, create new menuItems, and update*/

		state.getValidDatasets().subscribe(() -> {
			final Set<String> keys = state.getValidDatasets().get().keySet();
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				choices.setAll(keys);
				choices.sort(new AlphanumericComparator());
			});
		});
		return datasetDropDown;
	}

	public <T extends RealType<T> & NativeType<T>,
			V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	List<? extends SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> getChannels(
			final String name,
			final int[] channelSelection,
			final SharedQueue queue,
			final int priority) {

		final double[] resolution = asPrimitiveArray(resolution());
		final double[] offset = asPrimitiveArray(offset());
		final MetadataState metadataState = state.getMetadataState().copy();

		final Pair<Axis, Integer> channelAxis = metadataState.getChannelAxis();
		final Integer channelIdx = channelAxis.getSecond();
		final long numChannels = metadataState.getDatasetAttributes().getDimensions()[channelIdx];

		metadataState.updateTransform(resolution, offset);

		LOG.debug("Got channel info: num channels={} channels selection={}", numChannels, channelSelection);
		final N5BackendChannel<T, V> backend = new N5BackendChannel<>(metadataState, channelSelection, channelIdx);
		final ConnectomicsChannelState<T, V, RealComposite<T>, RealComposite<V>, VolatileWithSet<RealComposite<V>>> state =
				new ConnectomicsChannelState<>(
						backend,
						queue,
						priority,
						name + "-" + Arrays.toString(channelSelection));

		state.converter().setMins(i -> min().get());
		state.converter().setMaxs(i -> max().get());
		return Collections.singletonList(state);
	}

	public <
			T extends RealType<T> & NativeType<T>,
			V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	SourceState<T, V> getRaw(
			final String name,
			final SharedQueue queue,
			final int priority) {

		LOG.debug("Raw data set requested. Name={}", name);

		final double[] resolution = asPrimitiveArray(resolution());
		final double[] offset = asPrimitiveArray(offset());
		MetadataState metadataState = state.getMetadataState().copy();

		/* if they are the same, don't change the transform */
		if (!(Arrays.equals(metadataState.getResolution(), resolution) && Arrays.equals(metadataState.getTranslation(), offset))) {
			metadataState.updateTransform(resolution, offset);
		}

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
			final Supplier<String> projectDirectory) {

		final double[] resolution = asPrimitiveArray(resolution());
		final double[] offset = asPrimitiveArray(offset());
		MetadataState metadataState = state.getMetadataState().copy();

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
}
