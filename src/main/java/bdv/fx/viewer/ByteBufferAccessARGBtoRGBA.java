package bdv.fx.viewer;

import java.nio.ByteBuffer;

import net.imglib2.img.basictypeaccess.IntAccess;

public class ByteBufferAccessARGBtoRGBA implements IntAccess
{

	private final ByteBuffer buffer;

	public ByteBufferAccessARGBtoRGBA( final ByteBuffer buffer )
	{
		this.buffer = buffer;
	}

	@Override
	public int getValue( final int index )
	{
		final int byteIndex = index * Integer.BYTES;
		final int argb =
				  buffer.getInt( byteIndex + 0 ) <<  0
				| buffer.getInt( byteIndex + 1 ) <<  8
				| buffer.getInt( byteIndex + 2 ) << 16
				| buffer.getInt( byteIndex + 3 ) << 24;
		return argb;
	}

	@Override
	public void setValue( final int index, final int argb )
	{
		final int byteIndex = index * Integer.BYTES;
		buffer.put( byteIndex + 0, ( byte ) ( argb >>  0 ) );
		buffer.put( byteIndex + 1, ( byte ) ( argb >>  8 ) );
		buffer.put( byteIndex + 2, ( byte ) ( argb >> 16 ) );
		buffer.put( byteIndex + 3, ( byte ) ( argb >> 24 ) );
	}

}
