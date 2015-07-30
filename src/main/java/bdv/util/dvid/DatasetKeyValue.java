package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;

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
	
	public static void main( String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = "http://vm570.int.janelia.org:8080";
		String uuid ="9c7cc44aa0544d33905ce82d153e2544";
		String dataSet = "bigcat-keyvalue-test1";
		Node root = new Repository( url, uuid ).getRootNode();
		DatasetKeyValue ds = new DatasetKeyValue( root, dataSet );
		System.out.println( ds.getInfo() );
		ds.postKey( "123", "45ö6".getBytes( HttpRequest.CHARSET_UTF8 ) );
		try
		{
			System.out.println( new String( ds.getKey( "123" ), HttpRequest.CHARSET_UTF8 ) );
		} catch ( HTTPException e )
		{
			System.out.println( e.getStatusCode() );
		}
		
	}

}
