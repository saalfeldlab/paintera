package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;

public abstract class HighlightingStreamConverterVolatileType<I, V extends Volatile<I>> extends HighlightingStreamConverter<V> {

	protected final HighlightingStreamConverter<I> nonVolatileConverter;

	public HighlightingStreamConverterVolatileType(AbstractHighlightingARGBStream stream, HighlightingStreamConverter<I> nonVolatileConverter) {

		super(stream);
		this.nonVolatileConverter = nonVolatileConverter;
	}

	public HighlightingStreamConverter<I> getNonVolatileConverter() {

		return nonVolatileConverter;
	}

	@Override public void convert(V input, ARGBType output) {

		final boolean isValid = input.isValid();
		if (!isValid) {
			return;
		}
		getNonVolatileConverter().convert(input.get(), output);
	}
}
