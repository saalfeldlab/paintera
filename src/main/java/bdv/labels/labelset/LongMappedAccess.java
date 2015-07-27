package bdv.labels.labelset;

/**
 * A {@link MappedAccess} that stores its data in a portion of a {@code long[]}
 * array.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class LongMappedAccess implements MappedAccess< LongMappedAccess >
{
	/**
	 * The current base offset (in bytes) into the underlying
	 * {@link LongMappedAccessData#data storage array}.
	 */
	private long baseOffset;

	/**
	 * Contains the {@link LongMappedAccessData#data storage array}.
	 */
	private LongMappedAccessData dataArray;

	static Object lock = new Object();

	LongMappedAccess( final LongMappedAccessData dataArray, final long baseOffset )
	{
//		synchronized( lock )
//		{
//		System.out.println( "LongMappedAccess" );
//		for ( final StackTraceElement e : Thread.currentThread().getStackTrace() )
//			System.out.println( "  -  " + e );
//		System.out.println();
//		}

		this.dataArray = dataArray;
		this.baseOffset = baseOffset;
	}

	void setDataArray( final LongMappedAccessData dataArray )
	{
		this.dataArray = dataArray;
	}

	void setBaseOffset( final long baseOffset )
	{
		this.baseOffset = baseOffset;
	}

	@Override
	public void putByte( final byte value, final int offset )
	{
		ByteUtils.putByte( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public byte getByte( final int offset )
	{
		return ByteUtils.getByte( dataArray.data, baseOffset + offset );
	}

	@Override
	public void putBoolean( final boolean value, final int offset )
	{
		ByteUtils.putBoolean( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public boolean getBoolean( final int offset )
	{
		return ByteUtils.getBoolean( dataArray.data, baseOffset + offset );
	}

	@Override
	public void putInt( final int value, final int offset )
	{
		ByteUtils.putInt( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public int getInt( final int offset )
	{
		return ByteUtils.getInt( dataArray.data, baseOffset + offset );
	}

	@Override
	public void putLong( final long value, final int offset )
	{
		ByteUtils.putLong( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public long getLong( final int offset )
	{
		return ByteUtils.getLong( dataArray.data, baseOffset + offset );
	}

	@Override
	public void putFloat( final float value, final int offset )
	{
		ByteUtils.putFloat( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public float getFloat( final int offset )
	{
		return ByteUtils.getFloat( dataArray.data, baseOffset + offset );
	}

	@Override
	public void putDouble( final double value, final int offset )
	{
		ByteUtils.putDouble( value, dataArray.data, baseOffset + offset );
	}

	@Override
	public double getDouble( final int offset )
	{
		return ByteUtils.getDouble( dataArray.data, baseOffset + offset );
	}

	/**
	 * Two {@link LongMappedAccess} are equal if they refer to the same
	 * {@link #baseOffset} in the same {@link LongMappedAccessData}.
	 */
	@Override
	public boolean equals( final Object obj )
	{
		if ( obj instanceof LongMappedAccess )
		{
			final LongMappedAccess e = ( LongMappedAccess ) obj;
			return e.dataArray == dataArray && e.baseOffset == baseOffset;
		}
		else
			return false;
	}

	@Override
	public int hashCode()
	{
		return dataArray.hashCode() + ( int ) ( baseOffset & 0xFFFF );
	}

	@Override
	public void copyFrom( final LongMappedAccess fromAccess, final int numBytes )
	{
		ByteUtils.copyBytes( fromAccess.dataArray.data, fromAccess.baseOffset, dataArray.data, baseOffset, numBytes );
	}
}
