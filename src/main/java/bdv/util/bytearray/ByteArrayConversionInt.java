/**
 * 
 */
package bdv.util.bytearray;

import net.imglib2.type.numeric.integer.IntType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 */
public class ByteArrayConversionInt extends ByteArrayConversion< IntType >
{

	public ByteArrayConversionInt( int capacity )
	{
		super( capacity );
	}

	public ByteArrayConversionInt( byte[] array )
	{
		super( array );
	}

	@Override
	public void put( IntType type )
	{
		this.bb.putInt( 0 );
		this.bb.putInt( type.get() );
	}

	@Override
	public void getNext( IntType type )
	{
		// discard
		this.bb.getInt();
		type.set( this.bb.getInt() );
	}

}
