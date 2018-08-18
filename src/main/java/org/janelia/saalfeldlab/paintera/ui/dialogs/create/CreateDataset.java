package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.ui.DirectoryField;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.NamedNode;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDataset
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final double NAME_WIDTH = 150;

	private final Source<?> currentSource;

	private final List<Source<?>> allSources;

	private final ObservableList<MipMapLevel> mipmapLevels = FXCollections.observableArrayList();

	private final Node mipmapLevelsNode = MipMapLevel.makeNode(
			mipmapLevels,
			100,
			NAME_WIDTH,
			30,
			ObjectField.SubmitOn.values()
	                                                          );

	private final MenuItem populateFromCurrentSource = new MenuItem("From Current Source");

	private final MenuButton setFromButton = new MenuButton("Populate", null, populateFromCurrentSource);

	private final HBox setFromCurrentBox = new HBox(new Region(), setFromButton);

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

	private final DirectoryField n5Container = new DirectoryField(System.getProperty("user.home"), 100);

	private final ObjectField<String, StringProperty> dataset = ObjectField.stringField(
			"",
			ObjectField.SubmitOn.values()
	                                                                                   );

	{
		dataset.valueProperty().addListener((obs, oldv, newv) -> {
			if (newv != null && Optional.ofNullable(name.textProperty().get()).orElse("").equals(""))
			{
				final String[] split = newv.split("/");
				name.setText(split[split.length - 1]);
			}
		});
	}

	private final SpatialField<LongProperty> dimensions = SpatialField.longField(
			1,
			d -> d > 0,
			100,
			ObjectField.SubmitOn.values()
	                                                                            );

	private final SpatialField<IntegerProperty> blockSize = SpatialField.intField(
			1,
			d -> d > 0,
			100,
			ObjectField.SubmitOn.values()
	                                                                             );

	private final SpatialField<DoubleProperty> resolution = SpatialField.doubleField(
			1.0,
			r -> r > 0,
			100,
			ObjectField.SubmitOn.values()
	                                                                                );

	private final SpatialField<DoubleProperty> offset = SpatialField.doubleField(
			0.0,
			o -> true,
			100,
			ObjectField.SubmitOn.values()
	                                                                            );

	private final TitledPane scaleLevels = new TitledPane("Scale Levels", mipmapLevelsNode);

	private final VBox pane = new VBox(
			NamedNode.nameIt("Name", NAME_WIDTH, true, name),
			NamedNode.nameIt("N5", NAME_WIDTH, true, n5Container.asNode()),
			NamedNode.nameIt("Dataset", NAME_WIDTH, true, dataset.textField()),
			NamedNode.nameIt("Dimensions", NAME_WIDTH, false, NamedNode.bufferNode(new Region()),
					dimensions.getNode()
			                ),
			NamedNode.nameIt("Block Size", NAME_WIDTH, false, NamedNode.bufferNode(new Region()), blockSize.getNode()),
			NamedNode.nameIt("Resolution", NAME_WIDTH, false, NamedNode.bufferNode(new Region()),
					resolution.getNode()
			                ),
			NamedNode.nameIt("Offset", NAME_WIDTH, false, NamedNode.bufferNode(new Region()), offset.getNode()),
			setFromCurrentBox,
			scaleLevels
	);

	public CreateDataset(
			Source<?> currentSource,
			Source<?>... allSources)
	{
		this(currentSource, Arrays.asList(allSources));
	}

	public CreateDataset(
			Source<?> currentSource,
			Collection<Source<?>> allSources)
	{
		this.currentSource = currentSource;
		this.allSources = new ArrayList<>(allSources);
		Optional.ofNullable(currentSource).ifPresent(this::populateFrom);
	}

	public Optional<Pair<N5FSMeta, String>> showDialog()
	{
		final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setResizable(true);
		alert.setTitle("Paintera");
		alert.setHeaderText("Create new Label dataset");
		alert.getDialogPane().setContent(this.pane);
		((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).setText("Create");
		((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(
				ActionEvent.ACTION,
				e -> {
			final String container = this.n5Container.directoryProperty().getValue().getAbsolutePath();
			final String dataset = this.dataset.valueProperty().get();
			final String name = this.name.getText();
			try
			{

				LOG.warn("Trying to create empty label dataset `{}' in container `{}'", dataset, container);

				if (dataset == null || dataset.equals(""))
					throw new IOException("Dataset not specified!");

				if (name == null || name.equals(""))
					throw new IOException("Name not specified!");
				N5Helpers.createEmptyLabeLDataset(
						container,
						dataset,
						dimensions.getAs(new long[3]),
						blockSize.getAs(new int[3]),
						resolution.getAs(new double[3]),
						offset.getAs(new double[3]),
						mipmapLevels.stream().map(MipMapLevel::downsamplingFactors).toArray(double[][]::new),
						mipmapLevels.stream().mapToInt(MipMapLevel::maxNumEntries).toArray()
				                                 );
			} catch (IOException ex)
			{
				LOG.warn("Unable to create empty dataset");
				e.consume();
				Alert exceptionAlert = Exceptions.exceptionAlert(
						"Paintera",
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
		return button.filter( ButtonType.OK::equals ).map( bt -> new Pair<>(new N5FSMeta(container, dataset), name));
	}

	private Source<?> currentSource()
	{
		return this.currentSource;
	}

	private void populateFrom(Source<?> source)
	{
		if (source == null)
			return;

		if (source instanceof N5DataSource<?, ?>)
		{
			N5DataSource<?, ?> n5s = (N5DataSource<?, ?>) source;
			if (n5s.meta() instanceof N5FSMeta)
			{
				n5Container.directoryProperty().setValue(new File(((N5FSMeta) n5s.meta()).basePath()));
			}
		}

		final RandomAccessibleInterval<?> data = source.getSource(0, 0);
		this.dimensions.getX().valueProperty().set(data.dimension(0));
		this.dimensions.getY().valueProperty().set(data.dimension(1));
		this.dimensions.getZ().valueProperty().set(data.dimension(2));
		if (data instanceof AbstractCellImg<?, ?, ?, ?>)
		{
			final CellGrid grid = ((AbstractCellImg<?, ?, ?, ?>) data).getCellGrid();
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

	public static void main(String[] args) throws IOException, ReflectionException
	{
		PlatformImpl.startup(() -> {
		});

		final AffineTransform3D tf = new AffineTransform3D();
		tf.set(
				4.0, 0.0, 0.0, 1.0,
				0.0, 5.0, 0.0, 5.0,
				0.0, 0.0, 40., -1.
		      );
		final N5Reader reader = new N5FSReader(
				"/home/phil/local/tmp/sample_a_padded_20160501.n5");
		final DataSource<UnsignedByteType, VolatileUnsignedByteType> raw = N5Helpers.openRawAsSource(
				reader,
				"volumes/raw/data/s0",
				tf,
				new SharedQueue(1, 1),
				1,
				"NAME"
		                                                                                            );


		final CreateDataset cd = new CreateDataset(raw);

		Platform.runLater(() -> {
			final Button b = new Button("BUTTON");
			b.setOnAction(e -> LOG.warn( "Got new dataset meta: {}", cd.showDialog()));
			final Scene scene = new Scene(b);
			final Stage stage = new Stage();
			stage.setScene(scene);
			stage.show();
		});
	}
}
