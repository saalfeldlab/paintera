package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import net.imglib2.type.label.LabelMultisetType;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.ObservableMeshProgress;
import org.janelia.saalfeldlab.paintera.state.LabelSourceStateMeshPaneNode;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

import static org.janelia.saalfeldlab.paintera.meshes.MeshSettings.Defaults.Values.getMaxLevelOfDetail;

public class MeshInfoNode<T> implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final MeshInfo<T> meshInfo;

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

	private final CheckBox isVisible = new CheckBox("Is Visible");

	private final MeshProgressBar progressBar = new MeshProgressBar();

	public MeshInfoNode(final DataSource<?, ?> source, final MeshInfo<T> meshInfo)
	{
		this.source = source;
		this.meshInfo = meshInfo;

		LOG.debug("Initializing MeshinfoNode with draw mode {}", meshInfo.drawModeProperty());
		levelOfDetailSlider = new NumericSliderWithField(MeshSettings.Defaults.Values.getMinLevelOfDetail(), MeshSettings.Defaults.Values.getMaxLevelOfDetail(), meshInfo.levelOfDetailProperty().get());
		coarsestScaleLevelSlider = new NumericSliderWithField(0, meshInfo.numScaleLevels() - 1, meshInfo.coarsestScaleLevelProperty().get());
		finestScaleLevelSlider = new NumericSliderWithField(0, meshInfo.numScaleLevels() - 1, meshInfo.finestScaleLevelProperty().get());
		smoothingLambdaSlider = new NumericSliderWithField(0.0, 1.0, meshInfo.smoothingLambdaProperty().get());
		smoothingIterationsSlider = new NumericSliderWithField(0, 10, meshInfo.smoothingIterationsProperty().get());
		minLabelRatioSlider = new NumericSliderWithField(0.0, 1.0, meshInfo.minLabelRatioProperty().get());
		this.opacitySlider = new NumericSliderWithField(0, 1.0, meshInfo.opacityProperty().get());
		this.inflateSlider = new NumericSliderWithField(0.5, 2.0, meshInfo.inflateProperty().get());

		this.drawModeChoice = new ComboBox<>(FXCollections.observableArrayList(DrawMode.values()));
		this.drawModeChoice.setValue(meshInfo.drawModeProperty().get());

		this.cullFaceChoice = new ComboBox<>(FXCollections.observableArrayList(CullFace.values()));
		this.cullFaceChoice.setValue(meshInfo.cullFaceProperty().get());

		this.contents = createContents();
	}

	@Override
	public void bind()
	{
		LOG.debug("Binding to {}", meshInfo);
		levelOfDetailSlider.slider().valueProperty().bindBidirectional(meshInfo.levelOfDetailProperty());
		coarsestScaleLevelSlider.slider().valueProperty().bindBidirectional(meshInfo.coarsestScaleLevelProperty());
		finestScaleLevelSlider.slider().valueProperty().bindBidirectional(meshInfo.finestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingIterationsProperty());
		minLabelRatioSlider.slider().valueProperty().bindBidirectional(meshInfo.minLabelRatioProperty());
		opacitySlider.slider().valueProperty().bindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().bindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().bindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().bindBidirectional(meshInfo.cullFaceProperty());
		meshInfo.isManagedProperty().bind(this.hasIndividualSettings.selectedProperty().not());
		isVisible.selectedProperty().bindBidirectional(meshInfo.isVisibleProperty());

		final ObservableMeshProgress meshProgress = meshInfo.meshProgress();
		if (meshInfo.meshProgress() != null)
			progressBar.bindTo(meshProgress);
	}

	@Override
	public void unbind()
	{
		levelOfDetailSlider.slider().valueProperty().unbindBidirectional(meshInfo.levelOfDetailProperty());
		coarsestScaleLevelSlider.slider().valueProperty().unbindBidirectional(meshInfo.coarsestScaleLevelProperty());
		finestScaleLevelSlider.slider().valueProperty().unbindBidirectional(meshInfo.finestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingIterationsProperty());
		minLabelRatioSlider.slider().valueProperty().unbindBidirectional(meshInfo.minLabelRatioProperty());
		opacitySlider.slider().valueProperty().unbindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().unbindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().unbindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().unbindBidirectional(meshInfo.cullFaceProperty());
		meshInfo.isManagedProperty().unbind();
		isVisible.selectedProperty().unbindBidirectional(meshInfo.isVisibleProperty());
		progressBar.unbind();
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
		LabelSourceStateMeshPaneNode.Companion.populateGridWithMeshSettings(
				source.getDataType() instanceof LabelMultisetType,
				settingsGrid,
				0,
				opacitySlider,
				levelOfDetailSlider,
				coarsestScaleLevelSlider,
				finestScaleLevelSlider,
				smoothingLambdaSlider,
				smoothingIterationsSlider,
				minLabelRatioSlider,
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
