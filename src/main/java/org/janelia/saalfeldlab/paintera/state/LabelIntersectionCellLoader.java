package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;

public class LabelIntersectionCellLoader<T, U> implements CellLoader<UnsignedByteType>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final RandomAccessible<T> data1;

	private final RandomAccessible<U> data2;

	private final Predicate<T> check1;

	private final Predicate<U> check2;

	private final Supplier<U> extension;

	public LabelIntersectionCellLoader(
			final RandomAccessible<T> data1,
			final RandomAccessible<U> data2,
			final Predicate<T> check1,
			final Predicate<U> check2,
			final Supplier<U> extension)
	{
		super();
		this.data1 = data1;
		this.data2 = data2;
		this.check1 = check1;
		this.check2 = check2;
		this.extension = extension;
	}

	@Override
	public void load(final SingleCellArrayImg<UnsignedByteType, ?> cell) throws Exception
	{
		LOG.debug(
				"Populating cell {} {} {}",
				Intervals.minAsLongArray(cell),
				Intervals.maxAsLongArray(cell),
				cell.size()
		         );
		final IntervalView<U> label2Interval        = Views.interval(data2, cell);
		final Cursor<T> label1Cursor                = Views.flatIterable(Views.interval(data1, cell)).cursor();
		final Cursor<U> label2Cursor                = Views.flatIterable(label2Interval).cursor();
		final Cursor<UnsignedByteType> targetCursor = cell.localizingCursor();

		//		cell.forEach( UnsignedByteType::setZero );
		while (targetCursor.hasNext())
		{
			final UnsignedByteType targetType = targetCursor.next();
			final T                label1Type = label1Cursor.next();
			final U                label2Type = label2Cursor.next();
			if (targetType.get() == 0)
			{
				if (check1.test(label1Type) && check2.test(label2Type))
				{
					FloodFill.fill(
							Views.extend(label2Interval, new OutOfBoundsConstantValueFactory<>(extension)),
							Views.extendValue(cell, new UnsignedByteType(1)),
							targetCursor,
							new UnsignedByteType(1),
							new DiamondShape(1),
							(s, t) -> check2.test(s) && t.get() == 0
					              );
				}
			}
		}
	}
}
