package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.ValuePair;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BackgroundCanvasIterableTest {

	@Test
	public void test()
	{
		final UnsignedLongType[] canvas = {
		new UnsignedLongType(1),
				new UnsignedLongType(Label.INVALID),
				new UnsignedLongType(Label.BACKGROUND),
				new UnsignedLongType(Label.TRANSPARENT)
		};

		final LabelMultisetType[] background = {
				FromIntegerTypeConverter.geAppropriateType(),
				FromIntegerTypeConverter.geAppropriateType(),
				FromIntegerTypeConverter.geAppropriateType(),
				FromIntegerTypeConverter.geAppropriateType()
		};

		final List<ValuePair<LabelMultisetType, UnsignedLongType>> backgroundAndCanvas = Arrays.asList(
				new ValuePair<>(background[0], canvas[0]),
				new ValuePair<>(background[1], canvas[1]),
				new ValuePair<>(background[2], canvas[2]),
				new ValuePair<>(background[3], canvas[3])
		);

		final Iterator<LabelMultisetType> iterator = new BackgroundCanvasIterable(backgroundAndCanvas).iterator();

		final LabelMultisetType v0 = iterator.next();
		Assert.assertNotSame(background[0], v0);
		Assert.assertEquals(v0.entrySet().size(), 1);
		Assert.assertEquals(1, v0.entrySet().iterator().next().getCount());
		Assert.assertEquals(1, v0.entrySet().iterator().next().getElement().id());
		Assert.assertEquals(1, v0.argMax());

		final LabelMultisetType v1 = iterator.next();
		Assert.assertSame(background[1], v1);

		final LabelMultisetType v2 = iterator.next();
		Assert.assertNotSame(background[2], v2);
		Assert.assertEquals(v2.entrySet().size(), 1);
		Assert.assertEquals(1, v2.entrySet().iterator().next().getCount());
		Assert.assertEquals(Label.BACKGROUND, v2.entrySet().iterator().next().getElement().id());
		Assert.assertEquals(Label.BACKGROUND, v2.argMax());


		final LabelMultisetType v3 = iterator.next();
		Assert.assertNotSame(background[3], v3);
		Assert.assertEquals(v0.entrySet().size(), 1);
		Assert.assertEquals(1, v3.entrySet().iterator().next().getCount());
		Assert.assertEquals(Label.TRANSPARENT, v3.entrySet().iterator().next().getElement().id());
		Assert.assertEquals(Label.TRANSPARENT, v3.argMax());
	}


}
