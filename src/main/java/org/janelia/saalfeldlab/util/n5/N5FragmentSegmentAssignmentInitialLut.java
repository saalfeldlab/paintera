package org.janelia.saalfeldlab.util.n5;

import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

public class N5FragmentSegmentAssignmentInitialLut implements Supplier<TLongLongMap> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final N5Reader container;
  private final String dataset;

  public N5FragmentSegmentAssignmentInitialLut(final N5Reader container, final String dataset) throws ReflectionException {

	this.container = container;
	this.dataset = dataset;
  }


  @Override
  public TLongLongMap get() {

	try {
	  RandomAccessibleInterval<UnsignedLongType> data = openDatasetSafe(container, dataset);
	  final long[] keys = new long[(int)data.dimension(0)];
	  final long[] values = new long[keys.length];
	  LOG.debug("Found {} assignments", keys.length);
	  final Cursor<UnsignedLongType> keyCursor = Views.flatIterable(Views.hyperSlice(data, 1, 0L)).cursor();
	  final Cursor<UnsignedLongType> valueCursor = Views.flatIterable(Views.hyperSlice(data, 1, 1L)).cursor();
	  for (int i = 0; i < keys.length; ++i) {
		keys[i] = keyCursor.next().getIntegerLong();
		values[i] = valueCursor.next().getIntegerLong();
	  }
	  return new TLongLongHashMap(keys, values);
	} catch (IOException e) {
	  LOG.debug("Exception while trying to return initial lut from N5", e);
	  LOG.info("Unable to read initial lut from {} -- returning empty map", container + ": " + dataset);
	  return new TLongLongHashMap();
	}
  }

  private static RandomAccessibleInterval<UnsignedLongType> openDatasetSafe(
		  final N5Reader reader,
		  final String dataset
  ) throws IOException {

	return DataType.UINT64.equals(reader.getDatasetAttributes(dataset).getDataType())
			? N5Utils.open(reader, dataset)
			: openAnyIntegerTypeAsUnsignedLongType(reader, dataset);
  }

  private static <T extends IntegerType<T> & NativeType<T>> RandomAccessibleInterval<UnsignedLongType> openAnyIntegerTypeAsUnsignedLongType(
		  final N5Reader reader,
		  final String dataset
  ) throws IOException {

	final RandomAccessibleInterval<T> img = N5Utils.open(reader, dataset);
	return Converters.convert(img, (s, t) -> t.setInteger(s.getIntegerLong()), new UnsignedLongType());
  }
}
