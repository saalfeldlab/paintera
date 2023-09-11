package bdv.fx.viewer.project;

import com.sun.javafx.image.PixelUtils;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.Parallelization;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.view.BundleView;
import net.imglib2.view.Views;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileHierarchyProjectorPreMultiply<A extends Volatile<?>> extends VolatileHierarchyProjector<A, ARGBType> {

	public VolatileHierarchyProjectorPreMultiply(List<? extends RandomAccessible<A>> sources, Converter<? super A, ARGBType> converter,
			RandomAccessibleInterval<ARGBType> target, RandomAccessibleInterval<ByteType> mask, int numThreads, ExecutorService executorService) {

		super(sources, converter, target, mask, numThreads, executorService);
	}

	@Override
	protected void map(byte resolutionIndex, int startHeight, int endHeight) {


		if (canceled.get())
			return;

		final AtomicInteger myNumInvalidPixels = new AtomicInteger();

		final TaskExecutor taskExecutor = Parallelization.getTaskExecutor();
		LoopBuilder.setImages(
						Views.interval(new BundleView<>(target), sourceInterval),
						Views.interval(new BundleView<>(sources.get(resolutionIndex)), sourceInterval),
						Views.interval(mask, sourceInterval)
				).multiThreaded(taskExecutor)
				.forEachChunk(chunk -> {
					if (canceled.get()) {
						if (!taskExecutor.getExecutorService().isShutdown()) {
							taskExecutor.getExecutorService().shutdown();
						}
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
