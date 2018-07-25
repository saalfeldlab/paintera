package org.janelia.saalfeldlab.paintera.ui.source.converter;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.Map.Entry;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.converter.NumberStringConverter;
import net.imglib2.converter.Converter;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.stream.ColorFromSegmentId;
import org.janelia.saalfeldlab.paintera.stream.HideLockedSegments;
import org.janelia.saalfeldlab.paintera.stream.SeedProperty;
import org.janelia.saalfeldlab.paintera.stream.UserSpecifiedColors;
import org.janelia.saalfeldlab.paintera.stream.WithAlpha;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HighlightingStreamConverterNode<C extends Converter<?, ?> & SeedProperty & WithAlpha &
		ColorFromSegmentId & HideLockedSegments & UserSpecifiedColors>
		implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final C converter;

	private final DoubleProperty alpha = new SimpleDoubleProperty();

	private final DoubleProperty activeFragmentAlpha = new SimpleDoubleProperty();

	private final DoubleProperty activeSegmentAlpha = new SimpleDoubleProperty();

	private final BooleanProperty colorFromSegment = new SimpleBooleanProperty();

	private final IntegerProperty alphaInt = new SimpleIntegerProperty();

	private final IntegerProperty activeFragmentAlphaInt = new SimpleIntegerProperty();

	private final IntegerProperty activeSegmentAlphaInt = new SimpleIntegerProperty();

	public HighlightingStreamConverterNode(final C converter)
	{
		super();
		this.converter = converter;

		alpha.addListener((obs, oldv, newv) -> alphaInt.set(toIntegerBased(newv.doubleValue())));
		activeFragmentAlpha.addListener((obs, oldv, newv) -> activeFragmentAlphaInt.set(toIntegerBased(newv
				.doubleValue())));
		activeSegmentAlpha.addListener((obs, oldv, newv) -> activeSegmentAlphaInt.set(toIntegerBased(newv.doubleValue
				())));

		alphaInt.addListener((obs, oldv, newv) -> alpha.set(toDoubleBased(newv.intValue())));
		activeFragmentAlphaInt.addListener((obs, oldv, newv) -> activeFragmentAlpha.set(toDoubleBased(newv.intValue()
		                                                                                             )));
		activeSegmentAlphaInt.addListener((obs, oldv, newv) -> activeSegmentAlpha.set(toDoubleBased(newv.intValue())));
	}

	@Override
	public Node get()
	{
		return createNode();
	}

	@Override
	public void bind()
	{
		alphaInt.bindBidirectional(converter.alphaProperty());
		activeFragmentAlphaInt.bindBidirectional(converter.activeFragmentAlphaProperty());
		activeSegmentAlphaInt.bindBidirectional(converter.activeSegmentAlphaProperty());
		colorFromSegment.bindBidirectional(converter.colorFromSegmentIdProperty());
	}

	@Override
	public void unbind()
	{
		alphaInt.unbindBidirectional(converter.alphaProperty());
		activeFragmentAlphaInt.unbindBidirectional(converter.activeFragmentAlphaProperty());
		activeSegmentAlphaInt.unbindBidirectional(converter.activeSegmentAlphaProperty());
		colorFromSegment.unbindBidirectional(converter.colorFromSegmentIdProperty());
	}

	private Node createNode()
	{
		final VBox              contents                = new VBox();
		final GridPane          gp                      = new GridPane();
		final ColumnConstraints secondColumnConstraints = new ColumnConstraints();
		secondColumnConstraints.setMaxWidth(Double.MAX_VALUE);
		secondColumnConstraints.setHgrow(Priority.ALWAYS);
		gp.getColumnConstraints().addAll(secondColumnConstraints);

		final int textFieldWidth = 60;
		int       row            = 0;

		contents.getChildren().add(gp);

		{
			final Slider alphaSlider = new Slider(0, 1, alpha.get());
			alphaSlider.valueProperty().bindBidirectional(alpha);
			alphaSlider.setShowTickLabels(true);
			alphaSlider.setTooltip(new Tooltip("Alpha for inactive fragments."));
			final TextField alphaField = new TextField();
			alphaField.textProperty().bindBidirectional(alphaSlider.valueProperty(), new NumberStringConverter());
			alphaField.setMinWidth(textFieldWidth);
			alphaField.setMaxWidth(textFieldWidth);
			gp.add(alphaSlider, 0, row);
			gp.add(alphaField, 1, row);
			++row;
		}

		{
			LOG.debug("Active fragment alpha={}", activeFragmentAlpha);
			final Slider selectedFragmentAlphaSlider = new Slider(0, 1, activeFragmentAlpha.get());
			selectedFragmentAlphaSlider.valueProperty().bindBidirectional(activeFragmentAlpha);
			selectedFragmentAlphaSlider.setShowTickLabels(true);
			selectedFragmentAlphaSlider.setTooltip(new Tooltip("Alpha for selected fragments."));
			final TextField selectedFragmentAlphaField = new TextField();
			selectedFragmentAlphaField.textProperty().bindBidirectional(
					selectedFragmentAlphaSlider.valueProperty(),
					new NumberStringConverter()
			                                                           );
			selectedFragmentAlphaField.setMinWidth(textFieldWidth);
			selectedFragmentAlphaField.setMaxWidth(textFieldWidth);
			gp.add(selectedFragmentAlphaSlider, 0, row);
			gp.add(selectedFragmentAlphaField, 1, row);
			++row;
		}

		{
			final Slider selectedSegmentAlphaSlider = new Slider(0, 1, activeSegmentAlpha.get());
			selectedSegmentAlphaSlider.valueProperty().bindBidirectional(activeSegmentAlpha);
			selectedSegmentAlphaSlider.setShowTickLabels(true);
			selectedSegmentAlphaSlider.setTooltip(new Tooltip("Alpha for active segments."));
			final TextField selectedSegmentAlphaField = new TextField();
			selectedSegmentAlphaField.textProperty().bindBidirectional(
					selectedSegmentAlphaSlider.valueProperty(),
					new NumberStringConverter()
			                                                          );
			selectedSegmentAlphaField.setMinWidth(textFieldWidth);
			selectedSegmentAlphaField.setMaxWidth(textFieldWidth);
			gp.add(selectedSegmentAlphaSlider, 0, row);
			gp.add(selectedSegmentAlphaField, 1, row);
			++row;
		}

		{
			final double                     colorPickerWidth = 30;
			final double                     buttonWidth      = 40;
			final ObservableMap<Long, Color> colorsMap        = converter.userSpecifiedColors();
			final Button                     addButton        = new Button("+");
			addButton.setMinWidth(buttonWidth);
			addButton.setMaxWidth(buttonWidth);
			final ColorPicker addColorPicker = new ColorPicker();
			addColorPicker.setMaxWidth(colorPickerWidth);
			addColorPicker.setMinWidth(colorPickerWidth);
			final TextField addIdField = new TextField();
			GridPane.setHgrow(addIdField, Priority.ALWAYS);
			addButton.setOnAction(event -> {
				event.consume();
				try
				{
					final long id = Long.parseLong(addIdField.getText());
					converter.setColor(id, addColorPicker.getValue());
					addIdField.setText("");
				} catch (final NumberFormatException e)
				{
					LOG.error("Not a valid long/integer format: {}", addIdField.getText());
				}
			});

			{
				final CheckBox hideLockedSegments = new CheckBox("Hide locked segments.");
				hideLockedSegments.setTooltip(new Tooltip("Hide locked segments (toggle lock with L)"));
				hideLockedSegments.selectedProperty().bindBidirectional(converter.hideLockedSegmentsProperty());
				contents.getChildren().add(hideLockedSegments);
			}

			{
				final CheckBox colorFromSegmentId = new CheckBox("Color From segment Id.");
				colorFromSegmentId.setTooltip(new Tooltip(
						"Generate fragment color from segment id (on) or fragment id (off)"));
				colorFromSegmentId.selectedProperty().bindBidirectional(colorFromSegment);
				contents.getChildren().add(colorFromSegmentId);
			}

			final GridPane colorContents = new GridPane();
			colorContents.setHgap(5);
			final TitledPane colorPane = new TitledPane("Custom Colors", colorContents);
			colorPane.setExpanded(false);
			final MapChangeListener<Long, Color> colorsChanged = change -> {
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					int gridRow = 0;
					colorContents.getChildren().clear();
					for (final Iterator<Entry<Long, Color>> it = colorsMap.entrySet().iterator(); it.hasNext();
					     ++gridRow)
					{
						final Entry<Long, Color> entry = it.next();
						final TextField          tf    = new TextField(Long.toString(entry.getKey()));
						tf.setEditable(false);
						GridPane.setHgrow(tf, Priority.ALWAYS);
						final ColorPicker colorPicker = new ColorPicker(entry.getValue());
						colorPicker.setMinWidth(colorPickerWidth);
						colorPicker.setMaxWidth(colorPickerWidth);
						colorPicker.valueProperty().addListener((obs, oldv, newv) -> {
							converter.setColor(entry.getKey(), newv);
						});
						final Button removeButton = new Button("X");
						removeButton.setMaxWidth(buttonWidth);
						removeButton.setMinWidth(buttonWidth);
						removeButton.setOnAction(event -> {
							event.consume();
							converter.removeColor(entry.getKey());
						});
						colorContents.add(tf, 0, gridRow);
						colorContents.add(colorPicker, 1, gridRow);
						colorContents.add(removeButton, 2, gridRow);
					}
					colorContents.add(addIdField, 0, gridRow);
					colorContents.add(addColorPicker, 1, gridRow);
					colorContents.add(addButton, 2, gridRow);
				});
			};
			colorsMap.addListener(colorsChanged);
			colorsChanged.onChanged(null);
			contents.getChildren().add(colorPane);
		}

		//		{
		//			if ( state.selectedIdsProperty().get() != null )
		//			{
		//				final SelectedIdsTextField selectedIdsField = new SelectedIdsTextField( s.selectedIdsProperty
		// ().get() );
		//				contents.getChildren().add( selectedIdsField.textField() );
		//			}
		//		}

		final TitledPane tp = new TitledPane("Converter", contents);
		tp.setExpanded(false);
		return tp;
	}

	private static int toIntegerBased(final double opacity)
	{
		return (int) Math.round(255 * opacity);
	}

	private static double toDoubleBased(final int opacity)
	{
		return opacity / 255.0;
	}

}
