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

import bdv.bigcat.ui.ARGBStream;
import bdv.labels.labelset.Multiset.Entry;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Pair;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class PairVolatileLabelMultisetLongARGBConverter
		implements Converter< Pair< VolatileLabelMultisetType, LongType >, VolatileARGBType >
{
	static public long TRANSPARENT_LABEL = Long.MIN_VALUE;
	final static private double iFF = 1.0 / 255.0;

	final protected ARGBStream argbStream;

	public PairVolatileLabelMultisetLongARGBConverter( final ARGBStream argbStream )
	{
		this.argbStream = argbStream;
	}

	protected void convertValid( final VolatileLabelMultisetType input, final VolatileARGBType output )
	{
		double a = 0;
		double r = 0;
		double g = 0;
		double b = 0;
		double alphaCountSize = 0;

		for ( final Entry< SuperVoxel > entry : input.get().entrySet() )
		{
			final int argb = argbStream.argb( entry.getElement().id() );
			final double alpha = ARGBType.alpha( argb );
			final double alphaCount = alpha * iFF * entry.getCount();
			a += alphaCount * alpha;
			r += alphaCount * ARGBType.red( argb );
			g += alphaCount * ARGBType.green( argb );
			b += alphaCount * ARGBType.blue( argb );
			alphaCountSize += alphaCount;
		}
		final double iAlphaCountSize = 1.0 / alphaCountSize;
		final int aInt = Math.min( 255, ( int ) ( a * iAlphaCountSize ) );
		final int rInt = Math.min( 255, ( int ) ( r * iAlphaCountSize ) );
		final int gInt = Math.min( 255, ( int ) ( g * iAlphaCountSize ) );
		final int bInt = Math.min( 255, ( int ) ( b * iAlphaCountSize ) );
		output.setValid( true );
		output.set( ( ( ( ( ( aInt << 8 ) | rInt ) << 8 ) | gInt ) << 8 ) | bInt );
		// output.set( ARGBType.rgba( rInt, gInt, bInt, aInt ) );
	}

	@Override
	public void convert(
			final Pair< VolatileLabelMultisetType, LongType > input,
			final VolatileARGBType output )
	{
		final long inputB = input.getB().get();
		if ( inputB == TRANSPARENT_LABEL )
		{
			final VolatileLabelMultisetType inputA = input.getA();
			if ( inputA.isValid() )
			{
				convertValid( inputA, output );
			}
			else
			{
				output.setValid( false );
			}
		}
		else
		{
			output.set( argbStream.argb( inputB ) );
			output.setValid( true );
		}
	}
}
