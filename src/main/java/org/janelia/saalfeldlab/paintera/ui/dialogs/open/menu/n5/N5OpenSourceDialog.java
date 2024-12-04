package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.CombinesErrorMessages;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.NameField;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta.MetaPanel;
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;
import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5OpenSourceDialog extends Dialog<OpenSourceNode> implements CombinesErrorMessages {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class N5Opener implements OpenDialogMenuEntry {

		private final OpenSourceState openSourceState = new OpenSourceState();
		private final N5FactoryOpener factoryOpener = new N5FactoryOpener(openSourceState);

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {

			return (pbv, projectDirectory) -> {
				try {
					final OpenSourceNode dialog = factoryOpener.backendDialog();
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, openSourceState, dialog);
					osDialog.setHeaderFromBackendType("source");
					Optional<OpenSourceNode> optBackend = osDialog.showAndWait();
					if (optBackend.isEmpty())
						return;
					N5OpenSourceDialog.addSource(osDialog.getType(), openSourceState, osDialog.getChannelSelection(), pbv);
					factoryOpener.selectionAccepted();
				} catch (Exception e1) {
					LOG.debug("Unable to open dataset", e1);

					Alert alert = Exceptions.exceptionAlert(Constants.NAME, "Unable to open data set", e1);
					alert.initModality(Modality.APPLICATION_MODAL);
					Optional.ofNullable(pbv.getNode().getScene()).map(Scene::getWindow).ifPresent(alert::initOwner);
					alert.show();
				}
			};
		}

		public static ActionSet openSourceDialogAction(PainteraBaseView baseView, Supplier<String> projectDir) {

			final var menuText = "Open dataset";
			return painteraActionSet(menuText, MenuActionType.AddSource, actionSet -> actionSet.addKeyAction(KeyEvent.KEY_PRESSED, keyAction -> {
				keyAction.keyMatchesBinding(PainteraBaseKeys.namedCombinationsCopy(), PainteraBaseKeys.OPEN_SOURCE);
				keyAction.onAction(event -> PainteraMenuItems.OPEN_SOURCE.getMenu().fire());
				keyAction.handleException(exception -> {
					final Scene scene = baseView.viewer3D().getScene().getScene();
					Exceptions.exceptionAlert(
							Constants.NAME,
							"Unable to show open dataset menu",
							exception,
							null,
							scene == null ? null : scene.getWindow()
					);
				});
			}));
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

	private final OpenSourceNode openSourceNode;

	private final MetaPanel metaPanel;

	public N5OpenSourceDialog(final PainteraBaseView viewer, OpenSourceState openSourceState, final OpenSourceNode openSourceNode) {

		this.openSourceNode = openSourceNode;
		this.metaPanel = new MetaPanel(openSourceState.getMetadataStateBinding());
		this.metaPanel.listenOnDimensions(openSourceState.getDimensionsBinding());

		this.setTitle("Open data set");
		this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		((Button)this.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("_Cancel");
		((Button)this.getDialogPane().lookupButton(ButtonType.OK)).setText("_OK");
		this.errorMessage = new Label("");
		this.errorInfo = new TitledPane("", errorMessage);
		this.isError = Bindings.createBooleanBinding(() -> Optional.ofNullable(this.errorMessage.textProperty().get()).orElse("").length() > 0,
				this.errorMessage.textProperty());
		errorInfo.textProperty().bind(Bindings.createStringBinding(() -> this.isError.get() ? "ERROR" : "", this.isError));

		this.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(this.isError);
		this.errorInfo.visibleProperty().bind(this.isError);

		this.grid = new GridPane();
		this.nameField.errorMessageProperty().addListener((obs, oldv, newv) -> combineErrorMessages());
		this.dialogContent = new VBox(10, nameField.textField(), grid, metaPanel.getPane(), errorInfo);
		this.setResizable(true);


		GridPane.setMargin(this.openSourceNode, new Insets(0, 0, 0, 30));
		this.grid.add(this.openSourceNode, 1, 0);
		GridPane.setHgrow(this.openSourceNode, Priority.ALWAYS);

		this.getDialogPane().setContent(dialogContent);
		final VBox choices = new VBox();

		List<String> typeChoicesString = typeChoices.stream().map(Enum::name).collect(Collectors.toList());
		final Consumer<String> processSelection = s -> typeChoice.set(MetaPanel.TYPE.valueOf(s));
		this.typeChoiceButton = new MatchSelectionMenuButton(typeChoicesString, "_Type", 100.0, processSelection);
		final StringBinding typeChoiceButtonText = Bindings.createStringBinding(() -> typeChoice.get() == null ? "_Type" : "_Type: " + typeChoice.get(),
				typeChoice);
		final ObjectBinding<Tooltip> datasetDropDownTooltip = Bindings.createObjectBinding(
				() -> Optional.ofNullable(typeChoice.get()).map(t -> "Type of the dataset: " + t).map(Tooltip::new).orElse(null), typeChoice);
		typeChoiceButton.tooltipProperty().bind(datasetDropDownTooltip);
		typeChoiceButton.textProperty().bind(typeChoiceButtonText);
		this.metaPanel.bindDataTypeTo(this.typeChoice);
		openSourceState.getMetadataStateBinding().subscribe(it -> {
			if (it != null)
				typeChoice.set(updateType(it));
		});

		this.metaPanel.listenOnResolution(openSourceState.getResolutionProperty());
		this.metaPanel.listenOnOffset(openSourceState.getTranslationProperty());
		this.metaPanel.listenOnMinMax(
				openSourceState.getMinIntensityProperty(),
				openSourceState.getMaxIntensityProperty()
		);

		final ObjectBinding<UUID> openSourceStateChangeBinding = Bindings.createObjectBinding(UUID::randomUUID, openSourceState.getContainerStateProperty(), openSourceState.getActiveNodeProperty());
		openSourceStateChangeBinding.subscribe(() -> {
			openSourceStateChangeBinding.get(); //Necessary to re-validate the value (internal, and annoying)
			combineErrorMessages();
		});

		final SimpleStringProperty nameProperty = openSourceState.getSourceNameProperty();
		nameProperty.addListener((obs, oldName, newName) -> Optional.ofNullable(newName).ifPresent(nameField.textField().textProperty()::set));
		combineErrorMessages();
		Optional.ofNullable(nameProperty.get()).ifPresent(nameField.textField()::setText);

		metaPanel.getReverseButton().setOnAction(event -> {
			openSourceState.getResolutionProperty().set(reverse(metaPanel.getResolution()));
			openSourceState.getTranslationProperty().set(reverse(metaPanel.getOffset()));
		});

		this.typeChoice.setValue(typeChoices.get(0));
		choices.getChildren().addAll(this.typeChoiceButton);
		this.grid.add(choices, 0, 0);
		this.setResultConverter(button -> button.equals(ButtonType.OK) ? openSourceNode : null);
		combineErrorMessages();
		setTitle(Constants.NAME);

		/* Ensure the window opens up over the main view if possible */
		initModality(Modality.APPLICATION_MODAL);
		Optional.ofNullable(viewer.getNode().getScene().getWindow()).ifPresent(this::initOwner);
		final Window dialogWindow = getDialogPane().getScene().getWindow();
		if (dialogWindow instanceof Stage) {
			var dialogStage = (Stage)dialogWindow;
			dialogStage.sizeToScene();
		}
	}

	public MetaPanel.TYPE getType() {

		return typeChoice.getValue();
	}

	public int[] getChannelSelection() {

		return metaPanel.channelInformation().getChannelSelectionCopy();
	}

	public String getName() {

		return nameField.getText();
	}

	public MetaPanel getMeta() {

		return this.metaPanel;
	}

	@Override
	public Collection<ObservableValue<String>> errorMessages() {

		final var errors = new ArrayList<ObservableValue<String>>();
		errors.add(this.nameField.errorMessageProperty());

		final OpenSourceState openSourceState = openSourceNode.getOpenSourceState();
		if (openSourceState.getContainerState() == null)
			errors.add(new SimpleStringProperty("No Valid Container"));
		else if (openSourceState.getActiveNode() == null)
			errors.add(new SimpleStringProperty("No Dataset Selected"));

		return errors;
	}

	@Override
	public Consumer<Collection<String>> combiner() {

		return strings -> invoke(() -> this.errorMessage.setText(String.join(
				"\n",
				strings
		)));
	}

	private static double[] reverse(final double[] array) {

		final double[] reversed = new double[array.length];
		for (int i = 0; i < array.length; ++i) {
			reversed[i] = array[array.length - 1 - i];
		}
		return reversed;
	}

	private static void addSource(
			final MetaPanel.TYPE type,
			final OpenSourceState openSourceState,
			final int[] channelSelection,
			final PainteraBaseView viewer) throws Exception {

		LOG.debug("Type={}", type);
		switch (type) {
		case RAW:
			LOG.trace("Adding raw data");
			addRaw(channelSelection, openSourceState, viewer);
			break;
		case LABEL:
			LOG.trace("Adding label data");
			addLabel(openSourceState, viewer);
			break;
		default:
			break;
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final int[] channelSelection,
			final OpenSourceState openSourceState,
			PainteraBaseView viewer) {

		final DatasetAttributes attributes = Objects.requireNonNull(openSourceState.getDatasetAttributes());
		if (attributes.getNumDimensions() != 3) {
			LOG.debug("4-dimensional data, assuming channel index at {}", 3);
			final var channels = OpenSourceState.getChannels(openSourceState, channelSelection, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got {} channel sources", channels.size());
			invoke(() -> channels.forEach(viewer::addState));
			LOG.debug("Added {} channel sources", channels.size());
		} else {
			final SourceState<T, V> raw = OpenSourceState.getRaw(openSourceState, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got raw: {}", raw);
			invoke(() -> viewer.addState(raw));
		}
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final OpenSourceState openSourceState,
			final PainteraBaseView viewer) throws Exception {

		final DatasetAttributes attributes = openSourceState.getDatasetAttributes();
		if (attributes.getNumDimensions() > 3)
			throw new Exception("Only 3D label data supported but got " + attributes.getNumDimensions() + " dimensions.");

		final SourceState<D, T> rep = OpenSourceState.getLabels(
				openSourceState,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				viewer.viewer3D().getMeshesGroup(),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getPropagationQueue()
		);
		invoke(() -> viewer.addState(rep));
	}

	public void setHeaderFromBackendType(String backendType) {

		this.setHeaderText(String.format("Open %s dataset", backendType));
	}

	private MetaPanel.TYPE updateType(final MetadataState metadataState) {

		if (metadataState.isLabel()) {
			return MetaPanel.TYPE.LABEL;
		}
		return MetaPanel.TYPE.RAW;
	}
}
