/**
 * 
 */
package bdv.util.bytearray;

import net.imglib2.type.numeric.real.FloatType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 */
public class ByteArrayConversionFloat extends ByteArrayConversion< FloatType >
{

	public ByteArrayConversionFloat( int capacity )
	{
		super( capacity );
	}

	public ByteArrayConversionFloat( byte[] array )
	{
		super( array );
	}

	@Override
	public void put( FloatType type )
	{
		this.bb.putFloat( 0f );
		this.bb.putFloat( type.get() );
	}

	@Override
	public void getNext( FloatType type )
	{
		// discard
		this.bb.getFloat();
		type.set( this.bb.getFloat() );
	}

}
