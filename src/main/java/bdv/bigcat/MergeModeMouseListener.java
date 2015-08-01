/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.InteractiveDisplayCanvasComponent;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.SuperVoxel;
import bdv.labels.labelset.VolatileSuperVoxelMultisetType;
import bdv.viewer.ViewerPanel;

/**
 *
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class MergeModeMouseListener implements MouseListener
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< VolatileSuperVoxelMultisetType > labels;
	final protected RealRandomAccess< VolatileSuperVoxelMultisetType > labelAccess;

	public MergeModeMouseListener( final ViewerPanel viewer, final RealRandomAccessible< VolatileSuperVoxelMultisetType > labels )
	{
		this.viewer = viewer;
		this.labels = labels;
		labelAccess = labels.realRandomAccess();
	}

	@Override
	public void mouseClicked( final MouseEvent e )
	{
		final InteractiveDisplayCanvasComponent< AffineTransform3D > display = viewer.getDisplay();
		labelAccess.setPosition( e.getX(), 0 );
		labelAccess.setPosition( e.getY(), 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		System.out.println( "Mouse clicked at " + e.getX() + ", " + e.getY() );

		final VolatileSuperVoxelMultisetType labelValues = labelAccess.get();

		String labelSetString;
		if ( labelValues.isValid() )
		{
			labelSetString = "";
			for ( final Entry< SuperVoxel > label : labelValues.get().entrySet() )
				labelSetString += label.getElement().id() + ", ";

			labelSetString = labelSetString.substring( 0, labelSetString.length() - 2 );

		}
		else
			labelSetString = "invalid";

		System.out.println( " Labels = {" + labelSetString + "}" );
	}

	@Override
	public void mouseEntered( final MouseEvent e )
	{
		System.out.println( "Mouse entered at " + e.getX() + ", " + e.getY() );
	}

	/* (non-Javadoc)
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseExited( final MouseEvent arg0 )
	{
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	@Override
	public void mousePressed( final MouseEvent arg0 )
	{
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseReleased( final MouseEvent arg0 )
	{
		// TODO Auto-generated method stub

	}

}
