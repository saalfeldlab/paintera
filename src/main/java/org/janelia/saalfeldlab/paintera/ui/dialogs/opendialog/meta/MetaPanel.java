package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.meta;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.janelia.saalfeldlab.fx.Buttons;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn;

public class MetaPanel {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final double GRID_HGAP = 0;

  private static final double TEXTFIELD_WIDTH = 100;

  private static final String X_STRING = "X";

  private static final String Y_STRING = "Y";

  private static final String Z_STRING = "Z";

  private final SpatialInformation resolution;

  private final SpatialInformation offset;

  private final NumberField<DoubleProperty> min = NumberField.doubleField(0, d -> true, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST);

  private final NumberField<DoubleProperty> max = NumberField.doubleField(255, d -> true, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST);

  private final VBox content = new VBox();

  private final ScrollPane cc = new ScrollPane(content);

  private final TitledPane pane = new TitledPane("meta", cc);

  private final VBox rawMeta = new VBox();

  private final VBox labelMeta = new VBox();

  private final VBox channelMeta = new VBox();

  private final HashSet<Node> additionalMeta = new HashSet<>();

  private final SimpleObjectProperty<TYPE> dataType = new SimpleObjectProperty<>(null);

  private final SimpleObjectProperty<long[]> dimensionsProperty = new SimpleObjectProperty<>(null);

  private final Button reverseButton = Buttons.withTooltip("_Reverse", "Reverse array attributes", e -> {
  });

  private final ChannelInformation channelInfo = new ChannelInformation();

