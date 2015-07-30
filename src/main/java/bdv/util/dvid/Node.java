package bdv.util.dvid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import bdv.util.http.HttpRequest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Node
{
	
	private final String uuid;
	private final Repository repository;
	
	public String getUuid()
	{
		return uuid;
	}
	
	public Node getParent()
	{
		return null; // null for now
	}
	
	public Repository getRepository()
	{
		return repository;
	}
	
	public String getUrl()
	{
		return repository.getServer().getApiUrl() +"/node/" + this.uuid;
	}
	
	public void commit( String note, String[] log ) throws MalformedURLException, IOException
	{
		JsonArray arr = new JsonArray();
		
		for ( String l : log )
			arr.add(  new JsonPrimitive( l ) );
		
		JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		json.add( "log", arr );
		String url = getUrl() + "/commit";
		HttpRequest.postRequestJSON( url, json );
	}
	
	public Node branch( String note ) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		String url = getUrl() + "/branch";
		HttpURLConnection connection = HttpRequest.postRequestJSON( url, json );
		JsonObject response = new Gson().fromJson( new InputStreamReader( connection.getInputStream() ), JsonObject.class );
		connection.disconnect();
		return new Node( response.get( "child" ).getAsString(), this.repository );
	}
	
	public Node( String uuid, Repository repository )
	{
		super();
		this.uuid = uuid;
		this.repository = repository;
	}
	
	public Dataset createDataset( String name, String type, String... sync ) throws MalformedURLException, IOException
	{
		String postUrl = new StringBuilder( repository.getServer().getApiUrl() )
			.append( "/repo/" )
			.append( this.uuid )
			.append( "/instance" )
			.toString()
			;
		
		JsonObject json = new JsonObject();
		json.addProperty( Dataset.POPERTY_DATANAME, name );
		json.addProperty( Dataset.PROPERTY_TYPENAME, type );
		if ( sync.length > 0 )
		{
			StringBuilder syncString = new StringBuilder( sync[ 0 ] );
			for ( int i = 1; i < sync.length; ++i )
			{
				syncString
					.append( "," )
					.append( sync[ i ] )
					;
			}
			json.addProperty( Dataset.PROPERTY_SYNC, syncString.toString() );
		}
		
		HttpRequest.postRequestJSON( postUrl, json ).disconnect();
		
		return new Dataset( this, name );
	}
	
	public static int compareUuids( String uuid1, String uuid2 )
	{
		int length = Math.min( uuid1.length(), uuid2.length() );
		return uuid1.substring( 0, length ).compareTo( uuid2.substring( 0, length ) );
	}
	
	public static boolean uuidEquivalenceCheck( String uuid1, String uuid2 )
	{
		return compareUuids( uuid1, uuid2 ) == 0;
	}
	
}
