package bdv.fx.viewer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.imglib2.img.basictypeaccess.IntAccess;

public class IntBufferAccess implements IntAccess
{

	private final IntBuffer buffer;

	public IntBufferAccess( final ByteBuffer buffer )
	{
		this( buffer.asIntBuffer() );
	}

	public IntBufferAccess( final IntBuffer buffer )
	{
		super();
		this.buffer = buffer;
	}

	@Override
	public int getValue( final int index )
	{
		return buffer.get( index );
	}

	@Override
	public void setValue( final int index, final int value )
	{
		buffer.put( index, value );
	}

}
