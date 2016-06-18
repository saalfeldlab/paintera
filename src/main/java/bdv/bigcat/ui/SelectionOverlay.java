/**
 *
 */
package bdv.bigcat.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import bdv.bigcat.control.SelectionController;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;
import bdv.viewer.ViewerPanel;
import net.imglib2.ui.OverlayRenderer;

/**
 * Visualizes the current state of fragment and segment ID selection.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class SelectionOverlay implements OverlayRenderer
{
	final protected ViewerPanel viewer;
	final protected SelectionController selectionController;
	final protected FragmentSegmentAssignment assignment;
	final protected ARGBStream colorStream;
	protected boolean visible = true;
	protected int width, height;

	public SelectionOverlay(
			final ViewerPanel viewer,
			final SelectionController selectionController,
			final FragmentSegmentAssignment assignment,
			final ARGBStream colorStream )
	{
		this.viewer = viewer;
		this.selectionController = selectionController;
		this.assignment = assignment;
		this.colorStream = colorStream;
	}

	public void setVisible( final boolean visible )
	{
		this.visible = visible;
	}

	public void toggleVisible()
	{
		visible = !visible;
	}


	/**
	 * Using unsigned uint64 formatting from
	 * http://stackoverflow.com/questions/7031198/java-signed-long-to-unsigned-long-string
	 *
	 * @param id
	 * @return
	 */
	final static private String idString( final long id )
	{
		if ( id == Label.TRANSPARENT )
			return "         transparent";
		if ( id == Label.INVALID )
			return "           completed";
		else
		{
			final long idDiv10 = Long.divideUnsigned( id, 10 );
			return idDiv10 == 0 ? String.format( "%20d", id ) : String.format( "%19d%d", idDiv10, Long.remainderUnsigned( id, 10 ) );
		}
	}

	final void drawBox(
			final String title,
			final Graphics2D g2d,
			final int top,
			final int left,
			final long fid )
	{
		final long sid = assignment.getSegment( fid );
		final int argb = colorStream.argb( fid );

		final Color color = new Color( argb, false );
		final double brightness =
				color.getRed() * 0.3 +
				color.getGreen() * 0.6 +
				color.getBlue() * 0.1;

		g2d.setColor( color );
		g2d.fillRect( left, top, 186, 64 );

		g2d.setPaint( brightness < 127 ? Color.WHITE : Color.BLACK );
		g2d.setFont( new Font( "Monospaced", Font.PLAIN, 12 ) );
		g2d.drawString( title, left + 8, top + 20 );
		g2d.drawString( "FID", left + 8, top + 36 );
		g2d.drawString( "SID", left + 8, top + 52 );

		g2d.drawString( idString( fid ), left + 36, top + 36 );
		g2d.drawString( idString( sid ), left + 36, top + 52 );
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		if ( visible )
		{
			final Graphics2D g2d = ( Graphics2D )g;

			g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			g2d.setComposite( AlphaComposite.SrcOver );


			final int top = height - 64;
			final int left = width - 186;

			final long fid = selectionController.getActiveFragmentId();
			final long fidHover = selectionController.getHoverFragmentId();

			drawBox( "selection", g2d, top, left, fid );
			drawBox( "mouse over", g2d, top - 64, left, fidHover );
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

}
