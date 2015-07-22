package bdv.util.bytearray;

import java.nio.ByteBuffer;

import bdv.util.Constants;
import net.imglib2.type.numeric.RealType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Use a {@link ByteBuffer} to convert {@link RealType} into 8Byte
 *         representation or write an 8Byte representation into a
 *         {@link RealType}.
 *
 */
public abstract class ByteArrayConversion< T extends RealType< T > >
{

	protected final ByteBuffer bb;

	/**
	 * @param capacity
	 *            Capacity of new {@link ByteBuffer}.
	 * 
	 *            Create {@link ByteArrayConversion} using new
	 *            {@link ByteBuffer} with capacity.
	 */
	protected ByteArrayConversion( int capacity )
	{
		this( ByteBuffer.allocate( Constants.SizeOfLong * capacity ) );
	}

	/**
	 * @param bb
	 *            Existing {@link ByteBuffer} to be used.
	 * 
	 *            Create {@link ByteArrayConversion} using existing
	 *            {@link ByteBuffer}.
	 */
	protected ByteArrayConversion( ByteBuffer bb )
	{
		this.bb = bb;
	}

	/**
	 * @param array
	 *            Array to be wrapped.
	 * 
	 *            Create {@link ByteArrayConversion} by wrapping a byte array.
	 */
	protected ByteArrayConversion( byte[] array )
	{
		this( ByteBuffer.wrap( array ) );
		this.rewind();
	}

	/**
	 * @return Return underlying array.
	 */
	public byte[] toArray()
	{
		return this.bb.array();
	}

	/**
	 * Reset {@link ByteBuffer} {@link ByteArrayConversion#bb} position to 0.
	 * Only use this if writing a series is completed and existing data needs to
	 * be read or can be overwritten.
	 */
	public void rewind()
	{
		this.bb.rewind();
	}

	/**
	 * @return Underlying {@link ByteBuffer} {@link ByteArrayConversion#bb}
	 */
	public ByteBuffer getByteBuffer()
	{
		return this.bb;
	}

	/**
	 * @param type
	 *            {@link RealType} to be stored as 8Byte representation
	 * 
	 *            Store type as 8Byte representation and make sure position of
	 *            {@link ByteArrayConversion#bb} is forwarded by exactly 8 after
	 *            this operation.
	 */
	public abstract void put( T type );

	/**
	 * @param type
	 *            {@link RealType} to be written into from 8Byte representation
	 * 
	 * 
	 *            Write into type from 8Byte representation and make sure
	 *            position of {@link ByteArrayConversion#bb} is forwarded by
	 *            exactly 8 after this operation.
	 */
	public abstract void getNext( T type );

}
