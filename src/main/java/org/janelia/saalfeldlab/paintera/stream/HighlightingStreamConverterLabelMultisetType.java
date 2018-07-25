package org.janelia.saalfeldlab.paintera.stream;

import java.util.Set;

import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;

public class HighlightingStreamConverterLabelMultisetType extends HighlightingStreamConverter<VolatileLabelMultisetType>
{
	final static private double ONE_OVER_255 = 1.0 / 255.0;

	public HighlightingStreamConverterLabelMultisetType(final AbstractHighlightingARGBStream stream)
	{
		super(stream);
	}

	@Override
	public void convert(final VolatileLabelMultisetType input, final ARGBType output)
	{
		// TODO this needs to use all LabelMultisetType, not just first
		final boolean isValid = input.isValid();
		if (!isValid) { return; }
		// entry
		final Set<Entry<Label>> entries = input.get().entrySet();
		if (entries.size() == 0)
		{
			output.set(stream.argb(Label.INVALID));
		}
		else
		{
			double a              = 0;
			double r              = 0;
			double g              = 0;
			double b              = 0;
			double alphaCountSize = 0;
			for (final Entry<Label> entry : entries)
			{
				final int    argb       = stream.argb(entry.getElement().id());
				final double alpha      = ARGBType.alpha(argb);
				final double alphaCount = alpha * ONE_OVER_255 * entry.getCount();
				a += alphaCount * alpha;
				r += alphaCount * ARGBType.red(argb);
				g += alphaCount * ARGBType.green(argb);
				b += alphaCount * ARGBType.blue(argb);
				alphaCountSize += alphaCount;
			}
			final double iAlphaCountSize = 1.0 / alphaCountSize;
			final int    aInt            = Math.min(255, (int) (a * iAlphaCountSize));
			final int    rInt            = Math.min(255, (int) (r * iAlphaCountSize));
			final int    gInt            = Math.min(255, (int) (g * iAlphaCountSize));
			final int    bInt            = Math.min(255, (int) (b * iAlphaCountSize));
			output.set(((aInt << 8 | rInt) << 8 | gInt) << 8 | bInt);
		}
		//		final Iterator< LabelMultiset.Entry< Label > > it = input.get().entrySet().iterator();
		//		output.set( stream.argb( it.hasNext() ? considerMaxUnsignedInt( it.next().getElement().id() ) : Label.INVALID ) );
	}

}
