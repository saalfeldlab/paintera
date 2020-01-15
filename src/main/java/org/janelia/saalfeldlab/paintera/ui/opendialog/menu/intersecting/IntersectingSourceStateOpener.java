package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.intersecting;

import bdv.viewer.Source;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
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
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Paintera2;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IntersectingSourceStateOpener {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class Action implements BiConsumer<PainteraBaseView, Supplier<String>>  {

		@Override
		public void accept(PainteraBaseView viewer, Supplier<String> projectDirectory) {
			final ObjectProperty<SourceState<?, ?>> labelSourceState = new SimpleObjectProperty<>();
			final ObjectProperty<ThresholdingSourceState<?, ?>> thresholdingState = new SimpleObjectProperty<>();
			final StringProperty name = new SimpleStringProperty(null);
			final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);
			final BooleanBinding isValidSelection = labelSourceState
					.isNotNull()
					.and(thresholdingState.isNotNull())
					.and(name.isNotNull());
			final Alert dialog = makeDialog(viewer, labelSourceState, thresholdingState, name, color);
			final Optional<ButtonType> returnType = dialog.showAndWait();
			if (
					Alert.AlertType.CONFIRMATION.equals(dialog.getAlertType())
							&& ButtonType.OK.equals(returnType.orElse(ButtonType.CANCEL))
							&& isValidSelection.get()) {
				try {
					final SourceState<?, ?> labelState = labelSourceState.get();
					final IntersectingSourceState intersectingState;
					if (labelState instanceof ConnectomicsLabelState<?, ?>) {
						intersectingState = new IntersectingSourceState(
								thresholdingState.get(),
								(ConnectomicsLabelState) labelState,
								new ARGBCompositeAlphaAdd(),
								name.get(),
								viewer.getQueue(),
								0,
								viewer.viewer3D().meshesGroup(),
								viewer.viewer3D().viewFrustumProperty(),
								viewer.viewer3D().eyeToWorldTransformProperty(),
								viewer.viewer3D().meshesEnabledProperty(),
								viewer.getMeshManagerExecutorService(),
								viewer.getMeshWorkerExecutorService());
					} else if (labelState instanceof LabelSourceState<?, ?>) {
						intersectingState = new IntersectingSourceState(
								thresholdingState.get(),
								(LabelSourceState) labelState,
								new ARGBCompositeAlphaAdd(),
								name.get(),
								viewer.getQueue(),
								0,
								viewer.viewer3D().meshesGroup(),
								viewer.viewer3D().viewFrustumProperty(),
								viewer.viewer3D().eyeToWorldTransformProperty(),
								viewer.viewer3D().meshesEnabledProperty(),
								viewer.getMeshManagerExecutorService(),
								viewer.getMeshWorkerExecutorService());
					} else {
						intersectingState = null;
					}

					if (intersectingState != null) {
						intersectingState.converter().setColor(Colors.toARGBType(color.get()));
						viewer.addState(intersectingState);
					} else {
						LOG.error(
								"Unable to create intersecting state. Expected a label state of class {} or {} but got {} instead.",
								ConnectomicsLabelState.class,
								LabelSourceState.class,
								labelState);
					}
				} catch (final Exception e) {
					LOG.error("Unable to create intersecting state", e);
					Exceptions.exceptionAlert(Paintera2.Constants.NAME, "Unable to create intersecting state", e);
				}
			}
		}
	}

	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "_Connectomics>_Synapses for selection")
	public static class MenuEntry implements OpenDialogMenuEntry {

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {
			return new Action();
		}
	}

	private static Alert makeDialog(
			final PainteraBaseView viewer,
			final ObjectProperty<SourceState<?, ?>> labelSourceState,
			final ObjectProperty<ThresholdingSourceState<?, ?>> thresholdingState,
			final StringProperty name,
			final ObjectProperty<Color> color) {
		final SourceInfo sourceInfo = viewer.sourceInfo();
		final List<Source<?>> sources = new ArrayList<>(sourceInfo.trackSources());
		final List<SourceState<?, ?>> states = sources.stream().map(sourceInfo::getState).collect(Collectors.toList());
		final List<SourceState<?, ?>> labelSourceStates = states
				.stream()
				.filter(s -> s instanceof LabelSourceState<?, ?> || s instanceof ConnectomicsLabelState<?, ?>)
				.collect(Collectors.toList());

		final List<ThresholdingSourceState<?, ?>> thresholdingStates = states
				.stream()
				.filter(s -> s instanceof ThresholdingSourceState<?, ?>)
				.map(s -> (ThresholdingSourceState<?, ?>) s)
				.collect(Collectors.toList());

		if (labelSourceStates.isEmpty()) {
			final Alert dialog = PainteraAlerts.alert(Alert.AlertType.ERROR, true);
			dialog.setContentText("No label data loaded yet, cannot create intersecting state.");
			return dialog;
		}



		if (thresholdingStates.isEmpty()) {
			final Alert dialog = PainteraAlerts.alert(Alert.AlertType.ERROR, true);
			dialog.setContentText("No thresholded data available, cannot create intersecting state. Use the `ctrl-T` key combination to create a thresholded dataset from a currently active raw dataset.");
			return dialog;
		}

		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		dialog.setHeaderText("Choose label and raw source states for thresholded intersection and combined rendering.");

		final Map<SourceState<?, ?>, Integer> sourceIndices = sources
				.stream()
				.collect(Collectors.toMap(sourceInfo::getState, sourceInfo::indexOf));

		final ComboBox<SourceState<?, ?>> labelSelection = new ComboBox<>(FXCollections.observableArrayList(labelSourceStates));
		final ComboBox<ThresholdingSourceState<?, ?>> thresholdedSelection = new ComboBox<>(FXCollections.observableArrayList(thresholdingStates));

		labelSourceState.bind(labelSelection.valueProperty());
		thresholdingState.bind(thresholdedSelection.valueProperty());
		final double idLabelWidth = 20.0;

		labelSelection.setCellFactory(param -> new ListCell<SourceState<?, ?>>() {
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

		thresholdedSelection.setCellFactory(param -> new ListCell<ThresholdingSourceState<?, ?>>() {
			@Override
			protected void updateItem(ThresholdingSourceState<?, ?> item, boolean empty) {
				super.updateItem(item, empty);
				if (item == null || empty) {
					setGraphic(null);
				} else {
					final Label id = new Label(Integer.toString(sourceIndices.get(item)) + ":");
					id.setPrefWidth(idLabelWidth);
					setGraphic(id);
					setText(item.nameProperty().get());
				}
			}
		});

		labelSelection.setButtonCell(labelSelection.getCellFactory().call(null));
		thresholdedSelection.setButtonCell(thresholdedSelection.getCellFactory().call(null));
		labelSelection.setMaxWidth(Double.POSITIVE_INFINITY);
		thresholdedSelection.setMaxWidth(Double.POSITIVE_INFINITY);

		labelSelection.setPromptText("Select label dataset");
		thresholdedSelection.setPromptText("Select thresholded dataset");

		final TextField nameField = new TextField(null);
		nameField.setPromptText("Set name for intersecting source");
		name.bind(nameField.textProperty());

		final ColorPicker colorPicker = new ColorPicker();
		colorPicker.valueProperty().bindBidirectional(color);


		final GridPane grid = new GridPane();

		grid.add(Labels.withTooltip("Label data", "Select label dataset to be intersected."), 0, 0);
		grid.add(Labels.withTooltip("Thresholded data", "Select thresholded dataset to be intersected."), 0, 1);
		grid.add(new Label("Name"),0, 2);
		grid.add(new Label("Color"), 0, 3);

		grid.add(labelSelection, 1, 0);
		grid.add(thresholdedSelection, 1, 1);
		grid.add(nameField, 1, 2);
		grid.add(colorPicker, 1, 3);

		GridPane.setHgrow(labelSelection, Priority.ALWAYS);
		GridPane.setHgrow(thresholdedSelection, Priority.ALWAYS);

		dialog.getDialogPane().setContent(grid);

		dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
				labelSelection
						.valueProperty()
						.isNull()
						.or(thresholdedSelection.valueProperty().isNull())
						.or(name.isNull()));

		return dialog;
	}

}
