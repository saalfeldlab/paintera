package org.janelia.saalfeldlab.paintera.ui.source.converter;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.Buttons;
import org.janelia.saalfeldlab.fx.Menus;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.util.DoubleStringFormatter;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

public class ARGBCompositeColorConverterNode implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ARGBCompositeColorConverter<?, ?, ?> converter;

	private final ObjectProperty<Color>[] colorProperty;

	private final ObjectProperty<ARGBType>[] argbProperty;

	private final DoubleProperty alphaProperty = new SimpleDoubleProperty();

	private final DoubleProperty min[];

	private final DoubleProperty max[];

	private final DoubleProperty[] channelAlphaProperty;

	private final int numChannels;

	public ARGBCompositeColorConverterNode(final ARGBCompositeColorConverter<?, ?, ?> converter)
	{
		super();
		this.converter = converter;
		this.numChannels = converter.numChannels();

		this.colorProperty = IntStream
				.range(0, numChannels)
				.mapToObj(channel -> new SimpleObjectProperty<>(Colors.toColor(converter.colorProperty(channel).get()))).toArray(ObjectProperty[]::new);
		this.argbProperty  = IntStream
				.range(0, numChannels)
				.mapToObj(channel -> new SimpleObjectProperty<>(new ARGBType(converter.colorProperty(channel).get().get()))).toArray(ObjectProperty[]::new);
		this.min  = IntStream
				.range(0, numChannels)
				.mapToObj(channel -> new SimpleDoubleProperty(converter.minProperty(channel).get())).toArray(DoubleProperty[]::new);
		this.max  = IntStream
				.range(0, numChannels)
				.mapToObj(channel -> new SimpleDoubleProperty(converter.maxProperty(channel).get())).toArray(DoubleProperty[]::new);
		this.channelAlphaProperty  = IntStream
				.range(0, numChannels)
				.mapToObj(channel -> new SimpleDoubleProperty(converter.channelAlphaProperty(channel).get())).toArray(DoubleProperty[]::new);

		IntStream.range(0, numChannels).forEach(channel -> this.colorProperty[channel].addListener((obs, oldv, newv) -> this.argbProperty[channel].set(Colors.toARGBType(newv))));
		IntStream.range(0, numChannels).forEach(channel -> this.argbProperty[channel].addListener((obs, oldv, newv) -> this.colorProperty[channel].set(Colors.toColor(newv))));
	}

	@Override
	public Node get()
	{
		return getNodeForARGBColorConverter();
	}

	@Override
	public void bind()
	{
		IntStream.range(0, numChannels).forEach(channel -> this.argbProperty[channel].bindBidirectional(converter.colorProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.min[channel].bindBidirectional(converter.minProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.max[channel].bindBidirectional(converter.maxProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.channelAlphaProperty[channel].bindBidirectional(converter.channelAlphaProperty(channel)));
		alphaProperty.bindBidirectional(converter.alphaProperty());
	}

	@Override
	public void unbind()
	{
		IntStream.range(0, numChannels).forEach(channel -> this.argbProperty[channel].unbindBidirectional(converter.colorProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.min[channel].unbindBidirectional(converter.minProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.max[channel].unbindBidirectional(converter.maxProperty(channel)));
		IntStream.range(0, numChannels).forEach(channel -> this.channelAlphaProperty[channel].unbindBidirectional(converter.channelAlphaProperty(channel)));
		alphaProperty.unbindBidirectional(converter.alphaProperty());
	}

	private Node getNodeForARGBColorConverter()
	{

		VBox channelsBox = new VBox();

		for (int channel = 0; channel < numChannels; ++channel)
		{
			NumberField<DoubleProperty> minField = NumberField.doubleField(min[channel].get(), d -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
			NumberField<DoubleProperty> maxField = NumberField.doubleField(max[channel].get(), d -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
			minField.valueProperty().bindBidirectional(min[channel]);
			maxField.valueProperty().bindBidirectional(max[channel]);
			minField.textField().setTooltip(new Tooltip("min"));
			maxField.textField().setTooltip(new Tooltip("max"));
			final HBox minMaxBox = new HBox(minField.textField(), maxField.textField());

			final NumericSliderWithField alphaSliderWithField = new NumericSliderWithField(0, 1, this.channelAlphaProperty[channel].get());
			alphaSliderWithField.slider().valueProperty().bindBidirectional(this.channelAlphaProperty[channel]);
			alphaSliderWithField.textField().setMinWidth(48);
			alphaSliderWithField.textField().setMaxWidth(48);
			final HBox alphaBox = new HBox(alphaSliderWithField.slider(), alphaSliderWithField.textField());
			Tooltip.install(alphaSliderWithField.slider(), new Tooltip("alpha"));
			HBox.setHgrow(alphaSliderWithField.slider(), Priority.ALWAYS);

			TitledPane channelPane = TitledPanes.createCollapsed(String.format("Channel % 2d", channel), new VBox(minMaxBox, alphaBox));
			channelsBox.getChildren().add(channelPane);
			final ColorPicker colorSelector = new ColorPicker(colorProperty[channel].get());
//			colorSelector.setMinWidth(30);
//			colorSelector.setMaxWidth(30);
			colorSelector.valueProperty().bindBidirectional(colorProperty[channel]);
			channelPane.setGraphic(colorSelector);
		}

		final TitledPane channels = TitledPanes.createCollapsed("Channels", channelsBox);

		final NumericSliderWithField alphaSliderWithField = new NumericSliderWithField(0, 1, this.alphaProperty.get());
		alphaSliderWithField.slider().valueProperty().bindBidirectional(this.alphaProperty);
		alphaSliderWithField.textField().setMinWidth(48);
		alphaSliderWithField.textField().setMaxWidth(48);
		final HBox alphaBox = new HBox(alphaSliderWithField.slider(), alphaSliderWithField.textField());
		Tooltip.install(alphaSliderWithField.slider(), new Tooltip("alpha"));
		HBox.setHgrow(alphaSliderWithField.slider(), Priority.ALWAYS);

		MenuButton setButton = new MenuButton(
				"Set",
				null,
				Menus.menuItem("Equidistant Colors (Hue)", e -> setColorsEquidistant()),
				Menus.menuItem("Value Range", e -> setAllMinMax()));
		setButton.setTooltip(new Tooltip("Change channels globally."));

		final TitledPane contents = new TitledPane("Converter", new VBox(alphaBox, setButton, channels));
		contents.setExpanded(false);
		return contents;
	}

	private void setColorsEquidistant()
	{
		final Color[] colorBackup = IntStream.range(0, numChannels).mapToObj(channel -> colorProperty[channel].get()).toArray(Color[]::new);
		ColorPicker picker = new ColorPicker(colorProperty[0].get());
		double step = 360.0 / numChannels;
		picker.valueProperty().addListener((obs, oldv, newv) -> {
			double hue = newv.getHue();
			for (int channel = 0; channel < numChannels; ++channel)
				colorProperty[channel].set(Color.hsb(hue + channel * step, newv.getSaturation(), newv.getBrightness()));
		});

		Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
		dialog.setResizable(true);
		dialog.setTitle(Paintera.NAME);
		dialog.setHeaderText("Set channel colors equidistant across hue value of HSB space.");

		MenuButton maximizeButton = new MenuButton(
				"Maximize",
				null,
				Menus.menuItem("Saturation", e -> picker.setValue(Color.hsb(picker.getValue().getHue(), 1.0, picker.getValue().getBrightness()))),
				Menus.menuItem("Brightness", e -> picker.setValue(Color.hsb(picker.getValue().getHue(), picker.getValue().getSaturation(), 1.0)))
				);
		maximizeButton.setTooltip(new Tooltip("Maximize saturation or brightness of selected colors."));


		dialog.getDialogPane().setContent(new VBox(picker, maximizeButton));


		final Optional<ButtonType> bt = dialog.showAndWait();
		if (ButtonType.OK.equals(bt.orElse(ButtonType.CANCEL)))
		{
			final double hue = picker.valueProperty().get().getHue();
			final double saturation = picker.valueProperty().get().getSaturation();
			final double brightness = picker.valueProperty().get().getBrightness();
			for (int channel = 0; channel < numChannels; ++channel)
				colorProperty[channel].set(Color.hsb(hue + channel * step, saturation, brightness));

		} else
		{
			LOG.debug("Resetting colors");
			for (int channel = 0; channel < numChannels; ++channel)
				colorProperty[channel].set(colorBackup[channel]);
		}

	}

	private void setAllMinMax()
	{
		final double[] minBackUp = IntStream.range(0, numChannels).mapToObj(channel -> min[channel]).mapToDouble(DoubleProperty::get).toArray();
		final double[] maxBackUp = IntStream.range(0, numChannels).mapToObj(channel -> max[channel]).mapToDouble(DoubleProperty::get).toArray();
		final Label minLabel = new Label("Min");
		final Label maxLabel = new Label("Min");
		final NumberField<DoubleProperty> minField = NumberField.doubleField(min[0].get(), m -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final NumberField<DoubleProperty> maxField = NumberField.doubleField(max[0].get(), m -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);

		minField.textField().setPrefWidth(50);
		maxField.textField().setPrefWidth(50);

		minField.valueProperty().addListener((obs, oldv, newv) -> Arrays.stream(min).forEach(m -> m.setValue(newv)));
		maxField.valueProperty().addListener((obs, oldv, newv) -> Arrays.stream(max).forEach(m -> m.setValue(newv)));

		GridPane.setHgrow(minLabel, Priority.ALWAYS);
		GridPane.setHgrow(maxLabel, Priority.ALWAYS);

		GridPane grid = new GridPane();
		grid.add(minLabel, 0, 0);
		grid.add(maxLabel, 0, 1);
		grid.add(minField.textField(), 1, 0);
		grid.add(maxField.textField(), 1, 1);

		Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
		dialog.setResizable(true);
		dialog.setTitle(Paintera.NAME);
		dialog.setHeaderText("Set the same value range for all channels.");
		dialog.getDialogPane().setContent(grid);

		Optional<ButtonType> bt = dialog.showAndWait();

		if (ButtonType.OK.equals(bt.orElse(ButtonType.CANCEL)))
		{
			Arrays.stream(min).forEach(m -> m.set(minField.valueProperty().get()));
			Arrays.stream(max).forEach(m -> m.set(maxField.valueProperty().get()));
		}
		else
		{
			for (int channel = 0; channel < numChannels; ++channel)
			{
				min[channel].set(minBackUp[channel]);
				max[channel].set(maxBackUp[channel]);
			}
		}

	}

}
