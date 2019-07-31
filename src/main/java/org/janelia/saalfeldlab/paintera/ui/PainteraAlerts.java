package org.janelia.saalfeldlab.paintera.ui;

import javafx.beans.property.LongProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.LongConsumer;

public class PainteraAlerts {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 *
	 * delegates to {@link #alert(Alert.AlertType, boolean)} with {@code isResizable = true}.
	 *
	 * @param type type of alert
	 * @return {@link Alert} with the title set to {@link Paintera#NAME}
	 */
	public static Alert alert(final Alert.AlertType type) {
		return alert(type, true);
	}

	/**
	 *
	 * @param type type of alert
	 * @param isResizable set to {@code true} if dialog should be resizable
	 * @return {@link Alert} with the title set to {@link Paintera#NAME}
	 */
	public static Alert alert(final Alert.AlertType type, boolean isResizable) {
		final Alert alert = new Alert(type);
		alert.setTitle(Paintera.NAME);
		alert.setResizable(isResizable);
		return alert;
	}

	/**
	 * Get a {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 * @param source used to determine block sizes for marching cubes
	 * @return {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 */
	public static LabelBlockLookup getLabelBlockLookupFromN5DataSource(
			final N5Reader reader,
			final String group,
			final DataSource<?, ?> source) {
		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("Define label-to-block-lookup for on-the-fly mesh generation");
		final TextArea ta = new TextArea(String.format("Could not deserialize label-to-block-lookup for dataset `%s' in N5 container `%s' " +
				"that is required for on the fly mesh generation. " +
				"If you are not interested in 3D meshes, press cancel. Otherwise, press OK. Generating meshes on the fly will be slow " +
				"as the sparsity of objects can not be utilized.", group, reader));
		ta.setEditable(false);
		ta.setWrapText(true);
		alert.getDialogPane().setContent(ta);
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final CellGrid[] grids = source.getGrids();
			long[][] dims = new long[grids.length][];
			int[][] blockSizes = new int[grids.length][];
			for (int i = 0; i < grids.length; ++i) {
				dims[i] = grids[i].getImgDimensions();
				blockSizes[i] = new int[grids[i].numDimensions()];
				grids[i].cellDimensions(blockSizes[i]);
			}
			LOG.debug("Returning block lookup returning all blocks.");
			return new LabelBlockLookupAllBlocks(dims, blockSizes);
		} else {
			return new LabelBlockLookupNoBlocks();
		}
	}

	/**
	 * Get a {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 * @param source used to determine block sizes for marching cubes
	 * @return {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 */
	public static LabelBlockLookup getLabelBlockLookupFromDataSource(final DataSource<?, ?> source) {
		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("Define label-to-block-lookup for on-the-fly mesh generation");
		final TextArea ta = new TextArea("Could not deserialize label-to-block-lookup that is required for on the fly mesh generation. " +
				"If you are not interested in 3D meshes, press cancel. Otherwise, press OK. Generating meshes on the fly will be slow " +
				"as the sparsity of objects can not be utilized.");
		ta.setEditable(false);
		ta.setWrapText(true);
		alert.getDialogPane().setContent(ta);
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final CellGrid[] grids = source.getGrids();
			long[][] dims = new long[grids.length][];
			int[][] blockSizes = new int[grids.length][];
			for (int i = 0; i < grids.length; ++i) {
				dims[i] = grids[i].getImgDimensions();
				blockSizes[i] = new int[grids[i].numDimensions()];
				grids[i].cellDimensions(blockSizes[i]);
			}
			LOG.debug("Returning block lookup returning all blocks.");
			return new LabelBlockLookupAllBlocks(dims, blockSizes);
		} else {
			return new LabelBlockLookupNoBlocks();
		}
	}

	public static IdService getN5IdServiceFromData(
			final N5Writer n5,
			final String dataset,
			final DataSource<? extends IntegerType<?>, ?> source) throws IOException {
		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("maxId not specified in dataset.");
		final TextArea ta = new TextArea(String.format("Could not read maxId attribute from dataset `%s' in container `%s'. " +
				"You can specify the max id manually, or read it from the data set (this can take a long time if your data is big).\n" +
				"Alternatively, press cancel to load the data set without an id service. " +
				"Fragment-segment-assignments and selecting new (wrt to the data) labels require an id service " +
				"and will not be available if you press cancel.", dataset, n5));
		ta.setEditable(false);
		ta.setWrapText(true);
		final NumberField<LongProperty> nextIdField = NumberField.longField(0, v -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final Button scanButton = new Button("Scan Data");
		scanButton.setOnAction(event -> {
			event.consume();
			long maxId = findMaxId(source, 0, nextIdField.valueProperty()::set);
			nextIdField.valueProperty().setValue(maxId);
		});
		final HBox maxIdBox = new HBox(new Label("Max Id:"), nextIdField.textField(), scanButton);
		maxIdBox.setAlignment(Pos.CENTER);
		HBox.setHgrow(nextIdField.textField(), Priority.ALWAYS);
		alert.getDialogPane().setContent(new VBox(ta, maxIdBox));
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final long maxId = nextIdField.valueProperty().get() + 1;
			n5.setAttribute(dataset, "maxId", maxId);
			return new N5IdService(n5, dataset, maxId);
		}
		else
			return new IdService.IdServiceNotProvided();
	}

	private static long findMaxId(
			final DataSource<? extends IntegerType<?>, ?> source,
			final int level,
			final LongConsumer maxIdTracker) {
		final RandomAccessibleInterval<? extends IntegerType<?>> rai = source.getDataSource(0, level);
		long maxId = 0;
		maxIdTracker.accept(maxId);
		for (final IntegerType<?> t : Views.flatIterable(source.getDataSource(0, level))) {
			final long id = t.getIntegerLong();
			if (id > maxId) {
				maxId = id;
				maxIdTracker.accept(maxId);
			}
		}
		return maxId;
	}
}
