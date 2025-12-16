package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.intersecting;

import bdv.viewer.Source;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.InlineCssTextArea;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.IntersectableSourceState;
import org.janelia.saalfeldlab.paintera.state.IntersectingSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts;
import org.janelia.saalfeldlab.util.Colors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IntersectingSourceStateOpener {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	//TODO Caleb: Use CSS for this
	private static final String TEXT_BACKGROUND = "-rtfx-background-color: rgba(62,62,62,0.62); ";
	private static final String BOLD = "-fx-font-weight: bold; ";
	private static final String CONNECTED_COMPONENT_STYLE = TEXT_BACKGROUND + BOLD + "-fx-fill: #52ffea;";
	private static final String SEED_SOURCE_STYLE = TEXT_BACKGROUND + BOLD + "-fx-fill: #36ff60;";
	private static final String FILL_SOURCE_STYLE = TEXT_BACKGROUND + BOLD + "-fx-fill: #ffdbff;";

	public static void createAndAddVirtualIntersectionSource(final PainteraBaseView viewer, Supplier<String> projectDirectory) {

		if (!viewer.isActionAllowed(MenuActionType.CreateVirtualSource)) {
			LOG.debug("Creating Virtual Sources is disabled");
			return;
		}

		final ObjectProperty<IntersectableSourceState<?, ?, ?>> seedSourceStateProperty = new SimpleObjectProperty<>();
		final ObjectProperty<IntersectableSourceState<?, ?, ?>> fillSourceStateProperty = new SimpleObjectProperty<>();
		final StringProperty name = new SimpleStringProperty(null);
		final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);
		final Alert dialog = makeDialog(viewer, seedSourceStateProperty, fillSourceStateProperty, name, color);
		final Optional<ButtonType> returnType = dialog.showAndWait();
		if (Alert.AlertType.CONFIRMATION.equals(dialog.getAlertType()) && ButtonType.OK.equals(returnType.orElse(ButtonType.CANCEL))) {
			try {
				final IntersectingSourceState<?, ?> intersectingState = new IntersectingSourceState<>(
						fillSourceStateProperty.get(),
						seedSourceStateProperty.get(),
						new ARGBCompositeAlphaAdd(),
						name.get(),
						0,
						viewer
				);

				intersectingState.converter().setColor(Colors.toARGBType(color.get()));
				viewer.addState(intersectingState);
			} catch (final Exception e) {
				LOG.error("Unable to create intersecting state", e);
				Exceptions.exceptionAlert(Constants.NAME, "Unable to create intersecting state", e).show();
			}
		}
	}

	private static Alert makeDialog(
			final PainteraBaseView viewer,
			final ObjectProperty<IntersectableSourceState<?, ?, ?>> seedSourceProp,
			final ObjectProperty<IntersectableSourceState<?, ?, ?>> fillSourceProp,
			final StringProperty name,
			final ObjectProperty<Color> color) {

		final SourceInfo sourceInfo = viewer.sourceInfo();
		final List<Source<?>> sources = new ArrayList<>(sourceInfo.trackSources());
		final List<SourceState<?, ?>> states = sources.stream().map(sourceInfo::getState).collect(Collectors.toList());
		final List<IntersectableSourceState<?, ?, ?>> sourceStatesOne = states.stream()
				.filter(x -> x instanceof IntersectableSourceState<?, ?, ?>)
				.map(x -> (IntersectableSourceState<?, ?, ?>)x)
				.collect(Collectors.toList());

		final List<IntersectableSourceState<?, ?, ?>> sourceStatesTwo = List.copyOf(sourceStatesOne);

		if (sourceStatesOne.isEmpty()) {
			final Alert dialog = PainteraAlerts.alert(Alert.AlertType.ERROR, true);
			dialog.setContentText("No Intersectable sources loaded yet, cannot create connected component state.");
			return dialog;
		}

		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		dialog.setHeaderText("Choose sources for intersection and \nconnected component rendering.");

		final Map<SourceState<?, ?>, Integer> sourceIndices = sources
				.stream()
				.collect(Collectors.toMap(sourceInfo::getState, sourceInfo::indexOf));

		final ComboBox<IntersectableSourceState<?, ?, ?>> seedSourceSelection = new ComboBox<>(FXCollections.observableArrayList(sourceStatesOne));
		final ComboBox<IntersectableSourceState<?, ?, ?>> fillSourceSelection = new ComboBox<>(FXCollections.observableArrayList(sourceStatesTwo));

		seedSourceProp.bind(seedSourceSelection.valueProperty());
		fillSourceProp.bind(fillSourceSelection.valueProperty());
		final double idLabelWidth = 20.0;

		seedSourceSelection.setCellFactory(param -> new ListCell<>() {

			@Override
			protected void updateItem(IntersectableSourceState<?, ?, ?> item, boolean empty) {

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

		fillSourceSelection.setCellFactory(param -> new ListCell<>() {

			@Override
			protected void updateItem(IntersectableSourceState<?, ?, ?> item, boolean empty) {

				super.updateItem(item, empty);
				if (item == null || empty) {
					setGraphic(null);
				} else {
					final Label id = new Label(sourceIndices.get(item) + ":");
					id.setPrefWidth(idLabelWidth);
					setGraphic(id);
					setText(item.nameProperty().get());
				}
			}
		});

		seedSourceSelection.setButtonCell(seedSourceSelection.getCellFactory().call(null));
		fillSourceSelection.setButtonCell(fillSourceSelection.getCellFactory().call(null));
		seedSourceSelection.setMaxWidth(Double.POSITIVE_INFINITY);
		fillSourceSelection.setMaxWidth(Double.POSITIVE_INFINITY);

		seedSourceSelection.setPromptText("Select seed source");
		fillSourceSelection.setPromptText("Select filled component source");

		final TextField nameField = new TextField(null);
		nameField.setPromptText("Set name for intersecting source");
		name.bind(nameField.textProperty());

		final ColorPicker colorPicker = new ColorPicker();
		colorPicker.valueProperty().bindBidirectional(color);

		final GridPane grid = new GridPane();

		grid.add(Labels.withTooltip("Seed Source", "Select seed source."), 0, 0);
		grid.add(Labels.withTooltip("Fill Component Source", "Select component source."), 0, 1);
		grid.add(new Label("Name"), 0, 2);
		grid.add(new Label("Color"), 0, 3);

		grid.add(seedSourceSelection, 1, 0);
		grid.add(fillSourceSelection, 1, 1);
		grid.add(nameField, 1, 2);
		grid.add(colorPicker, 1, 3);

		final var infoPane = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true);
		final var view = new ImageView("PainteraIntersectionInfo.png");
		view.setFitHeight(400.0);
		view.preserveRatioProperty().setValue(true);
		view.setSmooth(true);

		final var richText = getTextHelpNode();
		final BorderPane pane = new BorderPane(view, richText, null, null, null);
		infoPane.getDialogPane().setContent(pane);
		infoPane.setWidth(800.0);
		GridPane.setHgrow(seedSourceSelection, Priority.ALWAYS);
		GridPane.setHgrow(fillSourceSelection, Priority.ALWAYS);

		dialog.getDialogPane().setContent(grid);

		BooleanProperty okButtonDisabledProp = dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty();
		okButtonDisabledProp.bind(seedSourceSelection
				.valueProperty()
				.isNull()
				.or(fillSourceSelection.valueProperty().isNull())
				.or(name.isEmpty())
		);

		final ButtonType helpButtonType = new ButtonType("Help", ButtonBar.ButtonData.HELP_2);
		dialog.getDialogPane().getButtonTypes().add(helpButtonType);

		final var helpButton = (Button)dialog.getDialogPane().lookupButton(helpButtonType);
		/* NOTE: I know it seems odd to trigger the help dialog in the event filter, instead of the `onAction`,
		 *	but the issue is that Dialog's automatically close when any button is pressed.
		 * 	To stop the dialog from closing when the HELP button is pressed, we need to intercept and consume the event.
		 * 	Since we need to consume the event though, it means */
		helpButton.addEventFilter(ActionEvent.ACTION, event -> {
			if (!infoPane.isShowing()) {
				infoPane.show();
			}
			event.consume();
		});

		return dialog;
	}

	@NotNull
	private static InlineCssTextArea getTextHelpNode() {

		final var richText = new InlineCssTextArea();

		richText.setEditable(false);
		richText.append("To create a ", "");
		richText.append(" connected component source ", CONNECTED_COMPONENT_STYLE);
		richText.append(" select a ", "");
		richText.append(" seed source ", SEED_SOURCE_STYLE);
		richText.append(" and a ", "");
		richText.append(" fill source", FILL_SOURCE_STYLE);
		richText.append(".\n\nThe ", "");
		richText.append(" seed source ", SEED_SOURCE_STYLE);
		richText.append(" is used to detect intersection with the", "");
		richText.append(" fill source ", FILL_SOURCE_STYLE);
		richText.append(".\n\nAll components in the", "");
		richText.append(" fill source ", FILL_SOURCE_STYLE);
		richText.append(" that overlap with the", "");
		richText.append(" seed source ", SEED_SOURCE_STYLE);
		richText.append(" are filled into to create", "");
		richText.append(" connected component source ", CONNECTED_COMPONENT_STYLE);
		richText.append(".", "");
		return richText;
	}

}
