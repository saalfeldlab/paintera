package bdv.fx.viewer.project;

import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.view.BundleView;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>> extends VolatileHierarchyProjector<A, ARGBType> {

	public VolatileHierarchyProjectorPreMultiply(
			List<? extends RandomAccessible<A>> sources,
			Converter<? super A, ARGBType> converter,
			RandomAccessibleInterval<ARGBType> target,
			RandomAccessibleInterval<ByteType> mask,
			int numThreads,
			ExecutorService executorService) {

		super(sources, converter, target, mask, 1, executorService);
	}

	@Override
	protected void map(byte resolutionIndex, int startHeight, int endHeight) {

		if (canceled.get())
			return;

		final BundleView<A> bundledSourceView = new BundleView<>(sources.get(resolutionIndex));
		final IntervalView<RandomAccess<A>> bundleSourceInterval = Views.interval(bundledSourceView, sourceInterval);
		final AtomicInteger numInvalidPixels = new AtomicInteger();

		LoopBuilder.setImages(target, bundleSourceInterval, mask)
				.forEachPixel((argbType, sourceValAccess, maskVal) -> {
					if (canceled.get()) {
						return;
					}

					if (maskVal.get() > resolutionIndex) {
						//FIXME Caleb: LabelMultiset has a synchronization issue here, so we need to synchronize
						synchronized (setTargetLock) {
							final A sourceVal = sourceValAccess.get();
							if (sourceVal.isValid()) {
								converter.convert(sourceVal, argbType);
								argbType.set(PixelUtils.NonPretoPre(argbType.get()));
								maskVal.set(resolutionIndex);
							} else
								numInvalidPixels.incrementAndGet();
						}
					}

				});

		this.numInvalidPixels.addAndGet(numInvalidPixels.get());
	}
}
