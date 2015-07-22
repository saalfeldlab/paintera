/**
 * 
 */
package bdv.util.bytearray;

import net.imglib2.type.numeric.integer.LongType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 */
public class ByteArrayConversionLong extends ByteArrayConversion< LongType >
{

	public ByteArrayConversionLong( int capacity )
	{
		super( capacity );
	}

	public ByteArrayConversionLong( byte[] array )
	{
		super( array );
	}

	@Override
	public void put( LongType type )
	{
		this.bb.putLong( type.get() );
	}

	@Override
	public void getNext( LongType type )
	{
		type.set( this.bb.getLong() );
	}

}
