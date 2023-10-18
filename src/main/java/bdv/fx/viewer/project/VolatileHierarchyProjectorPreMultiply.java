package bdv.fx.viewer.project;

import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import paintera.net.imglib2.view.BundleView;
import net.imglib2.view.Views;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>> extends VolatileHierarchyProjector<A, ARGBType> {

	public VolatileHierarchyProjectorPreMultiply(
			final List<? extends RandomAccessible<A>> sources,
			final Converter<? super A, ARGBType> converter,
			final RandomAccessibleInterval<ARGBType> target,
			final RandomAccessibleInterval<ByteType> mask,
			final TaskExecutor taskExecutor) {

		super(sources, converter, target, mask, taskExecutor);
	}

	@Override
	protected void map(byte resolutionIndex, int startHeight, int endHeight) {


		if (canceled.get())
			return;

		final AtomicInteger myNumInvalidPixels = new AtomicInteger();

		LoopBuilder.setImages(
						Views.interval(new BundleView<>(target), sourceInterval),
						Views.interval(new BundleView<>(sources.get(resolutionIndex)), sourceInterval),
						Views.interval(mask, sourceInterval)
				).multiThreaded(taskExecutor)
				.forEachChunk(chunk -> {
					if (canceled.get()) {
						Thread.currentThread().interrupt();
						return null;
					}
					chunk.forEachPixel((targetVal, sourceVal, maskVal) -> {
						if (maskVal.get() > resolutionIndex) {
							final A a = sourceVal.get();
							final boolean v = a.isValid();
							if (v) {
								final ARGBType argb = targetVal.get();
								converter.convert(a, argb);
								argb.set(PixelUtils.NonPretoPre(argb.get()));
								maskVal.set(resolutionIndex);
							} else
								myNumInvalidPixels.incrementAndGet();
						}
					});
					return null;
				});
		numInvalidPixels.addAndGet(myNumInvalidPixels.get());
		if (myNumInvalidPixels.get() != 0)
			valid = false;
	}
}
