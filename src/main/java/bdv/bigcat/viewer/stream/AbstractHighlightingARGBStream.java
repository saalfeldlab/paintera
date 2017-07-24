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
package bdv.bigcat.viewer.stream;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.SelectedIds;
import bdv.labels.labelset.Label;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongIntHashMap;

/**
 * Generates and caches a stream of colors.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
abstract public class AbstractHighlightingARGBStream implements ARGBStream
{
	final static protected double[] rs = new double[] { 1, 1, 0, 0, 0, 1, 1 };

	final static protected double[] gs = new double[] { 0, 1, 1, 1, 0, 0, 0 };

	final static protected double[] bs = new double[] { 0, 0, 0, 1, 1, 1, 0 };

	protected long seed = 0;

	protected int alpha = 0x20000000;

	protected int highlightAlpha = 0x80000000;

	protected int invalidSegmentAlpha = 0x00000000;

	final protected SelectedIds highlights;

	public AbstractHighlightingARGBStream( final SelectedIds highlights )
	{
		this.highlights = highlights;
	}

	protected TLongIntHashMap argbCache = new TLongIntHashMap(
			Constants.DEFAULT_CAPACITY,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.TRANSPARENT,
			0 );

//	public void highlight( final TLongHashSet highlights )
//	{
//		this.highlights.clear();
//		this.highlights.addAll( highlights );
//	}
//
//	public void highlight( final long[] highlights )
//	{
//		this.highlights.clear();
//		this.highlights.addAll( highlights );
//	}

	public boolean isHighlight( final long id )
	{
		return this.highlights.isActive( id );
	}

	final static protected int argb( final int r, final int g, final int b, final int alpha )
	{
		return ( r << 8 | g ) << 8 | b | alpha;
	}

	abstract protected double getDouble( final long id );

	/**
	 * Change the seed.
	 *
	 * @param seed
	 */
	public void setSeed( final long seed )
	{
		this.seed = seed;
	}

	/**
	 * Increment seed.
	 */
	public void incSeed()
	{
		++seed;
	}

	/**
	 * Decrement seed.
	 */
	public void decSeed()
	{
		--seed;
	}

	/**
	 * Change alpha. Values less or greater than [0,255] will be masked.
	 *
	 * @param alpha
	 */
	public void setAlpha( final int alpha )
	{
		this.alpha = alpha << 24;
	}

	/**
	 * Change active fragment alpha. Values less or greater than [0,255] will be
	 * masked.
	 *
	 * @param alpha
	 */
	public void setHighlightAlpha( final int alpha )
	{
		this.highlightAlpha = alpha << 24;
	}

	public void clearCache()
	{
		argbCache.clear();
	}
}
