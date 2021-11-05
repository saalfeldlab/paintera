package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import net.imglib2.type.label.LabelMultisetType;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
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

  private final CheckBox visibleCheckBox;

  private final NumericSliderWithField levelOfDetailSlider;

  private final NumericSliderWithField coarsestScaleLevelSlider;

  private final NumericSliderWithField finestScaleLevelSlider;

  private final NumericSliderWithField smoothingLambdaSlider;

  private final NumericSliderWithField smoothingIterationsSlider;

  private final NumericSliderWithField minLabelRatioSlider;

  private final NumericSliderWithField opacitySlider;

  private final NumericSliderWithField inflateSlider;

  private final Node contents;

  private final ComboBox<DrawMode> drawModeChoice;

  private final ComboBox<CullFace> cullFaceChoice;

  private final CheckBox hasIndividualSettings = new CheckBox("Individual Settings");

  private final BooleanProperty isManaged = new SimpleBooleanProperty();

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

	LOG.debug("Initializing MeshinfoNode with draw mode {}", settings.getDrawModeProperty());
	visibleCheckBox = new CheckBox();
	levelOfDetailSlider = new NumericSliderWithField(MeshSettings.Defaults.Values.getMinLevelOfDetail(), MeshSettings.Defaults.Values.getMaxLevelOfDetail(),
			settings.getLevelOfDetail());
	coarsestScaleLevelSlider = new NumericSliderWithField(0, settings.getNumScaleLevels() - 1, settings.getCoarsestScaleLevel());
	finestScaleLevelSlider = new NumericSliderWithField(0, settings.getNumScaleLevels() - 1, settings.getFinestScaleLevel());
	smoothingLambdaSlider = new NumericSliderWithField(0.0, 1.0, settings.getSmoothingLambda());
	smoothingIterationsSlider = new NumericSliderWithField(0, 10, settings.getSmoothingIterations());
	minLabelRatioSlider = new NumericSliderWithField(0.0, 1.0, settings.getMinLabelRatio());
	this.opacitySlider = new NumericSliderWithField(0, 1.0, settings.getOpacity());
	this.inflateSlider = new NumericSliderWithField(0.5, 2.0, settings.getInflate());

	this.drawModeChoice = new ComboBox<>(FXCollections.observableArrayList(DrawMode.values()));
	this.drawModeChoice.setValue(settings.getDrawMode());

	this.cullFaceChoice = new ComboBox<>(FXCollections.observableArrayList(CullFace.values()));
	this.cullFaceChoice.setValue(settings.getCullFace());

	bindSlidersToSettings();

	this.contents = createContents();
  }

  private void bindSlidersToSettings() {

	LOG.debug("Binding to {}", settings);
	levelOfDetailSlider.getSlider().valueProperty().bindBidirectional(settings.getLevelOfDetailProperty());
	coarsestScaleLevelSlider.getSlider().valueProperty().bindBidirectional(settings.getCoarsestScaleLevelProperty());
	finestScaleLevelSlider.getSlider().valueProperty().bindBidirectional(settings.getFinestScaleLevelProperty());
	smoothingLambdaSlider.getSlider().valueProperty().bindBidirectional(settings.getSmoothingLambdaProperty());
	smoothingIterationsSlider.getSlider().valueProperty().bindBidirectional(settings.getSmoothingIterationsProperty());
	minLabelRatioSlider.getSlider().valueProperty().bindBidirectional(settings.getMinLabelRatioProperty());
	opacitySlider.getSlider().valueProperty().bindBidirectional(settings.getOpacityProperty());
	inflateSlider.getSlider().valueProperty().bindBidirectional(settings.getInflateProperty());
	drawModeChoice.valueProperty().bindBidirectional(settings.getDrawModeProperty());
	cullFaceChoice.valueProperty().bindBidirectional(settings.getCullFaceProperty());
	visibleCheckBox.selectedProperty().bindBidirectional(settings.isVisibleProperty());

  }

  public Node get() {

	return contents;
  }

  private Node createContents() {

	final VBox vbox = new VBox();
	vbox.setSpacing(5.0);

	final TitledPane pane = new TitledPane(null, vbox);
	pane.setExpanded(false);

	final long[] fragments = meshInfo.containedFragments();

	// TODO come up with better way to ensure proper size of this!
	progressBar.setPrefWidth(200);
	progressBar.setMinWidth(Control.USE_PREF_SIZE);
	progressBar.setMaxWidth(Control.USE_PREF_SIZE);
	progressBar.setText("" + meshInfo.segmentId());
	pane.setGraphic(progressBar);

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

	final Label ids = new Label(Arrays.toString(fragments));
	final Label idsLabel = new Label("ids: ");
	final Tooltip idToolTip = new Tooltip();
	ids.setTooltip(idToolTip);
	idToolTip.textProperty().bind(ids.textProperty());
	idsLabel.setMinWidth(30);
	idsLabel.setMaxWidth(30);
	final Node spacer = NamedNode.bufferNode();
	final HBox idsRow = new HBox(idsLabel, spacer, ids);
	HBox.setHgrow(ids, Priority.ALWAYS);

	final GridPane settingsGrid = new MeshSettingsController(settings).createContents(source.getDataType() instanceof LabelMultisetType);
	final VBox individualSettingsBox = new VBox(hasIndividualSettings, settingsGrid);
	individualSettingsBox.setSpacing(5.0);
	settingsGrid.visibleProperty().bind(hasIndividualSettings.selectedProperty());
	settingsGrid.managedProperty().bind(settingsGrid.visibleProperty());
	hasIndividualSettings.setSelected(!meshInfo.isManagedProperty().get());
	isManaged.bindBidirectional(meshInfo.isManagedProperty());
	progressBar.bindTo(meshInfo.meshProgress());

	vbox.getChildren().addAll(idsRow, exportMeshButton, individualSettingsBox);

	return pane;
  }
}
