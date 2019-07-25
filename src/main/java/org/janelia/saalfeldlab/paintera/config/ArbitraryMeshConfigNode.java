package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat;
import org.janelia.saalfeldlab.paintera.meshes.io.obj.ObjFormat;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;

import javax.tools.Tool;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ArbitraryMeshConfigNode extends TitledPane {

	private final ArbitraryMeshConfig config = new ArbitraryMeshConfig();

	private final CheckBox isVisibleCheckbox = new CheckBox();

	private final Group meshGroup = new Group();

	private final Map<ArbitraryMeshConfig.MeshInfo, Node> nodeMap = new HashMap<>();

	private final VBox meshConfigs = new VBox();

	private final Button addButton = new Button("+");

	public ArbitraryMeshConfigNode() {
		super("Triangle Meshes",null);
		isVisibleCheckbox.selectedProperty().bindBidirectional(config.isVisibleProperty());
		meshGroup.visibleProperty().bindBidirectional(isVisibleCheckbox.selectedProperty());
		this.config.getUnmodifiableMeshes().addListener((InvalidationListener) obs -> update());

		addButton.setOnAction(e -> {
			final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
			((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("_OK");
			((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("_Cancel");
			dialog.setHeaderText("Open mesh from file");
			final ObservableList<TriangleMeshFormat> formats = FXCollections.observableArrayList(TriangleMeshFormat.availableFormats());
			final Map<String, List<TriangleMeshFormat>> extensionFormatMapping = TriangleMeshFormat.extensionsToFormatMapping();
			ComboBox<TriangleMeshFormat> formatChoiceBox = new ComboBox<>(formats);
			formatChoiceBox.setPromptText("Format");

			formatChoiceBox.setCellFactory(param -> new ListCell<TriangleMeshFormat>() {
				@Override
				protected void updateItem(TriangleMeshFormat item, boolean empty) {
					super.updateItem(item, empty);
					if (item == null || empty) {
						setGraphic(null);
					} else {
						setText(String.format("%s %s", item.formatName(), item.knownExtensions()));
					}
				}
			});
			formatChoiceBox.setButtonCell(formatChoiceBox.getCellFactory().call(null));

			final Path lastPath = config.lastPathProperty().get();
			final TextField path = new TextField(null);
			path.setTooltip(new Tooltip());
			path.getTooltip().textProperty().bind(path.textProperty());
			path.textProperty().addListener((obs, oldv, newv) -> {
				if (newv == null)
					formatChoiceBox.setValue(null);
				else {
					final String[] split = newv.split("\\.");
					final String extension = split[split.length - 1];
					formatChoiceBox.setValue(Optional.ofNullable(extensionFormatMapping.get(extension)).map(l -> l.get(0)).orElse(null));
				}
			});
			path.setText(lastPath == null ? null : lastPath.toAbsolutePath().toString());
			final BooleanBinding isNull = path.textProperty().isNull();
			dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(isNull);
			path.setEditable(false);
			final Button browseButton = new Button("_Browse");
			final FileChooser chooser = new FileChooser();
			chooser.setInitialDirectory(lastPath == null ? null : lastPath.getParent().toFile());
			final ObjectProperty<Path> newPath = new SimpleObjectProperty<>();
			browseButton.setOnAction(ev -> {
				final File newFile = chooser.showOpenDialog(dialog.getOwner());
				if (newFile != null) {
					newPath.set(newFile.toPath());
					path.setText(newPath.get().toAbsolutePath().toString());
				}
			});
			final GridPane contents = new GridPane();
			contents.add(path, 0, 0);
			contents.add(browseButton, 1, 0);
			contents.add(formatChoiceBox, 1, 1);
			GridPane.setHgrow(path, Priority.ALWAYS);
			browseButton.setMaxWidth(120);
			formatChoiceBox.setMaxWidth(120);
			browseButton.setTooltip(new Tooltip("Browse for mesh file"));
			dialog.getDialogPane().setContent(contents);
			final Optional<ButtonType> button = dialog.showAndWait();
			if (ButtonType.OK.equals(button.orElse(ButtonType.CANCEL)) && newPath.get() != null) {
				try {
					config.lastPathProperty().set(newPath.get());
					config.addMesh(new ArbitraryMeshConfig.MeshInfo(newPath.get(), new ObjFormat()));
				} catch (final Exception ex){
					Exceptions.exceptionAlert(String.format("Unable to load mesh at path %s", newPath.getName()), ex);
				}
			}
		});

		setContent(meshConfigs);
		setExpanded(false);
		setPadding(Insets.EMPTY);
		final HBox hbox = new HBox(isVisibleCheckbox, new Label("Triangle Meshes"));
		hbox.setAlignment(Pos.CENTER);
		TitledPanes.graphicsOnly(this, hbox, addButton);

		meshConfigs.setPadding(Insets.EMPTY);

		update();
	}

	private void update() {
		final List<ArbitraryMeshConfig.MeshInfo> meshInfos = new ArrayList<>(this.config.getUnmodifiableMeshes());
		final Set<ArbitraryMeshConfig.MeshInfo> toRemoveFromNodeMap = nodeMap
				.keySet()
				.stream()
				.filter(meshInfos::contains)
				.collect(Collectors.toSet());
		toRemoveFromNodeMap.forEach(nodeMap::remove);

		for (final ArbitraryMeshConfig.MeshInfo meshInfo : meshInfos) {
			if (!nodeMap.containsKey(meshInfo)) {
				final TitledPane tp = TitledPanes.createCollapsed(null, null);
				final CheckBox visibleBox = new CheckBox();
				visibleBox.selectedProperty().bindBidirectional(meshInfo.isVisibleProperty());
				final TextField nameField = new TextField(meshInfo.nameProperty().get());
				nameField.textProperty().bindBidirectional(meshInfo.nameProperty());
				tp.setPadding(Insets.EMPTY);

				final double prefCellWidth = 50.0;

				final GridPane settingsGrid = new GridPane();
				final ColorPicker colorPicker = new ColorPicker();
				colorPicker.valueProperty().bindBidirectional(meshInfo.colorProperty());
				settingsGrid.add(Labels.withTooltip("Color"), 0, 0);
				settingsGrid.add(colorPicker, 3, 0);
				colorPicker.setPrefWidth(prefCellWidth);

				final NumberField<DoubleProperty> translationX = NumberField.doubleField(0.0, v -> true, ObjectField.SubmitOn.values());
				final NumberField<DoubleProperty> translationY = NumberField.doubleField(0.0, v -> true, ObjectField.SubmitOn.values());
				final NumberField<DoubleProperty> translationZ = NumberField.doubleField(0.0, v -> true, ObjectField.SubmitOn.values());
				translationX.valueProperty().bindBidirectional(meshInfo.translateXProperty());
				translationY.valueProperty().bindBidirectional(meshInfo.translateYProperty());
				translationZ.valueProperty().bindBidirectional(meshInfo.translateZProperty());
				settingsGrid.add(Labels.withTooltip("Translation"), 0, 1);
				settingsGrid.add(translationX.textField(), 1, 1);
				settingsGrid.add(translationY.textField(), 2, 1);
				settingsGrid.add(translationZ.textField(), 3, 1);
				translationX.textField().setTooltip(new Tooltip());
				translationY.textField().setTooltip(new Tooltip());
				translationZ.textField().setTooltip(new Tooltip());
				translationX.textField().getTooltip().textProperty().bind(translationX.textField().textProperty());
				translationY.textField().getTooltip().textProperty().bind(translationY.textField().textProperty());
				translationZ.textField().getTooltip().textProperty().bind(translationZ.textField().textProperty());
				translationX.textField().setPrefWidth(prefCellWidth);
				translationY.textField().setPrefWidth(prefCellWidth);
				translationZ.textField().setPrefWidth(prefCellWidth);


				final NumberField<DoubleProperty> scale = NumberField.doubleField(1.0, v -> v > 0.0, ObjectField.SubmitOn.values());
				scale.valueProperty().bindBidirectional(meshInfo.scaleProperty());
				settingsGrid.add(Labels.withTooltip("Scale"), 0, 2);
				settingsGrid.add(scale.textField(), 3, 2);
				scale.textField().setPrefWidth(prefCellWidth);

				final ChoiceBox<DrawMode> drawMode = new ChoiceBox<>(FXCollections.observableArrayList(DrawMode.values()));
				drawMode.valueProperty().bindBidirectional(meshInfo.drawModeProperty());
				settingsGrid.add(Labels.withTooltip("Draw Mode"), 0, 3);
				settingsGrid.add(drawMode, 3, 3);
				drawMode.setPrefWidth(prefCellWidth);
				drawMode.setTooltip(new Tooltip("Select draw mode"));

				final ChoiceBox<CullFace> cullFace = new ChoiceBox<>(FXCollections.observableArrayList(CullFace.values()));
				cullFace.valueProperty().bindBidirectional(meshInfo.cullFaceProperty());
				settingsGrid.add(Labels.withTooltip("Cull Face"), 0, 4);
				settingsGrid.add(cullFace, 3, 4);
				cullFace.setPrefWidth(prefCellWidth);
				cullFace.setTooltip(new Tooltip("Select cull face"));

				tp.setContent(settingsGrid);

				final Button removeButton = new Button("x");
				removeButton.setOnAction(e -> config.removeMesh(meshInfo));

				final HBox hbox = new HBox(visibleBox, nameField);
				hbox.setAlignment(Pos.CENTER);
				HBox.setHgrow(nameField, Priority.ALWAYS);
				TitledPanes.graphicsOnly(tp, hbox, removeButton);

				nodeMap.put(meshInfo, tp);
			}
		}

		final List<Node> meshes = meshInfos
				.stream()
				.map(ArbitraryMeshConfig.MeshInfo::getMeshView)
				.collect(Collectors.toList());

		final List<Node> configNodes = meshInfos
				.stream()
				.map(nodeMap::get)
				.collect(Collectors.toList());

		meshConfigs.getChildren().setAll(configNodes);
		meshGroup.getChildren().setAll(meshes);
	}

	public Node getMeshGroup() {
		return this.meshGroup;
	}

	public ArbitraryMeshConfig getConfig() {
		return this.config;
	}
}
