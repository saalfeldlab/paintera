package bdv.labels.labelset;


/**
 * A {@link MappedElementArray} that stores {@link DoubleMappedElement
 * DoubleMappedElements} in a {@code double[]} array.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class LongMappedAccessData implements MappedAccessData< LongMappedAccess >
{
	/**
	 * The current data storage. This is changed when the array is
	 * {@link #resize(int) resized}.
	 */
	long[] data;

	private long size;

	@Override
	public LongMappedAccess createAccess()
	{
		return new LongMappedAccess( this, 0 );
	}

	@Override
	public void updateAccess( final LongMappedAccess access, final long baseOffset )
	{
		access.setDataArray( this );
		access.setBaseOffset( baseOffset );
	}

	private long longSizeFromByteSize( final long byteSize )
	{
		return ( byteSize + ByteUtils.LONG_SIZE - 1 ) / ByteUtils.LONG_SIZE;
	}

	/**
	 * Create a new array containing {@code numElements} elements of
	 * {@code bytesPerElement} bytes each.
	 */
	private LongMappedAccessData( final long size )
	{
		final long longSize = longSizeFromByteSize( size );
		if ( longSize > Integer.MAX_VALUE )
			throw new IllegalArgumentException(
					"trying to create a " + getClass().getName() + " with more than " + ( ( long ) ByteUtils.LONG_SIZE * Integer.MAX_VALUE ) + " bytes.");

		this.size = size;
		this.data = new long[ ( int ) longSize ];
	}

	@Override
	public long size()
	{
		return size;
	}

	/**
	 * {@inheritDoc} The storage array is reallocated and the old contents
	 * copied over.
	 */
	@Override
	public void resize( final long size )
	{
		final long longSize = longSizeFromByteSize( size );
		if ( longSize == longSizeFromByteSize( this.size ) )
			return;

		if ( longSize > Integer.MAX_VALUE )
			throw new IllegalArgumentException(
					"trying to resize a " + getClass().getName() + " with more than " + ( ( long ) ByteUtils.LONG_SIZE * Integer.MAX_VALUE ) + " bytes.");

		final long[] datacopy = new long[ ( int ) longSize ];
			final int copyLength = Math.min( data.length, datacopy.length );
			System.arraycopy( data, 0, datacopy, 0, copyLength );
		this.data = datacopy;
		this.size = size;
	}

//	/**
//	 * For internal use only!
//	 */
//	public long[] getCurrentDataArray()
//	{
//		return data;
//	}

	/**
	 * A factory for {@link LongMappedAccessData}s.
	 */
	public static final MappedAccessData.Factory< LongMappedAccessData, LongMappedAccess > factory =
			new MappedAccessData.Factory< LongMappedAccessData, LongMappedAccess >()
			{
				@Override
				public LongMappedAccessData createStorage( final long size )
				{
					return new LongMappedAccessData( size );
				}

				@Override
				public LongMappedAccess createAccess()
				{
					return new LongMappedAccess( null, 0 );
				}
			};
}
