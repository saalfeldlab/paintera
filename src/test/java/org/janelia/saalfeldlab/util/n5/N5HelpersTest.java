package org.janelia.saalfeldlab.util.n5;

import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.janelia.saalfeldlab.util.n5.N5Helpers.listAndSortScaleDatasets;

public class N5HelpersTest {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

	@Test public void testIsMultiscale() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();
		final String group = "group";
		writer.createGroup(group);

		Assert.assertFalse(N5Helpers.isMultiScale(writer, group));

		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);

		Assert.assertTrue(N5Helpers.isMultiScale(writer, group));

		writer.createGroup(group + "/MAKES_IT_FAIL");

		Assert.assertFalse(N5Helpers.isMultiScale(writer, group));

		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		Assert.assertTrue(N5Helpers.isMultiScale(writer, group));

	}

	@Test
	public void testListAndSortScaleDatasets() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();
		final String group = "group";
		writer.createGroup(group);
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);

		Assert.assertEquals(Arrays.asList("s0", "s1", "s2"), Arrays.asList(listAndSortScaleDatasets(writer, group)));

		Assert.assertEquals("group/s0", N5Helpers.getFinestLevel(writer, group));
		Assert.assertEquals("group/s2", N5Helpers.getCoarsestLevel(writer, group));
	}

	@Test
	public void testHighestResolutionDataset() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();
		final String group = "group";
		writer.createGroup(group);
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);

		Assert.assertEquals(group, N5Helpers.highestResolutionDataset(writer, group, false));
		Assert.assertEquals(group + "/s0", N5Helpers.highestResolutionDataset(writer, group, true));
		Assert.assertEquals(group + "/s0", N5Helpers.highestResolutionDataset(writer, group));

	}

	@Test
	public void testDiscoverDatasets() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();
		final String group = "group";
		writer.createGroup(group);
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);
		writer.createGroup("some_group");
		writer.createDataset("some_group/two", attrs);
		final List<String> groups = N5Helpers.discoverDatasets(writer, () -> {});
		Collections.sort(groups);
		LOG.debug("Got groups {}", groups);
		Assert.assertEquals(Arrays.asList("/group", "/some_group/two"), groups);

	}

}
