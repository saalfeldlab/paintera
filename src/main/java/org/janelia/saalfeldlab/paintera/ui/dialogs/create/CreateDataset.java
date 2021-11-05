package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.ui.DirectoryField;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceMetadata;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.util.n5.N5Data;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class CreateDataset {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final double NAME_WIDTH = 150;

  private final Source<?> currentSource;

  private final List<SourceState<?, ?>> allSources;

  private final ObservableList<MipMapLevel> mipmapLevels = FXCollections.observableArrayList();

  private final Node mipmapLevelsNode = MipMapLevel.makeNode(
		  mipmapLevels,
		  100,
		  NAME_WIDTH,
		  30,
		  ObjectField.SubmitOn.values());

  private final MenuItem populateFromCurrentSource = new MenuItem("_From Current Source");

  private final Menu populateFromSource = new Menu("_Select Source");

  private final MenuButton setFromButton = new MenuButton("_Populate", null, populateFromSource, populateFromCurrentSource);

  private final HBox setFromCurrentBox = new HBox(NamedNode.bufferNode(), setFromButton);

  {
	HBox.setHgrow(setFromCurrentBox.getChildren().get(0), Priority.ALWAYS);
	populateFromCurrentSource.setOnAction(e -> {
	  e.consume();
	  this.populateFrom(currentSource());
	});
  }

  private final TextField name = new TextField();

  {
	name.setMaxWidth(Double.MAX_VALUE);
  }

  /*TODO Caleb: Use Project directory if available, instead of user.home*/
  private final DirectoryField n5Container = new DirectoryField(System.getProperty("user.home"), 100);

  private final ObjectField<String, StringProperty> dataset = ObjectField.stringField(
		  "",
		  ObjectField.SubmitOn.values());

  {
	dataset.valueProperty().addListener((obs, oldv, newv) -> {
	  if (newv != null && Optional.ofNullable(name.textProperty().get()).orElse("").equals("")) {
		final String[] split = newv.split("/");
		name.setText(split[split.length - 1]);
	  }
	});
  }

  private final SpatialField<LongProperty> dimensions = SpatialField.longField(
		  1,
		  d -> d > 0,
		  100,
		  ObjectField.SubmitOn.values());

  private final SpatialField<IntegerProperty> blockSize = SpatialField.intField(
		  1,
		  d -> d > 0,
		  100,
		  ObjectField.SubmitOn.values());

  private final SpatialField<DoubleProperty> resolution = SpatialField.doubleField(
		  1.0,
		  r -> r > 0,
		  100,
		  ObjectField.SubmitOn.values());

  private final SpatialField<DoubleProperty> offset = SpatialField.doubleField(
		  0.0,
		  o -> true,
		  100,
		  ObjectField.SubmitOn.values());

  private final TitledPane scaleLevels = new TitledPane("Scale Levels", mipmapLevelsNode);

  private final VBox pane = new VBox(
		  NamedNode.nameIt("Name", NAME_WIDTH, true, name),
		  NamedNode.nameIt("N5", NAME_WIDTH, true, n5Container.asNode()),
		  NamedNode.nameIt("Dataset", NAME_WIDTH, true, dataset.getTextField()),
		  NamedNode.nameIt("Dimensions", NAME_WIDTH, false, NamedNode.bufferNode(), dimensions.getNode()),
		  NamedNode.nameIt("Block Size", NAME_WIDTH, false, NamedNode.bufferNode(), blockSize.getNode()),
		  NamedNode.nameIt("Resolution", NAME_WIDTH, false, NamedNode.bufferNode(), resolution.getNode()),
		  NamedNode.nameIt("Offset", NAME_WIDTH, false, NamedNode.bufferNode(), offset.getNode()),
		  setFromCurrentBox,
		  scaleLevels
  );

  public CreateDataset(
		  Source<?> currentSource,
		  SourceState<?, ?>... allSources) {

	this(currentSource, Arrays.asList(allSources));
  }

  public CreateDataset(
		  Source<?> currentSource,
		  Collection<SourceState<?, ?>> allSources) {

	this.currentSource = currentSource;
	this.allSources = new ArrayList<>(allSources);
	this.allSources.forEach(s -> {
	  final MenuItem mi = new MenuItem(s.nameProperty().get());
	  mi.setOnAction(e -> this.populateFrom(s.getDataSource()));
	  mi.setMnemonicParsing(false);
	  this.populateFromSource.getItems().add(mi);
	});
	this.populateFromSource.setVisible(this.allSources.size() > 0);
	Optional.ofNullable(currentSource).ifPresent(this::populateFrom);
  }

  public Optional<Pair<MetadataState, String>> showDialog(String projectDirectory) {

	final Alert alert = PainteraAlerts.confirmation("C_reate", "_Cancel", true);
	final var metadataStateProp = new SimpleObjectProperty<MetadataState>();
	n5Container.directoryProperty().setValue(Path.of(projectDirectory).toFile());
	alert.setHeaderText("Create new Label dataset");
	alert.getDialogPane().setContent(this.pane);
	alert.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(
			ActionEvent.ACTION,
			e -> {
			  final String container = this.n5Container.directoryProperty().getValue().getAbsolutePath();
			  final String dataset = this.dataset.valueProperty().get();
			  final String name = this.name.getText();
			  try {

				LOG.debug("Trying to create empty label dataset `{}' in container `{}'", dataset, container);

				if (dataset == null || dataset.equals(""))
				  throw new IOException("Dataset not specified!");

				if (name == null || name.equals(""))
				  throw new IOException("Name not specified!");
				N5Data.createEmptyLabelDataset(
						container,
						dataset,
						dimensions.getAs(new long[3]),
						blockSize.getAs(new int[3]),
						resolution.getAs(new double[3]),
						offset.getAs(new double[3]),
						mipmapLevels.stream().map(MipMapLevel::downsamplingFactors).toArray(double[][]::new),
						mipmapLevels.stream().mapToInt(MipMapLevel::maxNumEntries).toArray()
				);
				final var pathToDataset = Path.of(container, dataset).toFile().getCanonicalPath();
				final var writer = Paintera.getN5Factory().openWriter(pathToDataset);
				N5Helpers.parseMetadata(writer).ifPresent(tree -> {
				  final var metadata = tree.getMetadata();
				  final var containerState = new N5ContainerState(container, writer, writer);
				  MetadataUtils.createMetadataState(containerState, metadata).ifPresent(metadataStateProp::set);
				});
			  } catch (IOException ex) {
				LOG.error("Unable to create empty dataset", ex);
				e.consume();
				Alert exceptionAlert = Exceptions.exceptionAlert(
						Paintera.Constants.NAME,
						"Unable to create new dataset: " + ex.getMessage(),
						ex
				);
				exceptionAlert.show();
			  }
			});

	final Optional<ButtonType> button = alert.showAndWait();

	final String container = this.n5Container.directoryProperty().getValue().getAbsolutePath();
	final String dataset = this.dataset.valueProperty().get();
	final String name = this.name.getText();
	return Optional.ofNullable(metadataStateProp.get()).map(metadataState -> new Pair<>(metadataState, name));
  }

  private Source<?> currentSource() {

	return this.currentSource;
  }

  private void populateFrom(Source<?> source) {

	if (source == null)
	  return;

	mipmapLevels.clear();
	final var firstTransform = new AffineTransform3D();
	source.getSourceTransform(0, 0, firstTransform);
	var previousFactors = new double[]{firstTransform.get(0, 0), firstTransform.get(1, 1), firstTransform.get(2, 2)};
	for (int i = 1; i < source.getNumMipmapLevels(); i++) {
	  final var transform = new AffineTransform3D();
	  source.getSourceTransform(0, i, transform);
	  var downsamplingFactors = new double[]{transform.get(0, 0), transform.get(1, 1), transform.get(2, 2)};
	  final var level = new MipMapLevel(2, -1, 100, 150, ObjectField.SubmitOn.values());
	  level.relativeDownsamplingFactors.getX().setValue(downsamplingFactors[0] / previousFactors[0]);
	  level.relativeDownsamplingFactors.getY().setValue(downsamplingFactors[1] / previousFactors[1]);
	  level.relativeDownsamplingFactors.getZ().setValue(downsamplingFactors[2] / previousFactors[2]);
	  previousFactors = downsamplingFactors;
	  mipmapLevels.add(level);
	}

	if (source instanceof N5DataSourceMetadata<?, ?>) {
	  final var n5s = (N5DataSourceMetadata<?, ?>)source;
	  MetadataState metadataState = n5s.metaDataState();
	  String container = metadataState.getN5ContainerState().getUrl();
	  n5Container.directoryProperty().setValue(new File(container));
	}

	final RandomAccessibleInterval<?> data = source.getSource(0, 0);
	this.dimensions.getX().valueProperty().set(data.dimension(0));
	this.dimensions.getY().valueProperty().set(data.dimension(1));
	this.dimensions.getZ().valueProperty().set(data.dimension(2));
	if (data instanceof AbstractCellImg<?, ?, ?, ?>) {
	  final CellGrid grid = ((AbstractCellImg<?, ?, ?, ?>)data).getCellGrid();
	  this.blockSize.getX().valueProperty().set(grid.cellDimension(0));
	  this.blockSize.getY().valueProperty().set(grid.cellDimension(1));
	  this.blockSize.getZ().valueProperty().set(grid.cellDimension(2));
	}

	AffineTransform3D transform = new AffineTransform3D();
	source.getSourceTransform(0, 0, transform);
	this.resolution.getX().valueProperty().set(transform.get(0, 0));
	this.resolution.getY().valueProperty().set(transform.get(1, 1));
	this.resolution.getZ().valueProperty().set(transform.get(2, 2));
	this.offset.getX().valueProperty().set(transform.get(0, 3));
	this.offset.getY().valueProperty().set(transform.get(1, 3));
	this.offset.getZ().valueProperty().set(transform.get(2, 3));
  }
}
