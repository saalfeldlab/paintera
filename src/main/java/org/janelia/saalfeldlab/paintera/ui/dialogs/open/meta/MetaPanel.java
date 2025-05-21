package org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta;

import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import kotlin.Pair;
import org.janelia.saalfeldlab.fx.Buttons;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;
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

	private final HashSet<Node> additionalMeta = new HashSet<>();

	private final SimpleObjectProperty<TYPE> dataType = new SimpleObjectProperty<>(null);

	private final ObjectBinding<MetadataState> metadataStateBinding;

	private final SimpleObjectProperty<long[]> dimensionsProperty = new SimpleObjectProperty<>(null);

	private final Button reverseButton = Buttons.withTooltip("_Reverse", "Reverse array attributes", e -> {
	});

	private final ChannelInformation channelInfo = new ChannelInformation();

	public MetaPanel(final OpenSourceState openSourceState) {

		this.metadataStateBinding = openSourceState.getMetadataStateBinding();
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
		metadataStateBinding.addListener((obs, oldv, metadataState) -> {
			if (metadataState != null) {
				final HashMap<String, Axis> axisMap = new HashMap<>();
				metadataState.getSpatialAxes().keySet().forEach(axis -> axisMap.put(axis.getName(), axis));

				final Function<String, String> dimensionLabel = axis -> {
					final String lowerAxis = axis.toLowerCase();
					String label = axis;
					if (axisMap.containsKey(lowerAxis))
						label += "(" + axisMap.get(lowerAxis).getUnit() + ")";
					return label;
				};

				InvokeOnJavaFXApplicationThread.invoke(() -> {
					xLabel.textProperty().set(dimensionLabel.apply(X_STRING));
					yLabel.textProperty().set(dimensionLabel.apply(Y_STRING));
					zLabel.textProperty().set(dimensionLabel.apply(Z_STRING));
				});
			} else {
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					xLabel.textProperty().set(X_STRING);
					yLabel.textProperty().set(Y_STRING);
					zLabel.textProperty().set(Z_STRING);
				});
			}
		});

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
		addToGrid(spatialInfo, 0, 2, new Label("Offset (physical)"), offset.textX(), offset.textY(), offset.textZ());
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
				final var metadataState = metadataStateBinding.get();
				final Axis[] axes = metadataState != null ? MetadataUtils.getAxes(metadataState) : null;
				for (int d = 0; d < newv.length; ++d) {
					final var text = axes != null ? axes[d].getName() : "" + d;
					labels[d].setText(text);
					final TextField lbl = new TextField("" + newv[d]);
					lbl.setEditable(false);
					grid.add(labels[d], d + 1, 0);
					grid.add(lbl, d + 1, 1);
					lbl.setPrefWidth(TEXTFIELD_WIDTH);
				}

				final Pair<Axis, Integer> channelAxis = metadataState != null ? metadataState.getChannelAxis() : null;
				final Integer channelIdx;
				if (channelAxis != null)
					channelIdx = channelAxis.getSecond();
				else if (newv.length < 4)
					channelIdx = null;
				else
					channelIdx = 3;
				final int numChannels = channelIdx != null ? (int)newv[channelIdx] : 0;
				channelInfo.numChannelsProperty().set(numChannels);
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

		listenOnDimensions(openSourceState.getDimensionsBinding());
		listenOnResolution(openSourceState.getResolutionProperty());
		listenOnOffset(openSourceState.getTranslationProperty());
		listenOnMinMax(openSourceState.getMinIntensityProperty(), openSourceState.getMaxIntensityProperty());

		reverseButton.setOnAction(e -> {
			openSourceState.getResolutionProperty().set(reverse(getResolution()));
			openSourceState.getTranslationProperty().set(reverse(getOffset()));
		});
	}

	private static double[] reverse(final double[] array) {

		final double[] reversed = new double[array.length];
		for (int i = 0; i < array.length; ++i) {
			reversed[i] = array[array.length - 1 - i];
		}
		return reversed;
	}

	private void listenOnResolution(final ObservableValue<double[]> resolution) {

		/* Complicated, but needed to bidirectionally bind an array to individual properties*/
		bindSpatialInformation(this.resolution, resolution);
	}

	public void listenOnOffset(final ObservableValue<double[]> offset) {

		bindSpatialInformation(this.offset, offset);
	}

	private void bindSpatialInformation(final SpatialInformation spatial, final ObservableValue<double[]> observables) {

		/* Complicated, but needed to bidirectionally bind an array to individual properties*/

		final var props = new SimpleDoubleProperty[]{
				new SimpleDoubleProperty(),
				new SimpleDoubleProperty(),
				new SimpleDoubleProperty()
		};
		spatial.bindTo(props[0], props[1], props[2]);

		observables.subscribe(it -> {
			final double[] res;
			if (it != null)
				res = it;
			else
				res = new double[]{1.0, 1.0, 1.0};

			props[0].setValue(res[0]);
			props[1].setValue(res[1]);
			props[2].setValue(res[2]);
		});

		for (int i = 0; i < props.length; i++) {
			final SimpleDoubleProperty prop = props[i];
			int finalI = i;
			prop.subscribe(it -> {
				final double[] res = observables.getValue();
				if (res != null)
					res[finalI] = it.doubleValue();
			});
		}
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
