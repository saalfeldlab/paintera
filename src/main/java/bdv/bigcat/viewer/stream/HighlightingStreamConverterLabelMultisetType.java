package bdv.bigcat.viewer.stream;

import java.util.Iterator;

import net.imglib.type.label.Label;
import net.imglib.type.label.LabelMultiset;
import net.imglib.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;

public class HighlightingStreamConverterLabelMultisetType extends HighlightingStreamConverter< VolatileLabelMultisetType >
{

	public HighlightingStreamConverterLabelMultisetType( final AbstractHighlightingARGBStream stream )
	{
		super( stream );
	}

	@Override
	public void convert( final VolatileLabelMultisetType input, final ARGBType output )
	{
		// TODO this needs to use all LabelMultisetType, not just first
		// entry
		final Iterator< LabelMultiset.Entry< Label > > it = input.get().entrySet().iterator();
		output.set( stream.argb( it.hasNext() ? considerMaxUnsignedInt( it.next().getElement().id() ) : Label.INVALID ) );
	}

	private static long considerMaxUnsignedInt( final long val )
	{
		return val >= Integer.MAX_VALUE ? Label.INVALID : val;
	}

}
