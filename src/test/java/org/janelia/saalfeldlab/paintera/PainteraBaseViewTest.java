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

	ApplicationTest.launch(Paintera.class);
	final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 20, 30);
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
