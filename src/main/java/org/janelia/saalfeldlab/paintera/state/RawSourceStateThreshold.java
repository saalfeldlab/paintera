package org.janelia.saalfeldlab.paintera.state;

import javafx.beans.property.DoubleProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RawSourceStateThreshold<D extends RealType<D>, T extends AbstractVolatileRealType<D, T>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SourceState<D, T> toBeThresholded;

	private final DoubleSupplier minSupplier;

	private final DoubleSupplier maxSupplier;

	public RawSourceStateThreshold(
			final SourceState<D, T> toBeThresholded,
			final DoubleSupplier minSupplier,
			final DoubleSupplier maxSupplier) {
		this.toBeThresholded = toBeThresholded;
		this.minSupplier = minSupplier;
		this.maxSupplier = maxSupplier;
	}

	@Deprecated
	public RawSourceStateThreshold(final RawSourceState<D, T> toBeThresholded) {
		this(toBeThresholded, toBeThresholded.converter()::getMin, toBeThresholded.converter()::getMax);
	}

	public RawSourceStateThreshold(final ConnectomicsRawState<D, T> toBeThresholded) {
		this(toBeThresholded, toBeThresholded.converter()::getMin, toBeThresholded.converter()::getMax);
	}

	public EventHandler<KeyEvent> keyPressedHandler(final PainteraBaseView pbv, final Supplier<KeyCombination> binding) {
		return new Handler<>(pbv, e -> KeyEvent.KEY_PRESSED.equals(e.getEventType()) && binding.get().match(e));
	}

	private final class Handler<E extends Event> implements EventHandler<E> {

		private final PainteraBaseView pbv;

		private final Predicate<E> test;

		private Handler(
				final PainteraBaseView pbv,
				final Predicate<E> test) {
			this.pbv = pbv;
			this.test = test;
		}

		@Override
		public void handle(E event) {
			if (test.test(event)) {
				event.consume();

				final Label sourceIndexLabel = Labels.withTooltip("Source Index", "Index of the raw source to be thresholded.");
				final Label sourceNameLabel = Labels.withTooltip("Source Name", "Name of the raw source to be thresholded.");
				final Label targetNameLabel = Labels.withTooltip("Target Name", "Name of the new, thresholded source");
				final Label foregroundColorLabel = Labels.withTooltip("Foreground Color", "Color of foreground in thresholded source.");
				final Label backgroundColorLabel = Labels.withTooltip("Background Color", "Color of background in thresholded source.");
				final Label minLabel = Labels.withTooltip("Min", "Values x: min < x < max are considered foreground");
				final Label maxLabel = Labels.withTooltip("Max", "Values x: min < x < max are considered foreground");

				final TextField sourceIndex = new TextField(Integer.toString(pbv.sourceInfo().indexOf(toBeThresholded.getDataSource())));
				final TextField sourceName = new TextField(toBeThresholded.nameProperty().get());
				final TextField targetName = new TextField(sourceName.getText() + "-thresholded");
				final ColorPicker foregroundColorPicker = new ColorPicker(Color.WHITE);
				final ColorPicker backgroundColorPicker = new ColorPicker(Color.BLACK);
				final T viewerType = toBeThresholded.getDataSource().getType();
				final NumberField<DoubleProperty> minField = NumberField.doubleField(minSupplier.getAsDouble(), d -> true, ObjectField.SubmitOn.values());
				final NumberField<DoubleProperty> maxField = NumberField.doubleField(maxSupplier.getAsDouble(), d -> true, ObjectField.SubmitOn.values());
				foregroundColorPicker.getStyleClass().add("button");
				backgroundColorPicker.getStyleClass().add("button");
				foregroundColorPicker.setMaxWidth(Double.MAX_VALUE);
				backgroundColorPicker.setMaxWidth(Double.MAX_VALUE);

				sourceIndex.setEditable(false);
				sourceIndex.setDisable(true);
				sourceName.setEditable(false);
				sourceName.setDisable(true);

				final GridPane contents = new GridPane();
				contents.add(sourceIndexLabel, 0, 0);
				contents.add(sourceNameLabel, 0, 1);
				contents.add(targetNameLabel, 0, 2);
				contents.add(foregroundColorLabel, 0, 3);
				contents.add(backgroundColorLabel, 0, 4);
				contents.add(minLabel, 0, 5);
				contents.add(maxLabel, 0, 6);

				contents.add(sourceIndex, 1, 0);
				contents.add(sourceName, 1, 1);
				contents.add(targetName, 1, 2);
				contents.add(foregroundColorPicker, 1, 3);
				contents.add(backgroundColorPicker, 1, 4);
				contents.add(minField.textField(), 1, 5);
				contents.add(maxField.textField(), 1, 6);

				GridPane.setHgrow(sourceIndex, Priority.ALWAYS);
				GridPane.setHgrow(sourceName, Priority.ALWAYS);
				GridPane.setHgrow(targetName, Priority.ALWAYS);
				GridPane.setHgrow(foregroundColorPicker, Priority.ALWAYS);
				GridPane.setHgrow(backgroundColorPicker, Priority.ALWAYS);

				final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
				dialog.getDialogPane().setContent(contents);
				dialog.setHeaderText(String.format("Threshold raw source `%s'", sourceName.getText()));
				((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Threshold");
				makeMnemonic(dialog.getDialogPane());

				final Optional<ButtonType> buttonType = dialog.showAndWait();
				if (buttonType.filter(ButtonType.OK::equals).isPresent()) {
					final ThresholdingSourceState<D, T> thresholdedState = new ThresholdingSourceState<>(targetName.getText(), toBeThresholded);
					LOG.debug("Foreground color is {}", foregroundColorPicker.getValue());
					thresholdedState.colorProperty().set(foregroundColorPicker.getValue());
					LOG.debug("Background color is {}", backgroundColorPicker.getValue());
					thresholdedState.backgroundColorProperty().set(backgroundColorPicker.getValue());
					thresholdedState.minProperty().set(minField.valueProperty().get());
					thresholdedState.maxProperty().set(maxField.valueProperty().get());
					pbv.addState(thresholdedState);
				}
			}
		}
	}

	private static void makeMnemonic(final DialogPane dialog) {
		dialog
				.getButtonTypes()
				.stream()
				.map(dialog::lookupButton)
				.forEach(RawSourceStateThreshold::makeMnemonic);
	}

	private static void makeMnemonic(final Node button) {
		if (button instanceof Button)
			((Button) button).setText("_" + ((Button) button).getText());
	}
}