  public MetaPanel() {

	this.resolution = new SpatialInformation(
			TEXTFIELD_WIDTH,
			X_STRING,
			Y_STRING,
			Z_STRING,
			v -> v > 0,
			SpatialInformation.Submit.ON_ENTER,
			SpatialInformation.Submit.ON_FOCUS_LOST);
	this.resolution.textX().setText("1.0");
	this.resolution.textY().setText("1.0");
	this.resolution.textZ().setText("1.0");

	this.offset = new SpatialInformation(
			TEXTFIELD_WIDTH,
			X_STRING,
			Y_STRING,
			Z_STRING,
			v -> true,
			SpatialInformation.Submit.ON_ENTER,
			SpatialInformation.Submit.ON_FOCUS_LOST);

	cc.setFitToWidth(true);

	final GridPane spatialInfo = new GridPane();
	spatialInfo.setHgap(GRID_HGAP);
	final Label empty = new Label("");
	final Label xLabel = new Label(X_STRING);
	final Label yLabel = new Label(Y_STRING);
	final Label zLabel = new Label(Z_STRING);

	formatLabels(empty, xLabel, yLabel, zLabel);
	addToGrid(spatialInfo, 0, 0, empty, xLabel, yLabel, zLabel);
	addToGrid(
			spatialInfo,
			0,
			1,
			new Label("Resolution "),
			resolution.textX(),
			resolution.textY(),
			resolution.textZ()
	);
	addToGrid(spatialInfo, 0, 2, new Label("Offset"), offset.textX(), offset.textY(), offset.textZ());
	spatialInfo.add(reverseButton, 3, 3);
	reverseButton.setPrefWidth(TEXTFIELD_WIDTH);
	final ColumnConstraints cc = new ColumnConstraints();
	cc.setHgrow(Priority.ALWAYS);
	spatialInfo.getColumnConstraints().addAll(cc);

	final StackPane dimensionInfo = new StackPane();
	// max num of labels

	final StackPane channelInfoPane = new StackPane();

	this.dimensionsProperty.addListener((obs, oldv, newv) -> {
		if (newv == null) {
			InvokeOnJavaFXApplicationThread.invoke(dimensionInfo.getChildren()::clear);
			InvokeOnJavaFXApplicationThread.invoke(channelInfoPane.getChildren()::clear);
		} else {
			final Label[] labels = Stream.generate(Label::new).limit(newv.length).toArray(Label[]::new);
			Stream.of(labels).forEach(l -> {
				l.setTextAlignment(TextAlignment.CENTER);
				l.setAlignment(Pos.BASELINE_CENTER);
				l.setPrefWidth(TEXTFIELD_WIDTH);
			});
			final GridPane grid = new GridPane();
			grid.setHgap(GRID_HGAP);
			grid.getColumnConstraints().addAll(cc);
			grid.add(new Label("Dimensions"), 0, 1);
			for (int d = 0; d < newv.length; ++d) {
				labels[d].setText("" + d);
				final TextField lbl = new TextField("" + newv[d]);
				lbl.setEditable(false);
				grid.add(labels[d], d + 1, 0);
				grid.add(lbl, d + 1, 1);
				lbl.setPrefWidth(TEXTFIELD_WIDTH);
			}

		channelInfo.numChannelsProperty().set(newv.length < 4 ? 0 : (int)newv[3]);
		InvokeOnJavaFXApplicationThread.invoke(() -> dimensionInfo.getChildren().setAll(grid));
		if (channelInfo.numChannelsProperty().get() > 0) {
		  final Node channelInfoNode = channelInfo.getNode();
		  InvokeOnJavaFXApplicationThread.invoke(() -> channelInfoPane.getChildren().setAll(channelInfoNode));
		} else
		  InvokeOnJavaFXApplicationThread.invoke(() -> channelInfoPane.getChildren().clear());
	  }
	});

	content.getChildren().addAll(
			spatialInfo, new Separator(Orientation.HORIZONTAL),
			dimensionInfo, new Separator(Orientation.HORIZONTAL),
			channelInfoPane, new Separator(Orientation.HORIZONTAL));

	this.dataType.addListener((obs, oldv, newv) -> {
	  if (newv != null)
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				final ObservableList<Node> children = this.content.getChildren();
				children.removeAll(this.additionalMeta);
				this.additionalMeta.clear();
				switch (newv) {
					case RAW:
						children.add(this.rawMeta);
						this.additionalMeta.add(this.rawMeta);
						break;
					case LABEL:
						children.add(this.labelMeta);
						this.additionalMeta.add(this.labelMeta);
						break;
					default:
						break;
				}
			});
	});

	final GridPane rawMinMax = new GridPane();
	rawMinMax.getColumnConstraints().add(cc);
	rawMinMax.add(new Label("Intensity Range"), 0, 0);
	rawMinMax.add(this.min.getTextField(), 1, 0);
	rawMinMax.add(this.max.getTextField(), 2, 0);

	/* restrict to decimal numbers only */
	final EventHandler<KeyEvent> decimalCharFilter = event -> {
	  if (!".0123456789".contains(event.getCharacter())) {
		event.consume();
	  }
	};
	this.min.getTextField().addEventFilter(KeyEvent.KEY_TYPED, decimalCharFilter);
	this.max.getTextField().addEventFilter(KeyEvent.KEY_TYPED, decimalCharFilter);
	this.min.getTextField().setPromptText("min");
	this.max.getTextField().setPromptText("max");
	this.min.getTextField().setPrefWidth(TEXTFIELD_WIDTH);
	this.max.getTextField().setPrefWidth(TEXTFIELD_WIDTH);
	this.rawMeta.getChildren().add(rawMinMax);

  }

  public void listenOnResolution(final DoubleProperty x, final DoubleProperty y, final DoubleProperty z) {

	this.resolution.bindTo(x, y, z);
  }

  public void listenOnOffset(final DoubleProperty x, final DoubleProperty y, final DoubleProperty z) {

	this.offset.bindTo(x, y, z);
  }

  public void listenOnDimensions(final ObservableObjectValue<long[]> dimensions) {

	this.dimensionsProperty.bind(dimensions);
  }

  public void listenOnMinMax(final DoubleProperty min, final DoubleProperty max) {

	this.min.valueProperty().bindBidirectional(min);
	this.max.valueProperty().bindBidirectional(max);
  }

  public Node getPane() {

	return pane;
  }

  public enum TYPE {
	RAW, LABEL
  }

  public static class DoubleFilter implements UnaryOperator<Change> {

	@Override
	public Change apply(final Change t) {

	  final String input = t.getText();
	  return input.matches("\\d*(\\.\\d*)?") ? t : null;
	}
  }

  public double[] getResolution() {

	return asArray(
			resolution.textX().textProperty(),
			resolution.textY().textProperty(),
			resolution.textZ().textProperty()
	);
  }

  public double[] getOffset() {

	return asArray(offset.textX().textProperty(), offset.textY().textProperty(), offset.textZ().textProperty());
  }

  public double[] asArray(final ObservableStringValue... values) {

	return Arrays.stream(values).map(ObservableValue::getValue).mapToDouble(Double::parseDouble).toArray();
  }

  public double min() {

	return min.valueProperty().get();
  }

  public double max() {

	return max.valueProperty().get();
  }

  public void bindDataTypeTo(final ObjectProperty<TYPE> dataType) {

	this.dataType.bind(dataType);
  }

  public ChannelInformation channelInformation() {

	return this.channelInfo;
  }

  private static void addToGrid(final GridPane grid, final int startCol, final int row, final Node... nodes) {

		for (int col = startCol, i = 0; i < nodes.length; ++i, ++col) {
			grid.add(nodes[i], col, row);
		}
	}

  private static void formatLabels(final Label... labels) {

	for (final Label label : labels) {
	  label.setAlignment(Pos.BASELINE_CENTER);
	  label.setPrefWidth(TEXTFIELD_WIDTH);
	}
  }

  public Button getReverseButton() {

	return reverseButton;
  }

}
