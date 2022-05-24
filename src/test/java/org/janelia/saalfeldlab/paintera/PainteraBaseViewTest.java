package org.janelia.saalfeldlab.paintera;

// TODO uncomment imports and tests once #243 is fixed

import com.sun.javafx.application.PlatformImpl;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig;
import org.junit.Before;
import org.junit.Test;

public class PainteraBaseViewTest {

  	@Before
  	public void initialize() {
  		System.setProperty("glass.platform", "Monocle");
  		System.setProperty("monocle.platform", "Headless");
  		System.setProperty("prism.order", "sw");
  		PlatformImpl.startup(() -> {});
  	}

  	@Test
  	public void testAddSingleScaleLabelSource() {
  		final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 20, 30);
  		final PainteraBaseView viewer = new PainteraBaseView(3, new KeyAndMouseConfig());
  		viewer.addSingleScaleLabelSource(
  				labels,
  				new double[] {1.0, 1.0, 1.0},
  				new double[] {0.0, 0.0, 0.0},
  				1,
  				"blub");
  	}

}
