package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.http.HttpRequest;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 * Dataset class corresponding to dvid dataype uint8blk.
 *
 */
public class DatasetBlkUint8 extends DatasetBlk< UnsignedByteType >
{
	
	public static final String TYPE = "uint8blk";
	
	private final byte[] buffer;
	
	public DatasetBlkUint8( Node node, String name ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( node, name, TYPE );
		int nEntitiesPerBlock = Byte.BYTES;
		for ( int d = 0; d < this.blockSize.length; ++d )
			nEntitiesPerBlock *= this.blockSize[ d ];
		this.buffer = new byte[ nEntitiesPerBlock ];
	}

	@Override
	public void put( 
			RandomAccessibleInterval< UnsignedByteType > source,
			int[] offset
			) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( 
				getIntervalRequestUrl( source, offset ), 
				Views.flatIterable( source ), "application/octet-stream" );
	}

	@Override
	public void get( RandomAccessibleInterval< UnsignedByteType > target, int[] offset ) throws MalformedURLException, IOException
	{
		int size = Byte.BYTES;
		for ( int d = 0; d < target.numDimensions(); ++d )
			size *= target.dimension( d );
		
		byte[] data = new byte[ size ];
		
		getByteArray( target, data, offset );
		ByteBuffer bb = ByteBuffer.wrap( data );
		for( UnsignedByteType t : Views.flatIterable( target ) )
			t.setInteger( bb.get() );
	}

	@Override
	public void writeBlock( RandomAccessibleInterval< UnsignedByteType > source, int[] position ) throws MalformedURLException, IOException
	{
		for ( int d = 0; d < blockSize.length; ++d )
			assert source.dimension( d ) == blockSize[ d ];
		
		Cursor< UnsignedByteType > cursor = Views.flatIterable( source ).cursor();
		ByteBuffer bb = ByteBuffer.wrap( this.buffer );
		while( cursor.hasNext() )
		{
			bb.put( (byte) ( cursor.next().get() & 0xff ) );
		}
		String url = getBlockRequestUrl( position, 1 );
		HttpRequest.postRequest( url, this.buffer );
	}
}