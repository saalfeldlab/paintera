package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

public class MeshPane implements BindUnbindAndNodeSupplier, ListChangeListener<MeshInfo<TLongHashSet>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshManager<Long, TLongHashSet> manager;

	private final MeshInfos<TLongHashSet> meshInfos;

	private final int numScaleLevels;

	private final NumericSliderWithField preferredScaleLevelSlider;

	private final NumericSliderWithField highestScaleLevelSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final NumericSliderWithField opacitySlider;

	private final NumericSliderWithField inflateSlider;

	final ObservableMap<MeshInfo<TLongHashSet>, MeshInfoNode<TLongHashSet>> infoNodesCache = FXCollections
			.observableHashMap();

	final ObservableList<MeshInfoNode<TLongHashSet>> infoNodes = FXCollections.observableArrayList();

	private final VBox managerSettingsPane;

	private final VBox meshesBox = new VBox(5.0);

	private final TitledPane meshesPane = new TitledPane(null, meshesBox);

	private final CheckBox isMeshListEnabledCheckBox = new CheckBox();

	private final ComboBox<DrawMode> drawModeChoice;

	private final ComboBox<CullFace> cullFaceChoice;

	private final CheckBox isVisibleCheckBox = new CheckBox("Is Visible");

	private final MeshProgressBar totalProgressBar = new MeshProgressBar();

	private boolean isBound = false;

	public MeshPane(final MeshManager<Long, TLongHashSet> manager, final MeshInfos<TLongHashSet> meshInfos, final int
			numScaleLevels)
	{
		super();
		this.manager = manager;
		this.meshInfos = meshInfos;
		this.numScaleLevels = numScaleLevels;

		preferredScaleLevelSlider = new NumericSliderWithField(
				0,
				this.numScaleLevels - 1,
				meshInfos.meshSettings().getGlobalSettings().preferredScaleLevelProperty().get()
		);
		highestScaleLevelSlider = new NumericSliderWithField(
				0,
				this.numScaleLevels - 1,
				meshInfos.meshSettings().getGlobalSettings().highestScaleLevelProperty().get()
		);
		smoothingLambdaSlider = new NumericSliderWithField(0.0, 1.0, 0.5);
		smoothingIterationsSlider = new NumericSliderWithField(0, 10, 5);
		this.opacitySlider = new NumericSliderWithField(
				0.0,
				1.0,
				meshInfos.meshSettings().getGlobalSettings().opacityProperty().get()
		);
		this.inflateSlider = new NumericSliderWithField(
				0.5,
				2.0,
				meshInfos.meshSettings().getGlobalSettings().inflateProperty().get()
		);

		this.drawModeChoice = new ComboBox<>(FXCollections.observableArrayList(DrawMode.values()));
		this.drawModeChoice.setValue(meshInfos.meshSettings().getGlobalSettings().drawModeProperty().get());

		this.cullFaceChoice = new ComboBox<>(FXCollections.observableArrayList(CullFace.values()));
		this.cullFaceChoice.setValue(meshInfos.meshSettings().getGlobalSettings().cullFaceProperty().get());

		this.meshesPane.setExpanded(false);
		final InvalidationListener isMeshListEnabledListener = obs -> {
			// the order of setCollapsible and setExpanded is important here
			if (isMeshListEnabledCheckBox.isSelected())
			{
				this.meshesPane.setCollapsible(true);
				this.meshesPane.setExpanded(true);
			}
			else
			{
				this.meshesPane.setExpanded(false);
				this.meshesPane.setCollapsible(false);
			}
		};
		this.isMeshListEnabledCheckBox.selectedProperty().addListener(isMeshListEnabledListener);
		isMeshListEnabledListener.invalidated(null);

		managerSettingsPane = new VBox(setupManagerSliderGrid(), meshesPane);

		this.meshInfos.readOnlyInfos().addListener(this);

		setPreferredAndHighestScaleLevelSliderListeners(
				this.preferredScaleLevelSlider.slider(),
				this.highestScaleLevelSlider.slider()
			);

		totalProgressBar.setPrefWidth(200);
		totalProgressBar.setMinWidth(Control.USE_PREF_SIZE);
		totalProgressBar.setMaxWidth(Control.USE_PREF_SIZE);
		totalProgressBar.setText("Mesh List");

		final HBox meshesPaneGraphic = new HBox(10.0, isMeshListEnabledCheckBox, totalProgressBar);
		meshesPaneGraphic.setFillHeight(true);
		meshesPaneGraphic.setAlignment(Pos.CENTER_LEFT);
		this.meshesPane.setGraphic(meshesPaneGraphic);
	}

	@Override
	public Node get()
	{
		final TitledPane pane = new TitledPane("Meshes", this.managerSettingsPane);
		pane.setExpanded(false);
		return pane;
	}

	@Override
	public void bind()
	{
		final ManagedMeshSettings meshSettings   = this.meshInfos.meshSettings();
		final MeshSettings        globalSettings = meshSettings.getGlobalSettings();
		isBound = true;
		this.meshInfos.readOnlyInfos().addListener(this);
		preferredScaleLevelSlider.slider().valueProperty().bindBidirectional(globalSettings.preferredScaleLevelProperty());
		highestScaleLevelSlider.slider().valueProperty().bindBidirectional(globalSettings.highestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional(globalSettings.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional(globalSettings
				.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().bindBidirectional(globalSettings.opacityProperty());
		inflateSlider.slider().valueProperty().bindBidirectional(globalSettings.inflateProperty());
		drawModeChoice.valueProperty().bindBidirectional(globalSettings.drawModeProperty());
		cullFaceChoice.valueProperty().bindBidirectional(globalSettings.cullFaceProperty());
		new ArrayList<>(this.infoNodes).forEach(MeshInfoNode::bind);
		this.isVisibleCheckBox.selectedProperty().bindBidirectional(globalSettings.isVisibleProperty());
		this.isMeshListEnabledCheckBox.selectedProperty().bindBidirectional(meshSettings.isMeshListEnabledProperty());
	}

	@Override
	public void unbind()
	{
		isBound = false;
		this.meshInfos.readOnlyInfos().removeListener(this);
		final ManagedMeshSettings meshSettings   = this.meshInfos.meshSettings();
		final MeshSettings        globalSettings = meshSettings.getGlobalSettings();
		preferredScaleLevelSlider.slider().valueProperty().unbindBidirectional(globalSettings.preferredScaleLevelProperty());
		highestScaleLevelSlider.slider().valueProperty().unbindBidirectional(globalSettings.highestScaleLevelProperty());
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional(globalSettings.smoothingLambdaProperty());
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional(globalSettings
				.smoothingIterationsProperty());
		opacitySlider.slider().valueProperty().unbindBidirectional(globalSettings.opacityProperty());
		inflateSlider.slider().valueProperty().unbindBidirectional(globalSettings.inflateProperty());
		drawModeChoice.valueProperty().unbindBidirectional(globalSettings.drawModeProperty());
		cullFaceChoice.valueProperty().unbindBidirectional(globalSettings.cullFaceProperty());
		new ArrayList<>(this.infoNodes).forEach(MeshInfoNode::unbind);
		this.isVisibleCheckBox.selectedProperty().unbindBidirectional(globalSettings.isVisibleProperty());
		this.isMeshListEnabledCheckBox.selectedProperty().unbindBidirectional(meshSettings.isMeshListEnabledProperty());
	}

	final AtomicInteger counter = new AtomicInteger();

	@Override
	public void onChanged(final Change<? extends MeshInfo<TLongHashSet>> change)
	{
		System.out.println("MeshInfos changed: " + counter.incrementAndGet());

		while (change.next())
		{
			if (change.wasRemoved())
			{
				change.getRemoved().forEach(info -> Optional.ofNullable(infoNodesCache.remove(info)).ifPresent(
						MeshInfoNode::unbind));
			}
		}

		if (isMeshListEnabledCheckBox.isSelected())
			populateInfoNodes(this.meshInfos.readOnlyInfos());

		updateTotalProgressBindings(this.meshInfos.readOnlyInfos());
	}

	private void populateInfoNodes(final List<MeshInfo<TLongHashSet>> infos)
	{
		final List<MeshInfoNode<TLongHashSet>> infoNodes = new ArrayList<>(infos).stream().map(this::fromMeshInfo)
				.collect(
				Collectors.toList());
		LOG.debug("Setting info nodes: {}: ", infoNodes);
		this.infoNodes.setAll(infoNodes);
		final Button exportMeshButton = new Button("Export all");
		exportMeshButton.setOnAction(event -> {
			final MeshExporterDialog<TLongHashSet>     exportDialog = new MeshExporterDialog<>(meshInfos);
			final Optional<ExportResult<TLongHashSet>> result       = exportDialog.showAndWait();
			if (result.isPresent())
			{
				final ExportResult<TLongHashSet> parameters = result.get();

				@SuppressWarnings("unchecked") final InterruptibleFunction<TLongHashSet, Interval[]>[][]
						blockListCaches = Stream
						.generate(manager::blockListCache)
						.limit(meshInfos.readOnlyInfos().size())
						.toArray(InterruptibleFunction[][]::new);

				final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[][] meshCaches = Stream
						.generate(manager::meshCache)
						.limit(meshInfos.readOnlyInfos().size())
						.toArray(InterruptibleFunction[][]::new);

				parameters.getMeshExporter().exportMesh(
						blockListCaches,
						meshCaches,
						Arrays.stream(parameters.getSegmentId()).mapToObj(id -> manager.unmodifiableMeshMap().get(id)
								.getId()).toArray(
								TLongHashSet[]::new),
						parameters.getScale(),
						parameters.getFilePaths()
				                                       );
			}
		});

		InvokeOnJavaFXApplicationThread.invoke(() -> {
			final VBox meshesListBox = new VBox();
			meshesListBox.getChildren().setAll(infoNodes.stream().map(MeshInfoNode::get).collect(Collectors.toList()));
			this.meshesBox.getChildren().setAll(meshesListBox, exportMeshButton);
		});
	}

	private void updateTotalProgressBindings(final List<MeshInfo<TLongHashSet>> infos)
	{
		InvokeOnJavaFXApplicationThread.invoke(() -> {
			final List<ObservableIntegerValue> numPendingTasksList = infos.stream().map(MeshInfo::numPendingTasksProperty).filter(Objects::nonNull).collect(Collectors.toList());
			final List<ObservableIntegerValue> numCompletedTasksList = infos.stream().map(MeshInfo::numCompletedTasksProperty).filter(Objects::nonNull).collect(Collectors.toList());

			final IntegerBinding numTotalPendingTasksBinding = Bindings.createIntegerBinding(
					() -> numPendingTasksList.stream().collect(Collectors.summingInt(ObservableIntegerValue::get)),
					numPendingTasksList.toArray(new Observable[0])
				);
			final IntegerBinding numTotalCompletedTasksBinding = Bindings.createIntegerBinding(
					() -> numCompletedTasksList.stream().collect(Collectors.summingInt(ObservableIntegerValue::get)),
					numCompletedTasksList.toArray(new Observable[0])
				);

			this.totalProgressBar.numPendingTasksProperty().bind(numTotalPendingTasksBinding);
			this.totalProgressBar.numCompletedTasksProperty().bind(numTotalCompletedTasksBinding);
		});
	}

	private Node setupManagerSliderGrid()
	{

		final GridPane contents = new GridPane();

		final int row = populateGridWithMeshSettings(
				contents,
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

		final Button refresh = new Button("Refresh Meshes");
		refresh.setOnAction(event -> manager.refreshMeshes());

		final TitledPane pane = new TitledPane("Settings", new VBox(isVisibleCheckBox, contents, refresh));
		pane.setExpanded(false);

		return pane;
	}

	static int populateGridWithMeshSettings(
			final GridPane contents,
			final int initialRow,
			final NumericSliderWithField opacitySlider,
			final NumericSliderWithField preferredScaleLevelSlider,
			final NumericSliderWithField highestScaleLevelSlider,
			final NumericSliderWithField smoothingLambdaSlider,
			final NumericSliderWithField smoothingIterationsSlider,
			final NumericSliderWithField inflateSlider,
			final ComboBox<DrawMode> drawModeChoice,
			final ComboBox<CullFace> cullFaceChoice)
	{
		int row = initialRow;

		final double textFieldWidth = 48;
		final double choiceWidth = 95;

		// arrange the grid as 4 columns to fine-tune size and layout of the elements
		for (int i = 0; i < 3; ++i)
			contents.getColumnConstraints().add(new ColumnConstraints());
		contents.getColumnConstraints().add(new ColumnConstraints(textFieldWidth));

		contents.add(labelWithToolTip("Opacity"), 0, row);
		contents.add(opacitySlider.slider(), 1, row);
		GridPane.setColumnSpan(opacitySlider.slider(), 2);
		contents.add(opacitySlider.textField(), 3, row);
		opacitySlider.slider().setShowTickLabels(true);
		opacitySlider.slider().setTooltip(new Tooltip("Mesh opacity."));
		opacitySlider.textField().setPrefWidth(textFieldWidth);
		opacitySlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(opacitySlider.slider(), Priority.ALWAYS);
		++row;

		contents.add(labelWithToolTip("Preferred scale", "Preferred scale level"), 0, row);
		contents.add(preferredScaleLevelSlider.slider(), 1, row);
		GridPane.setColumnSpan(preferredScaleLevelSlider.slider(), 2);
		contents.add(preferredScaleLevelSlider.textField(), 3, row);
		preferredScaleLevelSlider.slider().setShowTickLabels(true);
		preferredScaleLevelSlider.slider().setTooltip(new Tooltip("Sets desired projected pixel size. Mesh resolution will be chosen automatically based on this setting."));
		preferredScaleLevelSlider.textField().setPrefWidth(textFieldWidth);
		preferredScaleLevelSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(preferredScaleLevelSlider.slider(), Priority.ALWAYS);
		++row;

		contents.add(labelWithToolTip("Highest scale", "Highest scale level"), 0, row);
		contents.add(highestScaleLevelSlider.slider(), 1, row);
		GridPane.setColumnSpan(highestScaleLevelSlider.slider(), 2);
		contents.add(highestScaleLevelSlider.textField(), 3, row);
		highestScaleLevelSlider.slider().setShowTickLabels(true);
		highestScaleLevelSlider.slider().setTooltip(new Tooltip("Limits the highest resolution that the mesh can be rendered at."));
		highestScaleLevelSlider.textField().setPrefWidth(textFieldWidth);
		highestScaleLevelSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(highestScaleLevelSlider.slider(), Priority.ALWAYS);
		++row;

		contents.add(labelWithToolTip("Lambda"), 0, row);
		contents.add(smoothingLambdaSlider.slider(), 1, row);
		GridPane.setColumnSpan(smoothingLambdaSlider.slider(), 2);
		contents.add(smoothingLambdaSlider.textField(), 3, row);
		smoothingLambdaSlider.slider().setShowTickLabels(true);
		smoothingLambdaSlider.slider().setTooltip(new Tooltip("Smoothing lambda."));
		smoothingLambdaSlider.textField().setPrefWidth(textFieldWidth);
		smoothingLambdaSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(smoothingLambdaSlider.slider(), Priority.ALWAYS);
		++row;

		contents.add(labelWithToolTip("Iterations"), 0, row);
		contents.add(smoothingIterationsSlider.slider(), 1, row);
		GridPane.setColumnSpan(smoothingIterationsSlider.slider(), 2);
		contents.add(smoothingIterationsSlider.textField(), 3, row);
		smoothingIterationsSlider.slider().setShowTickLabels(true);
		smoothingIterationsSlider.slider().setTooltip(new Tooltip("Smoothing iterations."));
		smoothingIterationsSlider.textField().setPrefWidth(textFieldWidth);
		smoothingIterationsSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(smoothingIterationsSlider.slider(), Priority.ALWAYS);
		++row;

		contents.add(labelWithToolTip("Inflate"), 0, row);
		contents.add(inflateSlider.slider(), 1, row);
		GridPane.setColumnSpan(inflateSlider.slider(), 2);
		contents.add(inflateSlider.textField(), 3, row);
		inflateSlider.slider().setShowTickLabels(true);
		inflateSlider.slider().setTooltip(new Tooltip("Inflate meshes by factor"));
		inflateSlider.textField().setPrefWidth(textFieldWidth);
		inflateSlider.textField().setMaxWidth(Control.USE_PREF_SIZE);
		GridPane.setHgrow(inflateSlider.slider(), Priority.ALWAYS);
		++row;

		final Node drawModeLabel = labelWithToolTip("Draw Mode");
		contents.add(drawModeLabel, 0, row);
		GridPane.setColumnSpan(drawModeLabel, 2);
		contents.add(drawModeChoice, 2, row);
		GridPane.setColumnSpan(drawModeChoice, GridPane.REMAINING);
		GridPane.setHalignment(drawModeChoice, HPos.RIGHT);
		drawModeChoice.setPrefWidth(choiceWidth);
		++row;

		final Node cullFaceLabel = labelWithToolTip("Cull Face");
		contents.add(cullFaceLabel, 0, row);
		GridPane.setColumnSpan(cullFaceLabel, 2);
		contents.add(cullFaceChoice, 2, row);
		GridPane.setColumnSpan(cullFaceChoice, GridPane.REMAINING);
		GridPane.setHalignment(cullFaceChoice, HPos.RIGHT);
		cullFaceChoice.setPrefWidth(choiceWidth);
		++row;

		return row;
	}

	static void setPreferredAndHighestScaleLevelSliderListeners(
			final Slider preferredScaleLevelSlider,
			final Slider highestScaleLevelSlider)
	{
		preferredScaleLevelSlider.valueProperty().addListener(obs ->
				highestScaleLevelSlider.setValue(
						Math.min(
								preferredScaleLevelSlider.getValue(),
								highestScaleLevelSlider.getValue()
							)
					)
			);

		highestScaleLevelSlider.valueProperty().addListener(obs ->
				preferredScaleLevelSlider.setValue(
						Math.max(
								preferredScaleLevelSlider.getValue(),
								highestScaleLevelSlider.getValue()
							)
					)
			);
	}

	private MeshInfoNode<TLongHashSet> fromMeshInfo(final MeshInfo<TLongHashSet> info)
	{
		final MeshInfoNode<TLongHashSet> node = new MeshInfoNode<>(info);
		if (this.isBound)
		{
			node.bind();
		}
		return node;
	}

	private static final Node labelWithToolTip(final String text)
	{
		return labelWithToolTip(text, text);
	}

	private static final Node labelWithToolTip(final String text, final String tooltipText)
	{
		final Label   label = new Label(text);
		final Tooltip tt    = new Tooltip(tooltipText);
		label.setTooltip(tt);
		return label;
	}
}
