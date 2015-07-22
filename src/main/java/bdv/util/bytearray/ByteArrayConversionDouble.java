/**
 * 
 */
package bdv.util.bytearray;

import net.imglib2.type.numeric.real.DoubleType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 */
public class ByteArrayConversionDouble extends ByteArrayConversion< DoubleType >
{

	public ByteArrayConversionDouble( int capacity )
	{
		super( capacity );
	}

	public ByteArrayConversionDouble( byte[] array )
	{
		super( array );
	}

	@Override
	public void put( DoubleType type )
	{
		this.bb.putDouble( type.get() );
	}

	@Override
	public void getNext( DoubleType type )
	{
		type.set( this.bb.getDouble() );
	}

}
