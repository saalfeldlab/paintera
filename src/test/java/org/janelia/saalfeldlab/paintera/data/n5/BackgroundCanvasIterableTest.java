package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.ValuePair;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BackgroundCanvasIterableTest {

	@Test
	public void test() {

		final UnsignedLongType[] canvas = {
				new UnsignedLongType(1),
				new UnsignedLongType(Label.INVALID),
				new UnsignedLongType(Label.BACKGROUND),
				new UnsignedLongType(Label.TRANSPARENT)
		};

		final LabelMultisetType[] background = {
				LabelMultisetType.singleEntryWithSingleOccurrence(),
				LabelMultisetType.singleEntryWithSingleOccurrence(),
				LabelMultisetType.singleEntryWithSingleOccurrence(),
				LabelMultisetType.singleEntryWithSingleOccurrence()
		};

		final List<ValuePair<LabelMultisetType, UnsignedLongType>> backgroundAndCanvas = Arrays.asList(
				new ValuePair<>(background[0], canvas[0]),
				new ValuePair<>(background[1], canvas[1]),
				new ValuePair<>(background[2], canvas[2]),
				new ValuePair<>(background[3], canvas[3])
		);

		final Iterator<LabelMultisetType> iterator = new BackgroundCanvasIterable(backgroundAndCanvas).iterator();

		final LabelMultisetType v0 = iterator.next();
		assertNotSame(background[0], v0);
		assertEquals(v0.entrySet().size(), 1);
		assertEquals(1, v0.entrySet().iterator().next().getCount());
		assertEquals(1, v0.entrySet().iterator().next().getElement().id());
		assertEquals(1, v0.argMax());

		final LabelMultisetType v1 = iterator.next();
		assertSame(background[1], v1);

		final LabelMultisetType v2 = iterator.next();
		assertNotSame(background[2], v2);
		assertEquals(v2.entrySet().size(), 1);
		assertEquals(1, v2.entrySet().iterator().next().getCount());
		assertEquals(Label.BACKGROUND, v2.entrySet().iterator().next().getElement().id());
		assertEquals(Label.BACKGROUND, v2.argMax());

		final LabelMultisetType v3 = iterator.next();
		// Transparent should remove only in canvas and not be propagated into background
		// As discussed in saalfeldlab/paintera#305
		assertSame(background[3], v3);
	}

}
