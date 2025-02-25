package org.janelia.saalfeldlab.util.n5;

import com.google.gson.GsonBuilder;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class N5TypesTest {

	@BeforeAll
	public static void setupN5Factory() {

		final var builder = new GsonBuilder();
		builder.registerTypeHierarchyAdapter(LabelBlockLookup.class, LabelBlockLookupAdapter.getJsonAdapter());
		Paintera.getN5Factory().gsonBuilder(builder);
	}

	@Test
	public void testAndLogIsIntegerType() {

		final Set<DataType> toBeTested = Stream.of(DataType.values()).collect(Collectors.toSet());
		final Set<DataType> wasTested = new HashSet<>();
		testAndLogIsIntegerType(DataType.FLOAT32, false, wasTested);
		testAndLogIsIntegerType(DataType.FLOAT64, false, wasTested);
		testAndLogIsIntegerType(DataType.INT8, true, wasTested);
		testAndLogIsIntegerType(DataType.INT16, true, wasTested);
		testAndLogIsIntegerType(DataType.INT32, true, wasTested);
		testAndLogIsIntegerType(DataType.INT64, true, wasTested);
		testAndLogIsIntegerType(DataType.UINT8, true, wasTested);
		testAndLogIsIntegerType(DataType.UINT16, true, wasTested);
		testAndLogIsIntegerType(DataType.UINT32, true, wasTested);
		testAndLogIsIntegerType(DataType.UINT64, true, wasTested);
		testAndLogIsIntegerType(DataType.OBJECT, false, wasTested);
		testAndLogIsIntegerType(DataType.STRING, false, wasTested);
		assertEquals(toBeTested, wasTested);
	}

	@Test
	public void testIsLabelMultisetType(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());

		final String noMultisetGroup = "no-multiset-group";
		final String multisetGroup = "multiset-group";
		writer.createGroup(noMultisetGroup);
		writer.createGroup(multisetGroup);
		writer.setAttribute(noMultisetGroup, N5Helpers.LABEL_MULTISETTYPE_KEY, false);
		writer.setAttribute(multisetGroup, N5Helpers.LABEL_MULTISETTYPE_KEY, true);
		assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroup));
		assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroup, false));
		assertTrue(N5Types.isLabelMultisetType(writer, multisetGroup));
		assertTrue(N5Types.isLabelMultisetType(writer, multisetGroup, false));

		final String noMultisetGroupMultiscale = "no-multiset-group-multiscale";
		final String multisetGroupMultiscale = "multiset-group-multiscale";
		writer.createGroup(noMultisetGroupMultiscale);
		writer.createGroup(multisetGroupMultiscale);

		final DatasetAttributes defaultAttributes = new DatasetAttributes(new long[]{1}, new int[]{1}, DataType.UINT8, new RawCompression());

		writer.createDataset(noMultisetGroupMultiscale + "/s0", defaultAttributes);
		writer.createDataset(noMultisetGroupMultiscale + "/s1", defaultAttributes);
		writer.createDataset(multisetGroupMultiscale + "/s0", defaultAttributes);
		writer.createDataset(multisetGroupMultiscale + "/s1", defaultAttributes);
		writer.setAttribute(noMultisetGroupMultiscale + "/s0", N5Helpers.LABEL_MULTISETTYPE_KEY, false);
		writer.setAttribute(multisetGroupMultiscale + "/s0", N5Helpers.LABEL_MULTISETTYPE_KEY, true);
		assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroupMultiscale));
		assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroupMultiscale, true));
		assertFalse(N5Types.isLabelMultisetType(writer, multisetGroupMultiscale));
		assertTrue(N5Types.isLabelMultisetType(writer, multisetGroupMultiscale, true));
	}

	@Test
	public void testMinForType() {

		for (final DataType t : DataType.values())
			assertEquals(0.0, N5Types.minForType(t), 0.0);
	}

	@Test
	public void testMaxForType() {

		final Set<DataType> toBeTested = Stream.of(DataType.values()).collect(Collectors.toSet());
		final Set<DataType> wasTested = new HashSet<>();
		testAndLogMaxForType(DataType.FLOAT32, 1.0, wasTested);
		testAndLogMaxForType(DataType.FLOAT64, 1.0, wasTested);
		testAndLogMaxForType(DataType.OBJECT, 1.0, wasTested);
		testAndLogMaxForType(DataType.STRING, 1.0, wasTested);
		testAndLogMaxForType(DataType.INT8, Byte.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT16, Short.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT32, Integer.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT64, Long.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.UINT8, 0xff, wasTested);
		testAndLogMaxForType(DataType.UINT16, 0xffff, wasTested);
		testAndLogMaxForType(DataType.UINT32, 0xffffffffL, wasTested);
		testAndLogMaxForType(DataType.UINT64, 2.0 * Long.MAX_VALUE, wasTested);
		assertEquals(toBeTested, wasTested);
	}

	@Test
	public void testGetDataType(@TempDir Path tmp) throws IOException {

		final N5Writer writer = Paintera.getN5Factory().newWriter(tmp.toAbsolutePath().toString());
		for (final DataType t : DataType.values()) {
			writer.createDataset(t.toString(), new long[]{1}, new int[]{1}, t, new RawCompression());
			assertEquals(t, N5Types.getDataType(writer, t.toString()));
		}
	}

	@Test
	public void testType() {

		final Set<DataType> toBeTested = Stream.of(DataType.values()).collect(Collectors.toSet());
		final Set<DataType> wasTested = new HashSet<>();
		testAndLogNativeTypeForType(DataType.FLOAT32, FloatType.class, wasTested);
		testAndLogNativeTypeForType(DataType.FLOAT64, DoubleType.class, wasTested);
		testAndLogNativeTypeForType(DataType.INT8, ByteType.class, wasTested);
		testAndLogNativeTypeForType(DataType.INT16, ShortType.class, wasTested);
		testAndLogNativeTypeForType(DataType.INT32, IntType.class, wasTested);
		testAndLogNativeTypeForType(DataType.INT64, LongType.class, wasTested);
		testAndLogNativeTypeForType(DataType.UINT8, UnsignedByteType.class, wasTested);
		testAndLogNativeTypeForType(DataType.UINT16, UnsignedShortType.class, wasTested);
		testAndLogNativeTypeForType(DataType.UINT32, UnsignedIntType.class, wasTested);
		testAndLogNativeTypeForType(DataType.UINT64, UnsignedLongType.class, wasTested);
		testAndLogNativeTypeForType(DataType.OBJECT, ByteType.class, wasTested);
		testAndLogNativeTypeForType(DataType.STRING, ByteType.class, wasTested);
		assertEquals(toBeTested, wasTested);
	}

	private static void testAndLogIsIntegerType(final DataType type, final boolean expected, Set<DataType> log) {

		final boolean actual = N5Types.isIntegerType(type);
		log.add(type);
		if (expected)
			assertTrue(actual);
		else
			assertFalse(actual);
	}

	private static void testAndLogMaxForType(final DataType type, final double expected, Set<DataType> log) {

		log.add(type);
		assertEquals(expected, N5Types.maxForType(type), 0.0);
	}

	private static <T> void testAndLogNativeTypeForType(final DataType type, final Class<T> expected, Set<DataType> log) {

		log.add(type);
		assertEquals(expected, N5Types.type(type).getClass());
	}
}
