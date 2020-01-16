package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import com.pivovarit.function.ThrowingFunction;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.MatchSelection;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.paintera.Paintera2;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.opendialog.CombinesErrorMessages;
import org.janelia.saalfeldlab.paintera.ui.opendialog.NameField;
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.paintera.ui.opendialog.meta.MetaPanel;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class N5OpenSourceDialog extends Dialog<GenericBackendDialogN5> implements CombinesErrorMessages {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "_N5", priority = Double.MAX_VALUE)
	public static class N5FSOpener implements OpenDialogMenuEntry {
		private static final FileSystem fs = new FileSystem();

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {
			return (pbv, projectDirectory) -> {
				try (final GenericBackendDialogN5 dialog = fs.backendDialog(pbv.getPropagationQueue())) {
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("N5");
					Optional<GenericBackendDialogN5> backend = osDialog.showAndWait();
					if (backend == null || !backend.isPresent())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, osDialog.getChannelSelection(), pbv, projectDirectory);
					fs.containerAccepted();
				} catch (Exception e1) {
					LOG.debug("Unable to open n5 dataset", e1);
					Exceptions.exceptionAlert(Paintera2.Constants.NAME, "Unable to open N5 data set", e1).show();
				}
			};
		}
	}

	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "_HDF5", priority = Double.MAX_VALUE / 2.0)
	public static class N5HDFOpener implements OpenDialogMenuEntry {

		private static final HDF5 hdf5 = new HDF5();

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {
			return (pbv, projectDirectory) -> {
				try (final GenericBackendDialogN5 dialog = hdf5.backendDialog(pbv.getPropagationQueue())) {
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("HDF5");
					Optional<GenericBackendDialogN5> backend = osDialog.showAndWait();
					if (backend == null || !backend.isPresent())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, osDialog.getChannelSelection(), pbv, projectDirectory);
					hdf5.containerAccepted();
				} catch (Exception e1) {
					LOG.debug("Unable to open hdf5 dataset", e1);
					Exceptions.exceptionAlert(Paintera2.Constants.NAME, "Unable to open HDF5 data set", e1).show();
				}
			};
		}
	}

	@Plugin(type = OpenDialogMenuEntry.class, menuPath= "_Google Cloud", priority = Double.MAX_VALUE / 4.0)
	public static class GoogleCloudOpener implements OpenDialogMenuEntry {

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {
			return (pbv, projectDirectory) -> {
				try {
					final GoogleCloud googleCloud = new GoogleCloud();
					try (final GenericBackendDialogN5 dialog = googleCloud.backendDialog(pbv.getPropagationQueue())) {
						final N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
						osDialog.setHeaderFromBackendType("Google Cloud");
						Optional<GenericBackendDialogN5> backend = osDialog.showAndWait();
						if (backend == null || !backend.isPresent())
							return;
						N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, osDialog.getChannelSelection(), pbv, projectDirectory);
					}
				} catch (Exception e1) {
					LOG.debug("Unable to open google cloud dataset", e1);
					Exceptions.exceptionAlert(Paintera2.Constants.NAME, "Unable to open Google Cloud data set", e1).show();
				}
			};
		}
	}

	private final VBox dialogContent;

	private final GridPane grid;

	private final MenuButton typeChoiceButton;

	private final ObjectProperty<MetaPanel.TYPE> typeChoice = new SimpleObjectProperty<>(MetaPanel.TYPE.LABEL);

	private final Label errorMessage;

	private final TitledPane errorInfo;

	private final ObservableList<MetaPanel.TYPE> typeChoices = FXCollections.observableArrayList(MetaPanel.TYPE.values());

	private final NameField nameField = new NameField(
			"Source name",
			"Specify source name (required)",
			new InnerShadow(10, Color.ORANGE)
	);

	private final BooleanBinding isError;

	private final ExecutorService propagationExecutor;

	private final GenericBackendDialogN5 backendDialog;

	private final MetaPanel metaPanel = new MetaPanel();

	public N5OpenSourceDialog(final PainteraBaseView viewer, final GenericBackendDialogN5 backendDialog) {
		super();

		this.backendDialog = backendDialog;
		this.metaPanel.listenOnDimensions(backendDialog.dimensionsProperty());

		this.propagationExecutor = viewer.getPropagationQueue();

		this.setTitle("Open data set");
		this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		((Button)this.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("_Cancel");
		((Button)this.getDialogPane().lookupButton(ButtonType.OK)).setText("_OK");
		this.errorMessage = new Label("");
		this.errorInfo = new TitledPane("", errorMessage);
		this.isError = Bindings.createBooleanBinding(() -> Optional.ofNullable(this.errorMessage.textProperty().get())
				.orElse(
						"").length() > 0, this.errorMessage.textProperty());
		errorInfo.textProperty().bind(Bindings.createStringBinding(
				() -> this.isError.get() ? "ERROR" : "",
				this.isError
		));

		this.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(this.isError);
		this.errorInfo.visibleProperty().bind(this.isError);

		final Tooltip revertAxisTooltip = new Tooltip("If you data is using `zyx` you should revert it.");
		this.grid = new GridPane();
		this.nameField.errorMessageProperty().addListener((obs, oldv, newv) -> combineErrorMessages());
		this.dialogContent = new VBox(10, nameField.textField(), grid, metaPanel.getPane(), errorInfo);
		this.setResizable(true);

		GridPane.setMargin(this.backendDialog.getDialogNode(), new Insets(0, 0, 0, 30));
		this.grid.add(this.backendDialog.getDialogNode(), 1, 0);
		GridPane.setHgrow(this.backendDialog.getDialogNode(), Priority.ALWAYS);

		this.getDialogPane().setContent(dialogContent);
		final VBox choices = new VBox();
		this.typeChoiceButton = new MenuButton("_Type");
		List<String> typeChoicesString = typeChoices.stream().map(Enum::name).collect(Collectors.toList());
		final StringBinding typeChoiceButtonText = Bindings.createStringBinding(() -> typeChoice.get() == null ? "_Type" : "_Type: " + typeChoice.get(), typeChoice);
		final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(() -> Optional.ofNullable(typeChoice.get()).map(t -> "Type of the dataset: " + t).map(Tooltip::new).orElse(null), typeChoice);
		typeChoiceButton.tooltipProperty().bind(datasetDropDownTooltip);
		typeChoiceButton.textProperty().bind(typeChoiceButtonText);
		final MatchSelection matcher = MatchSelection.fuzzySorted(typeChoicesString, s -> {
			typeChoice.set(MetaPanel.TYPE.valueOf(s));
			typeChoiceButton.hide();
		});
		// clear style to avoid weird blue highlight
		final CustomMenuItem cmi = new CustomMenuItem(matcher, false);
		cmi.getStyleClass().clear();
		typeChoiceButton.getItems().setAll(cmi);
		typeChoiceButton.setOnAction(e -> {typeChoiceButton.show(); matcher.requestFocus();});
		this.metaPanel.bindDataTypeTo(this.typeChoice);
		backendDialog.datsetAttributesProperty().addListener((obs, oldv, newv) -> Optional
				.ofNullable(newv)
				.map(ThrowingFunction.unchecked(this::updateType))
				.ifPresent(this.typeChoice::set));

		final ObservableObjectValue<DatasetAttributes> attributesProperty = backendDialog.datsetAttributesProperty();
		final ObjectBinding<long[]> dimensionsProperty = Bindings.createObjectBinding(() -> attributesProperty.get().getDimensions().clone(), attributesProperty);

		final DoubleProperty[] res = backendDialog.resolution();
		final DoubleProperty[] off = backendDialog.offset();
		this.metaPanel.listenOnResolution(res[0], res[1], res[2]);
		this.metaPanel.listenOnOffset(off[0], off[1], off[2]);
		this.metaPanel.listenOnMinMax(backendDialog.min(), backendDialog.max());
		backendDialog.errorMessage().addListener((obs, oldErr, newErr) -> combineErrorMessages());
		backendDialog.nameProperty().addListener((obs, oldName, newName) -> Optional.ofNullable(newName).ifPresent(nameField.textField().textProperty()::set));
		combineErrorMessages();
		Optional.ofNullable(backendDialog.nameProperty().get()).ifPresent(nameField.textField()::setText);

		metaPanel.listenOnResolution(backendDialog.resolution()[0], backendDialog.resolution()[1], backendDialog.resolution()[2]);

		metaPanel.getRevertButton().setOnAction(event -> {
			backendDialog.setResolution(revert(metaPanel.getResolution()));
			backendDialog.setOffset(revert(metaPanel.getOffset()));
		});

		this.typeChoice.setValue(typeChoices.get(0));
		this.typeChoiceButton.setMinWidth(100);
		choices.getChildren().addAll(this.typeChoiceButton);
		this.grid.add(choices, 0, 0);
		this.setResultConverter(button -> button.equals(ButtonType.OK) ? backendDialog : null);
		combineErrorMessages();
		setTitle(Paintera2.Constants.NAME);

	}

	public MetaPanel.TYPE getType() {
		return typeChoice.getValue();
	}

	public int[] getChannelSelection() { return metaPanel.channelInformation().getChannelSelectionCopy(); }

	public String getName() {
		return nameField.getText();
	}

	public MetaPanel getMeta() {
		return this.metaPanel;
	}

	@Override
	public Collection<ObservableValue<String>> errorMessages() {
		return Arrays.asList(this.nameField.errorMessageProperty(), getBackend().errorMessage());
	}

	@Override
	public Consumer<Collection<String>> combiner() {
		return strings -> InvokeOnJavaFXApplicationThread.invoke(() -> this.errorMessage.setText(String.join(
				"\n",
				strings
		)));
	}

	private GenericBackendDialogN5 getBackend() {
		return this.backendDialog;
	}

	private static final double[] revert(final double[] array) {
		final double[] reverted = new double[array.length];
		for (int i = 0; i < array.length; ++i) {
			reverted[i] = array[array.length - 1 - i];
		}
		return reverted;
	}

	private static void addSource(
			final String name,
			final MetaPanel.TYPE type,
			final GenericBackendDialogN5 dataset,
			final int[] channelSelection,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws Exception {
		LOG.debug("Type={}", type);
		switch (type) {
			case RAW:
				LOG.trace("Adding raw data");
				addRaw(name, channelSelection, dataset, viewer);
				break;
			case LABEL:
				addLabel(name, dataset, viewer, projectDirectory);
				break;
			default:
				break;
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final String name,
			final int[] channelSelection,
			final GenericBackendDialogN5 dataset,
			PainteraBaseView viewer) throws Exception {
		final DatasetAttributes attributes = dataset.getAttributes();
		if (attributes.getNumDimensions() == 4)
		{
			LOG.debug("4-dimensional data, assuming channel index at {}", 3);
			final List<? extends SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> channels = dataset.getChannels(
					name,
					channelSelection,
					viewer.getQueue(),
					viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got {} channel sources", channels.size());
			InvokeOnJavaFXApplicationThread.invoke(() -> channels.forEach(viewer::addState));
			LOG.debug("Added {} channel sources", channels.size());
		}
		else {
			final SourceState<T, V> raw = dataset.getRaw(name, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got raw: {}", raw);
			InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addState(raw));
		}
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final String name,
			final GenericBackendDialogN5 dataset,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws Exception {
		final DatasetAttributes attributes = dataset.getAttributes();
		if (attributes.getNumDimensions() > 3)
			throw new Exception("Only 3D label data supported but got " + attributes.getNumDimensions() + " dimensions.");

		final SourceState<D, T> rep = dataset.getLabels(
				name,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				viewer.viewer3D().meshesGroup(),
				viewer.viewer3D().viewFrustumProperty(),
				viewer.viewer3D().eyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getPropagationQueue(),
				projectDirectory
		);
		InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addState(rep));
	}


	private static int[] blockSize(final CellGrid grid) {
		final int[] blockSize = new int[grid.numDimensions()];
		Arrays.setAll(blockSize, grid::cellDimension);
		return blockSize;
	}

	public void setHeaderFromBackendType(String backendType)
	{
		this.setHeaderText(String.format("Open %s dataset", backendType));
	}

	private MetaPanel.TYPE updateType(final DatasetAttributes attributes) throws Exception {

		if (attributes == null)
			return null;

		if (this.backendDialog.isLabelMultisetType()) {
			return MetaPanel.TYPE.LABEL;
		}

		switch (attributes.getDataType()) {
			case UINT64:
			case UINT32:
			case INT64:
				return MetaPanel.TYPE.LABEL;
			default:
				return MetaPanel.TYPE.RAW;
		}
	}
}
