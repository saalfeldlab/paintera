package org.janelia.saalfeldlab.util.n5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.CompressionAdapter;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.janelia.saalfeldlab.n5.universe.N5TreeNode.flattenN5Tree;
import static org.janelia.saalfeldlab.util.n5.DatasetDiscoveryKt.discoverAndParseRecursive;
import static org.janelia.saalfeldlab.util.n5.N5Helpers.listAndSortScaleDatasets;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class N5HelpersTest {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final Gson gsonWithCompression = new GsonBuilder().registerTypeHierarchyAdapter(Compression.class, CompressionAdapter.getJsonAdapter())
			.create();

	private static void assertDatasetAttributesEquals(final DatasetAttributes expected, final DatasetAttributes actual) {

		assertEquals(expected.getNumDimensions(), actual.getNumDimensions());
		assertArrayEquals(expected.getDimensions(), actual.getDimensions());
		assertArrayEquals(expected.getBlockSize(), actual.getBlockSize());
		assertEquals(expected.getDataType(), actual.getDataType());
		assertEquals(expected.getCompression().getClass(), actual.getCompression().getClass());
		assertEquals(expected.getCompression().getType(), actual.getCompression().getType());
		assertEquals(gsonWithCompression.toJson(expected.getCompression()), gsonWithCompression.toJson(actual.getCompression()));
	}

	@BeforeAll
	public static void setupN5Factory() {

		final var builder = new GsonBuilder();
		builder.registerTypeHierarchyAdapter(LabelBlockLookup.class, LabelBlockLookupAdapter.getJsonAdapter());
		Paintera.getN5Factory().gsonBuilder(builder);
	}

	@Test
	public void testAsCellGrid() {

		final long[] dims = new long[]{4, 5, 6};
		final int[] blockSize = new int[]{3, 2, 1};
		final DatasetAttributes attributes = new DatasetAttributes(dims, blockSize, DataType.UINT64, new GzipCompression());
		assertEquals(new CellGrid(dims, blockSize), N5Helpers.asCellGrid(attributes));
	}

	@Test
	public void testVolumetricDataGroup() {

		final String group = "some/group";
		assertEquals(group, N5Helpers.volumetricDataGroup(group, false));
		assertEquals(group + "/" + N5Helpers.PAINTERA_DATA_DATASET, N5Helpers.volumetricDataGroup(group, true));
	}

	@Test
	public void testIsMultiscale(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());
		final String group = "group";
		writer.createGroup(group);

		assertFalse(N5Helpers.isMultiScale(writer, group));

		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);

		assertTrue(N5Helpers.isMultiScale(writer, group));

		writer.createGroup(group + "/MAKES_IT_FAIL");

		assertFalse(N5Helpers.isMultiScale(writer, group));

		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		assertTrue(N5Helpers.isMultiScale(writer, group));

	}

	@Test
	public void testListAndSortScaleDatasets(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());
		final String group = "group";
		writer.createGroup(group);
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);

		assertEquals(Arrays.asList("s0", "s1", "s2"), Arrays.asList(listAndSortScaleDatasets(writer, group)));

		assertEquals("group/s0", String.join("/", N5Helpers.getFinestLevelJoinWithGroup(writer, group)));
		assertEquals("group/s2", String.join("/", N5Helpers.getCoarsestLevelJoinWithGroup(writer, group)));
	}

	@Test
	public void testDiscoverDatasets(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());
		final String group = "group";
		writer.createGroup(group);
		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		final DatasetAttributes attrs = new DatasetAttributes(new long[]{1, 1, 1}, new int[]{1, 1, 1}, DataType.UINT8, new RawCompression());
		writer.createDataset(group + "/s0", attrs);
		writer.createDataset(group + "/s1", attrs);
		writer.createDataset(group + "/s2", attrs);
		writer.createGroup("some_group");
		writer.createDataset("some_group/two", attrs);
		final var metadataTree = discoverAndParseRecursive(writer);

		final var groups = flattenN5Tree(metadataTree)
				.filter(node -> node.getMetadata() != null)
				.map(N5TreeNode::getPath)
				.sorted()
				.collect(Collectors.toCollection(ArrayList::new));
		LOG.debug("Got groups {}", groups);
		assertEquals(Arrays.asList("/group", "/group/s0", "/group/s1", "/group/s2", "/some_group/two"), groups);

	}

	@Test
	public void testGetDatasetAttributes(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());
		final DatasetAttributes attributes = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new GzipCompression());

		// single scale
		final String dataset = "dataset";
		writer.createDataset(dataset, attributes);
		assertDatasetAttributesEquals(attributes, N5Helpers.getDatasetAttributes(writer, dataset));

		// multi scale
		final String group = "group";
		writer.createGroup(group);
		writer.createDataset("group/s0", attributes);
		assertDatasetAttributesEquals(attributes, N5Helpers.getDatasetAttributes(writer, group));

		writer.setAttribute(group, N5Helpers.MULTI_SCALE_KEY, true);
		assertDatasetAttributesEquals(attributes, N5Helpers.getDatasetAttributes(writer, group));

		// paintera data
		final String painteraData = "paintera-data";
		final String data = painteraData + "/data";
		writer.createDataset(data + "/s0", attributes);
		writer.createGroup(painteraData);
		writer.createGroup(data);
		writer.setAttribute(data, N5Helpers.MULTI_SCALE_KEY, true);

		assertNull(N5Helpers.getDatasetAttributes(writer, painteraData));
		assertDatasetAttributesEquals(attributes, N5Helpers.getDatasetAttributes(writer, data));

		writer.setAttribute(painteraData, N5Helpers.PAINTERA_DATA_KEY, Stream.of(1).collect(Collectors.toMap(o -> "type", o -> "raw")));
		assertDatasetAttributesEquals(attributes, N5Helpers.getDatasetAttributes(writer, painteraData));

	}

}
