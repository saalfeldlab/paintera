/**
 * 
 */
package bdv.img.labelpair;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessible;
import net.imglib2.util.Pair;

/**
 * A {@link RandomAccessible} over two independent
 * {@link RandomAccessible RandomAccessibles} whose type is the pair of
 * corresponding values at the same coordinates in either of the two sources.
 * 
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class RandomAccessiblePair< A, B > implements RandomAccessible< Pair< A, B > >
{
	public class Pair implements net.imglib2.util.Pair< A, B >
	{
		private A a = null;
		private B b = null;
		
		@Override
		public A getA()
		{
			return a;
		}

		@Override
		public B getB()
		{
			return b;
		}
		
		public void setA( final A a )
		{
			this.a = a;
		}

		public void setB( final B b )
		{
			this.b = b;
		}
	}
	
	public class RandomAccess implements net.imglib2.RandomAccess< net.imglib2.util.Pair< A, B > >
	{
		final protected net.imglib2.RandomAccess< A > raA;
		final protected net.imglib2.RandomAccess< B > raB;
		final protected Pair pair;
		
		public RandomAccess()
		{
			raA = a.randomAccess();
			raB = b.randomAccess();
			pair = new Pair();
		}
		
		@Override
		public void localize( int[] position )
		{
			raA.localize( position );
		}

		@Override
		public void localize( long[] position )
		{
			raA.localize( position );
		}

		@Override
		public int getIntPosition( int d )
		{
			return raA.getIntPosition( d );
		}

		@Override
		public long getLongPosition( int d )
		{
			return raA.getLongPosition( d );
		}

		@Override
		public void localize( float[] position )
		{
			raA.localize( position );
		}

		@Override
		public void localize( double[] position )
		{
			raA.localize( position );
		}

		@Override
		public float getFloatPosition( int d )
		{
			return raA.getFloatPosition( d );
		}

		@Override
		public double getDoublePosition( int d )
		{
			return raA.getDoublePosition( d );
		}

		@Override
		public int numDimensions()
		{
			return RandomAccessiblePair.this.numDimensions();
		}

		@Override
		public void fwd( int d )
		{
			raA.fwd( d );
			raB.fwd( d );
		}

		@Override
		public void bck( int d )
		{
			raA.bck( d );
			raB.bck( d );
		}

		@Override
		public void move( int distance, int d )
		{
			raA.move( distance, d );
			raB.move( distance, d );
		}

		@Override
		public void move( long distance, int d )
		{
			raA.move( distance, d );
			raB.move( distance, d );
		}

		@Override
		public void move( Localizable localizable )
		{
			raA.move( localizable );
			raB.move( localizable );
		}

		@Override
		public void move( int[] distance )
		{
			raA.move( distance );
			raB.move( distance );
		}

		@Override
		public void move( long[] distance )
		{
			raA.move( distance );
			raB.move( distance );
		}

		@Override
		public void setPosition( Localizable localizable )
		{
			raA.setPosition( localizable );
			raB.setPosition( localizable );
		}

		@Override
		public void setPosition( int[] position )
		{
			raA.setPosition( position );
			raB.setPosition( position );
		}

		@Override
		public void setPosition( long[] position )
		{
			raA.setPosition( position );
			raB.setPosition( position );
		}

		@Override
		public void setPosition( int position, int d )
		{
			raA.setPosition( position, d );
			raB.setPosition( position, d );
		}

		@Override
		public void setPosition( long position, int d )
		{
			raA.setPosition( position, d );
			raB.setPosition( position, d );
		}

		@Override
		public Pair get()
		{
			pair.setA( raA.get() );
			pair.setB( raB.get() );
			return pair;
		}

		@Override
		public RandomAccess copy()
		{
			final RandomAccess copy = new RandomAccess();
			copy.setPosition( this );
			return copy;
		}

		@Override
		public RandomAccess copyRandomAccess()
		{
			return copy();
		}
	}
	
	final protected RandomAccessible< A > a;
	final protected RandomAccessible< B > b;
	
	public RandomAccessiblePair(
			final RandomAccessible< A > a,
			final RandomAccessible< B > b )
	{
		assert a.numDimensions() == b.numDimensions() : "Sources must have same number of dimensions.";
		
		this.a = a;
		this.b = b;
	}

	@Override
	public int numDimensions()
	{
		return a.numDimensions();
	}

	@Override
	public RandomAccess randomAccess()
	{
		return new RandomAccess();
	}

	@Override
	public RandomAccess randomAccess( Interval interval )
	{
		return new RandomAccess();
	}
}
