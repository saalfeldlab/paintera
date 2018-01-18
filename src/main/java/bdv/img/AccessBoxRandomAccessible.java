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
package bdv.img;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;

/**
 * A {@link RandomAccessible} that tracks the bounding box in which its
 * {@link RandomAccess} has moved. If the {@link RandomAccess} did not move, the
 * bounding box covers the complete range from (2<sup>-63</sup>)<sup>n</sup> to
 * (2<sup>63</sup>)<sup>n</sup> but with min pointing at max and max pointing at
 * min. I.e. the initialization of the {@link RandomAccess} at 0<sup>n</sup> is
 * not considered part of the access box.
 *
 * This {@link RandomAccessible} has a singleton {@link RandomAccess} (itself)
 * such that updates of the access box do not have to be synchronized. If using
 * in multithreaded contexts, make many instances of the
 * {@link RandomAccessible}, and merge the access boxes later
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class AccessBoxRandomAccessible< T > implements RandomAccessible< T >, RandomAccess< T >
{
	final protected RandomAccessible< T > source;

	protected RandomAccess< T > sourceAccess;

	final protected long[] min;

	final protected long[] max;

	public AccessBoxRandomAccessible( final RandomAccessible< T > source )
	{
		this.source = source;
		min = new long[ source.numDimensions() ];
		max = new long[ source.numDimensions() ];
		sourceAccess = source.randomAccess();
	}

	protected void initAccessBox()
	{
		for ( int i = 0; i < min.length; ++i )
		{
			min[ i ] = Long.MAX_VALUE;
			max[ i ] = Long.MIN_VALUE;
		}
	}

	protected void updateAccessBox( final int d )
	{
		final long x = sourceAccess.getLongPosition( d );
		if ( x < min[ d ] )
			min[ d ] = x;
		if ( x > max[ d ] )
			max[ d ] = x;
	}

	protected void updateAccessBox()
	{
		for ( int d = 0; d < min.length; ++d )
			updateAccessBox( d );
	}

	public long[] getMin()
	{
		return min;
	}

	public long[] getMax()
	{
		return max;
	}

	public Interval createAccessInterval()
	{
		return new FinalInterval( min, max );
	}

	@Override
	public int numDimensions()
	{
		return source.numDimensions();
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		sourceAccess = source.randomAccess();
		initAccessBox();
		return this;
	}

	@Override
	public RandomAccess< T > randomAccess( final Interval interval )
	{
		sourceAccess = source.randomAccess( interval );
		initAccessBox();
		return this;
	}

	@Override
	public int getIntPosition( final int d )
	{
		return sourceAccess.getIntPosition( d );
	}

	@Override
	public long getLongPosition( final int d )
	{
		return sourceAccess.getLongPosition( d );
	}

	@Override
	public void localize( final int[] position )
	{
		sourceAccess.localize( position );
	}

	@Override
	public void localize( final long[] position )
	{
		sourceAccess.localize( position );
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return sourceAccess.getDoublePosition( d );
	}

	@Override
	public float getFloatPosition( final int d )
	{
		return sourceAccess.getFloatPosition( d );
	}

	@Override
	public void localize( final float[] position )
	{
		sourceAccess.localize( position );
	}

	@Override
	public void localize( final double[] position )
	{
		sourceAccess.localize( position );
	}

	@Override
	public void bck( final int d )
	{
		sourceAccess.bck( d );
		final long x = sourceAccess.getLongPosition( d );
		if ( x < min[ d ] )
			min[ d ] = x;
	}

	@Override
	public void fwd( final int d )
	{
		sourceAccess.fwd( d );
		final long x = sourceAccess.getLongPosition( d );
		if ( x > max[ d ] )
			max[ d ] = x;
	}

	@Override
	public void move( final Localizable distance )
	{
		sourceAccess.move( distance );
		updateAccessBox();
	}

	@Override
	public void move( final int[] distance )
	{
		sourceAccess.move( distance );
		updateAccessBox();
	}

	@Override
	public void move( final long[] distance )
	{
		sourceAccess.move( distance );
		updateAccessBox();
	}

	@Override
	public void move( final int distance, final int d )
	{
		sourceAccess.move( distance, d );
		updateAccessBox( d );
	}

	@Override
	public void move( final long distance, final int d )
	{
		sourceAccess.move( distance, d );
		updateAccessBox( d );
	}

	@Override
	public void setPosition( final Localizable position )
	{
		sourceAccess.setPosition( position );
		updateAccessBox();
	}

	@Override
	public void setPosition( final int[] position )
	{
		sourceAccess.setPosition( position );
		updateAccessBox();
	}

	@Override
	public void setPosition( final long[] position )
	{
		sourceAccess.setPosition( position );
		updateAccessBox();
	}

	@Override
	public void setPosition( final int position, final int d )
	{
		sourceAccess.setPosition( position, d );
		updateAccessBox( d );
	}

	@Override
	public void setPosition( final long position, final int d )
	{
		sourceAccess.setPosition( position, d );
		updateAccessBox( d );
	}

	@Override
	public AccessBoxRandomAccessible< T > copy()
	{
		return this;
	}

	@Override
	public T get()
	{
		return sourceAccess.get();
	}

	@Override
	public AccessBoxRandomAccessible< T > copyRandomAccess()
	{
		return copy();
	}
}
