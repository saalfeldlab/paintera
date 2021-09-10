package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporter;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterBinary;
import org.janelia.saalfeldlab.paintera.meshes.MeshExporterObj;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.util.fx.UIUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: make more uniform with SegmentMeshExporterDialog
public class MeshExporterDialog<T> extends Dialog<MeshExportResult<T>> {

  public enum FILETYPE {
	obj, binary
  }

  private final MeshInfo<T> meshInfo;

  private final TextField scale;

  private final TextField dirPath;

  private String filePath;

  private MeshExporter<T> meshExporter;

  private ComboBox<String> fileFormats;

  private final BooleanBinding isError;

  public MeshExporterDialog(final MeshInfo<T> meshInfo) {

	super();
	this.meshInfo = meshInfo;
	this.dirPath = new TextField();
	this.setTitle("Export mesh");
	this.isError = (Bindings.createBooleanBinding(() -> dirPath.getText().isEmpty(), dirPath.textProperty()));
	final MeshSettings settings = meshInfo.getMeshSettings();
	this.scale = new TextField(Integer.toString(settings.getFinestScaleLevel()));
	UIUtils.setNumericTextField(scale, settings.getNumScaleLevels() - 1);

	setResultConverter(button -> {
	  if (button.getButtonData().isCancelButton()) {
		return null;
	  }
	  return new MeshExportResult(
			  meshExporter,
			  filePath,
			  Integer.parseInt(scale.getText())
	  );
	});

	createDialog();
  }

  private void createDialog() {

	final VBox vbox = new VBox();
	final GridPane contents = new GridPane();

	int row = createCommonDialog(contents);

	final Button button = new Button("Browse");
	button.setOnAction(event -> {
	  final DirectoryChooser directoryChooser = new DirectoryChooser();
	  final File directory = directoryChooser.showDialog(contents.getScene().getWindow());
	  if (directory != null) {
		dirPath.setText(directory.getAbsolutePath());
		filePath = Paths.get(directory.getAbsolutePath(), "synapses" + meshInfo.getKey().toString()).toString();
		createMeshExporter(fileFormats.getSelectionModel().getSelectedItem());
	  }
	});

	contents.add(button, 2, row);
	++row;

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

	final List<String> typeNames = Stream.of(FILETYPE.values()).map(FILETYPE::name).collect(Collectors
			.toList());
	final ObservableList<String> options = FXCollections.observableArrayList(typeNames);
	fileFormats = new ComboBox<>(options);
	fileFormats.getSelectionModel().select(0);
	fileFormats.setMinWidth(0);
	fileFormats.setMaxWidth(Double.POSITIVE_INFINITY);
	contents.add(fileFormats, 1, row);
	fileFormats.maxWidth(300);
	GridPane.setFillWidth(fileFormats, true);
	GridPane.setHgrow(fileFormats, Priority.ALWAYS);

	++row;

	contents.add(new Label("Save to:"), 0, row);
	contents.add(dirPath, 1, row);

	this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
	this.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(this.isError);

	return row;
  }

  private void createMeshExporter(final String filetype) {

	switch (filetype) {
	case "binary":
	  meshExporter = new MeshExporterBinary<>();
	  break;
	case ".obj":
	default:
	  meshExporter = new MeshExporterObj<>();
	  break;
	}
  }
}
