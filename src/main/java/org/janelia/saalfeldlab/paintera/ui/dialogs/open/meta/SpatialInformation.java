package org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta;

import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta.MetaPanel.DoubleFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.DoublePredicate;

public class SpatialInformation {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public enum Submit {
		WHILE_TYPING,
		ON_ENTER,
		ON_FOCUS_LOST
	}

	private final DoubleProperty x = new SimpleDoubleProperty();

	private final DoubleProperty y = new SimpleDoubleProperty();

	private final DoubleProperty z = new SimpleDoubleProperty();

	private final TextField textX = new TextField("");

	private final TextField textY = new TextField("");

	private final TextField textZ = new TextField("");

	public SpatialInformation(
			final double textFieldWidth,
			final String promptTextX,
			final String promptTextY,
			final String promptTextZ,
			final DoublePredicate checkValues,
			final Submit checkContents,
			final Submit... moreCheckContents) {

		textX.setPrefWidth(textFieldWidth);
		textY.setPrefWidth(textFieldWidth);
		textZ.setPrefWidth(textFieldWidth);

		textX.setPromptText(promptTextX);
		textY.setPromptText(promptTextY);
		textZ.setPromptText(promptTextZ);

		textX.setText(Double.toString(x.doubleValue()));
		textY.setText(Double.toString(y.doubleValue()));
		textZ.setText(Double.toString(z.doubleValue()));

		x.addListener((obs, oldv, newv) -> {
			if (!checkValues.test(newv.doubleValue()))
				x.set(oldv.doubleValue());
		});
		y.addListener((obs, oldv, newv) -> {
			if (!checkValues.test(newv.doubleValue()))
				y.set(oldv.doubleValue());
		});
		z.addListener((obs, oldv, newv) -> {
			if (!checkValues.test(newv.doubleValue()))
				z.set(oldv.doubleValue());
		});

		final Set<Submit> checkSet = new HashSet<>(Arrays.asList(moreCheckContents));
		checkSet.add(checkContents);
		for (Submit check : checkSet) {
			switch (check) {
			case WHILE_TYPING:
				textX.setTextFormatter(new TextFormatter<>(new DoubleFilter()));
				textY.setTextFormatter(new TextFormatter<>(new DoubleFilter()));
				textZ.setTextFormatter(new TextFormatter<>(new DoubleFilter()));
				textX.textProperty().bindBidirectional(x, new Converter());
				textY.textProperty().bindBidirectional(y, new Converter());
				textZ.textProperty().bindBidirectional(z, new Converter());
				break;
			case ON_ENTER:
				textX.addEventFilter(KeyEvent.KEY_PRESSED, event -> submitOnKeyPressed(textX.textProperty(), x, event, KeyCode.ENTER));
				textY.addEventFilter(KeyEvent.KEY_PRESSED, event -> submitOnKeyPressed(textY.textProperty(), y, event, KeyCode.ENTER));
				textZ.addEventFilter(KeyEvent.KEY_PRESSED, event -> submitOnKeyPressed(textZ.textProperty(), z, event, KeyCode.ENTER));
				x.addListener((obs, oldv, newv) -> textX.setText(Double.toString(newv.doubleValue())));
				y.addListener((obs, oldv, newv) -> textY.setText(Double.toString(newv.doubleValue())));
				z.addListener((obs, oldv, newv) -> textZ.setText(Double.toString(newv.doubleValue())));
				break;
			case ON_FOCUS_LOST:
				textX.focusedProperty().addListener((obs, oldv, newv) -> submitOnFocusLost(textX.textProperty(), x, newv));
				textY.focusedProperty().addListener((obs, oldv, newv) -> submitOnFocusLost(textY.textProperty(), y, newv));
				textZ.focusedProperty().addListener((obs, oldv, newv) -> submitOnFocusLost(textZ.textProperty(), z, newv));
				x.addListener((obs, oldv, newv) -> textX.setText(Double.toString(newv.doubleValue())));
				y.addListener((obs, oldv, newv) -> textY.setText(Double.toString(newv.doubleValue())));
				z.addListener((obs, oldv, newv) -> textZ.setText(Double.toString(newv.doubleValue())));
			}
		}
	}

	private static void submitIfValid(StringProperty text, DoubleProperty number) {

		try {
			number.set(Double.parseDouble(text.get()));
		} catch (NumberFormatException e) {
			text.set(Double.toString(number.doubleValue()));
		}
	}

	private static void submitOnFocusLost(StringProperty text, DoubleProperty number, boolean hasFocus) {

		if (!hasFocus)
			submitIfValid(text, number);
	}

	private static void submitOnKeyPressed(StringProperty text, DoubleProperty number, KeyEvent event, KeyCode key) {

		if (event.getCode().equals(key)) {
			LOG.debug("submitting {} to {}", text, number);
			event.consume();
			submitIfValid(text, number);
		}
	}

	public TextField textX() {

		return this.textX;
	}

	public TextField textY() {

		return this.textY;
	}

	public TextField textZ() {

		return this.textZ;
	}

	public void bindTo(final DoubleProperty x, final DoubleProperty y, final DoubleProperty z) {

		this.x.bindBidirectional(x);
		this.y.bindBidirectional(y);
		this.z.bindBidirectional(z);

	}

	private static double parseDouble(final String s) {

		return s.length() == 0 ? Double.NaN : Double.parseDouble(s);
	}

	// TODO create own converter because I wasted hours of trying to find out
	// 	why some of my inputs wouldn't come through (it would work for resolution
	// 	but not offset). It turns out that the default constructed
	// 	NumberStringConverter would ignore any values >= 1000. So much time
	// 	wasted!!
	private static class Converter extends StringConverter<Number> {

		@Override
		public String toString(final Number object) {

			return object.toString();
		}

		@Override
		public Number fromString(final String string) {

			return parseDouble(string);
		}

	}

}
