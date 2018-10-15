package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

public class ScreenScalesConfigNode {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObjectProperty<ScreenScalesConfig.ScreenScales> screenScales = new SimpleObjectProperty<>(new ScreenScalesConfig.ScreenScales(1.0, 0.5));

	private final Node contents;

	public ScreenScalesConfigNode() {
		this.contents = createContents();
	}

	public Node getContents()
	{
		return this.contents;
	}

	public void bind(final ScreenScalesConfig config)
	{
		this.screenScales.bindBidirectional(config.screenScalesProperty());
	}

	private final Node createContents()
	{
		final ObjectField<ScreenScalesConfig.ScreenScales, ObjectProperty<ScreenScalesConfig.ScreenScales>> screenScalesField = new ObjectField<>(
				screenScales,
				new ScreenScalesStringConverter(),
				ObjectField.SubmitOn.ENTER_PRESSED
		);
		screenScalesField.textField().setTooltip(new Tooltip(
				"Comma separated list of at least one screen-scale(s), monotonically decreasing and in the half-closed interval (0, 1]"
		));
		final MenuItem geometricSequenceButton = new MenuItem("From Geometric Sequence");
		geometricSequenceButton.setOnAction(e -> fromGeometricSequence().showAndWait().ifPresent(screenScales::set));
		final MenuButton setButton = new MenuButton("Set", null, geometricSequenceButton);

		final TitledPane pane = TitledPanes.createCollapsed("Screen Scales", new HBox(screenScalesField.textField(), setButton));
		return pane;
	}

	private static class ScreenScalesStringConverter extends StringConverter<ScreenScalesConfig.ScreenScales>
	{

		@Override
		public String toString(ScreenScalesConfig.ScreenScales scales) {
			final double[] scalesArray = scales.getScalesCopy();
			final StringBuilder sb = new StringBuilder().append(scalesArray[0]);
			for (int i = 1; i < scalesArray.length; ++i)
			{
				sb.append(", ").append(scalesArray[i]);
			}
			return sb.toString();
		}

		@Override
		public ScreenScalesConfig.ScreenScales fromString(String string) {

			try {
				final double[] scales = Arrays
						.stream(string.split(","))
						.map(String::trim)
						.mapToDouble(Double::parseDouble)
						.toArray();

				if (scales.length < 1)
					throw new ObjectField.InvalidUserInput("Need at least one screen scale.");

				if (Arrays.stream(scales).filter(s -> s <= 0.0 || s > 1.0).count() > 0)
					throw new ObjectField.InvalidUserInput("All scales must be in the half closed interval (0, 1]");

				for (int i = 1; i < scales.length; ++i)
				{
					if (scales[i] >= scales[i - 1])
						throw new ObjectField.InvalidUserInput("Scales must be strictly monotonically decreasing!");
				}

				LOG.debug("Setting scales: {}", scales);
				return new ScreenScalesConfig.ScreenScales(scales);

			} catch (Exception e)
			{
				throw new ObjectField.InvalidUserInput("Invalid screen scale supplied.", e);
			}
		}
	}

	private static Dialog<ScreenScalesConfig.ScreenScales> fromGeometricSequence()
	{
		Dialog<ScreenScalesConfig.ScreenScales> d = new Dialog<>();
		d.setTitle(Paintera.NAME);
		d.setHeaderText("Set N screen scales from geometric sequence: a_n = a * f^n");

		final NumberField<DoubleProperty> aField = NumberField.doubleField(1.0, a -> a <= 1.0 && a > 0, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final NumberField<DoubleProperty> fField = NumberField.doubleField(0.5, f -> f < 1.0 && f > 0, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final NumberField<IntegerProperty> NField = NumberField.intField(5, N -> N > 0, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);

		final GridPane grid = new GridPane();

		grid.add(new Label("a"), 0, 0);
		grid.add(new Label("f"), 0, 1);
		grid.add(new Label("N"), 0, 2);

		grid.add(aField.textField(), 1, 0);
		grid.add(fField.textField(), 1, 1);
		grid.add(NField.textField(), 1, 2);


		d.getDialogPane().getButtonTypes().add(ButtonType.OK);
		d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		d.getDialogPane().setContent(grid);

		d.setResultConverter(bt -> {
			if (ButtonType.OK.equals(bt))
			{
				double[] screenScales = new double[NField.valueProperty().get()];
				screenScales[0] = aField.valueProperty().get();
				for (int i = 1; i < screenScales.length; ++i)
					screenScales[i] = screenScales[i -1] * fField.valueProperty().get();
				return new ScreenScalesConfig.ScreenScales(screenScales);
			}
			return null;
		});

		return d;
	}


	public ObjectProperty<ScreenScalesConfig.ScreenScales> screenScalesProperty()
	{
		return this.screenScales;
	}
}
