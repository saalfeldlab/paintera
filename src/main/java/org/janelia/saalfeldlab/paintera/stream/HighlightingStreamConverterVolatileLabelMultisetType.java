package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;

public class HighlightingStreamConverterVolatileLabelMultisetType extends HighlightingStreamConverter<VolatileLabelMultisetType> {

	private final HighlightingStreamConverterLabelMultisetType nonVolatileConverter;
	public HighlightingStreamConverterVolatileLabelMultisetType(final AbstractHighlightingARGBStream stream) {

		super(stream);
		nonVolatileConverter = new HighlightingStreamConverterLabelMultisetType(stream);
	}

	public HighlightingStreamConverterLabelMultisetType getNonVolatileConverter() {
		return nonVolatileConverter;
	}

	@Override
	public void convert(final VolatileLabelMultisetType input, final ARGBType output) {

		final boolean isValid = input.isValid();
		if (!isValid) {
			return;
		}
		nonVolatileConverter.convert(input.get(), output);
	}
}
