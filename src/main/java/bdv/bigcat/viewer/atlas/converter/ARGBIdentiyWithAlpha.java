package bdv.bigcat.viewer.atlas.converter;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class ARGBIdentiyWithAlpha implements Converter< ARGBType, ARGBType >
{

	private int alpha = 0xff000000;

	final static double ONE_OVER_ALPHA_SQUARED_MAX = 255.0 / ( 0xff * 0xff );

	final static int RGB_BITS = 0x00ffffff;

	public ARGBIdentiyWithAlpha( final int alpha )
	{
		this.setAlpha( alpha );
	}

	public void setAlpha( final int alpha )
	{
		this.alpha = ( alpha & 0x000000ff ) << 24;
	}

	public int getAlpha()
	{
		return this.alpha >>> 24;
	}

	@Override
	public void convert( final ARGBType input, final ARGBType output )
	{
		final int val = input.get();
		final int alpha = val >>> 24;
		final int targetAlpha = ( int ) ( alpha * this.alpha * ONE_OVER_ALPHA_SQUARED_MAX );
		final int target = ( targetAlpha & 0x000000ff ) << 24 | val & RGB_BITS;
		output.set( target );
	}

}
