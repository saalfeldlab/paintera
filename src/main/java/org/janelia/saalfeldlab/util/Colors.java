package org.janelia.saalfeldlab.util;

import javafx.scene.paint.Color;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class Colors
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	// #ff0088
	public static final Color CREMI = Color.rgb(0xff, 0x00, 0x88, 1.0);

	public static Color cremi(final double opacity)
	{
		final double factor = opacity / CREMI.getOpacity();
		return CREMI.deriveColor(0, 1, 1, factor);
	}

	public static ARGBType toARGBType(final Color color)
	{
		final int r = (int) Math.round(255 * color.getRed());
		final int g = (int) Math.round(255 * color.getGreen());
		final int b = (int) Math.round(255 * color.getBlue());
		final int a = (int) Math.round(255 * color.getOpacity());
		final ARGBType argb = new ARGBType(ARGBType.rgba(r, g, b, a));
		LOG.debug("color={} argb={}", color, argb);
		return argb;
	}

	public static Color toColor(final ARGBType type)
	{
		final int value = type.get();
		final int r     = ARGBType.red(value);
		final int g     = ARGBType.green(value);
		final int b     = ARGBType.blue(value);
		final int a     = ARGBType.alpha(value);
		return Color.rgb(r, g, b, a / 255.0);
	}

	public static String toHTML(final ARGBType color)
	{
		final int c = color.get();
		return String.format(
				"#%02X%02X%02X",
				ARGBType.red(c),
				ARGBType.green(c),
				ARGBType.blue(c)
		                    );

	}

	public static String toHTML(final Color color)
	{
		final int r = (int) Math.round(color.getRed() * 255) & 0xff;
		final int g = (int) Math.round(color.getGreen() * 255) & 0xff;
		final int b = (int) Math.round(color.getBlue() * 255) & 0xff;
		final int a = (int) Math.round(color.getOpacity()) * 255 & 0xff;
		return String.format("#%02X%02X%02X%02X", r, g, b, a);
	}

	public static ARGBType toARGBType(final String html)
	{
		return Colors.toARGBType(Color.web(html));
	}
}
