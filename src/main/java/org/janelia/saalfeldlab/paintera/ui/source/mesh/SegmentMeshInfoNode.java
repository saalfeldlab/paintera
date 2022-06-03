package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.imglib2.type.label.LabelMultisetType;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshSettingsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

public class SegmentMeshInfoNode {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final DataSource<?, ?> source;

  private final SegmentMeshInfo meshInfo;

  private final Region contents;

  private final CheckBox hasIndividualSettings = new CheckBox("Individual Settings");

  private final BooleanProperty isManaged = new SimpleBooleanProperty();

  private final MeshSettingsController controller;

  {
	hasIndividualSettings.selectedProperty().addListener((obs, oldv, newv) -> isManaged.set(!newv));
	isManaged.addListener((obs, oldv, newv) -> hasIndividualSettings.setSelected(!newv));
	isManaged.set(!hasIndividualSettings.isSelected());
  }

  private final MeshProgressBar progressBar = new MeshProgressBar();

  private final MeshSettings settings;

  public SegmentMeshInfoNode(final DataSource<?, ?> source, final SegmentMeshInfo meshInfo) {

	this.source = source;
	this.meshInfo = meshInfo;
	this.settings = meshInfo.getMeshSettings();
	this.controller = new MeshSettingsController(this.settings);

	LOG.debug("Initializing MeshinfoNode with draw mode {}", settings.getDrawModeProperty());
	this.contents = createContents();
  }

  public Region getNode() {

	return contents;
  }

  private Region createContents() {

	final TitledPane pane = new TitledPane(null, null);
	pane.setExpanded(false);
	pane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
	  if (isExpanded && pane.getContent() == null) {
		InvokeOnJavaFXApplicationThread.invoke(() -> pane.setContent(getMeshInfoGrid()));
	  }
	});

	// TODO come up with better way to ensure proper size of this!
	progressBar.setPrefWidth(200);
	progressBar.setMinWidth(Control.USE_PREF_SIZE);
	progressBar.setMaxWidth(Control.USE_PREF_SIZE);
	progressBar.setText("" + meshInfo.segmentId());
	pane.setGraphic(progressBar);

	return pane;
  }

  private GridPane getMeshInfoGrid() {

	final var grid = new GridPane();

	final GridPane settingsGrid = controller.createContents(source.getDataType() instanceof LabelMultisetType);
	final VBox individualSettingsBox = new VBox(hasIndividualSettings, settingsGrid);
	individualSettingsBox.setSpacing(5.0);
	settingsGrid.visibleProperty().bind(hasIndividualSettings.selectedProperty());
	settingsGrid.managedProperty().bind(settingsGrid.visibleProperty());
	hasIndividualSettings.setSelected(!meshInfo.isManagedProperty().get());
	isManaged.bindBidirectional(meshInfo.isManagedProperty());
	progressBar.bindTo(meshInfo.meshProgress());

	final var ids = new InlineCssTextArea(Arrays.toString(meshInfo.containedFragments()));
	final var virtualPane = new VirtualizedScrollPane<>(ids);
	ids.setWrapText(true);

	final Label idsLabel = new Label("ids: ");
	idsLabel.setMinWidth(30);
	idsLabel.setMaxWidth(30);
	final Node spacer = NamedNode.bufferNode();
	final HBox idsHeader = new HBox(idsLabel, spacer);

	final Button exportMeshButton = new Button("Export");
	exportMeshButton.setOnAction(event -> {
	  final SegmentMeshExporterDialog<Long> exportDialog = new SegmentMeshExporterDialog<>(meshInfo);
	  final Optional<SegmentMeshExportResult<Long>> result = exportDialog.showAndWait();
	  if (result.isPresent()) {
		final SegmentMeshExportResult<Long> parameters = result.get();
		parameters.getMeshExporter().exportMesh(
				meshInfo.meshManager().getGetBlockListForLongKey(),
				meshInfo.meshManager().getGetMeshForLongKey(),
				parameters.getSegmentId()[0],
				parameters.getScale(),
				parameters.getFilePaths()[0]);
	  }
	});

	grid.add(idsHeader, 0, 0);
	GridPane.setValignment(idsHeader, VPos.CENTER);
	grid.add(virtualPane, 1, 0);
	GridPane.setHgrow(virtualPane, Priority.ALWAYS);
	grid.add(exportMeshButton, 0, 1, 2, 1);
	grid.add(individualSettingsBox, 0, 2, 2, 1);

	return grid;
  }
}
