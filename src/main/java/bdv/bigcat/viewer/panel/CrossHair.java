package bdv.bigcat.viewer.panel;

import bdv.bigcat.viewer.bdvfx.OverlayRendererGeneric;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CrossHair implements OverlayRendererGeneric< GraphicsContext >
{

	private int w, h;

	private final int strokeWidth = 1;

	private Color color = new Color( Color.WHITE.getRed(), Color.WHITE.getGreen(), Color.WHITE.getBlue(), 0.5 );

	public void setColor( final int r, final int g, final int b )
	{
		setColor( r, g, b, 127 );
	}

	public void setColor( final double r, final double g, final double b, final double a )
	{
		setColor( new Color( r, g, b, a ) );
	}

	public void setColor( final int r, final int g, final int b, final double a )
	{
		setColor( Color.rgb( r, g, b, a ) );
	}

	public void setColor( final Color color )
	{
		this.color = color;
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		w = width;
		h = height;
	}

	@Override
	public void drawOverlays( final GraphicsContext g )
	{

		if ( color.getOpacity() > 0 )
		{
			g.setStroke( color );
			g.setLineWidth( strokeWidth );
			g.strokeLine( 0, h / 2, w, h / 2 );
			g.strokeLine( w / 2, 0, w / 2, h );
		}

	}

}
