package org.janelia.saalfeldlab.paintera;

import com.sun.javafx.application.PlatformImpl;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrderNotSupported;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class PainteraBaseViewTest {

	@Before
	public void initialize() {
		System.setProperty("glass.platform", "Monocle");
		System.setProperty("monocle.platform", "Headless");
		System.setProperty("prism.order", "sw");
		PlatformImpl.startup(() -> {});
	}

	@Test
	public void testAddSingleScaleLabelSource() throws IOException, AxisOrderNotSupported {
		final RandomAccessibleInterval<UnsignedLongType> labels = ArrayImgs.unsignedLongs(10, 20, 30);
		final PainteraBaseView.DefaultPainteraBaseView viewer = PainteraBaseView.defaultView();
		viewer.baseView.addSingleScaleLabelSource(
				labels,
				new double[] {1.0, 1.0, 1.0},
				new double[] {0.0, 0.0, 0.0},
				1,
				"blub");
	}

}
