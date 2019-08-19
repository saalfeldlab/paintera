package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;

public class MeshInfoNode<T> implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshInfo<T> meshInfo;

	private final NumericSliderWithField preferredScaleLevelSlider;

	private final NumericSliderWithField highestScaleLevelSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final NumericSliderWithField opacitySlider;

	private final NumericSliderWithField inflateSlider;

	private final Node contents;

	private final ComboBox<DrawMode> drawModeChoice;

	private final ComboBox<CullFace> cullFaceChoice;

	private final CheckBox hasIndividualSettings = new CheckBox("Individual Settings");

	private final CheckBox isVisible = new CheckBox("Is Visible");

	private final MeshProgressBar progressBar = new MeshProgressBar();

	public MeshInfoNode(final MeshInfo<T> meshInfo)
	{
		super();
		this.meshInfo = meshInfo;
		LOG.debug("Initializing MeshinfoNode with draw mode {}", meshInfo.drawModeProperty());
		preferredScaleLevelSlider = new NumericSliderWithField(0, meshInfo.numScaleLevels() - 1, meshInfo.preferredScaleLevelProperty().get());
		highestScaleLevelSlider = new NumericSliderWithField(0, meshInfo.numScaleLevels() - 1, meshInfo.highestScaleLevelProperty().get());
		smoothingLambdaSlider = new NumericSliderWithField(0.0, 1.0, meshInfo.smoothingLambdaProperty().get());
		smoothingIterationsSlider = new NumericSliderWithField(0, 10, meshInfo.smoothingIterationsProperty().get());
		this.opacitySlider = new NumericSliderWithField(0, 1.0, meshInfo.opacityProperty().get());
		this.inflateSlider = new NumericSliderWithField(0.5, 2.0, meshInfo.inflateProperty().get());

		this.drawModeChoice = new ComboBox<>(FXCollections.observableArrayList(DrawMode.values()));
		this.drawModeChoice.setValue(meshInfo.drawModeProperty().get());

		this.cullFaceChoice = new ComboBox<>(FXCollections.observableArrayList(CullFace.values()));
		this.cullFaceChoice.setValue(meshInfo.cullFaceProperty().get());

		this.contents = createContents();

		MeshPane.setPreferredAndHighestScaleLevelSliderListeners(
				preferredScaleLevelSlider.slider(),
				highestScaleLevelSlider.slider()
			);
	}

	@Override
	public void bind()
	{
		LOG.debug("Binding to {}", meshInfo);
		preferredScaleLevelSlider.slider().setValue(meshInfo.preferredScaleLevelProperty().get());
		preferredScaleLevelSlider.slider().valueProperty().bindBidirectional(meshInfo.preferredScaleLevelProperty());
		highestScaleLevelSlider.slider().setValue(meshInfo.highestScaleLevelProperty().get());
		highestScaleLevelSlider.slider().valueProperty().bindBidirectional(meshInfo.highestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().bindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().bindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().bindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().bindBidirectional(meshInfo.cullFaceProperty());
		meshInfo.isManagedProperty().bind(this.hasIndividualSettings.selectedProperty().not());
		isVisible.selectedProperty().bindBidirectional(meshInfo.isVisibleProperty());

		if (meshInfo.numPendingTasksProperty() != null && meshInfo.numCompletedTasksProperty() != null)
		{
			progressBar.numPendingTasksProperty().bind(meshInfo.numPendingTasksProperty());
			progressBar.numCompletedTasksProperty().bind(meshInfo.numCompletedTasksProperty());
		}
	}

	@Override
	public void unbind()
	{
		preferredScaleLevelSlider.slider().valueProperty().unbindBidirectional(meshInfo.preferredScaleLevelProperty());
		highestScaleLevelSlider.slider().valueProperty().unbindBidirectional(meshInfo.highestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().unbindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().unbindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().unbindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().unbindBidirectional(meshInfo.cullFaceProperty());
		meshInfo.isManagedProperty().unbind();
		isVisible.selectedProperty().unbindBidirectional(meshInfo.isVisibleProperty());

		progressBar.numPendingTasksProperty().unbind();
		progressBar.numCompletedTasksProperty().unbind();
	}

	@Override
	public Node get()
	{
		return contents;
	}

	private Node createContents()
	{
		final VBox vbox = new VBox();
		vbox.setSpacing(5.0);

		final TitledPane pane = new TitledPane(null, vbox);
		pane.setExpanded(false);

		final long[] fragments = meshInfo.meshManager().containedFragments(meshInfo.segmentId());

		// TODO come up with better way to ensure proper size of this!
		progressBar.setPrefWidth(200);
		progressBar.setMinWidth(Control.USE_PREF_SIZE);
		progressBar.setMaxWidth(Control.USE_PREF_SIZE);
		progressBar.setText("" + meshInfo.segmentId());
		pane.setGraphic(progressBar);

		final Button exportMeshButton = new Button("Export");
		exportMeshButton.setOnAction(event -> {
			final MeshExporterDialog<T>     exportDialog = new MeshExporterDialog<>(meshInfo);
			final Optional<ExportResult<T>> result       = exportDialog.showAndWait();
			if (result.isPresent())
			{
				final ExportResult<T> parameters = result.get();
				parameters.getMeshExporter().exportMesh(
						meshInfo.meshManager().blockListCache(),
						meshInfo.meshManager().meshCache(),
						meshInfo.meshManager().unmodifiableMeshMap().get(parameters.getSegmentId()[0]).getId(),
						parameters.getScale(),
						parameters.getFilePaths()[0]
					);
			}
		});

		final Label   ids       = new Label(Arrays.toString(fragments));
		final Label   idsLabel  = new Label("ids: ");
		final Tooltip idToolTip = new Tooltip();
		ids.setTooltip(idToolTip);
		idToolTip.textProperty().bind(ids.textProperty());
		idsLabel.setMinWidth(30);
		idsLabel.setMaxWidth(30);
		final Region spacer = new Region();
		final HBox   idsRow = new HBox(idsLabel, spacer, ids);
		HBox.setHgrow(ids, Priority.ALWAYS);
		HBox.setHgrow(spacer, Priority.ALWAYS);

		final VBox individualSettingsBox = new VBox(hasIndividualSettings);
		individualSettingsBox.setSpacing(5.0);
		hasIndividualSettings.setSelected(false);
		final GridPane settingsGrid = new GridPane();
		MeshPane.populateGridWithMeshSettings(
				settingsGrid,
				0,
				opacitySlider,
				preferredScaleLevelSlider,
				highestScaleLevelSlider,
				smoothingLambdaSlider,
				smoothingIterationsSlider,
				inflateSlider,
				drawModeChoice,
				cullFaceChoice
		                                     );
		hasIndividualSettings.selectedProperty().addListener((obs, oldv, newv) -> {
			if (newv)
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> individualSettingsBox.getChildren().setAll(
						hasIndividualSettings,
						isVisible,
						settingsGrid
				                                                                                       ));
			}
			else
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> individualSettingsBox.getChildren().setAll(
						hasIndividualSettings));
			}
		});
		final boolean isManagedCurrent = meshInfo.isManagedProperty().get();
		hasIndividualSettings.setSelected(!isManagedCurrent);
		meshInfo.isManagedProperty().bind(hasIndividualSettings.selectedProperty().not());

		vbox.getChildren().addAll(idsRow, exportMeshButton, individualSettingsBox);

		return pane;
	}
}
