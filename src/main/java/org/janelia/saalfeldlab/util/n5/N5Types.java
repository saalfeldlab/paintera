package org.janelia.saalfeldlab.util.n5;

import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
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
import org.janelia.saalfeldlab.n5.N5Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public class N5Types {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


	/**
	 * Check if {@code type} is integer tpye
	 * @param type {@link DataType}
	 * @return {@code true} if {@code type} is integer type, false otherwise
	 */
	public static boolean isIntegerType(final DataType type)
	{
		switch (type)
		{
			case INT8:
			case INT16:
			case INT32:
			case INT64:
			case UINT8:
			case UINT16:
			case UINT32:
			case UINT64:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Determine if a group contains {@link LabelMultisetType} data.
	 *
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for {@link LabelMultisetType} data.
	 * @param isMultiscale whether or not {@code group} is multi-scale
	 * @return {@code} true if {@code group} (or first scale dataset if {@code isMultiscale == true})
	 * has attribute {@code "isLabelMultiset": true}, {@code false} otherwise.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isLabelMultisetType(final N5Reader n5, final String group, final boolean isMultiscale)
	throws IOException
	{
		return isMultiscale
		 	? isLabelMultisetType(n5, N5Helpers.getFinestLevel(n5, group))
			: Optional.ofNullable(n5.getAttribute(group, N5Helpers.IS_LABEL_MULTISET_KEY, Boolean.class)).orElse(false);
	}

	/**
	 * Determine if a group contains {@link LabelMultisetType} data.
	 *
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for {@link LabelMultisetType} data.
	 * @return {@code} true if {@code group} has attribute {@code "isLabelMultiset": true},
	 * {@code false} otherwise.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isLabelMultisetType(final N5Reader n5, final String group) throws IOException
	{
		return Optional.ofNullable(n5.getAttribute(group, N5Helpers.IS_LABEL_MULTISET_KEY, Boolean.class)).orElse(false);
	}

	/**
	 * Get appropriate min value for {@link DataType}
	 * @param t {@link DataType}
	 * @return {@code 0.0}
	 */
	public static double minForType(final DataType t)
	{
		// TODO ever return non-zero here?
		switch (t)
		{
			default:
				return 0.0;
		}
	}

	/**
	 * Get appropriate max value for {@link DataType}
	 * @param t {@link DataType}
	 * @return 1.0 for floating point, otherwise the max integer value that can be represented with {@code t}, cast to double.
	 */
	public static double maxForType(final DataType t)
	{
		switch (t)
		{
			case UINT8:
				return 0xff;
			case UINT16:
				return 0xffff;
			case UINT32:
				return 0xffffffffL;
			case UINT64:
				return 2.0 * Long.MAX_VALUE;
			case INT8:
				return Byte.MAX_VALUE;
			case INT16:
				return Short.MAX_VALUE;
			case INT32:
				return Integer.MAX_VALUE;
			case INT64:
				return Long.MAX_VALUE;
			case FLOAT32:
			case FLOAT64:
				return 1.0;
			default:
				return 1.0;
		}
	}

	/**
	 *
	 * @param n5 {@link N5Reader} container
	 * @param group multi-scale group, dataset, or paintera dataset
	 * @return {@link DataType} found in appropriate {@code attributes.json}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static DataType getDataType(final N5Reader n5, final String group) throws IOException
	{
		LOG.debug("Getting data type for group/dataset {}", group);
		if (N5Helpers.isPainteraDataset(n5, group)) { return getDataType(n5, group + "/" + N5Helpers.PAINTERA_DATA_DATASET); }
		if (N5Helpers.isMultiScale(n5, group)) { return getDataType(n5, N5Helpers.getFinestLevel(n5, group)); }
		return n5.getDatasetAttributes(group).getDataType();
	}

	/**
	 *
	 * @param dataType N5 {@link DataType}
	 * @param <T> {@link NativeType}
	 * @return appropriate {@link NativeType} for {@code dataType}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>> T type(final DataType dataType) {
		switch (dataType) {
			case INT8:
				return (T) new ByteType();
			case UINT8:
				return (T) new UnsignedByteType();
			case INT16:
				return (T) new ShortType();
			case UINT16:
				return (T) new UnsignedShortType();
			case INT32:
				return (T) new IntType();
			case UINT32:
				return (T) new UnsignedIntType();
			case INT64:
				return (T) new LongType();
			case UINT64:
				return (T) new UnsignedLongType();
			case FLOAT32:
				return (T) new FloatType();
			case FLOAT64:
				return (T) new DoubleType();
			default:
				return null;
		}
	}
}
