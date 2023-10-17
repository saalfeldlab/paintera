package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import paintera.net.imglib2.view.BundleView;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class SourceMask implements Mask {

	protected MaskInfo info;
	protected RandomAccessibleInterval<UnsignedLongType> raiOverSource;
	protected RandomAccessibleInterval<VolatileUnsignedLongType> volatileRaiOverSource;
	protected Invalidate<?> invalidate;
	protected Invalidate<?> invalidateVolatile;
	protected Runnable shutdown;

	protected SourceMask() {

	}

	protected SourceMask(MaskInfo info) {

		this.info = info;
	}

	public SourceMask(
			final MaskInfo info,
			final RandomAccessibleInterval<UnsignedLongType> rai,
			final RandomAccessibleInterval<VolatileUnsignedLongType> volatileRai,
			final Invalidate<?> invalidate,
			final Invalidate<?> invalidateVolatile,
			final Runnable shutdown) {

		this.info = info;
		this.raiOverSource = rai;
		this.volatileRaiOverSource = volatileRai;
		this.invalidate = invalidate;
		this.invalidateVolatile = invalidateVolatile;
		this.shutdown = shutdown;
	}

	@Override public MaskInfo getInfo() {

		return info;
	}

	@Override public RandomAccessibleInterval<UnsignedLongType> getRai() {

		return raiOverSource;
	}

	@Override public RandomAccessibleInterval<VolatileUnsignedLongType> getVolatileRai() {

		return volatileRaiOverSource;
	}

	@Override public Invalidate<?> getInvalidate() {

		return invalidate;
	}

	@Override public Invalidate<?> getInvalidateVolatile() {

		return invalidateVolatile;
	}

	@Override public Runnable getShutdown() {

		return shutdown;
	}

	public <C extends IntegerType<C>> Set<Long> applyMaskToCanvas(
			final RandomAccessibleInterval<C> canvas,
			final Predicate<Long> acceptAsPainted) {

		final IntervalView<UnsignedLongType> maskOverCanvas = Views.interval(Views.extendValue(getRai(), Label.INVALID), canvas);
		final IntervalView<RandomAccess<C>> bundledCanvas = Views.interval(new BundleView<>(canvas), canvas);

		final var labels = LoopBuilder.setImages(maskOverCanvas, bundledCanvas).multiThreaded().forEachChunk(chunk -> {
			final HashSet<Long> labelsForChunk = new HashSet<>();
			chunk.forEachPixel((maskVal, bundledCanvasVal) -> {
				final long label = maskVal.getIntegerLong();
				if (acceptAsPainted.test(label)) {
					bundledCanvasVal.get().setInteger(label);
					labelsForChunk.add(label);
				}
			});
			return labelsForChunk;
		});
		final var allLabels = new HashSet<Long>();
		for (HashSet<Long> labelSet : labels) {
			allLabels.addAll(labelSet);
		}
		return allLabels;
	}
}
