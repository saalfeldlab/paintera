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
package bdv.bigcat.label;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.viewer.ViewerPanel;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class LabelMultiSetIdPicker implements IdPicker
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< LabelMultisetType > labels;
	final protected RealRandomAccess< LabelMultisetType > labelAccess;

	public LabelMultiSetIdPicker(
			final ViewerPanel viewer,
			final RealRandomAccessible< LabelMultisetType > labels )
	{
		this.viewer = viewer;
		this.labels = labels;
		labelAccess = labels.realRandomAccess();
	}

	final static public long getMostSignificantId( final LabelMultisetType t )
	{
		long fragmentId = Label.TRANSPARENT;
		long maxCount = 0;
		for ( final Entry< Label > entry : t.entrySet() )
		{
			final Label label = entry.getElement();
			final long count = entry.getCount();

			if ( count > maxCount )
			{
				maxCount = count;
				fragmentId = label.id();
			}
		}
		return fragmentId;
	}

	@Override
	public long getIdAtDisplayCoordinate( final int x, final int y )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		return getMostSignificantId( labelAccess.get() );
	}

	@Override
	public long getIdAtWorldCoordinate( final double x, final double y, final double z )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( z, 2 );

		return getMostSignificantId( labelAccess.get() );
	}

	@Override
	public synchronized TLongHashSet getVisibleIds()
	{
		final TLongHashSet visibleIds = new TLongHashSet();
		final int w = viewer.getWidth();
		final int h = viewer.getHeight();
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewer.getState().getViewerTransform( viewerTransform );
		IntervalView< LabelMultisetType > screenLabels =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine( labels, viewerTransform ), 2, 0 ),
						new FinalInterval( w, h ) );

		for ( final LabelMultisetType pixel : Views.iterable( screenLabels ) )
		{
			for ( final Entry< Label > entry : pixel.entrySet() )
					visibleIds.add( entry.getElement().id() );
		}

		return visibleIds;
	}
}
