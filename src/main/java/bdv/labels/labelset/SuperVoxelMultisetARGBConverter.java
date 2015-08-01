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
package bdv.labels.labelset;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.util.ColorStream;

/**
 * TODO make the converter reference and use a lookuptable instead of ColorStream.
 * TODO use alpha and calculate alpha
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class SuperVoxelMultisetARGBConverter implements Converter< SuperVoxelMultisetType, ARGBType >
{
	@Override
	public void convert( final SuperVoxelMultisetType input, final ARGBType output )
	{
		double r = 0;
		double g = 0;
		double b = 0;
		int size = 0;
		for ( final Entry< SuperVoxel > entry : input.entrySet() )
		{
			final long superVoxelId = entry.getElement().id();
			final int count = entry.getCount();
			final int argb = ColorStream.get( superVoxelId );
			r += count * ARGBType.red( argb );
			g += count * ARGBType.green( argb );
			b += count * ARGBType.blue( argb );
			size += count;
		}
		r = Math.min( 255, r / size );
		g = Math.min( 255, g / size );
		b = Math.min( 255, b / size );
		output.set( ARGBType.rgba( r, g, b, 255 ) );
	}
}
