package org.janelia.saalfeldlab.paintera;

// TODO uncomment imports and tests once #243 is fixed

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.lazy.Lazy;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.net.imglib2.view.BundleView;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PainteraBaseViewTest {

	@PainteraApplicationTest(reuseInstance = true)
	public void testAddSingleScaleLabelSource(final PainteraTestApplication app) {

		final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 15, 20);
		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsLabelSource(
				labels,
				new double[]{1.0, 1.0, 1.0},
				new double[]{0.0, 0.0, 0.0},
				1,
				"singleScaleLabelSource", new LabelBlockLookupNoBlocks());
	}

	@PainteraApplicationTest(reuseInstance = true)
	public void testAddSingleScaleConnectomicsRawSource(final PainteraTestApplication app) {

		final Random random = new Random();
		final RandomAccessibleInterval<UnsignedLongType> rawData =
				Lazy.generate(
						Intervals.createMinSize(0, 0, 0, 100, 100, 100),
						new int[]{10, 10, 10},
						new UnsignedLongType(),
						AccessFlags.setOf(AccessFlags.VOLATILE),
						rai -> Views.flatIterable(rai).forEach(val -> val.set(random.nextInt(255)))
				);
		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsRawSource(
				rawData,
				new double[]{1.0, 1.0, 1.0},
				new double[]{0.0, 0.0, 0.0},
				0, 255,
				"singleScaleRawSource"
		);
	}

	@PainteraApplicationTest(reuseInstance = true)
	public void testAddMultiScaleConnectomicsRawSource(final PainteraTestApplication app) {

		var random = new Random();
		final double[] center2D = new double[]{500, 500};
		final var multiscale = generateMultiscaleLabels(
				4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				(scale, chunk) -> {
					fillCylinderChunk(center2D, scale, chunk, () -> random.nextInt(255));
					return null;
				});

		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		var raw = viewer.addMultiscaleConnectomicsRawSource(
				multiscale.images,
				multiscale.resolutions,
				multiscale.translations,
				0, 255,
				"multiscale"
		);

		final var converter = raw.converter();
		converter.setMin(0.0);
		converter.setMax(255.0);
	}

	private static void fillCylinderChunk(double[] center2D, Double scale, LoopBuilder.Chunk<Consumer<RandomAccess<UnsignedLongType>>> chunk, Supplier<Integer> value) {

		final double[] pos = new double[3];
		final double[] pos2D = new double[2];

		chunk.forEachPixel(pixel -> {
			pixel.localize(pos);
			System.arraycopy(pos, 0, pos2D, 0, 2);
			if (LinAlgHelpers.distance(pos2D, center2D) < (700 * scale)) {
				pixel.get().set(value.get());
			} else {
				pixel.get().set(0);
			}
		});
	}

	@PainteraApplicationTest(reuseInstance = true)
	public void testAddMultiScaleConnectomicsLabelSource(final PainteraTestApplication app) throws InterruptedException {

		final Random random = new Random();

		final var multiscale = generateMultiscaleLabels(4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				(scale, chunk) -> {
					fillCylinderChunk(new double[]{500, 500}, scale, chunk, () -> random.nextInt(1));
					return null;
				});

		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsLabelSource(
				multiscale.images,
				multiscale.resolutions,
				multiscale.translations,
				3,
				"multiscale-label",
				new LabelBlockLookupAllBlocks(multiscale.dims, multiscale.blocks)
		);
	}

	public static void main(String[] args) {

		final var painteraTestApp = PainteraTestAppProvider.launchPaintera();
		new PainteraBaseViewTest().testAddSingleScaleConnectomicsRawSource(painteraTestApp);
	}

	private static class GeneratedMultiscaleImage<T> {

		private final RandomAccessibleInterval<T>[] images;
		private final double[][] resolutions;
		private final double[][] translations;
		private final long[][] dims;
		private final int[][] blocks;

		public GeneratedMultiscaleImage(RandomAccessibleInterval<T>[] images, double[][] resolutions, double[][] translations, long[][] dims, int[][] blocks) {

			this.images = images;
			this.resolutions = resolutions;
			this.translations = translations;
			this.dims = dims;
			this.blocks = blocks;
		}
	}

	@NotNull
	private static GeneratedMultiscaleImage<UnsignedLongType> generateMultiscaleLabels(int numScales, FinalInterval interval, int[] blockSize, BiFunction<Double, LoopBuilder.Chunk<Consumer<RandomAccess<UnsignedLongType>>>, ?> fillLabelByChunk) {

		final CachedCellImg<UnsignedLongType, ?>[] multiScaleImages = new CachedCellImg[numScales];
		for (int i = 0; i < multiScaleImages.length; i++) {
			final double scale = 1 / (Math.pow(2, i));
			final Interval scaledInterval = Intervals.smallestContainingInterval(Intervals.scale(interval, scale));

			final CachedCellImg<UnsignedLongType, ?> virtualimg = Lazy.generate(scaledInterval,
					blockSize,
					new UnsignedLongType(),
					AccessFlags.setOf(AccessFlags.VOLATILE),
					rai -> {
						final IntervalView<RandomAccess<UnsignedLongType>> bundledView = Views.interval(new BundleView<>(rai), rai);
						LoopBuilder.setImages(bundledView).multiThreaded().forEachChunk(chunk -> fillLabelByChunk.apply(scale, chunk));
					}
			);
			multiScaleImages[i] = virtualimg;
		}

		final long[][] dims = new long[multiScaleImages.length][3];
		for (int i = 0; i < dims.length; i++) {
			dims[i] = multiScaleImages[i].dimensionsAsLongArray();
		}

		final int[][] blocks = new int[multiScaleImages.length][3];
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = multiScaleImages[i].getCellGrid().getCellDimensions();
		}

		double[][] resolutions = new double[multiScaleImages.length][3];

		for (int i = 0; i < resolutions.length; i++) {
			Arrays.fill(resolutions[i], Math.pow(2, i));
		}
		double[][] translations = new double[multiScaleImages.length][3];

		return new GeneratedMultiscaleImage(multiScaleImages, resolutions, translations, dims, blocks);
	}
}


