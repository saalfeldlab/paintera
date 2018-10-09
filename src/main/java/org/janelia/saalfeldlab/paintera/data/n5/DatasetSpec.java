package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.util.n5.N5Helpers;

import java.io.IOException;

public class DatasetSpec {

	public final N5Writer container;

	public final String dataset;

	public final DatasetAttributes attributes;

	public final CellGrid grid;

	public final long[] dimensions;

	public final int[] blockSize;

	public DatasetSpec(final N5Writer container, final String dataset) throws IOException {
		this.container = container;
		this.dataset = dataset;
		this.attributes = N5Helpers.getDatasetAttributes(container, dataset);
		this.grid = N5Helpers.asCellGrid(this.attributes);
		this.dimensions = this.attributes.getDimensions().clone();
		this.blockSize = this.attributes.getBlockSize().clone();
	}

	public static DatasetSpec of(final N5Writer container, final String dataset) throws IOException {
		return new DatasetSpec(container, dataset);
	}

}
