package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.thresholded;

import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ThresholdedRawSourceStateOpenerDialog {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void createAndAddNewVirtualThresholdSource(final PainteraBaseView viewer, Supplier<String> projectDirectory) {

		if (!viewer.isActionAllowed(MenuActionType.CreateVirtualSource)) {
			LOG.debug("Creating Virtual Sources is disabled");
			return;
		}

		final ObjectProperty<SourceState<?, ?>> rawSourceState = new SimpleObjectProperty<>();
		final StringProperty name = new SimpleStringProperty(null);
		final ObjectProperty<Color> foregroundColor = new SimpleObjectProperty<>(Color.WHITE);
		final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.BLACK);
		final DoubleProperty minThreshold = new SimpleDoubleProperty();
		final DoubleProperty maxThreshold = new SimpleDoubleProperty();
		final Alert dialog = makeDialog(
				viewer,
				rawSourceState,
				name,
				foregroundColor,
				backgroundColor,
				minThreshold,
				maxThreshold);
		final Optional<ButtonType> returnType = dialog.showAndWait();
		if (
				Alert.AlertType.CONFIRMATION.equals(dialog.getAlertType())
						&& ButtonType.OK.equals(returnType.orElse(ButtonType.CANCEL))) {
			try {
				final SourceState<?, ?> rawState = rawSourceState.get();
				final ThresholdingSourceState thresholdingState = new ThresholdingSourceState(name.get(), rawState, viewer);
				thresholdingState.colorProperty().setValue(foregroundColor.getValue());
				thresholdingState.backgroundColorProperty().setValue(backgroundColor.getValue());
				thresholdingState.minProperty().setValue(minThreshold.getValue());
				thresholdingState.maxProperty().setValue(maxThreshold.getValue());
				viewer.addState(thresholdingState);
			} catch (final Exception e) {
				LOG.error("Unable to create thresholded raw source", e);
				Exceptions.exceptionAlert(Constants.NAME, "Unable to create thresholded raw source", e).show();
			}
		}
	}

	private static Alert makeDialog(
			final PainteraBaseView viewer,
			final ObjectProperty<SourceState<?, ?>> rawSourceState,
			final StringProperty name,
			final ObjectProperty<Color> foregroundColor,
			final ObjectProperty<Color> backgroundColor,
			final DoubleProperty minThreshold,
			final DoubleProperty maxThreshold) {

		final SourceInfo sourceInfo = viewer.sourceInfo();
		final List<Source<?>> sources = new ArrayList<>(sourceInfo.trackSources());
		final List<SourceState<?, ?>> states = sources.stream().map(sourceInfo::getState).collect(Collectors.toList());
		final List<SourceState<?, ?>> rawSourceStates = states
				.stream()
				.filter(s -> s instanceof ConnectomicsRawState<?, ?>)
				.collect(Collectors.toList());

		if (rawSourceStates.isEmpty()) {
			final Alert dialog = PainteraAlerts.alert(Alert.AlertType.ERROR, true);
			dialog.setContentText("No raw data loaded yet, cannot create thresholded source.");
			return dialog;
		}

		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		dialog.setHeaderText("Choose raw source state for thresholded rendering.");

		final Map<SourceState<?, ?>, Integer> sourceIndices = sources
				.stream()
				.collect(Collectors.toMap(sourceInfo::getState, sourceInfo::indexOf));

		final ComboBox<SourceState<?, ?>> rawSourceSelection = new ComboBox<>(FXCollections.observableArrayList(rawSourceStates));

		rawSourceState.bind(rawSourceSelection.valueProperty());
		final double idLabelWidth = 20.0;

		rawSourceSelection.setCellFactory(param -> new ListCell<SourceState<?, ?>>() {

			@Override
			protected void updateItem(SourceState<?, ?> item, boolean empty) {

				super.updateItem(item, empty);
				if (item == null || empty) {
					setGraphic(null);
				} else {
					final Label id = new Label(String.format("%d:", sourceIndices.get(item)));
					id.setPrefWidth(idLabelWidth);
					setGraphic(id);
					setText(item.nameProperty().get());
				}
			}
		});

		rawSourceSelection.setButtonCell(rawSourceSelection.getCellFactory().call(null));
		rawSourceSelection.setMaxWidth(Double.POSITIVE_INFINITY);
		rawSourceSelection.setPromptText("Select raw data source");

		final TextField nameField = new TextField(null);
		nameField.setPromptText("Set name for thresholded source");
		name.bind(nameField.textProperty());

		final ColorPicker foregroundColorPicker = new ColorPicker();
		foregroundColorPicker.valueProperty().bindBidirectional(foregroundColor);

		final ColorPicker backgroundColorPicker = new ColorPicker();
		backgroundColorPicker.valueProperty().bindBidirectional(backgroundColor);

		final NumberField<DoubleProperty> minField = NumberField.doubleField(0.0, d -> true, ObjectField.SubmitOn.values());
		minThreshold.bind(minField.valueProperty());

		final NumberField<DoubleProperty> maxField = NumberField.doubleField(255.0, d -> true, ObjectField.SubmitOn.values());
		maxThreshold.bind(maxField.valueProperty());

		rawSourceSelection.valueProperty().addListener((obs, oldv, newv) -> {
			if (newv != null) {
				nameField.setText(newv.nameProperty().get() + "-thresholded");

				final ARGBColorConverter<?> converter;
				if (newv instanceof ConnectomicsRawState<?, ?>)
					converter = ((ConnectomicsRawState<?, ?>)newv).converter();
				else
					converter = null;

				if (converter != null) {
					minField.setValue(converter.getMin());
					maxField.setValue(converter.getMax());
				}
			}
		});

		final GridPane grid = new GridPane();
		int row = 0;

		grid.add(Labels.withTooltip("Raw data source", "Select raw data source."), 0, row);
		grid.add(rawSourceSelection, 1, row);
		++row;

		grid.add(new Label("Name"), 0, row);
		grid.add(nameField, 1, row);
		++row;

		grid.add(new Label("Foreground color"), 0, row);
		grid.add(foregroundColorPicker, 1, row);
		++row;

		grid.add(new Label("Background color"), 0, row);
		grid.add(backgroundColorPicker, 1, row);
		++row;

		grid.add(new Label("Min"), 0, row);
		grid.add(minField.getTextField(), 1, row);
		++row;

		grid.add(new Label("Max"), 0, row);
		grid.add(maxField.getTextField(), 1, row);
		++row;

		GridPane.setHgrow(rawSourceSelection, Priority.ALWAYS);
		dialog.getDialogPane().setContent(grid);

		dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
				rawSourceSelection.valueProperty().isNull()
						.or(name.isEmpty()));

		return dialog;
	}

}
