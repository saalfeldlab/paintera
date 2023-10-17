package org.janelia.saalfeldlab.paintera;

// TODO uncomment imports and tests once #243 is fixed

import javafx.application.Application;
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
import paintera.net.imglib2.view.BundleView;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.jetbrains.annotations.NotNull;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit.ApplicationTest;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class PainteraBaseViewTest extends FxRobot {

	//	@BeforeClass
	public static void setup() throws Exception {

		System.setProperty("headless.geometry", "1600x1200-32");

		/* With default log level of INFO we get a flod of WARNINGs that the ConditionalSupport.SCENE3D is not available in the headless testing.
		 * 	Since we are not relying on that here, just set log to ERROR to ignore that message. */
		ApplicationTest.launch(Paintera.class, "--log-level=ERROR");
	}

	//	@AfterClass
	public static void cleanup() throws InterruptedException, TimeoutException {
		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			Paintera.getPaintera().getBaseView().stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}


	//	@Test
	public void testAddSingleScaleLabelSource() {
		final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 15, 20);
		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsLabelSource(
				labels,
				new double[]{1.0, 1.0, 1.0},
				new double[]{0.0, 0.0, 0.0},
				1,
				"singleScaleLabelSource", new LabelBlockLookupNoBlocks());
	}

	//	@Test
	public void testAddSingleScaleConnectomicsRawSource() {
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

	//	@Test
	public void testAddMultiScaleConnectomicsRawSource() {
		var random = new Random();
		final double[] center2D = new double[]{500, 500};
		final var multiscale = generateMultiscaleLabels(
				4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				new double[]{500, 500, 0},
				(scale, chunk) -> {
					fillCylinderChunk(center2D, scale, chunk, () -> random.nextInt(255));
					return null;
				});

		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		var raw = viewer.addConnectomicsRawSource(
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
			if (LinAlgHelpers.distance(pos2D, center2D) < (25 * scale)) {
				pixel.get().set(value.get());
			} else {
				pixel.get().set(0);
			}

		});
	}

	//	@Test
	public void testAddMultiScaleConnectomicsLabelSource() {
		final Random random = new Random();

		final var multiscale = generateMultiscaleLabels(4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				new double[]{500, 500, 0},
				(scale, chunk) -> {
					fillCylinderChunk(new double[]{500, 500}, scale, chunk, () -> random.nextInt(255));
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

		Paintera.whenPaintable(() -> {


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
			var raw = viewer.addConnectomicsRawSource(
					rawData,
					new double[]{1.0, 1.0, 1.0},
					new double[]{0.0, 0.0, 0.0},
					0, 255,
					"singleScaleRawSource"
			);
//			final Random random = new Random();
//
//			final double[] center2D = new double[]{500, 500};
//			final var multiscale = generateMultiscaleLabels(4,
//					Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
//					new int[]{64, 64, 64},
//					new double[]{500, 500, 0},
//					(scale, chunk) -> {
//						fillCylinderChunk(center2D, scale, chunk);
//						return null;
//					});
//
//			final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
//			final var raw = viewer.addConnectomicsRawSource(
//					multiscale.images,
//					multiscale.resolutions,
//					multiscale.translations,
//					0, 2,
//					"multiscale"
//			);

//			final FinalInterval interval = Intervals.createMinSize(0, 0, 0, 100, 100, 100);
//			final int[] blockSize = {25, 25, 25};
//			int radius = 50;
//			double[] center = new double[]{50, 50, 50};
//			center2D[0] = 50;
//			center2D[1] = 50;
//			final var generatedMultiscaleCylinder = generateMultiscaleLabels(
//					4,
//					interval,
//					blockSize,
//					center,
//					(scale, chunk) -> {
//						fillCylinderChunk(center, scale, chunk);
//						return null;
//					});
//
//			final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
//
//			final var raw = viewer.addConnectomicsRawSource(
//					generatedMultiscaleCylinder.images,
//					generatedMultiscaleCylinder.resolutions,
//					generatedMultiscaleCylinder.translations,
//					0,
//					10,
//					"raw"
//			);
			final var converter = raw.converter();
			converter.setMin(0.0);
			converter.setMax(255.0);
//
//			final var labels = viewer.addConnectomicsLabelSource(
//					generatedMultiscaleCylinder.images,
//					generatedMultiscaleCylinder.resolutions,
//					generatedMultiscaleCylinder.translations,
//					10,
//					"labels",
//					new LabelBlockLookupAllBlocks(generatedMultiscaleCylinder.dims, generatedMultiscaleCylinder.blocks)
//			);
//			labels.getSelectedIds().activate(2);
//
//			final MeshSettings meshSettings = labels.getMeshManager().getSettings();
//			meshSettings.setCoarsestScaleLevel(generatedMultiscaleCylinder.images.length - 1);
//			meshSettings.setFinestScaleLevel(0);
//			meshSettings.setLevelOfDetail(5);


		});
		Application.launch(Paintera.class);

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
	private static GeneratedMultiscaleImage<UnsignedLongType> generateMultiscaleLabels(int numScales, FinalInterval interval, int[] blockSize,
	                                                                                   double[] center, BiFunction<Double, LoopBuilder.Chunk<Consumer<RandomAccess<UnsignedLongType>>>, ?> fillLabelByChunk) {

		final CachedCellImg<UnsignedLongType, ?>[] multiScaleImages = new CachedCellImg[numScales];
		for (int i = 0; i < multiScaleImages.length; i++) {
			final double scale = 1 / (Math.pow(2, i));
			final Interval scaledInterval = Intervals.smallestContainingInterval(Intervals.scale(interval, scale));
			final int fi = i;
			final int[] ids = new int[]{2, 2, 2, 2};

			final CachedCellImg<UnsignedLongType, ?> virtualimg = Lazy.generate(scaledInterval,
					blockSize,
					new UnsignedLongType(),
					AccessFlags.setOf(AccessFlags.VOLATILE),
					rai -> {
						final IntervalView<RandomAccess<UnsignedLongType>> bundledView = Views.interval(new BundleView<>(rai), rai);
						final double[] center2D = new double[]{center[0] * scaledInterval.dimension(0), center[1] * scaledInterval.dimension(1)};
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


