package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.ui.opendialog.meta.MetaPanel;

public class OpenSourceDialog extends Dialog<BackendDialog> implements CombinesErrorMessages
{

	public static Color TEXTFIELD_ERROR = Color.ORANGE;

	public static enum BACKEND
	{
		N5, HDF5, GOOGLE
	}

	;

	public static enum TYPE
	{
		RAW, LABEL
	}

	;

	private final VBox dialogContent;

	private final GridPane grid;

	private final StackPane backendDialog;

	private final ComboBox<BACKEND> backendChoice;

	private final ComboBox<TYPE> typeChoice;

	private final Label errorMessage;

	private final TitledPane errorInfo;

	private final ObservableBooleanValue isLabelType;

	private final ObservableList<BACKEND> backendChoices = FXCollections.observableArrayList(BACKEND.values());

	private final ObservableList<TYPE> typeChoices = FXCollections.observableArrayList(TYPE.values());

	private final NameField nameField = new NameField(
			"Source name",
			"Specify source name (required)",
			new InnerShadow(10, Color.ORANGE)
	);

	private final BooleanBinding isError;

	private final ExecutorService propagationExecutor;

	private final ObservableMap<BACKEND, BackendDialog> backendInfoDialogs = FXCollections.observableHashMap();

	{}

	private final SimpleObjectProperty<BackendDialog> currentBackend;

	private final MetaPanel metaPanel = new MetaPanel();

	private final Button revertAxisOrder = new Button(" Revert axis");

	private final HBox revertAxisHBox = new HBox(revertAxisOrder);

	public OpenSourceDialog(final PainteraBaseView viewer)
	{
		super();

		this.propagationExecutor = viewer.getPropagationQueue();

		backendInfoDialogs.put(BACKEND.N5, N5BackendDialogs.fileSystem(propagationExecutor));
		backendInfoDialogs.put(BACKEND.HDF5, N5BackendDialogs.hdf5(propagationExecutor));
		backendInfoDialogs.put(BACKEND.GOOGLE, N5BackendDialogs.googleCloud(propagationExecutor));
		currentBackend = new SimpleObjectProperty<>(backendInfoDialogs.get(BACKEND.N5));

		this.setTitle("Open data set");
		this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
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
		this.backendDialog = new StackPane();
		this.nameField.errorMessageProperty().addListener((obs, oldv, newv) -> combineErrorMessages());
		this.revertAxisOrder.setTooltip(revertAxisTooltip);
		this.revertAxisHBox.setAlignment(Pos.BASELINE_RIGHT);
		this.dialogContent = new VBox(10, nameField.textField(), grid, metaPanel.getPane(), revertAxisHBox, errorInfo);
		this.setResizable(true);

		GridPane.setMargin(this.backendDialog, new Insets(0, 0, 0, 30));
		this.grid.add(this.backendDialog, 1, 0);
		GridPane.setHgrow(this.backendDialog, Priority.ALWAYS);

		this.getDialogPane().setContent(dialogContent);
		final VBox choices = new VBox();
		this.backendChoice = new ComboBox<>(backendChoices);
		this.typeChoice = new ComboBox<>(typeChoices);
		this.metaPanel.bindDataTypeTo(this.typeChoice.valueProperty());
		isLabelType = Bindings.createBooleanBinding(() -> Optional.ofNullable(typeChoice.getValue()).map(b -> b.equals(
				TYPE.LABEL)).orElse(false), this.typeChoice.valueProperty());

		this.backendChoice.valueProperty().addListener((obs, oldv, newv) -> {
			if (this.currentBackend.get() != null)
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					final BackendDialog backendDialog = backendInfoDialogs.get(newv);
					this.backendDialog.getChildren().setAll(backendDialog.getDialogNode());
					this.currentBackend.set(backendDialog);

					final DoubleProperty[] res = backendDialog.resolution();
					final DoubleProperty[] off = backendDialog.offset();
					this.metaPanel.listenOnResolution(res[0], res[1], res[2]);
					this.metaPanel.listenOnOffset(off[0], off[1], off[2]);
					this.metaPanel.listenOnMinMax(backendDialog.min(), backendDialog.max());

					backendDialog.errorMessage().addListener((obsErr, oldErr, newErr) -> combineErrorMessages());
					backendDialog.nameProperty().addListener((obsName, oldName, newName) -> Optional.ofNullable
							(newName).ifPresent(
							nameField.textField().textProperty()::set));
					Optional.ofNullable(backendDialog.nameProperty().get()).ifPresent(nameField.textField()
							.textProperty()::set);
					combineErrorMessages();
				});
			}
		});

		this.revertAxisOrder.setOnAction(event -> {
			final BackendDialog backendDialog = backendInfoDialogs.get(backendChoice.getValue());
			backendDialog.setResolution(revert(metaPanel.getResolution()));
			backendDialog.setOffset(revert(metaPanel.getOffset()));
		});
		HBox.setHgrow(revertAxisHBox, Priority.ALWAYS);

		this.backendChoice.setValue(backendChoices.get(0));
		this.typeChoice.setValue(typeChoices.get(0));
		this.backendChoice.setMinWidth(100);
		this.typeChoice.setMinWidth(100);
		choices.getChildren().addAll(this.backendChoice, this.typeChoice);
		this.grid.add(choices, 0, 0);
		this.setResultConverter(button -> button.equals(ButtonType.OK) ? currentBackend.get() : null);
		combineErrorMessages();

	}

	public TYPE getType()
	{
		return typeChoice.getValue();
	}

	public String getName()
	{
		return nameField.getText();
	}

	public MetaPanel getMeta()
	{
		return this.metaPanel;
	}

	@Override
	public Collection<ObservableValue<String>> errorMessages()
	{
		return Arrays.asList(this.nameField.errorMessageProperty(), this.currentBackend.get().errorMessage());
	}

	@Override
	public Consumer<Collection<String>> combiner()
	{
		return strings -> InvokeOnJavaFXApplicationThread.invoke(() -> this.errorMessage.setText(String.join(
				"\n",
				strings
		                                                                                                    )));
	}

	public BackendDialog getBackend()
	{
		return this.currentBackend.get();
	}

	private static final double[] revert(final double[] array)
	{
		final double[] reverted = new double[array.length];
		for (int i = 0; i < array.length; ++i)
		{
			reverted[i] = array[array.length - 1 - i];
		}
		return reverted;
	}
}
