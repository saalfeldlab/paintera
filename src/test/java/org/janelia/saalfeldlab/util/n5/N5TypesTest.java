package org.janelia.saalfeldlab.util.n5;

import net.imglib2.type.numeric.IntegerType;
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
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class N5TypesTest {

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
		Assert.assertEquals(toBeTested, wasTested);
	}

	@Test
	public void testIsLabelMultisetType() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();

		final String noMultisetGroup = "no-multiset-group";
		final String multisetGroup = "multiset-group";
		writer.createGroup(noMultisetGroup);
		writer.createGroup(multisetGroup);
		writer.setAttribute(noMultisetGroup, N5Helpers.LABEL_MULTISETTYPE_KEY, false);
		writer.setAttribute(multisetGroup, N5Helpers.LABEL_MULTISETTYPE_KEY, true);
		Assert.assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroup));
		Assert.assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroup, false));
		Assert.assertTrue(N5Types.isLabelMultisetType(writer, multisetGroup));
		Assert.assertTrue(N5Types.isLabelMultisetType(writer, multisetGroup, false));

		final String noMultisetGroupMultiscale = "no-multiset-group-multiscale";
		final String multisetGroupMultiscale = "multiset-group-multiscale";
		writer.createGroup(noMultisetGroupMultiscale);
		writer.createGroup(multisetGroupMultiscale);
		writer.createDataset(noMultisetGroupMultiscale + "/s0", N5TestUtil.defaultAttributes());
		writer.createDataset(noMultisetGroupMultiscale + "/s1", N5TestUtil.defaultAttributes());
		writer.createDataset(multisetGroupMultiscale + "/s0", N5TestUtil.defaultAttributes());
		writer.createDataset(multisetGroupMultiscale + "/s1", N5TestUtil.defaultAttributes());
		writer.setAttribute(noMultisetGroupMultiscale + "/s0", N5Helpers.LABEL_MULTISETTYPE_KEY, false);
		writer.setAttribute(multisetGroupMultiscale + "/s0", N5Helpers.LABEL_MULTISETTYPE_KEY, true);
		Assert.assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroupMultiscale));
		Assert.assertFalse(N5Types.isLabelMultisetType(writer, noMultisetGroupMultiscale, true));
		Assert.assertFalse(N5Types.isLabelMultisetType(writer, multisetGroupMultiscale));
		Assert.assertTrue(N5Types.isLabelMultisetType(writer, multisetGroupMultiscale, true));
	}

	@Test
	public void testMinForType() {
		for (final DataType t : DataType.values())
			Assert.assertEquals(0.0, N5Types.minForType(t), 0.0);
	}

	@Test
	public void testMaxForType() {
		final Set<DataType> toBeTested = Stream.of(DataType.values()).collect(Collectors.toSet());
		final Set<DataType> wasTested = new HashSet<>();
		testAndLogMaxForType(DataType.FLOAT32, 1.0, wasTested);
		testAndLogMaxForType(DataType.FLOAT64, 1.0, wasTested);
		testAndLogMaxForType(DataType.INT8, Byte.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT16, Short.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT32, Integer.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.INT64, Long.MAX_VALUE, wasTested);
		testAndLogMaxForType(DataType.UINT8, 0xff, wasTested);
		testAndLogMaxForType(DataType.UINT16, 0xffff, wasTested);
		testAndLogMaxForType(DataType.UINT32, 0xffffffffL, wasTested);
		testAndLogMaxForType(DataType.UINT64, 2.0 * Long.MAX_VALUE, wasTested);
		Assert.assertEquals(toBeTested, wasTested);
	}

	@Test
	public void testGetDataType() throws IOException {
		final N5Writer writer = N5TestUtil.fileSystemWriterAtTmpDir();
		for (final DataType t : DataType.values()) {
			writer.createDataset(t.toString(), new long[] {1}, new int[] {1}, t, new RawCompression());
			Assert.assertEquals(t, N5Types.getDataType(writer, t.toString()));
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
		Assert.assertEquals(toBeTested, wasTested);
	}

	private static void testAndLogIsIntegerType(final DataType type, final boolean expected, Set<DataType> log)
	{
		final boolean actual = N5Types.isIntegerType(type);
		log.add(type);
		if (expected)
			Assert.assertTrue(actual);
		else
			Assert.assertFalse(actual);
	}

	private static void testAndLogMaxForType(final DataType type, final double expected, Set<DataType> log)
	{
		log.add(type);
		Assert.assertEquals(expected, N5Types.maxForType(type), 0.0);
	}

	private static <T> void testAndLogNativeTypeForType(final DataType type, final Class<T> expected, Set<DataType> log)
	{
		log.add(type);
		Assert.assertEquals(expected, N5Types.type(type).getClass());
	}
}
