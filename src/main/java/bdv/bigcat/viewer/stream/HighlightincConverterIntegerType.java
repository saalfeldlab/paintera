package bdv.bigcat.viewer.stream;

import java.util.function.ToLongFunction;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class HighlightincConverterIntegerType< I extends RealType< I > > implements Converter< I, ARGBType >
{

	private final AbstractHighlightingARGBStream stream;

	private final ToLongFunction< I > toLong;

	public HighlightincConverterIntegerType( final AbstractHighlightingARGBStream stream, final ToLongFunction< I > toLong )
	{
		super();
		this.stream = stream;
		this.toLong = toLong;
	}

	@Override
	public void convert( final I input, final ARGBType output )
	{
		output.set( stream.argb( toLong.applyAsLong( input ) ) );
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

}
