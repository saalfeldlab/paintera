package bdv.bigcat.viewer.stream;

import bdv.labels.labelset.Label;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public abstract class HighlightingStreamConverter< T > implements Converter< T, ARGBType >, SetSeed
{

	protected final AbstractHighlightingARGBStream stream;

	public HighlightingStreamConverter( final AbstractHighlightingARGBStream stream )
	{
		super();
		this.stream = stream;
	}

	public void setAlpha( final int alpha )
	{
		stream.setAlpha( alpha );
	}

	public void setHighlightAlpha( final int alpha )
	{
		stream.setActiveSegmentAlpha( alpha );
	}

	public void setInvalidSegmentAlpha( final int alpha )
	{
		stream.setInvalidSegmentAlpha( alpha );
	}

	public int getAlpha()
	{
		return stream.getAlpha();
	}

	public int getHighlightAlpha()
	{
		return stream.getActiveSegmentAlpha();
	}

	public int getInvalidSegmentAlpha()
	{
		return stream.getInvalidSegmentAlpha();
	}

	private static long considerMaxUnsignedInt( final long val )
	{
		return val >= Integer.MAX_VALUE ? Label.INVALID : val;
	}

	@Override
	public void setSeed( final long seed )
	{
		this.stream.setSeed( seed );
	}

}
