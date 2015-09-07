package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.http.HttpRequest;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 * @param <T>
 * 
 * Base class for blocked dataset types, e.g. labelblk, imageblk
 */
public abstract class DatasetBlk< T > extends Dataset
{

	public DatasetBlk( Node node, String name, String type )
	{
		super( node, name, type );
	}

	/**
	 * @param target {@link RandomAccessibleInterval} to be written into.
	 * @param offset Specifies top left position of target within the dataset.
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 * Load data from server into {@link RandomAccessibleInterval}.
	 * 
	 */
	public abstract void get( 
			RandomAccessibleInterval< T > target,
			int[] offset
			) throws MalformedURLException, IOException;

	/**
	 * @param source {@link RandomAccessibleInterval} to be read from.
	 * @param offset Specifies top left position of source within the dataset.
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 * Write data to server from {@link RandomAccessibleInterval} 
	 * 
	 */
	public abstract void put( 
			RandomAccessibleInterval< T > source,
			int[] offset
			) throws MalformedURLException, IOException;
	
	/**
	 * @param image Defines image dimensions.
	 * @param offset Defines image position.
	 * @return The URL for retrieving an arbitrary interval with
	 * dimensions defined by image and at position defined by offset.
	 */
	public String getIntervalRequestUrl( Interval image, int[] offset )
	{
		return getRequestString( getIntervalRequestString( image, offset ) );
	}

	/**
	 * @param min Pixel contained in block to be retrieved.
	 * @param blockSize Size of blocks in data set.
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Appropriate url to retrieve block that contains pixel
	 * defined by min.
	 */
	public String getBlockRequestUrl( int[] min, int[] blockSize, int nBlocks )
	{
		return getRequestString( getBlockRequestString( min, blockSize, nBlocks ) );
	}

	/**
	 * @param position Coordinates of block (e.g. for block size [32, 32, 32],
	 * the top left corner of the block identified by position [1, 2, 3] is at
	 * [32, 64, 96] in real world coordinates).
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Appropriate url to retrieve block defined by position.
	 */
	public String getBlockRequestUrl( int[] position, int nBlocks )
	{
		return getRequestString( getBlockRequestString( position, nBlocks ) );
	}

	/**
	 * @param min Pixel contained in block to be retrieved.
	 * @param blockSize Size of blocks in data set.  
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Request String to retrieve block that contains pixel
	 * defined by min.
	 */
	public static String getBlockRequestString( int[] min, int[] blockSize, int nBlocks )
	{
		int[] position = new int[] {
				min[ 0 ] / blockSize[ 0 ],
				min[ 1 ] / blockSize[ 1 ],
				min[ 2 ] / blockSize[ 2 ]
		};
		return getBlockRequestString( position, nBlocks );
	}

	/**
	 * @param position Coordinates of block (e.g. for block size [32, 32, 32],
	 * the top left corner of the block identified by position [1, 2, 3] is at
	 * [32, 64, 96] in real world coordinates). 
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Request String to retrieve block Appropriate url to retrieve block defined by position.
	 */
	public static String getBlockRequestString( int[] position, int nBlocks )
	{
		StringBuffer buf = new StringBuffer( "blocks/" )
			.append( position[0] )
			.append( "_" )
			.append( position[1] )
			.append( "_" )
			.append( position[2] )
			.append( "/" )
			.append( nBlocks )
			;
		return buf.toString();
	}

	/**
	 * @param min Pixel contained in block to be retrieved.
	 * @param blockSize Size of blocks in data set.
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Block as byte[] that contains pixel defined by min.
	 */
	public byte[] getBlock( int[] min, int[] blockSize, int nBlocks ) throws MalformedURLException, IOException
	{
		String requestUrl = getBlockRequestUrl( min, blockSize, nBlocks );
		return HttpRequest.getRequest( requestUrl );
	}

	/**
	 * @param position Coordinates of block (e.g. for block size [32, 32, 32],
	 * the top left corner of the block identified by position [1, 2, 3] is at
	 * [32, 64, 96] in real world coordinates).
	 * @param nBlocks Number of blocks along X coordinate.
	 * @return Block as byte[] defined by position.
	 */
	public byte[] getBlock( int[] position, int nBlocks ) throws MalformedURLException, IOException
	{
		String requestUrl = getBlockRequestUrl( position, nBlocks );
		return HttpRequest.getRequest( requestUrl );
	}

	/**
	 * @param image Defines image dimensions.
	 * @param offset Defines image position.
	 * @return The request string for retrieving an arbitrary interval with
	 * dimensions defined by image and at position defined by offset.
	 */
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

	/**
	 * @param image Defines image dimensions.
	 * @param result Store result within this array.
	 * @param offset Defines image position.
	 * @return Retrieve image as defined by interval and offset.
	 */
	public void getByteArray(
			Interval interval,
			byte[] result,
			int[] offset ) throws MalformedURLException, IOException
	{
		
		String requestUrl = getRequestString( getIntervalRequestString( interval, offset ) );
		HttpRequest.getRequest( requestUrl, result );
	}

	/**
	 * @return Block size of this data set.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public int[] getBlockSize() throws JsonSyntaxException, JsonIOException, IOException
	{
		int[] blockSize = new int[ 3 ];
		getBlockSize( blockSize );
		return blockSize;
	}

	/**
	 * @param blockSize Write block size of this data set into this array.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public void getBlockSize( int[] blockSize ) throws JsonSyntaxException, JsonIOException, IOException
	{
		JsonArray bs = getInfo().get( "Extended" ).getAsJsonObject().get( "BlockSize" ).getAsJsonArray();
		for ( int d = 0; d < bs.size(); ++d )
			blockSize[ d ] = bs.get( d  ).getAsInt();
	}

	/**
	 * @return Default block size for blocked data sets.
	 */
	public static int[] defaultBlockSize()
	{
		return new int[] { 32, 32, 32 };
	}

}
