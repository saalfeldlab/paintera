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
import net.imglib2.type.volatiles.VolatileARGBType;

/**
 * TODO make the converter reference and use a lookuptable instead of ColorStream.
 * TODO use alpha and calculate alpha
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class VolatileSuperVoxelMultisetARGBConverter implements Converter< VolatileSuperVoxelMultisetType, VolatileARGBType >
{
	@Override
	public void convert( final VolatileSuperVoxelMultisetType input, final VolatileARGBType output )
	{
//		final double r = 0;
//		final double g = 0;
//		final double b = 0;
//		final int size = 0;

		if ( input.isValid() )
		{
			output.setValid( true );
			if ( input.get().contains( 7131l ) )
				output.set( ARGBType.rgba( 0, 250, 0, 255 ) );
			else
				output.set( ARGBType.rgba( 0, 0, 0, 255 ) );

//			for ( final Entry< SuperVoxel > entry : input.get().entrySet() )
//			{
//				final long superVoxelId = entry.getElement().id();
//				final int count = entry.getCount();
//				final int argb = ColorStream.get( superVoxelId );
//				r += count * ARGBType.red( argb );
//				g += count * ARGBType.green( argb );
//				b += count * ARGBType.blue( argb );
//				size += count;
//			}
//			r = Math.min( 255, r / size );
//			g = Math.min( 255, g / size );
//			b = Math.min( 255, b / size );
//			output.setValid( true );
//			output.set( ARGBType.rgba( r, g, b, 255 ) );
		}
		else {
			output.setValid( false );
		}
	}
}
