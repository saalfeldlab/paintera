package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;

import bdv.util.Constants;
import bdv.util.DvidLabelBlkURL;
import bdv.util.bytearray.ByteArrayConversion;
import bdv.util.bytearray.ByteArrayConversions;
import bdv.util.http.HttpRequest;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class DatasetBlkLabel extends DatasetBlk
{
	public static String TYPE = "labelblk";

	public DatasetBlkLabel( Node node, String name )
	{
		super( node, name );
	}

	@Override
	public < T extends RealType< T > > void get(
			RandomAccessibleInterval< T > target, 
			int[] offset ) throws MalformedURLException, IOException
	{
		
		String requestUrl = getRequestString( getBlockRequestString( target, offset ) );
		int size = ( int ) ( target.dimension( 0 ) * target.dimension( 1 ) * target.dimension( 2 ) ) * Constants.SizeOfLong;
		byte[] result = new byte[ size ];
		HttpRequest.getRequest( requestUrl, result );
		ByteArrayConversions.toIterable( Views.flatIterable( target ), result );
	}
	
	@Override
	public < T extends RealType< T > > void put( 
			RandomAccessibleInterval< T > source,
			int[] offset
			) throws MalformedURLException, IOException
	{
		// Size of the block to be written to dvid.
		int[] size = new int[ source.numDimensions() ];

		for ( int d = 0; d < size.length; ++d )
			size[ d ] = ( int ) source.dimension( d );

		// Convert input to byte[] data. One voxel of input covers 8bytes,
		// i.e. 8 entries in data.
		ByteArrayConversion< T > toByteArray = ByteArrayConversions.toByteBuffer( source );
		toByteArray.rewind(); // unnecessary because toArray() returns
								// underlying array?
		byte[] data = toByteArray.toArray();

		// Create URL and open connection.
		HttpRequest.postRequest( getBlockRequestString( source, offset ), data, "application/octet-stream" );
	}
	
	
	public static < T > String getBlockRequestString( RandomAccessibleInterval< T > image, int[] offset )
	{
		StringBuilder requestString = new StringBuilder( "raw/0_1_2/" )
				.append( image.dimension( 0 ) ).append( "_" )
				.append( image.dimension( 1 ) ).append( "_" )
				.append( image.dimension( 2 ) ).append( "/" )
				.append( offset[ 0 ] ).append( "_" )
				.append( offset[ 1 ] ).append( "_" )
				.append( offset[ 2 ] )
				;
		return requestString.toString();
	}

}
