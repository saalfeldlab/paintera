package bdv.fx.viewer.project;

import com.sun.javafx.image.PixelUtils;
import net.imglib2.*;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>> extends VolatileHierarchyProjector<A, ARGBType> {

	public VolatileHierarchyProjectorPreMultiply(List<? extends RandomAccessible<A>> sources, Converter<? super A, ARGBType> converter, RandomAccessibleInterval<ARGBType> target, RandomAccessibleInterval<ByteType> mask, int numThreads, ExecutorService executorService) {
		super(sources, converter, target, mask, numThreads, executorService);
	}

	@Override
	protected void map(byte resolutionIndex, int startHeight, int endHeight) {

		if (canceled.get())
			return;

		final RandomAccess<ARGBType> targetRandomAccess = target.randomAccess(target);
		final RandomAccess<A> sourceRandomAccess = sources.get(resolutionIndex).randomAccess(sourceInterval);
		final int width = (int) target.dimension(0);
		final long[] smin = Intervals.minAsLongArray(sourceInterval);
		int myNumInvalidPixels = 0;

		final Cursor<ByteType> maskCursor = Views.iterable(mask).cursor();
		maskCursor.jumpFwd((long) startHeight * width);

		final int targetMin = (int) target.min(1);
		for (int y = startHeight; y < endHeight; ++y) {
			if (canceled.get())
				return;

			smin[1] = y + targetMin;
			sourceRandomAccess.setPosition(smin);
			targetRandomAccess.setPosition(smin);

			for (int x = 0; x < width; ++x) {

				final ByteType maskByte = maskCursor.next();
				if (maskByte.get() > resolutionIndex) {
					final A a = sourceRandomAccess.get();
					final boolean v = a.isValid();
					if (v) {
						final ARGBType argb = targetRandomAccess.get();
						converter.convert(a, argb);
						argb.set(PixelUtils.NonPretoPre(argb.get()));
						maskByte.set(resolutionIndex);
					} else
						++myNumInvalidPixels;
				}
				sourceRandomAccess.fwd(0);
				targetRandomAccess.fwd(0);
			}
		}
		numInvalidPixels.addAndGet(myNumInvalidPixels);
	}
}
