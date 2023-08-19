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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SegmentMeshExporterDialog<T> extends Dialog<SegmentMeshExportResult<T>> {

	public enum MeshFileFormat {
		Obj, Binary
	}

	final int LIST_CELL_HEIGHT = 25;

	private final Spinner<Integer> scale;

	private final TextField filePath;

	private final String[] filePaths;

	private MeshExporter<T> meshExporter;

	private long[][] fragmentIds;

	private long[] segmentIds;

	private ComboBox<MeshFileFormat> fileFormats;

	private CheckListView<Long> checkListView;

	private final BooleanBinding isError;

	public SegmentMeshExporterDialog(final SegmentMeshInfo meshInfo) {

		super();
		this.segmentIds = new long[]{meshInfo.segmentId()};
		this.fragmentIds = new long[][]{meshInfo.containedFragments()};
		this.filePath = new TextField();
		this.filePaths = new String[]{""};
		this.setTitle("Export Mesh " + Arrays.toString(segmentIds));
		this.isError = (Bindings.createBooleanBinding(() -> filePath.getText().isEmpty() || !pathIsDirectory(), filePath.textProperty()));
		final MeshSettings settings = meshInfo.getMeshSettings();
		this.scale = new Spinner<>(0, settings.getNumScaleLevels() - 1, settings.getFinestScaleLevel());
		listenForFilePaths();

		setResultConverter(button -> {
			if (button.getButtonData().isCancelButton()) {
				return null;
			}
			return new SegmentMeshExportResult<>(
					meshExporter,
					fragmentIds,
					segmentIds,
					scale.getValue(),
					filePaths
			);
		});

		createDialog();

	}

	private boolean pathIsDirectory() {

		return Path.of(resolveFilePath()).toFile().isDirectory();
	}

	@NotNull
	private String resolveFilePath() {
		final String file = filePath.getText();
		final String path = file.replace("~", System.getProperty("user.home"));
		return path;
	}

	private void listenForFilePaths() {
		filePath.textProperty().addListener((obs, oldv, newv) -> {
			if (!isError.get())
				for (int i = 0; i < segmentIds.length; i++) {
					filePaths[i] = Paths.get(resolveFilePath(), "mesh" + segmentIds[i]).toAbsolutePath().toString();
				}
		});
	}

	public SegmentMeshExporterDialog(ObservableList<SegmentMeshInfo> meshInfoList) {

		super();
		this.filePath = new TextField();
		this.setTitle("Export mesh ");
		this.segmentIds = new long[meshInfoList.size()];
		this.fragmentIds = new long[meshInfoList.size()][];
		this.filePaths = new String[meshInfoList.size()];
		this.checkListView = new CheckListView<>();
		this.isError = (Bindings.createBooleanBinding(() ->
						filePath.getText().isEmpty()
								|| checkListView.getItems().isEmpty()
								|| !pathIsDirectory(),
				filePath.textProperty(),
				checkListView.itemsProperty()
		));
		listenForFilePaths();

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

		scale = new Spinner<>(minCommonScale, minCommonScaleLevels, 1);

		setResultConverter(button -> {
			if (button.getButtonData().isCancelButton()) {
				return null;
			}
			return new SegmentMeshExportResult<>(
					meshExporter,
					fragmentIds,
					segmentIds,
					scale.getValue(),
					filePaths
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
				filePath.setText(directory.getAbsolutePath());
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
		checkListView.prefHeightProperty().bind(Bindings.size(checkListView.itemsProperty().get()).multiply(
				LIST_CELL_HEIGHT));

		final Button button = new Button("Browse");
		button.setOnAction(event -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File directory = directoryChooser.showDialog(contents.getScene().getWindow());
			if (directory != null) {
				filePath.setText(directory.getAbsolutePath());

				// recover selected ids
				if (checkListView.getItems().isEmpty())
					return;

				final List<Long> selectedIds = new ArrayList<>();
				for (int i = 0; i < checkListView.getItems().size(); i++) {
					if (checkListView.getItemBooleanProperty(i).get())
						selectedIds.add(checkListView.getItems().get(i));
				}

				segmentIds = selectedIds.stream().mapToLong(l -> l).toArray();

				for (int i = 0; i < selectedIds.size(); i++) {
					filePaths[i] = Paths.get(directory.getAbsolutePath(), "mesh" + selectedIds.get(i)).toString();
				}
			}
		});

		contents.add(button, 2, row);
		++row;

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

		contents.add(new Label("Save to "), 0, row);
		contents.add(filePath, 1, row);

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
