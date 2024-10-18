package org.janelia.saalfeldlab.paintera.stream;

import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.ARGBType;

public class HighlightingStreamConverterLabelMultisetType extends HighlightingStreamConverter<LabelMultisetType> {

	final static private double ONE_OVER_255 = 1.0 / 255.0;

	public HighlightingStreamConverterLabelMultisetType(final AbstractHighlightingARGBStream stream) {

		super(stream);
	}

	@Override public void convert(LabelMultisetType input, ARGBType output) {

		final var entries = input.entrySet();
		final int numEntries = entries.size();
		if (numEntries == 0) {
			final long emptyValue = 0;
			output.set(stream.argb(emptyValue));
		} else {
			double a = 0;
			double r = 0;
			double g = 0;
			double b = 0;
			double alphaCountSize = 0;
			for (final var entry : entries) {
				final int argb = stream.argb(entry.getElement().id());
				final double alpha = ARGBType.alpha(argb);
				final int count = numEntries == 1 ? 1 : entry.getCount();
				final double alphaCount = alpha * ONE_OVER_255 * count;
				a += alphaCount * alpha;
				r += alphaCount * ARGBType.red(argb);
				g += alphaCount * ARGBType.green(argb);
				b += alphaCount * ARGBType.blue(argb);
				alphaCountSize += alphaCount;
			}
			final double iAlphaCountSize = 1.0 / alphaCountSize;
			final int aInt = Math.min(255, (int)(a * iAlphaCountSize));
			final int rInt = Math.min(255, (int)(r * iAlphaCountSize));
			final int gInt = Math.min(255, (int)(g * iAlphaCountSize));
			final int bInt = Math.min(255, (int)(b * iAlphaCountSize));
			output.set(((aInt << 8 | rInt) << 8 | gInt) << 8 | bInt);
		}
	}
}
