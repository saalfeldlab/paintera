package org.janelia.saalfeldlab.paintera;

// TODO uncomment imports and tests once #243 is fixed

import javafx.application.Application;
import net.imglib2.FinalInterval;
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

		Paintera.whenPaintable(() -> {

			final Random random = new Random();
			final FinalInterval interval = Intervals.createMinSize(0, 0, 0, 100, 100, 100);
			final int[] blockSize = {10, 10, 10};

			int radius = 50;
			double[] center = new double[]{
					(interval.max(0) - interval.min(0)) / 2.0,
					(interval.max(1) - interval.min(1)) / 2.0,
					(interval.max(2) - interval.min(2)) / 2.0
			};

			final CachedCellImg<UnsignedLongType, ?> virtualimg = Lazy.generate(interval,
					blockSize,
					new UnsignedLongType(),
					AccessFlags.setOf(AccessFlags.VOLATILE),
					rai -> {
						final IntervalView<RandomAccess<UnsignedLongType>> bundledView = Views.interval(new BundleView<>(rai), rai);
						LoopBuilder.setImages(bundledView).multiThreaded().forEachChunk(chunk -> {
							final double[] pos = new double[3];
							chunk.forEachPixel(pixel -> {
								pixel.localize(pos);
								if (LinAlgHelpers.distance(pos, center) <= radius) {
									pixel.get().set(10);
								} else {
									pixel.get().set(0);
								}

							});
							return null;
						});
					}
			);

			final long[][] dims = new long[1][3];
			dims[0] = interval.dimensionsAsLongArray();

			final int[][] blocks = new int[1][3];
			blocks[0] = blockSize;

			final PainteraBaseView viewer = Paintera.getPaintera().getBaseView();
			viewer.addSingleScaleConnectomicsLabelSource(
					virtualimg,
					new double[]{1.0, 1.0, 1.0},
					new double[]{0.0, 0.0, 0.0},
					10,
					"blub",
					new LabelBlockLookupAllBlocks(dims, blocks)
			);
		});
		Application.launch(Paintera.class);

	}
}


