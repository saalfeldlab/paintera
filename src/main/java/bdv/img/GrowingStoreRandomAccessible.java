package bdv.img;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
class GrowingStoreRandomAccessible< T extends Type< T > > implements RandomAccessible< T >
{

	public interface Factory< U extends Type< U > >
	{
		RandomAccessibleInterval< U > createStore( long[] min, long[] max, U u );

		default RandomAccessibleInterval< U > createStore( long[] dimensions, U u )
		{
			long[] min = new long[ dimensions.length ];
			long[] max = new long[ dimensions.length ];
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = 0;
				max[ d ] = dimensions[ d ] - 1;
			}
			return createStore( min, max, u );
		}
	}

	private final int nDim;

	private final long[] min;

	private final long[] max;

	private final long[] dimensions;

	private RandomAccessibleInterval< T > store;

	private final Factory< T > factory;

	T t;

	private final ArrayList< WeakReference< GrowingStoreRandomAccess > > randomAccessRefs;

	public enum SIDE
	{
		TOP, BOTTOM
	}

	public GrowingStoreRandomAccessible( long[] initialDimension, final Factory< T > factory, final T t )
	{
		this( factory.createStore( initialDimension, t ), factory );
	}

	public GrowingStoreRandomAccessible( long[] initialMin, long[] initialMax, Factory< T > factory, T t )
	{
		this( factory.createStore( initialMin, initialMax, t ), factory );
	}

	public GrowingStoreRandomAccessible( RandomAccessibleInterval< T > initialStore, Factory< T > factory )
	{
		this.nDim = initialStore.numDimensions();
		this.min = new long[ this.nDim ];
		this.max = new long[ this.nDim ];
		this.dimensions = new long[ this.nDim ];
		initStore( initialStore );
		this.factory = factory;
		this.t = initialStore.randomAccess().get().createVariable();
		this.randomAccessRefs = new ArrayList<>();
	}

	private synchronized void growStore( Localizable position )
	{
		boolean changedStore = false;
		for ( int d = 0; d < this.nDim; ++d )
		{

			long currentPos = position.getLongPosition( d );
			long currentMin = this.min[ d ];

			if ( currentPos < currentMin )
			{
				long currentDim = this.dimensions[ d ];
				long diff = currentMin - currentPos;
				// what if diff / currentDim == 2**n ?
				double ratio = ( diff + currentDim ) * 1.0 / currentDim;
				int ceil = ( int ) Math.ceil( ratio );
				int highBit = ( Integer.highestOneBit( ceil ) );
				long growFactor = 2 << Integer.numberOfTrailingZeros( highBit == ceil ? 1 : 0 );
				// n & ( n - 1 ) == 0 : power of 2
				// growStoreBottom( d, ( ( growFactor & ( growFactor - 1 ) ) ==
				// 0 ? 1*growFactor : growFactor )* currentDim );
				growStoreBottom( d, growFactor * currentDim - currentDim );
				changedStore = true;
			}
			else
			{
				long currentMax = this.max[ d ];
				if ( currentPos > currentMax )
				{
					long currentDim = this.dimensions[ d ];
					long diff = currentPos - currentMax;
					// what if diff / currentDim == 2**n ?
					double ratio = ( diff + currentDim ) * 1.0 / currentDim;
					int ceil = ( int ) Math.ceil( ratio );
					int highBit = ( Integer.highestOneBit( ceil ) );
					long growFactor = 2 << Integer.numberOfTrailingZeros( highBit ) - ( highBit == ceil ? 1 : 0 );
					growStoreTop( d, growFactor * currentDim - currentDim );
					changedStore = true;
				}
			}
		}

		if ( changedStore )
		{
			createNewStoreAndCopyOldContents();
			updateRandomAccessRefs();
		}
	}

	private synchronized void growStoreTop( int d, long diff )
	{
		this.dimensions[ d ] += diff;
		this.max[ d ] += diff;
	}

	private synchronized void growStoreBottom( int d, long diff )
	{
		this.dimensions[ d ] += diff;
		this.min[ d ] -= diff;
	}

	private synchronized void initStore( RandomAccessibleInterval< T > newStore )
	{
		newStore.dimensions( this.dimensions );
		newStore.min( this.min );
		newStore.max( this.max );
		this.store = newStore;
	}

	private synchronized void createNewStoreAndCopyOldContents()
	{
		RandomAccessibleInterval< T > newStore = this.factory.createStore( this.min, this.max, t );
		Cursor< T > cursor = Views.iterable( this.store ).cursor();
		RandomAccess< T > access = newStore.randomAccess();
		while ( cursor.hasNext() )
		{
			T val = cursor.next();
			access.setPosition( cursor );
			access.get().set( val );
		}
		this.store = newStore;
	}

	@Override
	public synchronized RandomAccess< T > randomAccess()
	{
		this.cleanRandomAccessRefs();
		GrowingStoreRandomAccess access = new GrowingStoreRandomAccess();
		access.setPosition( this.min );
		this.randomAccessRefs.add( new WeakReference< GrowingStoreRandomAccess >( access ) );
		return access;
	}

	@Override
	public synchronized RandomAccess< T > randomAccess( Interval interval )
	{
		return Views.interval( this.store, interval ).randomAccess();
	}

	// should this method even exist? or just return FinalInterval?
	public synchronized FinalInterval getIntervalOfSizeOfStore()
	{
		synchronized ( this.store )
		{
			return new FinalInterval( this.min, this.max );
		}
	}

	private synchronized void cleanRandomAccessRefs()
	{
		for ( int i = this.randomAccessRefs.size() - 1; i >= 0; --i )
		{
			if ( this.randomAccessRefs.get( i ).get() == null )
				this.randomAccessRefs.remove( i );
		}
	}

	private synchronized void updateRandomAccessRefs()
	{
		// go backwards to avoid creating objects that would be removed anyway
		for ( int i = this.randomAccessRefs.size() - 1; i >= 0; --i )
		{
			GrowingStoreRandomAccess ra = this.randomAccessRefs.get( i ).get();
			if ( ra == null )
				this.randomAccessRefs.remove( i );
			else
			{
				RandomAccess< T > newAccess = store.randomAccess();
				newAccess.setPosition( ra );
				ra.storeRandomAccess = newAccess;
			}
		}
	}

	@Override
	public int numDimensions()
	{
		return this.nDim;
	}

	public class GrowingStoreRandomAccess implements RandomAccess< T >
	{

		private RandomAccess< T > storeRandomAccess;

		private GrowingStoreRandomAccess()
		{
			this( store.randomAccess() );
		}

		private GrowingStoreRandomAccess( RandomAccess< T > storeRandomAccess )
		{
			this.storeRandomAccess = storeRandomAccess;
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			synchronized ( GrowingStoreRandomAccessible.this )
			{
				GrowingStoreRandomAccess access = new GrowingStoreRandomAccess( this.storeRandomAccess.copyRandomAccess() );
				GrowingStoreRandomAccessible.this.randomAccessRefs.add( new WeakReference<>( access ) );
				return access;
			}
		}

		@Override
		public T get()
		{
			for ( int d = 0; d < nDim; ++d )
			{
				long pos = this.storeRandomAccess.getLongPosition( d );
				if ( pos < min[ d ] || pos > max[ d ] )
				{
					growStore( this.storeRandomAccess );
					break;
				}
			}

			return this.storeRandomAccess.get();
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		// only for test purposes
		@Override
		public String toString()
		{
			long[] pos = new long[ nDim ];
			localize( pos );
			return Arrays.toString( pos );
		}

		@Override
		public void localize( int[] pos )
		{
			this.storeRandomAccess.localize( pos );
		}

		@Override
		public void localize( long[] pos )
		{
			this.storeRandomAccess.localize( pos );
		}

		@Override
		public int getIntPosition( int d )
		{
			return this.storeRandomAccess.getIntPosition( d );
		}

		@Override
		public long getLongPosition( int d )
		{
			return this.storeRandomAccess.getLongPosition( d );
		}

		@Override
		public void fwd( int d )
		{
			this.storeRandomAccess.fwd( d );
		}

		@Override
		public void bck( int d )
		{
			this.storeRandomAccess.bck( d );
		}

		@Override
		public void move( int pos, int d )
		{
			this.storeRandomAccess.move( pos, d );
		}

		@Override
		public void move( long pos, int d )
		{
			this.storeRandomAccess.move( pos, d );
		}

		@Override
		public void move( Localizable pos )
		{
			this.storeRandomAccess.move( pos );
		}

		@Override
		public void move( int[] pos )
		{
			this.storeRandomAccess.move( pos );
		}

		@Override
		public void move( long[] pos )
		{
			this.storeRandomAccess.move( pos );
		}

		@Override
		public void setPosition( Localizable pos )
		{
			this.storeRandomAccess.setPosition( pos );
		}

		@Override
		public void setPosition( int[] pos )
		{
			this.storeRandomAccess.setPosition( pos );
		}

		@Override
		public void setPosition( long[] pos )
		{
			this.storeRandomAccess.setPosition( pos );
		}

		@Override
		public void setPosition( int pos, int d )
		{
			this.storeRandomAccess.setPosition( pos, d );
		}

		@Override
		public void setPosition( long pos, int d )
		{
			this.storeRandomAccess.setPosition( pos, d );
		}

		@Override
		public void localize( float[] pos )
		{
			this.storeRandomAccess.localize( pos );
		}

		@Override
		public void localize( double[] pos )
		{
			this.storeRandomAccess.localize( pos );
		}

		@Override
		public float getFloatPosition( int d )
		{
			return this.storeRandomAccess.getFloatPosition( d );
		}

		@Override
		public double getDoublePosition( int d )
		{
			return this.storeRandomAccess.getDoublePosition( d );
		}

		@Override
		public int numDimensions()
		{
			// return GrowingStoreRandomAccessible.this.nDim;
			return this.storeRandomAccess.numDimensions();
		}
	}

	public static void main( String[] args )
	{
		final long[] dim = new long[] { 2, 3 };
		long[] m = new long[ dim.length ];
		long[] M = new long[ dim.length ];
		ArrayImg< IntType, IntArray > img = ArrayImgs.ints( dim );
		int i = 0;
		for ( IntType c : img )
			c.set( i++ );

		Factory< IntType > factory = ( min1, max1, t1 ) -> {
			final long[] dimensions1 = new long[ min1.length ];
			for ( int d = 0; d < min1.length; ++d )
				dimensions1[ d ] = max1[ d ] - min1[ d ] + 1;
			ArrayImg< IntType, IntArray > imgFac = ArrayImgs.ints( dimensions1 );
			return Views.translate( imgFac, min1 );
		};

		Function< long[], long[] > l = ( long[] array ) -> {
			long[] result = array.clone();
			for ( int k = 0; k < result.length; ++k )
				result[ k ] = -result[ k ];
			return result;
		};

		GrowingStoreRandomAccessible< IntType > rra = new GrowingStoreRandomAccessible<>( img, factory );

		new ImageJ();
		RandomAccess< IntType > ra = rra.randomAccess();
		// Bdv bdv = BdvFunctions.show(rra, "1");

		IntType f;
		ImageJFunctions.show( Views.offsetInterval( rra, rra.getIntervalOfSizeOfStore() ), "1" );
		System.out.println( Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra + " " + rra.getIntervalOfSizeOfStore() );
		ra.setPosition( -2, 0 );
		f = ra.get();
		f.get();
		f.set( ( byte ) 25 );
		ImageJFunctions.show( Views.interval( rra, rra.getIntervalOfSizeOfStore() ), "2" );
		System.out.println( Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra + " " + rra.getIntervalOfSizeOfStore() );
		ra.setPosition( dim[ 1 ], 1 );
		f = ra.get();
		f.get();
		f.set( ( byte ) 25 );
		ImageJFunctions.show( Views.interval( rra, rra.getIntervalOfSizeOfStore() ), "3" );
		System.out.println( Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra + " " + rra.getIntervalOfSizeOfStore() );
		ra.setPosition( M );
		ra.setPosition( 32, 0 );
		ra.setPosition( 17, 1 ); // 11 now, was 17 before
		long[] pos = new long[ ra.numDimensions() ];
		ra.localize( pos );
		System.out.println( Arrays.toString( pos ) );
		f = ra.get();
		f.get();
		f.set( ( byte ) 25 );
		ImageJFunctions.show( Views.interval( rra, rra.getIntervalOfSizeOfStore() ), "4" );
		System.out.println( Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra + " " + rra.getIntervalOfSizeOfStore() );

		// {
		// RandomAccess<FloatType> accessToBeCollected = rra.randomAccess();
		// for ( WeakReference< ? > a : rra.randomAccessRefs ) {
		// System.out.println( a.get() );
		// }
		// }
		// System.gc();
		// System.gc();
		// for ( WeakReference< ? > a : rra.randomAccessRefs ) {
		// System.out.println( a.get() );
		// }

	}

}
