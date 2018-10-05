package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class ScreenScalesConfigNode {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObjectProperty<double[]> screenScales = new SimpleObjectProperty<>(new double[] {1.0, 0.5});

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
		this.screenScales.bindBidirectional(config.screenScalersProperty());
	}

	private final Node createContents()
	{
		TitledPane pane = new TitledPane("Screen Scales", null);
		ObjectField<double[], ObjectProperty<double[]>> screenScalesField = new ObjectField<>(
				screenScales,
				new ScreenScalesStringConverter(),
				ObjectField.SubmitOn.ENTER_PRESSED,
				ObjectField.SubmitOn.FOCUS_LOST
		);
		pane.setContent(screenScalesField.textField());
		screenScalesField.textField().setTooltip(new Tooltip(
				"Comma separated list of at least one screen-scale(s), monotonically decreasing and in the half-closed interval (0, 1]"
		));
		return pane;
	}

	private static class ScreenScalesStringConverter extends StringConverter<double[]>
	{

		@Override
		public String toString(double[] scales) {
			final StringBuilder sb = new StringBuilder().append(scales[0]);
			for (int i = 1; i < scales.length; ++i)
			{
				sb.append(", ").append(scales[i]);
			}
			return sb.toString();
		}

		@Override
		public double[] fromString(String string) {

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

				LOG.warn("Setting scales: {}", scales);
				return scales;

			} catch (Exception e)
			{
				throw new ObjectField.InvalidUserInput("Invalid screen scale supplied.", e);
			}
		}
	}
}
