package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.controlsfx.control.CheckListView;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporter;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterBinary;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MeshExporterDialog<K> extends Dialog<MeshExportResult<K>> {


	public enum MeshFileFormat {
		Obj, Binary;
	}

	final int LIST_CELL_HEIGHT = 25;

	protected final Spinner<Integer> scale;

	private final TextField filePathField;

	private final TextField meshFileNameField;

	private MeshExporter<K> meshExporter;

	private ComboBox<MeshFileFormat> fileFormats;

	private CheckListView<K> checkListView;
	private CheckBox selectAll;
	private final BooleanBinding isError;

	private static String previousFilePath = null;

	public MeshExporterDialog(MeshInfo<K> meshInfo) {
		super();
		PainteraAlerts.initAppDialog(this);
		this.filePathField = new TextField();
		if (previousFilePath != null)
			filePathField.setText(previousFilePath);
		this.meshFileNameField = new TextField();
		this.setTitle("Export Mesh ");
		this.isError = (Bindings.createBooleanBinding(() -> filePathField.getText().isEmpty() || meshFileNameField.getText().isEmpty() || pathExists(), filePathField.textProperty(), meshFileNameField.textProperty()));
		final K meshKey = meshInfo.getKey();
		final MeshSettings settings = meshInfo.getManager().getManagedSettings().getMeshSettings(meshKey);
		this.scale = new Spinner<>(0, settings.getNumScaleLevels() - 1, settings.getFinestScaleLevel());
		this.scale.setEditable(true);

		setResultConverter(button -> {
			if (button.getButtonData().isCancelButton()) {
				return null;
			}
			previousFilePath = filePathField.getText();

			return new MeshExportResult<>(
					meshExporter,
					resolveFilePath(),
					scale.getValue(),
					meshKey
			);
		});

		createDialog();
	}

	public MeshExporterDialog(ObservableList<MeshInfo<K>> meshInfoList) {

		super();
		this.filePathField = new TextField();
		if (previousFilePath != null)
			filePathField.setText(previousFilePath);
		this.meshFileNameField = new TextField();
		this.setTitle("Export mesh ");
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
								|| meshFileNameField.getText().isEmpty()
								|| checkListView.getItems().isEmpty()
								|| pathExists(),
				meshFileNameField.textProperty(),
				filePathField.textProperty(),
				checkListView.itemsProperty()
		));

		int minCommonScaleLevels = Integer.MAX_VALUE;
		int minCommonScale = Integer.MAX_VALUE;
		final ObservableList<K> ids = FXCollections.observableArrayList();
		for (int i = 0; i < meshInfoList.size(); i++) {
			final MeshInfo<K> info = meshInfoList.get(i);
			final MeshSettings settings = info.getMeshSettings();
			ids.add(info.getKey());

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

			return new MeshExportResult<>(
					meshExporter,
					resolveFilePath(),
					scale.getValue(),
					checkListView.getCheckModel().getCheckedItems()
			);
		});

		createMultiKeyDialog(ids);


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

	private void createMultiKeyDialog(final ObservableList<K> keys) {

		final VBox vbox = new VBox();
		final GridPane contents = new GridPane();
		int row = createCommonDialog(contents);

		checkListView.setItems(keys);
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
