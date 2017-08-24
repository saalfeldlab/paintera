package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.labels.labelset.ByteUtils;

public class MultisetDataset
{
	private final DatasetBlkLabel original;
	private final DatasetKeyValue[] levels;
	
	public static class VolatileCachedKeyValue extends DatasetKeyValue
	{

		public VolatileCachedKeyValue( Node node, String name )
		{
			super( node, name );
		}
		
	}
	
	public MultisetDataset( DatasetBlkLabel original, DatasetKeyValue[] levels )
	{
		super();
		this.original = original;
		this.levels = levels;
	}
	
	public static MultisetDataset create( 
			DatasetBlkLabel original,
			String[] targetDatasetNames ) throws JsonSyntaxException, JsonIOException, IOException
	{
		DatasetKeyValue[] levels = new DatasetKeyValue[ targetDatasetNames.length ];
		
		for ( int  l = 0; l < levels.length; ++ l )
		{
			levels[ l ] = new DatasetKeyValue( original.getNode(), targetDatasetNames[ l ] );
		}
		
		return new MultisetDataset( original, levels );
	}
	
	public void put( int level, long[] coordinate, long[] data ) throws MalformedURLException, IOException
	{
		DatasetKeyValue ds = levels[ level ];
		String key = Arrays.toString( coordinate );
		ds.postKey( key, data );
	}
	
	public long[] get( int level, long[] coordinate ) throws MalformedURLException, IOException
	{
		DatasetKeyValue ds = levels[ level ];
		String key = Arrays.toString( coordinate );
		ByteBuffer bb = ByteBuffer.wrap( ds.getKey( key ) );
		assert bb.capacity() % ByteUtils.LONG_SIZE == 0;
		long[] target = new long[ bb.capacity() / ByteUtils.LONG_SIZE ];
		for ( int i = 0; i < target.length; ++i )
			target[ i ] = bb.getLong();
		return target;
	}
	
	public static void main( String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "c8d25fde1fc44325a330722162905d0a";
		String checkout = "0514249a2c354167ac569575b80c1dbd";
		String dataset = "labelblk";
		
		Repository repo = new Repository( url, uuid );
		Node root = repo.checkout( checkout );
		DatasetBlkLabel original = new DatasetBlkLabel( root, dataset );
		
		create( original, null );
		
	}
	
	
	
	
}
