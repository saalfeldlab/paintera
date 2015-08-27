package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Random;

import javax.xml.ws.http.HTTPException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.http.HttpRequest;

public class DatasetKeyValue extends Dataset
{
	
	public static final String TYPE = "keyvalue";

	public DatasetKeyValue( Node node, String name )
	{
		super( node, name );
	}
	
	public static String getKeyRequestString( String key )
	{
		return "key/" + key;
	}
	
	public String getKeyRequestUrl( String key )
	{
		return getRequestString( getKeyRequestString( key ) );
	}
	
	public byte[] getKey( String key ) throws MalformedURLException, IOException
	{
		return get( getKeyRequestString( key ) );
	}
	
	public void postKey( String key, byte[] data ) throws MalformedURLException, IOException
	{
		post( getKeyRequestString( key ), data );
	}
	
	public void postKey( String key, long[] data ) throws MalformedURLException, IOException
	{
		post( getKeyRequestString( key ), data );
	}
	
	public static void main( String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = "http://vm570.int.janelia.org:8080";
		String uuid ="8c1516c4bee14af7b5f56bf91c1e3cca";
		String dataSet = "bigcat-keyvalue-test1";
		Node root = new Repository( url, uuid ).getRootNode();
		DatasetKeyValue ds = new DatasetKeyValue( root, dataSet );
		try
		{
			root.createDataset( dataSet, DatasetKeyValue.TYPE );
		}
		catch ( Exception e1 )
		{
//			e1.printStackTrace();
		}
		System.out.println( ds.getInfo() );
		ds.postKey( "123", "45ö6".getBytes( HttpRequest.CHARSET_UTF8 ) );
		byte[] b = new byte[ 32*32*32*16 ];
		Random rng = new Random( 100 );
		rng.nextBytes( b );
		ds.postKey( "456", b );
//		try
//		{
//			System.out.println( new String( ds.getKey( "123" ), HttpRequest.CHARSET_UTF8 ) );
//		} catch ( HTTPException e )
//		{
//			System.out.println( e.getStatusCode() );
//		}
		try {
			byte[] res = ds.getKey( "456" );
			for ( int i = 0; i < res.length; ++i )
			{
				if ( res[i] != b[i] )
				{
					System.out.println( "MIZZGE LÄUFT SCHIEF! " + i + ": " + res[i] + " " + b[i] );
					System.exit( 9002 );
				}
			}
//			System.out.println( Arrays.toString( res ) );
		} catch ( HTTPException e )
		{
			System.out.println( e.getStatusCode() );
		}
	}

}
