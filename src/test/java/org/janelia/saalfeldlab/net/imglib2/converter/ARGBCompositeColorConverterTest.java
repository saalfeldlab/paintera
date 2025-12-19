package org.janelia.saalfeldlab.net.imglib2.converter;

import javafx.scene.paint.Color;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.volatiles.VolatileDoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.util.Colors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ARGBCompositeColorConverterTest {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Test
	public void test() {

		ARGBCompositeColorConverter<VolatileDoubleType, RealComposite<VolatileDoubleType>, Volatile<RealComposite<VolatileDoubleType>>> c = ARGBCompositeColorConverter.imp1(
				3);

		c.minProperty(0).set(0.0);
		c.minProperty(1).set(0.0);
		c.minProperty(2).set(0.0);

		c.maxProperty(0).set(1.0);
		c.maxProperty(1).set(1.0);
		c.maxProperty(2).set(1.0);

		c.colorProperty(0).set(Colors.toARGBType(Color.RED));
		// Color.GREEN is only 127 green
		c.colorProperty(1).set(Colors.toARGBType(Color.valueOf("#00ff00")));
		c.colorProperty(2).set(Colors.toARGBType(Color.BLUE));

		double[] data = {
				1.0, 1.0, 0.0, 0.0,
				1.0, 0.0, 1.0, 0.0,
				1.0, 0.0, 0.0, 1.0
		};
		RandomAccessibleInterval<DoubleType> img = ArrayImgs.doubles(data, data.length / 3, 3);

		var volatileTypeConverter = new Converter<DoubleType, VolatileDoubleType>() {

			@Override public void convert(DoubleType input, VolatileDoubleType output) {

				output.setValid(true);
				output.get().set(input);
			}
		};
		RandomAccessibleInterval<VolatileDoubleType> asVolatile = Converters.convert(img, volatileTypeConverter, new VolatileDoubleType());
		RandomAccessibleInterval<RealComposite<VolatileDoubleType>> collapsed = Views.collapseReal(asVolatile);

		int[] groundTruthData = {
				0xFFFFFFFF,
				0xFFFF0000,
				0xFF00FF00,
				0xFF0000FF
		};

		final Converter<RealComposite<VolatileDoubleType>, VolatileWithSet<RealComposite<VolatileDoubleType>>> viewerConverter = (source, target) -> {
			target.setT(source);
			target.setValid(source.get(0).isValid());
		};

		final RandomAccessibleInterval<ARGBType> groundTruth = ArrayImgs.argbs(groundTruthData, groundTruthData.length);

		LOG.debug("Dimensions: {}", Intervals.dimensionsAsLongArray(collapsed));

		RandomAccessibleInterval<VolatileWithSet<RealComposite<VolatileDoubleType>>> volatileComposite =
				Converters.convert(collapsed, viewerConverter, new VolatileWithSet<>(null, true));

		RandomAccessibleInterval<ARGBType> asColor = Converters.convert(volatileComposite, c, new ARGBType(1));

		Views
				.interval(Views.pair(groundTruth, asColor), asColor)
				.forEach(p -> assertEquals(p.getA().get(), p.getB().get()));
	}

}
