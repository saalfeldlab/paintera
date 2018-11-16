package org.janelia.saalfeldlab.paintera.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

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
}
