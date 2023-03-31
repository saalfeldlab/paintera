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
import net.imglib2.view.BundleView;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit.ApplicationTest;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class PainteraBaseViewTest extends FxRobot {

	@BeforeClass
	public static void setup() {

		System.setProperty("headless.geometry", "1600x1200-32");
	}

	@Test
	public void testAddSingleScaleLabelSource() throws Exception {
		/* With default log level of INFO we get a flod of WARNINGs that the ConditionalSupport.SCENE3D is not available in the headless testing.
		 * 	Since we are not relying on that here, just set log to ERROR to ignore that message. */
		ApplicationTest.launch(Paintera.class, "--log-level=ERROR");
		final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 15, 20);
		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsLabelSource(
				labels,
				new double[]{1.0, 1.0, 1.0},
				new double[]{0.0, 0.0, 0.0},
				1,
				"blub", new LabelBlockLookupNoBlocks());

		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			viewer.stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}

	@Test
	public void testAddSingleScaleConnectomicsRawSource() throws Exception {
		/* With default log level of INFO we get a flod of WARNINGs that the ConditionalSupport.SCENE3D is not available in the headless testing.
		 * 	Since we are not relying on that here, just set log to ERROR to ignore that message. */
		ApplicationTest.launch(Paintera.class, "--log-level=ERROR");
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
				"blub"
		);

		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			viewer.stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}

	@Test
	public void testAddMultiScaleConnectomicsRawSource() throws Exception {
		/* With default log level of INFO we get a flod of WARNINGs that the ConditionalSupport.SCENE3D is not available in the headless testing.
		 * 	Since we are not relying on that here, just set log to ERROR to ignore that message. */
		ApplicationTest.launch(Paintera.class, "--log-level=ERROR");
		final Random random = new Random();

		final double[] center2D = new double[]{500, 500};
		final var multiscale = generateMultiscaleLabels(4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				new double[]{500, 500, 0},
				(scale, chunk) -> {
					generateCylinder(center2D, scale, chunk);
					return null;
				});

		final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
		viewer.addConnectomicsRawSource(
				multiscale.images,
				multiscale.resolutions,
				multiscale.translations,
				0, 255,
				"multiscale"
		);

		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			viewer.stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}

	private static void generateCylinder(double[] center2D, Double scale, LoopBuilder.Chunk<Consumer<RandomAccess<UnsignedLongType>>> chunk) {

		BiConsumer<Double, LoopBuilder.Chunk<UnsignedLongType>> test = (s, c) -> {
			final double[] pos = new double[3];
			final double[] pos2D = new double[2];

			chunk.forEachPixel(pixel -> {
				pixel.localize(pos);
				System.arraycopy(pos, 0, pos2D, 0, 2);
				if (LinAlgHelpers.distance(pos2D, center2D) < 25 * scale) {
					pixel.get().set(2);
				} else {
					pixel.get().set(0);
				}

			});
		};
	}

	@Test
	public void testAddMultiScaleConnectomicsLabelSource() throws Exception {
		/* With default log level of INFO we get a flod of WARNINGs that the ConditionalSupport.SCENE3D is not available in the headless testing.
		 * 	Since we are not relying on that here, just set log to ERROR to ignore that message. */
		ApplicationTest.launch(Paintera.class, "--log-level=ERROR");
		final Random random = new Random();

		final var multiscale = generateMultiscaleLabels(4,
				Intervals.createMinSize(0, 0, 0, 1000, 1000, 1000),
				new int[]{64, 64, 64},
				new double[]{500, 500, 0},
				(scale, chunk) -> {
					generateCylinder(new double[]{500, 500}, scale, chunk);
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

		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			viewer.stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}

	public static void main(String[] args) {

		Paintera.whenPaintable(() -> {

			final FinalInterval interval = Intervals.createMinSize(0, 0, 0, 100, 100, 100);
			final int[] blockSize = {25, 25, 25};
			int radius = 50;
			double[] center = new double[]{50, 50, 50};
			double[] center2D = new double[]{50, 50};
			final var generatedMultiscaleCylinder = generateMultiscaleLabels(
					4,
					interval,
					blockSize,
					center,
					(scale, chunk) -> {
						generateCylinder(center, scale, chunk);
						return null;
					});

			final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();

			final var raw = viewer.addConnectomicsRawSource(
					generatedMultiscaleCylinder.images,
					generatedMultiscaleCylinder.resolutions,
					generatedMultiscaleCylinder.translations,
					0,
					10,
					"raw"
			);
			raw.converter().setMax(10.0);

			final var labels = viewer.addConnectomicsLabelSource(
					generatedMultiscaleCylinder.images,
					generatedMultiscaleCylinder.resolutions,
					generatedMultiscaleCylinder.translations,
					10,
					"labels",
					new LabelBlockLookupAllBlocks(generatedMultiscaleCylinder.dims, generatedMultiscaleCylinder.blocks)
			);
			labels.getSelectedIds().activate(2);

			final MeshSettings meshSettings = labels.getMeshManager().getSettings();
			meshSettings.setCoarsestScaleLevel(generatedMultiscaleCylinder.images.length - 1);
			meshSettings.setFinestScaleLevel(0);
			meshSettings.setLevelOfDetail(5);

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

	@NotNull private static GeneratedMultiscaleImage<UnsignedLongType> generateMultiscaleLabels(int numScales, FinalInterval interval, int[] blockSize,
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


