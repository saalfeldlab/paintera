package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.Volatile;
import net.imglib2.type.numeric.IntegerType;

public class HighlightingStreamConverterVolatileIntegerType<I extends IntegerType<I>, V extends Volatile<I>> extends HighlightingStreamConverterVolatileType<I, V> {

	public HighlightingStreamConverterVolatileIntegerType(final AbstractHighlightingARGBStream stream) {

		super(stream, new HighlightingStreamConverterIntegerType(stream));
	}
}
