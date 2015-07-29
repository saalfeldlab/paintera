package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import bdv.util.http.HttpRequest;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;

public class DatasetBlkLabel< T extends IntegerType< T > > extends DatasetBlk< T >
{
	public static String TYPE = "labelblk";

	public DatasetBlkLabel( Node node, String name )
	{
		super( node, name );
	}

	@Override
	public void put( 
			RandomAccessibleInterval< T > source,
			int[] offset
			) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( 
				getBlockRequestUrl( source, offset ), 
				Views.flatIterable( source ), "application/octet-stream" );
	}

	@Override
	public void get( RandomAccessibleInterval< T > target, int[] offset ) throws MalformedURLException, IOException
	{
		int size = Long.BYTES;
		for ( int d = 0; d < target.numDimensions(); ++d )
			size *= target.dimension( d );
		
		byte[] data = new byte[ size ];
		
		getByteArray( target, data, offset );
		ByteBuffer bb = ByteBuffer.wrap( data );
		for( T t : Views.flatIterable( target ) )
			t.setInteger( bb.getLong() );
		
	}
	

}
