package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;

public class HighlightingStreamConverterVolatileLabelMultisetType extends HighlightingStreamConverterVolatileType<LabelMultisetType, VolatileLabelMultisetType> {

	public HighlightingStreamConverterVolatileLabelMultisetType(final AbstractHighlightingARGBStream stream) {

		super(stream, new HighlightingStreamConverterLabelMultisetType(stream));
	}
}
