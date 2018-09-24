package org.janelia.saalfeldlab.paintera;

import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.junit.Assert;
import org.junit.Test;

public class N5HelpersTest {

	@Test
	public void testAsCellGrid()
	{
		final long[] dims = new long[] {4, 5, 6};
		final int[] blockSize = new int[] {3, 2, 1};
		final DatasetAttributes attributes = new DatasetAttributes(dims, blockSize, DataType.UINT64, new GzipCompression());
		Assert.assertEquals(new CellGrid(dims, blockSize), N5Helpers.asCellGrid(attributes));
	}

	@Test
	public void testVolumetricDataGroup()
	{
		final String group = "some/group";
		Assert.assertEquals(group, N5Helpers.volumetricDataGroup(group, false));
		Assert.assertEquals(group + "/" + N5Helpers.PAINTERA_DATA_DATASET, N5Helpers.volumetricDataGroup(group, true));
	}

}
