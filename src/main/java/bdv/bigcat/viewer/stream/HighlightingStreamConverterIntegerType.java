package bdv.bigcat.viewer.stream;

import java.util.function.ToLongFunction;

import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class HighlightingStreamConverterIntegerType< I extends RealType< I > > extends HighlightingStreamConverter< I >
{

	private final ToLongFunction< I > toLong;

	public HighlightingStreamConverterIntegerType( final AbstractHighlightingARGBStream stream, final ToLongFunction< I > toLong )
	{
		super( stream );
		this.toLong = toLong;
	}

	@Override
	public void convert( final I input, final ARGBType output )
	{
		output.set( stream.argb( toLong.applyAsLong( input ) ) );
	}

}
