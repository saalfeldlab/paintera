package bdv.util;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Wrap RandomAccessibleInterval such that it can be accessed by blocks.
 *         This representation makes flat iteration over blocks very easy.
 *
 */
public class BlockedInterval< T > implements RandomAccessibleInterval< RandomAccessibleInterval< T > >
{

	private final RandomAccessible< T > extendedSource;

	private final long[] stepSize;

	private final long[] max;

	private final long[] min;

	private final long[] dimensions;

	private final int nDim;

	/**
	 * @param source
	 *            Source to be wrapped.
	 * @param stepSize
	 *            Strides for each dimension
	 * @return BlockedInterval wrapped around zero extended source.
	 */
	public static < U extends NumericType< U > > BlockedInterval< U > createZeroExtended(
			RandomAccessibleInterval< U > source,
			long[] stepSize )
	{
		U value = source.randomAccess().get().copy();
		value.setZero();
		return createValueExtended( source, stepSize, value );
	}

	/**
	 * @param source
	 *            Source to be wrapped.
	 * @param stepSize
	 *            Strides for each dimension.
	 * @param value
	 *            Value for extending source.
	 * @return BlockedInterval wrapped around value extended source.
	 */
	public static < U extends Type< U > > BlockedInterval< U > createValueExtended(
			RandomAccessibleInterval< U > source,
			long[] stepSize,
			U value )
	{
		OutOfBoundsConstantValueFactory< U, RandomAccessibleInterval< U >> factory =
				new OutOfBoundsConstantValueFactory< U, RandomAccessibleInterval< U > >( value );
		return new BlockedInterval< U >( source, stepSize, factory );
	}

	/**
	 * @param source
	 *            Source to be wrapped.
	 * @param stepSize
	 *            Strides for each dimension.
	 * @param factory
	 *            Factory for extending source.
	 */
	public BlockedInterval(
			RandomAccessibleInterval< T > source,
			long[] stepSize,
			OutOfBoundsFactory< T, RandomAccessibleInterval< T > > factory )
	{
		super();
		this.stepSize = stepSize;
		this.extendedSource = new ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > >( source, factory );
		this.nDim = source.numDimensions();

		this.max = new long[ nDim ];
		this.min = new long[ nDim ];
		this.dimensions = new long[ nDim ];

		source.min( this.min );
		source.max( this.max );
		source.dimensions( dimensions );

		for ( int d = 0; d < this.nDim; ++d )
		{
			long val = this.max[ d ] - this.min[ d ];
			this.max[ d ] = val / stepSize[ d ] + this.min[ d ];

			long dim = this.dimensions[ d ] - this.min[ d ];
			this.dimensions[ d ] = dim / stepSize[ d ] + this.min[ d ];
		}

	}

	public class BlockedIntervalRandomAccess extends Point implements RandomAccess< RandomAccessibleInterval< T > >
	{

		private BlockedIntervalRandomAccess()
		{
			super( nDim );
		}

		private BlockedIntervalRandomAccess( long[] position )
		{
			super( position.clone() );
		}

		@Override
		public RandomAccessibleInterval< T > get()
		{
			long[] lower = position.clone();
			long[] upper = new long[ lower.length ];
			for ( int d = 0; d < lower.length; d++ )
			{
				long m = min[ d ];
				long s = stepSize[ d ];
				long val = ( lower[ d ] - m ) * s + m;
				lower[ d ] = val;
				upper[ d ] = val + s - 1;
			}
			return Views.interval( extendedSource, lower, upper );
		}

		@Override
		public BlockedIntervalRandomAccess copy()
		{
			return copyRandomAccess();
		}

		@Override
		public BlockedIntervalRandomAccess copyRandomAccess()
		{
			return new BlockedIntervalRandomAccess( this.position );
		}

	}

	@Override
	public RandomAccess< RandomAccessibleInterval< T >> randomAccess()
	{
		return new BlockedIntervalRandomAccess();
	}

	@Override
	public RandomAccess< RandomAccessibleInterval< T >> randomAccess( Interval arg0 )
	{
		return this.randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return nDim;
	}

	@Override
	public long max( int d )
	{
		return this.max[ d ];
	}

	@Override
	public void max( long[] max )
	{
		System.arraycopy( this.max, 0, max, 0, this.nDim );
	}

	@Override
	public void max( Positionable max )
	{
		max.setPosition( this.max );
	}

	@Override
	public long min( int d )
	{
		return this.min[ d ];
	}

	@Override
	public void min( long[] min )
	{
		System.arraycopy( this.min, 0, min, 0, this.nDim );
	}

	@Override
	public void min( Positionable min )
	{
		min.setPosition( this.min );
	}

	@Override
	public double realMax( int d )
	{
		return this.max[ d ];
	}

	@Override
	public void realMax( double[] max )
	{
		for ( int d = 0; d < this.nDim; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void realMax( RealPositionable max )
	{
		max.setPosition( this.max );
	}

	@Override
	public double realMin( int d )
	{
		return this.min( d );
	}

	@Override
	public void realMin( double[] min )
	{
		for ( int d = 0; d < this.nDim; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void realMin( RealPositionable min )
	{
		min.setPosition( this.min );
	}

	@Override
	public long dimension( int d )
	{
		return this.dimensions[ d ];
	}

	@Override
	public void dimensions( long[] dimensions )
	{
		System.arraycopy( this.dimensions, 0, dimensions, 0, this.nDim );
	}

	public static void main( String[] args )
	{
//		String fn = "/data/hanslovskyp/jain-nobackup/234/data/data.tif";
//		new ImageJ();
//		ImagePlus imp = new ImagePlus( fn );
//		imp.show();
//		Img<FloatType> img = ImageJFunctions.wrapFloat( imp );
//		long[] blockSize = new long[] { 128, 128, 30 };
//		BlockedInterval<FloatType> blocked = BlockedInterval.createZeroExtended( img, blockSize );
//		RandomAccess<RandomAccessibleInterval<FloatType>> ra = blocked.randomAccess();
//		ra.setPosition( new int[] { 2, 2, 3 } );
//		ImageJFunctions.show( ra.get() );
	}

}
