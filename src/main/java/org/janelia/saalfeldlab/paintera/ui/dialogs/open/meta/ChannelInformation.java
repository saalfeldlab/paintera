package org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.stream.IntStream;

public class ChannelInformation {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntegerProperty numChannels = new SimpleIntegerProperty(0);

	private final ObjectProperty<int[]> channelSelection = new SimpleObjectProperty<>(new int[]{});

	{
		numChannels.addListener((obs, oldv, newv) -> channelSelection.set(range(Math.max(newv.intValue(), 0))));
	}

	public IntegerProperty numChannelsProperty() {

		return numChannels;
	}

	public int[] getChannelSelectionCopy() {

		return this.channelSelection.get().clone();
	}

	public Node getNode() {

		final TextField channels = new TextField();
		channels.setEditable(false);
		channels.setTooltip(new Tooltip("Channel selection"));
		channelSelection
				.addListener((obs, oldv, newv) -> channels.setText(String.join(", ", IntStream.of(newv).boxed().map(Object::toString).toArray(String[]::new))));

		MenuItem all = new MenuItem("_All");
		all.setOnAction(e -> channelSelection.set(range()));

		MenuItem reverse = new MenuItem("_Reverse");
		reverse.setOnAction(e -> channelSelection.set(reversed(channelSelection.get())));

		MenuItem everyNth = new MenuItem("Every _Nth");
		everyNth.setOnAction(e -> everyNth(numChannels.get()).ifPresent(channelSelection::set));

		final MenuButton selectionButton = new MenuButton("_Select", null, all, reverse, everyNth);

		HBox.setHgrow(channels, Priority.ALWAYS);
		return new VBox(
				new Label("Channel Settings"),
				new HBox(channels, selectionButton));
	}

	private int[] range() {

		return range(Math.max(0, this.numChannels.get()));
	}

	private static int[] range(final int n) {

		return IntStream.range(0, n).toArray();
	}

	private static int[] reversed(int[] array) {

		final int[] reversed = new int[array.length];
		for (int i = 0, k = reversed.length - 1; i < reversed.length; ++i, --k) {
			reversed[i] = array[k];
		}
		return reversed;
	}

	private static Optional<int[]> everyNth(int k) {

		if (k == 0)
			return Optional.empty();

		final NumberField<IntegerProperty> step = NumberField.intField(1, i -> i > 0, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final NumberField<IntegerProperty> start = NumberField
				.intField(0, i -> i >= 0 && i < k, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final NumberField<IntegerProperty> stop = NumberField
				.intField(k, i -> i >= 0 && i < k, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);

		final GridPane grid = new GridPane();
		grid.add(new Label("Start"), 1, 1);
		grid.add(new Label("Stop"), 1, 2);
		grid.add(new Label("Step"), 1, 3);
		grid.add(start.getTextField(), 2, 1);
		grid.add(stop.getTextField(), 2, 2);
		grid.add(step.getTextField(), 2, 3);

		final CheckBox reverse = new CheckBox("_Reverse");
		reverse.setTooltip(new Tooltip("Reverse order in which channels are added"));

		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		dialog.setHeaderText(
				"Select subset of channels: {start, start + step, ...}. The start value is inclusive, stop is exclusive. And start has to be larger than stop.");
		final Button OK = ((Button)dialog.getDialogPane().lookupButton(ButtonType.OK));
		final Button CANCEL = ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL));
		// always submit all text fields when hitting enter.
		OK.setOnAction(e -> {
			step.submit();
			start.submit();
			stop.submit();
		});
		OK.setText("_Ok");
		CANCEL.setText("_Cancel");

		start.valueProperty().addListener((obs, oldv, newv) -> OK.setDisable(newv.intValue() >= stop.valueProperty().get()));
		stop.valueProperty().addListener((obs, oldv, newv) -> OK.setDisable(newv.intValue() <= start.valueProperty().get()));

		dialog.getDialogPane().setContent(new VBox(grid, reverse));

		if (dialog.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
			final int inc = step.valueProperty().get();
			final int s = start.valueProperty().get();
			final int S = stop.valueProperty().get();
			final int[] array = new int[(int)Math.ceil((S - s) * 1.0 / inc)];
			LOG.debug("start={} stop={} step={}", s, S, inc);
			for (int i = 0, v = s; v < S; v += inc, ++i) {
				array[i] = v;
			}
			LOG.debug("Every nth array: {} {}", array, reverse.isSelected());
			return Optional.of(reverse.isSelected() ? reversed(array) : array);
		}

		return Optional.empty();

	}

}
