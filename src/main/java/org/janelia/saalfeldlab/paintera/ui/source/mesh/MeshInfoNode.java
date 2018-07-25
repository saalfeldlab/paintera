package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshInfoNode<T> implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshInfo<T> meshInfo;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final NumericSliderWithField opacitySlider;

	private final NumericSliderWithField inflateSlider;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty completedTasks = new SimpleIntegerProperty(0);

	private final Node contents;

	private final ComboBox<DrawMode> drawModeChoice;

	private final ComboBox<CullFace> cullFaceChoice;

	private final CheckBox hasIndividualSettings = new CheckBox("Individual Settings");

	private final CheckBox isVisible = new CheckBox("Is Visible");

	public MeshInfoNode(final MeshInfo<T> meshInfo)
	{
		super();
		this.meshInfo = meshInfo;
		LOG.debug("Initializing MeshinfoNode with draw mode {}", meshInfo.drawModeProperty());
		scaleSlider = new NumericSliderWithField(0, meshInfo.numScaleLevels() - 1, meshInfo.scaleLevelProperty().get
				());
		smoothingLambdaSlider = new NumericSliderWithField(0.0, 1.0, meshInfo.smoothingLambdaProperty().get());
		smoothingIterationsSlider = new NumericSliderWithField(0, 10, meshInfo.smoothingIterationsProperty().get());
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
		scaleSlider.slider().setValue(meshInfo.scaleLevelProperty().get());
		scaleSlider.slider().valueProperty().bindBidirectional(meshInfo.scaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional(meshInfo.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().bindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().bindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().bindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().bindBidirectional(meshInfo.cullFaceProperty());
		this.submittedTasks.bind(meshInfo.submittedTasksProperty());
		this.completedTasks.bind(meshInfo.completedTasksProperty());
		meshInfo.isManagedProperty().bind(this.hasIndividualSettings.selectedProperty().not());
		this.isVisible.selectedProperty().bindBidirectional(meshInfo.isVisibleProperty());
	}

	@Override
	public void unbind()
	{
		scaleSlider.slider().valueProperty().unbindBidirectional(meshInfo.scaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional(meshInfo.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().unbindBidirectional(meshInfo.opacityProperty());
		inflateSlider.slider().valueProperty().unbindBidirectional(meshInfo.inflateProperty());
		drawModeChoice.valueProperty().unbindBidirectional(meshInfo.drawModeProperty());
		cullFaceChoice.valueProperty().unbindBidirectional(meshInfo.cullFaceProperty());
		this.submittedTasks.unbind();
		this.completedTasks.unbind();
		meshInfo.isManagedProperty().unbind();
		this.isVisible.selectedProperty().unbindBidirectional(meshInfo.isVisibleProperty());
	}

	@Override
	public Node get()
	{
		return contents;
	}

	private Node createContents()
	{
		final VBox       vbox = new VBox();
		final TitledPane pane = new TitledPane(null, vbox);
		pane.setExpanded(false);

		final long[] fragments = meshInfo.meshManager().containedFragments(meshInfo.segmentId());

		final DoubleProperty progress = new SimpleDoubleProperty(0);
		submittedTasks.addListener((obs, oldv, newv) -> progress.set(submittedTasks.intValue() <= 0
		                                                             ? submittedTasks.intValue()
		                                                             : completedTasks.doubleValue() / submittedTasks
				                                                             .doubleValue()));
		completedTasks.addListener((obs, oldv, newv) -> progress.set(submittedTasks.intValue() <= 0
		                                                             ? submittedTasks.intValue()
		                                                             : completedTasks.doubleValue() / submittedTasks
				                                                             .doubleValue()));
		final StatusBar statusBar = new StatusBar();
		//		final ProgressBar statusBar = new ProgressBar( 0.0 );
		// TODO come up with better way to ensure proper size of this!
		statusBar.setMinWidth(200);
		statusBar.setMaxWidth(200);
		statusBar.setPrefWidth(200);
		statusBar.setText("" + meshInfo.segmentId());
		final Tooltip statusToolTip = new Tooltip();
		progress.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> statusToolTip.setText(
				statusBarToolTipText(submittedTasks.intValue(), completedTasks.intValue()))));
		submittedTasks.addListener(obs -> InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setStyle(
				progressBarStyleColor(submittedTasks.get()))));
		statusBar.setTooltip(statusToolTip);
		statusBar.setProgress(0.0);
		progress.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setProgress(
				Double.isFinite(newv.doubleValue()) ? newv.doubleValue() : 0.0)));
		InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setProgress(Math.max(progress.get(), 0.0)));
		pane.setGraphic(statusBar);
		//		pane.setGraphic( pb );

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
		hasIndividualSettings.setSelected(false);
		final GridPane settingsGrid = new GridPane();
		MeshPane.populateGridWithMeshSettings(
				settingsGrid,
				0,
				opacitySlider,
				scaleSlider,
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

	private static String statusBarToolTipText(final int submittedTasks, final int completedTasks)
	{

		return submittedTasks == MeshGenerator.RETRIEVING_RELEVANT_BLOCKS
		       ? "Retrieving blocks for mesh"
		       : submittedTasks == MeshGenerator.SUBMITTED_MESH_GENERATION_TASK
		         ? "Submitted mesh generation task"
		         : completedTasks + "/" + submittedTasks;
	}

	private static String progressBarStyleColor(final int submittedTasks)
	{

		if (submittedTasks == MeshGenerator.SUBMITTED_MESH_GENERATION_TASK)
		{
			LOG.debug("Submitted tasks={}, changing color to red", submittedTasks);
			return "-fx-accent: red; ";
		}

		if (submittedTasks == MeshGenerator.RETRIEVING_RELEVANT_BLOCKS)
		{
			LOG.debug("Submitted tasks={}, changing color to orange", submittedTasks);
			return "-fx-accent: orange; ";
		}

		LOG.debug("Submitted tasks={}, changing color to green", submittedTasks);
		return "-fx-accent: green; ";
	}

}
