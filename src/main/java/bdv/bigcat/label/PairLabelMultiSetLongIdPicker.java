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

import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.PairLabelMultisetLongARGBConverter;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.util.LocalIdService;
import bdv.viewer.ViewerPanel;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class PairLabelMultiSetLongIdPicker implements IdPicker
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< Pair< LabelMultisetType, LongType > > labels;
	final protected RealRandomAccess< Pair< LabelMultisetType, LongType > > labelAccess;

	public PairLabelMultiSetLongIdPicker(
			final ViewerPanel viewer,
			final RealRandomAccessible< Pair< LabelMultisetType, LongType > > labels )
	{
		this.viewer = viewer;
		this.labels = labels;
		labelAccess = labels.realRandomAccess();
	}

	final private long getId()
	{
		final Pair< LabelMultisetType, LongType > ab = labelAccess.get();
		final LongType b = ab.getB();
		long id = b.get();
		if ( id == Label.TRANSPARENT )
			id = LabelMultiSetIdPicker.getMostSignificantId( ab.getA() );

		return id;
	}

	@Override
	public synchronized long getIdAtDisplayCoordinate( final int x, final int y )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		return getId();
	}

	@Override
	public long getIdAtWorldCoordinate( final double x, final double y, final double z )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( z, 2 );

		return getId();
	}

	@Override
	public synchronized TLongHashSet getVisibleIds()
	{
		final TLongHashSet visibleIds = new TLongHashSet();
		final int w = viewer.getWidth();
		final int h = viewer.getHeight();
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewer.getState().getViewerTransform( viewerTransform );
		IntervalView< Pair< LabelMultisetType, LongType > > screenLabels =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine( labels, viewerTransform ), 2, 0 ),
						new FinalInterval( w, h ) );

		for ( final Pair< LabelMultisetType, LongType > pixel : Views.iterable( screenLabels ) )
		{
			final long b = pixel.getB().get();
			if ( b == Label.TRANSPARENT )
			{
				final LabelMultisetType a = pixel.getA();
				for ( final Entry< Label > entry : a.entrySet() )
					visibleIds.add( entry.getElement().id() );
			}
			else
				visibleIds.add( b );
		}

		return visibleIds;
	}

	/**
	 * Visualization to test how it works.
	 *
	 * @param screenLabels
	 */
	@SuppressWarnings( "unused" )
	private void visualizeVisibleIds(
			final RandomAccessibleInterval< Pair< LabelMultisetType, LongType > > screenLabels )
	{
		final GoldenAngleSaturatedARGBStream argbStream =
				new GoldenAngleSaturatedARGBStream(
						new FragmentSegmentAssignment(
								new LocalIdService() ) );

		final RandomAccessibleInterval< ARGBType > convertedScreenLabels =
				Converters.convert(
						screenLabels,
						new PairLabelMultisetLongARGBConverter( argbStream ),
						new ARGBType() );

		ImageJFunctions.show( convertedScreenLabels );
	}
}
