package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5;

import com.pivovarit.function.ThrowingFunction;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.CombinesErrorMessages;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.NameField;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.meta.MetaPanel;
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems;
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

import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;
import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5OpenSourceDialog extends Dialog<GenericBackendDialogN5> implements CombinesErrorMessages {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class N5Opener implements OpenDialogMenuEntry {

		private static N5FactoryOpener factoryOpener = new N5FactoryOpener();

		private PainteraBaseView paintera = Paintera.getPaintera().getBaseView();

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {

			return (pbv, projectDirectory) -> {
				if (pbv != paintera) {
					factoryOpener = new N5FactoryOpener();
					paintera = pbv;
				}
				try (final GenericBackendDialogN5 dialog = factoryOpener.backendDialog()) {
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("source");
					Optional<GenericBackendDialogN5> optBackend = osDialog.showAndWait();
					if (optBackend.isEmpty())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, osDialog.getChannelSelection(), pbv, projectDirectory);
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

	private final GenericBackendDialogN5 backendDialog;

	private final MetaPanel metaPanel = new MetaPanel();

	public N5OpenSourceDialog(final PainteraBaseView viewer, final GenericBackendDialogN5 backendDialog) {

		this.backendDialog = backendDialog;
		this.metaPanel.listenOnDimensions(backendDialog.dimensionsProperty());

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

		GridPane.setMargin(this.backendDialog.getDialogNode(), new Insets(0, 0, 0, 30));
		this.grid.add(this.backendDialog.getDialogNode(), 1, 0);
		GridPane.setHgrow(this.backendDialog.getDialogNode(), Priority.ALWAYS);

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
		backendDialog.metadataStateProperty().addListener((obs, oldv, newv) ->
				Optional.ofNullable(newv)
						.map(ThrowingFunction.unchecked(this::updateType))
						.ifPresent(this.typeChoice::set)
		);

		final DoubleProperty[] res = backendDialog.resolution();
		final DoubleProperty[] off = backendDialog.offset();
		this.metaPanel.listenOnResolution(res[0], res[1], res[2]);
		this.metaPanel.listenOnOffset(off[0], off[1], off[2]);
		this.metaPanel.listenOnMinMax(backendDialog.min(), backendDialog.max());

		backendDialog.errorMessage().addListener((obs, oldErr, newErr) -> combineErrorMessages());
		backendDialog.nameProperty().addListener((obs, oldName, newName) -> Optional.ofNullable(newName).ifPresent(nameField.textField().textProperty()::set));
		combineErrorMessages();
		Optional.ofNullable(backendDialog.nameProperty().get()).ifPresent(nameField.textField()::setText);

		metaPanel.getReverseButton().setOnAction(event -> {
			backendDialog.setResolution(reverse(metaPanel.getResolution()));
			backendDialog.setOffset(reverse(metaPanel.getOffset()));
		});

		metaPanel.getCropIntervalProperty().subscribe(interval -> {
			final MetadataState metadataState = backendDialog.metadataStateProperty().get();
			if (metadataState != null) {
				metadataState.setCrop(interval);
			}
		});

		this.typeChoice.setValue(typeChoices.get(0));
		choices.getChildren().addAll(this.typeChoiceButton);
		this.grid.add(choices, 0, 0);
		this.setResultConverter(button -> button.equals(ButtonType.OK) ? backendDialog : null);
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

		return Arrays.asList(this.nameField.errorMessageProperty(), getBackend().errorMessage());
	}

	@Override
	public Consumer<Collection<String>> combiner() {

		return strings -> invoke(() -> this.errorMessage.setText(String.join(
				"\n",
				strings
		)));
	}

	private GenericBackendDialogN5 getBackend() {

		return this.backendDialog;
	}

	private static double[] reverse(final double[] array) {

		final double[] reversed = new double[array.length];
		for (int i = 0; i < array.length; ++i) {
			reversed[i] = array[array.length - 1 - i];
		}
		return reversed;
	}

	private static void addSource(
			final String name,
			final MetaPanel.TYPE type,
			final GenericBackendDialogN5 backendDialog,
			final int[] channelSelection,
			final PainteraBaseView viewer,
			final Supplier<String> projectDirectory) throws Exception {

		LOG.debug("Type={}", type);
		switch (type) {
		case RAW:
			LOG.trace("Adding raw data");
			addRaw(name, channelSelection, backendDialog, viewer);
			break;
		case LABEL:
			LOG.trace("Adding label data");
			addLabel(name, backendDialog, viewer, projectDirectory);
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
		if (attributes.getNumDimensions() == 4) {
			LOG.debug("4-dimensional data, assuming channel index at {}", 3);
			final List<? extends SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> channels = dataset.getChannels(
					name,
					channelSelection,
					viewer.getQueue(),
					viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got {} channel sources", channels.size());
			invoke(() -> channels.forEach(viewer::addState));
			LOG.debug("Added {} channel sources", channels.size());
		} else {
			final SourceState<T, V> raw = dataset.getRaw(name, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got raw: {}", raw);
			invoke(() -> viewer.addState(raw));
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
				viewer.viewer3D().getMeshesGroup(),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getPropagationQueue(),
				projectDirectory
		);
		invoke(() -> viewer.addState(rep));
	}

	private static int[] blockSize(final CellGrid grid) {

		final int[] blockSize = new int[grid.numDimensions()];
		Arrays.setAll(blockSize, grid::cellDimension);
		return blockSize;
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
