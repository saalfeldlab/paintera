package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.http.HttpRequest;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

public abstract class DatasetBlk< T > extends Dataset
{

	public DatasetBlk( Node node, String name )
	{
		super( node, name );
	}
	
	public abstract void get( 
			RandomAccessibleInterval< T > target,
			int[] offset
			) throws MalformedURLException, IOException;
	
	public abstract void put( 
			RandomAccessibleInterval< T > source,
			int[] offset
			) throws MalformedURLException, IOException;
	
	public String getIntervalRequestUrl( Interval image, int[] offset )
	{
		return getRequestString( getIntervalRequestString( image, offset ) );
	}
	
	public String getBlockRequestUrl( int[] min, int[] blockSize )
	{
		return getRequestString( getBlockRequestString( min, blockSize ) );
	}
	
	public static String getBlockRequestString( int[] min, int[] blockSize )
	{
		StringBuffer buf = new StringBuffer( "/blocks/" )
			.append( min[ 0 ] / blockSize[ 0 ] )
			.append( "_" )
			.append( min[ 1 ] / blockSize[ 1 ] )
			.append( "_" )
			.append( min[ 2 ] / blockSize[ 2 ] )
			.append( "/1" )
			;
		return buf.toString();
	}
	
	public byte[] getBlock( int[] min, int[] blockSize ) throws MalformedURLException, IOException
	{
		String requestUrl = getBlockRequestUrl( min, blockSize );
		return HttpRequest.getRequest( requestUrl );
	}
	
	public static String getIntervalRequestString( Interval image, int[] offset )
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
	
	public void getByteArray(
			Interval interval,
			byte[] result,
			int[] offset ) throws MalformedURLException, IOException
	{
		
		String requestUrl = getRequestString( getIntervalRequestString( interval, offset ) );
		HttpRequest.getRequest( requestUrl, result );
	}
	
	public int[] getBlockSize() throws JsonSyntaxException, JsonIOException, IOException
	{
		int[] blockSize = new int[ 3 ];
		getBlockSize( blockSize );
		return blockSize;
	}
	
	public void getBlockSize( int[] blockSize ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonArray bs = getInfo().get( "Extended" ).getAsJsonObject().get( "BlockSize" ).getAsJsonArray();
		for ( int d = 0; d < bs.size(); ++d )
			blockSize[ d ] = bs.get( d  ).getAsInt();
	}
	
	public static int[] defaultBlockSize()
	{
		return new int[] { 32, 32, 32 };
	}
			
}
