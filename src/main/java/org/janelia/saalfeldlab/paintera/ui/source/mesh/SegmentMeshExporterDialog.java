package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.controlsfx.control.CheckListView;
import org.janelia.saalfeldlab.paintera.meshes.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SegmentMeshExporterDialog<T> extends Dialog<SegmentMeshExportResult<T>> {


	public enum MeshFileFormat {
		Obj, Binary;
	}

	final int LIST_CELL_HEIGHT = 25;

	private final Spinner<Integer> scale;

	private final TextField filePathField;

	private final TextField meshFileNameField;

	private MeshExporter<T> meshExporter;

	private long[][] fragmentIds;

	private long[] segmentIds;

	private ComboBox<MeshFileFormat> fileFormats;

	private CheckListView<Long> checkListView;
	private CheckBox selectAll;
	private final BooleanBinding isError;

	private static String previousFilePath = null;

	public SegmentMeshExporterDialog(final SegmentMeshInfo meshInfo) {

		super();
		this.segmentIds = new long[]{meshInfo.segmentId()};
		this.fragmentIds = new long[][]{meshInfo.containedFragments()};
		this.filePathField = new TextField();
		if (previousFilePath != null)
			filePathField.setText(previousFilePath);
		this.meshFileNameField = new TextField();
		meshFileNameField.setText(meshInfo.segmentId().toString());
		this.setTitle("Export Mesh " + Arrays.toString(segmentIds));
		this.isError = (Bindings.createBooleanBinding(() -> filePathField.getText().isEmpty() || meshFileNameField.getText().isEmpty() || pathExists(), filePathField.textProperty(), meshFileNameField.textProperty()));
		final MeshSettings settings = meshInfo.getMeshSettings();
		this.scale = new Spinner<>(0, settings.getNumScaleLevels() - 1, settings.getFinestScaleLevel());
		this.scale.setEditable(true);

		setResultConverter(button -> {
			if (button.getButtonData().isCancelButton()) {
				return null;
			}
			previousFilePath = filePathField.getText();
			return new SegmentMeshExportResult<>(
					meshExporter,
					fragmentIds,
					segmentIds,
					scale.getValue(),
					resolveFilePath()
			);
		});

		createDialog();

	}

	private boolean pathExists() {

		return Path.of(resolveFilePath()).toFile().exists();
	}

	@NotNull
	private String resolveFilePath() {
		final String file = Path.of(filePathField.getText(), meshFileNameField.getText()).toString();
		final String path = file.replace("~", System.getProperty("user.home"));
		return path;
	}

	public SegmentMeshExporterDialog(ObservableList<SegmentMeshInfo> meshInfoList) {

		super();
		this.filePathField = new TextField();
		if (previousFilePath != null)
			filePathField.setText(previousFilePath);
		this.meshFileNameField = new TextField();
		this.setTitle("Export mesh ");
		this.segmentIds = new long[meshInfoList.size()];
		this.fragmentIds = new long[meshInfoList.size()][];
		this.checkListView = new CheckListView<>();
		this.selectAll = new CheckBox("Select All");
		selectAll.selectedProperty().addListener((obs, oldv, selected) -> {
			if (selected) {
				checkListView.getCheckModel().checkAll();
			} else {
				checkListView.getCheckModel().clearChecks();
			}
		});
		this.isError = (Bindings.createBooleanBinding(() ->
						filePathField.getText().isEmpty()
								|| checkListView.getItems().isEmpty()
								|| !pathExists(),
				filePathField.textProperty(),
				checkListView.itemsProperty()
		));

			int minCommonScaleLevels = Integer.MAX_VALUE;
		int minCommonScale = Integer.MAX_VALUE;
		final ObservableList<Long> ids = FXCollections.observableArrayList();
		for (int i = 0; i < meshInfoList.size(); i++) {
			final SegmentMeshInfo info = meshInfoList.get(i);
			this.segmentIds[i] = info.segmentId();
			this.fragmentIds[i] = info.containedFragments();
			final MeshSettings settings = info.getMeshSettings();
			ids.add(info.segmentId());

			if (minCommonScaleLevels > settings.getNumScaleLevels()) {
				minCommonScaleLevels = settings.getNumScaleLevels();
			}

			if (minCommonScale > settings.getFinestScaleLevel()) {
				minCommonScale = settings.getFinestScaleLevel();
			}
		}

		scale = new Spinner<>(minCommonScale, minCommonScaleLevels-1, minCommonScale);
		scale.setEditable(true);

		setResultConverter(button -> {
			if (button.getButtonData().isCancelButton()) {
				return null;
			}
			previousFilePath = filePathField.getText();
			return new SegmentMeshExportResult<>(
					meshExporter,
					fragmentIds,
					segmentIds,
					scale.getValue(),
					resolveFilePath()
			);
		});

		createMultiIdsDialog(ids);
	}

	private void createDialog() {

		final VBox vbox = new VBox();
		final GridPane contents = new GridPane();

		int row = createCommonDialog(contents);

		final Button browseButton = new Button("Browse");
		browseButton.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog(contents.getScene().getWindow());
			if (directory != null) {
				filePathField.setText(directory.getAbsolutePath());
			}
		});

		contents.add(browseButton, 2, row);
		++row;

		vbox.getChildren().add(contents);
		this.getDialogPane().setContent(vbox);
	}

	private void createMultiIdsDialog(final ObservableList<Long> ids) {

		final VBox vbox = new VBox();
		final GridPane contents = new GridPane();
		int row = createCommonDialog(contents);

		checkListView.setItems(ids);
		checkListView.getSelectionModel().selectAll();
		selectAll.setSelected(true);
		checkListView.prefHeightProperty().bind(Bindings.size(checkListView.itemsProperty().get()).multiply(LIST_CELL_HEIGHT));

		final Button button = new Button("Browse");
		button.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog(contents.getScene().getWindow());
			if (directory != null)
				filePathField.setText(directory.getAbsolutePath());
		});

		contents.add(button, 2, row);
		++row;

		vbox.getChildren().add(selectAll);
		vbox.getChildren().add(checkListView);
		vbox.getChildren().add(contents);
		this.getDialogPane().setContent(vbox);
	}

	private int createCommonDialog(final GridPane contents) {

		int row = 0;

		contents.add(new Label("Scale"), 0, row);
		contents.add(scale, 1, row);
		GridPane.setFillWidth(scale, true);
		++row;

		contents.add(new Label("Format"), 0, row);

		final List<String> typeNames = Stream.of(MeshFileFormat.values()).map(MeshFileFormat::name).collect(Collectors
				.toList());
		fileFormats = new ComboBox<>();
		fileFormats.getItems().addAll(MeshFileFormat.values());
		fileFormats.getSelectionModel().select(MeshFileFormat.Obj);
		createMeshExporter(MeshFileFormat.Obj);
		fileFormats.setMinWidth(0);
		fileFormats.setMaxWidth(Double.POSITIVE_INFINITY);
		fileFormats.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> createMeshExporter(newVal));
		contents.add(fileFormats, 1, row);
		fileFormats.maxWidth(300);
		GridPane.setFillWidth(fileFormats, true);
		GridPane.setHgrow(fileFormats, Priority.ALWAYS);

		++row;

		contents.add(new Label("Name "), 0, row);
		contents.add(meshFileNameField, 1, row++);
		contents.add(new Label("Save to "), 0, row);
		contents.add(filePathField, 1, row);

		this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		this.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(this.isError);

		return row;
	}

	private void createMeshExporter(final MeshFileFormat format) {

		switch (format) {
			case Binary:
				meshExporter = new MeshExporterBinary<>();
				break;
			case Obj:
				meshExporter = new MeshExporterObj<>();
				break;
		}
	}

}
