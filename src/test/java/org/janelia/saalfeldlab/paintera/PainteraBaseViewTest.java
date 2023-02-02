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
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit.ApplicationTest;

import java.util.Random;

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
		viewer.addSingleScaleConnectomicsLabelSource(
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
		viewer.addSingleScaleConnectomicsRawSource(
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

	public static void main(String[] args) {

//		final TObjectIntHashMap<TFloatArrayList> map = new TObjectIntHashMap<>();
//		final float[] data = {1, 2, 3};
//		final TFloatArrayList key = new TFloatArrayList(data);
//		map.put(key, 10);
//		System.out.println(map.contains(key));
//		key.setQuick(2, 3);
//		System.out.println(map.contains(key));
//		key.setQuick(2, 4);
//		map.put(key, 20);
//		System.out.println(map.contains(key));
//		key.setQuick(2, 5);
//		System.out.println(map.contains(key));
//		key.setQuick(2, 4);
//		System.out.println(map.contains(key));
//		key.setQuick(2, 3);
//		System.out.println(map.contains(key));

		Paintera.whenPaintable(() -> {

			final Random random = new Random();
			final FinalInterval interval = Intervals.createMinSize(0, 0, 0, 500, 500, 500);
			final int[] blockSize = {25, 25, 25};

			int radius = 50;
			double[] center = new double[]{.75, .75, 0};

			final CachedCellImg<UnsignedLongType, ?>[] multiScaleImages = new CachedCellImg[4];

			for (int i = 0; i < multiScaleImages.length; i++) {
				final Interval scaledInterval = Intervals.smallestContainingInterval(Intervals.scale(interval, 1.0 - i / ((double)multiScaleImages.length)));
				final CachedCellImg<UnsignedLongType, ?> virtualimg = Lazy.generate(scaledInterval,
						blockSize,
						new UnsignedLongType(),
						AccessFlags.setOf(AccessFlags.VOLATILE),
						rai -> {
							final IntervalView<RandomAccess<UnsignedLongType>> bundledView = Views.interval(new BundleView<>(rai), rai);
							final double[] center2D = new double[]{ center[0]* scaledInterval.dimension(0), center[1]* scaledInterval.dimension(1)};
							LoopBuilder.setImages(bundledView).multiThreaded().forEachChunk(chunk -> {
								final double[] pos = new double[3];
								final double[] pos2D = new double[2];

								chunk.forEachPixel(pixel -> {
									pixel.localize(pos);
									/* Sphere*/
//								if (LinAlgHelpers.distance(pos, center) <= radius) {
//									pixel.get().set(10);
//								} else {
//									pixel.get().set(0);
//								}
									/* Cylinder */
									System.arraycopy(pos, 0, pos2D, 0, 2);
									if (LinAlgHelpers.distance(pos2D, center2D) < radius) {
										pixel.get().set(2);
									} else {
										pixel.get().set(0);
									}

								});
								return null;
							});
						}
				);
				multiScaleImages[i] = virtualimg;
			}

			final long[][] dims = new long[1][3];
			dims[0] = interval.dimensionsAsLongArray();

			final int[][] blocks = new int[1][3];
			blocks[0] = blockSize;





			final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();

			final var source = viewer.addSingleScaleConnectomicsLabelSource(
					multiScaleImages[0],
					new double[]{1.0, 1.0, 1.0},
					new double[]{0.0, 0.0, 0.0},
					10,
					"blub",
					new LabelBlockLookupAllBlocks(dims, blocks)
			);
			final var source2 = viewer.addSingleScaleConnectomicsLabelSource(
					multiScaleImages[1],
					new double[]{1.0, 1.0, 1.0},
					new double[]{0.0, 0.0, 0.0},
					10,
					"blub2",
					new LabelBlockLookupAllBlocks(dims, blocks)
			);
			source.getSelectedIds().activate(2);
			source2.getSelectedIds().activate(2);

		});
		Application.launch(Paintera.class);

	}
}


