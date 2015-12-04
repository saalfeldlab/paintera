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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.SuperVoxel;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

/**
 *
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class MergeModeController implements MouseListener, KeyListener
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< VolatileLabelMultisetType > labels;
	final protected RealRandomAccess< VolatileLabelMultisetType > labelAccess;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	protected long activeFragmentId = 0;

	final GsonBuilder gsonBuilder = new GsonBuilder();
	{
		gsonBuilder.registerTypeAdapter( FragmentSegmentAssignment.class, new FragmentSegmentAssignment.FragmentSegmentSerializer() );
		//gsonBuilder.setPrettyPrinting();
	}
	final Gson gson = gsonBuilder.create();


	public MergeModeController(
			final ViewerPanel viewer,
			final RealRandomAccessible< VolatileLabelMultisetType > labels,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.colorStream = colorStream;
		this.assignment = assignment;
		labelAccess = labels.realRandomAccess();
	}

	@Override
	public void mouseClicked( final MouseEvent e )
	{
		labelAccess.setPosition( e.getX(), 0 );
		labelAccess.setPosition( e.getY(), 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		final VolatileLabelMultisetType labelValues = labelAccess.get();

		String labelSetString;
		if ( labelValues.isValid() )
		{
			labelSetString = "";
			final long oldActiveFragmentId = activeFragmentId;
			long maxCount = 0;
			for ( final Entry< SuperVoxel > entry : labelValues.get().entrySet() )
			{
				final SuperVoxel label = entry.getElement();
				final long count = entry.getCount();

				if ( count > maxCount )
				{
					maxCount = count;
					activeFragmentId = label.id();
				}
				labelSetString += entry.getElement().id() + ", ";
			}
			labelSetString = labelSetString.substring( 0, labelSetString.length() - 2 );

			if ( ( e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK ) != 0 )
			{
				System.out.println( "Merging" );

				assignment.mergeFragmentSegments( oldActiveFragmentId, activeFragmentId );
			}
			else
			{
				if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 )
				{
					System.out.println( "Detaching" );

					assignment.detachFragment( activeFragmentId );
				}

				colorStream.setActive( activeFragmentId );
			}

			colorStream.clearCache();

			viewer.requestRepaint();
		}
		else
			labelSetString = "invalid";

		System.out.println( " Labels = {" + labelSetString + "}" );
	}

	@Override
	public void mouseEntered( final MouseEvent e ) {}

	@Override
	public void mouseExited( final MouseEvent arg0 ) {}

	@Override
	public void mousePressed( final MouseEvent arg0 ) {}

	@Override
	public void mouseReleased( final MouseEvent arg0 ) {}

	@Override
	public void keyReleased( final KeyEvent e )	{}

	@Override
	public void keyPressed( final KeyEvent e ) {}

	@Override
	public void keyTyped( final KeyEvent e )
	{
		if ( e.getKeyChar() == 'c' )
		{
			colorStream.incSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
		else if ( e.getKeyChar() == 'C' )
		{
			colorStream.decSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
		else if ( e.getKeyChar() == 'e' )
		{
			System.out.println( gson.toJson( assignment ) );
		}
	}
}
