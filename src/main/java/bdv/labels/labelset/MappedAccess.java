package bdv.labels.labelset;

/**
 * Maps into region of underlying memory area (a primitive array or similar). By
 * {@link MappedElementArray#updateAccess(MappedAccess, long)}, the base offset
 * of this {@link MappedAccess} in the memory area can be set. Values of
 * different types can be read or written at (byte) offsets relative to the
 * current base offset. For example {@code putLong( 42l, 2 )} would put write
 * the {@code long} value 42 into the bytes 2 ... 10 relative to the current
 * base offset.
 *
 * <p>
 * This is used to build imglib2-like proxy objects that map into primitive
 * arrays.
 *
 * <p>
 * Note: The method for updating the base offset
 * {@link MappedElementArray#updateAccess(MappedAccess, int)} needs to be in
 * the {@link MappedElementArray}, not here. This is because data might be split
 * up into several {@link MappedElementArray MappedElementArrays}, in which case
 * the reference to the memory area must be updated in addition to the base
 * offset.
 *
 * @author Tobias Pietzsch &gt;tobias.pietzsch@gmail.com&lt;
 */
public interface MappedAccess< T extends MappedAccess< T > >
{
	public void putByte( final byte value, final int offset );

	public byte getByte( final int offset );

	public void putBoolean( final boolean value, final int offset );

	public boolean getBoolean( final int offset );

	public void putInt( final int value, final int offset );

	public int getInt( final int offset );

	public void putLong( final long value, final int offset );

	public long getLong( final int offset );

	public void putFloat( final float value, final int offset );

	public float getFloat( final int offset );

	public void putDouble( final double value, final int offset );

	public double getDouble( final int offset );

	public void copyFrom( final T fromAccess, final int numBytes );
}
