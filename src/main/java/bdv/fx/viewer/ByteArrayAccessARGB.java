package bdv.fx.viewer;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.img.basictypeaccess.IntAccess;

public class ByteArrayAccessARGB implements IntAccess
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final byte[] array;

	public ByteArrayAccessARGB( final byte[] array )
	{
		LOG.debug( "Creating {} with {} entries", this.getClass().getName(), array.length );
		this.array = array;
	}

	@Override
	public int getValue( final int index )
	{
		final int byteIndex = index * Integer.BYTES;
		final int argb =
	   			( array[ byteIndex + 0 ] & 0xff ) << 0
    		  | ( array[ byteIndex + 1 ] & 0xff ) << 8
    		  | ( array[ byteIndex + 2 ] & 0xff ) << 16
    		  | ( array[ byteIndex + 3 ] & 0xff ) << 24;
		return argb;
	}

	@Override
	public void setValue( final int index, final int argb )
	{
		final int byteIndex = index * Integer.BYTES;
		array[ byteIndex + 0 ] = ( byte ) ( argb >>> 0 );
		array[ byteIndex + 1 ] = ( byte ) ( argb >>> 8 );
		array[ byteIndex + 2 ] = ( byte ) ( argb >>> 16 );
		array[ byteIndex + 3 ] = ( byte ) ( argb >>> 24 );
	}

}
