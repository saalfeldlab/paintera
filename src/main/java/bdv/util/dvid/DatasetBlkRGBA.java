package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import bdv.util.http.HttpRequest;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 * Dataset class corresponding to dvid dataype rgba8blk.
 *
 */
public class DatasetBlkRGBA extends DatasetBlk< UnsignedIntType >
{
	
	public static final String TYPE = "rgba8blk";
	
	public DatasetBlkRGBA( Node node, String name )
	{
		super( node, name, TYPE );
	}

	@Override
	public void put( 
			RandomAccessibleInterval< UnsignedIntType > source,
			int[] offset
			) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( 
				getIntervalRequestUrl( source, offset ), 
				Views.flatIterable( source ), "application/octet-stream" );
	}

	@Override
	public void get( RandomAccessibleInterval< UnsignedIntType > target, int[] offset ) throws MalformedURLException, IOException
	{
		int size = Integer.BYTES;
		for ( int d = 0; d < target.numDimensions(); ++d )
			size *= target.dimension( d );
		
		byte[] data = new byte[ size ];
		
		getByteArray( target, data, offset );
		ByteBuffer bb = ByteBuffer.wrap( data );
		for( UnsignedIntType t : Views.flatIterable( target ) )
			t.setInteger( bb.getInt() );
		
	}

}