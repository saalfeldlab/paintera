package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.img.basictypeaccess.array.*;
import net.imglib2.img.cell.Cell;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class AccessMemoryUsage {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class ByteArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractByteArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			if (object instanceof Cell<?>)
			{
				final Cell<?> cell = (Cell<?>)object;
				final Object data = cell.getData();
				LOG.trace("is applicable? {}", data);
				return data instanceof AbstractByteArray;
			}
			return false;
		}

		@Override
		public long applyAsLong(Cell<AbstractByteArray<?>> byteAccess) {
			return byteAccess.getData().getArrayLength() * Byte.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class CharArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractCharArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractCharArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractCharArray<?>> charAccess) {
			return charAccess.getData().getArrayLength() * Character.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class ShortArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractShortArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractShortArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractShortArray<?>> shortAccess) {
			return shortAccess.getData().getArrayLength() * Short.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class IntArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractIntArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractIntArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractIntArray<?>> intAccess) {
			return intAccess.getData().getArrayLength() * Integer.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class LongArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractLongArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractLongArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractLongArray<?>> longAccess) {
			return longAccess.getData().getArrayLength() * Long.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class FloatArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractFloatArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractFloatArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractFloatArray<?>> floatAccess) {
			return floatAccess.getData().getArrayLength() * Float.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class DoubleArrayDataAccessMemoryUsage implements DiscoverableMemoryUsage<Cell<AbstractDoubleArray<?>>> {

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof AbstractDoubleArray<?>;
		}

		@Override
		public long applyAsLong(Cell<AbstractDoubleArray<?>> doubleAccess) {
			return doubleAccess.getData().getArrayLength() * Double.BYTES;
		}
	}

	@Plugin(type = DiscoverableMemoryUsage.class)
	public static class VolatileLabelMultisetArrayMemoryUsage implements DiscoverableMemoryUsage<Cell<VolatileLabelMultisetArray>>
	{

		@Override
		public boolean isApplicable(Object object) {
			return object instanceof Cell<?> && ((Cell<?>)object).getData() instanceof VolatileLabelMultisetArray;
		}

		@Override
		public long applyAsLong(Cell<VolatileLabelMultisetArray> volatileLabelMultisetArray) {
			return VolatileLabelMultisetArray.getRequiredNumberOfBytes(volatileLabelMultisetArray.getData());
		}
	}

}
