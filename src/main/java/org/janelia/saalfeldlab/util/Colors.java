package org.janelia.saalfeldlab.util;

import javafx.scene.paint.Color;
import net.imglib2.type.numeric.ARGBType;

public class Colors
{
	// #ff0088
	public static final Color CREMI = Color.rgb( 0xff, 0x00, 0x88, 1.0 );

	public static Color cremi( final double opacity )
	{
		final double factor = opacity / CREMI.getOpacity();
		return CREMI.deriveColor( 0, 1, 1, factor );
	}

	public static ARGBType toARGBType( final Color color )
	{
		final int r = ( int ) Math.round( 255 * color.getRed() );
		final int g = ( int ) Math.round( 255 * color.getGreen() );
		final int b = ( int ) Math.round( 255 * color.getBlue() );
		final int a = ( int ) Math.round( 255 * color.getOpacity() );
		return new ARGBType( ARGBType.rgba( r, g, b, a ) );
	}

	public static Color toColor( final ARGBType type )
	{
		final int value = type.get();
		final int r = ARGBType.red( value );
		final int g = ARGBType.green( value );
		final int b = ARGBType.blue( value );
		final int a = ARGBType.alpha( value );
		return Color.rgb( r, g, b, a / 255.0 );

	}
}
