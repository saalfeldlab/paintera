package org.janelia.saalfeldlab.paintera;

// TODO uncomment imports and tests once #243 is fixed

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit.ApplicationTest;

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
		viewer.addSingleScaleLabelSource(
				labels,
				new double[]{1.0, 1.0, 1.0},
				new double[]{0.0, 0.0, 0.0},
				1,
				"blub");

		InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
			viewer.stop();
			Paintera.getPaintera().getProjectDirectory().close();
		});
		FxToolkit.cleanupApplication(Paintera.getApplication());
	}


}
