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
package bdv.bigcat.ui;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

/**
 * TODO make the converter reference and use a lookuptable instead of
 * ColorStream. TODO use alpha and calculate alpha
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class VolatileLabelMultisetARGBConverterNonVolatile implements Converter< LabelMultisetType, ARGBType >
{
	final protected ARGBStream argbSource;

	final static private double iFF = 1.0 / 255.0;

	public VolatileLabelMultisetARGBConverterNonVolatile( final ARGBStream argbSource )
	{
		this.argbSource = argbSource;
	}

	protected void convertValid( final LabelMultisetType input, final ARGBType output )
	{
		double a = 0;
		double r = 0;
		double g = 0;
		double b = 0;
		double alphaCountSize = 0;

		for ( final Entry< Label > entry : input.entrySet() )
		{
			final int argb = argbSource.argb( entry.getElement().id() );
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
		output.set( ( ( aInt << 8 | rInt ) << 8 | gInt ) << 8 | bInt );
//		output.set( ARGBType.rgba( rInt, gInt, bInt, aInt ) );

	}

	@Override
	public void convert( final LabelMultisetType input, final ARGBType output )
	{
		convertValid( input, output );
	}
}
