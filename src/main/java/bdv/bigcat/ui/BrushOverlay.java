/**
 *
 */
package bdv.bigcat.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import bdv.util.Affine3DHelpers;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class BrushOverlay implements OverlayRenderer
{
	final static BasicStroke stroke = new BasicStroke( 2 );
	final protected ViewerPanel viewer;
	protected int x, y, width, height, radius = 5;
	protected boolean visible = false;
	final AffineTransform3D viewerTransform = new AffineTransform3D();

	public BrushOverlay( final ViewerPanel viewer )
	{
		this.viewer = viewer;
	}

	public void setPosition( final int x, final int y )
	{
		this.x = x;
		this.y = y;
	}

	public void setRadius( final int radius )
	{
		this.radius = radius;
	}

	public void setVisible( final boolean visible )
	{
		this.visible = visible;
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		if ( visible )
		{
			final Graphics2D g2d = ( Graphics2D )g;

			final double scale;
			synchronized ( viewer )
			{
				viewer.getState().getViewerTransform( viewerTransform );
				scale = Affine3DHelpers.extractScale( viewerTransform, 0 );
			}
			final double scaledRadius = scale * radius;

			if (
					x + scaledRadius > 0 &&
					x - scaledRadius < width &&
					y + scaledRadius > 0 &&
					y - scaledRadius < height )
			{
				final int roundScaledRadius = ( int )Math.round( scaledRadius );
				g2d.setColor( Color.WHITE );
				g2d.setStroke( stroke );
				g2d.drawOval( x - roundScaledRadius, y - roundScaledRadius, 2 * roundScaledRadius + 1, 2 * roundScaledRadius + 1 );
			}
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

}
