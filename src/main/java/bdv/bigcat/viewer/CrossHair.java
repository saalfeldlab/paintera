package bdv.bigcat.viewer;

import java.awt.Color;
import java.awt.Graphics;

import net.imglib2.ui.OverlayRenderer;

public class CrossHair implements OverlayRenderer
{

	private int w, h;

	private Color color = new Color( Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 127 );

	public void setColor( final int r, final int g, final int b )
	{
		setColor( r, g, b, 127 );
	}

	public void setColor( final int r, final int g, final int b, final int a )
	{
		this.color = new Color( r, g, b, a );
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		w = width;
		h = height;
	}

	@Override
	public void drawOverlays( final Graphics g )
	{

		if ( color.getAlpha() > 0 )
		{
			g.setColor( color );
			g.drawLine( 0, h / 2, w, h / 2 );
			g.drawLine( w / 2, 0, w / 2, h );
		}

	}

}
