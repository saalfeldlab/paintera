package bdv.util.dvid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.ws.http.HTTPException;

import bdv.util.Constants;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Repository
{
	
	private final String apiUrl;
	private final String uuid;
	
	
	public Repository( String apiUrl ) throws MalformedURLException, IOException
	{
		this( apiUrl, initialize( apiUrl, new JsonObject() ).get( "root" ).getAsString() );
	}
	
	
	public Repository( String apiUrl, String alias, String description ) throws MalformedURLException, IOException
	{
		this( apiUrl, initialize( apiUrl, alias, description ) );
	}
	
	
	public Repository( String apiUrl, String uuid )
	{
		this.apiUrl = apiUrl;
		this.uuid = uuid;
	}
	
	
	public String getApiUrl()
	{
		return apiUrl;
	}


	public String getUuid()
	{
		return uuid;
	}


	public Dataset create( String name, String type, String... sync ) throws MalformedURLException, IOException
	{
		String postUrl = new StringBuilder( apiUrl )
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
		
		
		HttpURLConnection connection = (HttpURLConnection ) new URL( postUrl ).openConnection();
		connection.setRequestMethod( Constants.POST );
		connection.setDoOutput( true );
		connection.setRequestProperty( "Content-Type", "application/json; charset=UTF-8");
		
		OutputStream out = connection.getOutputStream();
		out.write( json.toString().getBytes( Constants.CHARSET_UTF8 ) );
		
		int response = connection.getResponseCode();
		
		if ( response != 200 )
		{
			throw new HTTPException( response );
		}
		
		return new Dataset( this.uuid, name, type, sync );
		
	}

	
	public static String initialize( 
			String apiUrl, 
			String alias, 
			String description ) throws MalformedURLException, IOException
	{
		JsonObject json = generateFromAliasAndDescription( alias, description );
		String uuid = initialize( apiUrl, json ).get( "root" ).getAsString();
		return uuid;
	}
	
	
	public static JsonObject initialize( String apiUrl, JsonObject json ) throws MalformedURLException, IOException
	{
		String postUrl = new StringBuilder( apiUrl )
			.append( "/repos" )
			.toString()
			;
		
		HttpURLConnection connection = (HttpURLConnection) new URL( postUrl ).openConnection();
		connection.setRequestMethod( Constants.POST );
		connection.setDoInput( true );
		connection.setDoOutput( true );
		connection.setRequestProperty( "Content-Type", "application/json; charset=UTF-8");
		
		OutputStream out = connection.getOutputStream();
		InputStream in = connection.getInputStream();
		
		out.write( json.toString().getBytes( Constants.CHARSET_UTF8 ) );
		out.flush();
		out.close();
		
		int response = connection.getResponseCode();
		
		if ( response != 200 )
		{
			throw new HTTPException( response );
		}
		
		int len = connection.getContentLength();
		byte[] byteRepresentation = new byte[ len ];
		
		int off = 0;
		do off = in.read( byteRepresentation, off, len - off );
		while ( off > 0 );
		
		JsonObject rootInfo = 
				new JsonParser().parse( new String( byteRepresentation, Constants.CHARSET_UTF8 ) ).getAsJsonObject();
		
		return rootInfo;
		
	}
	
	
	public static JsonObject generateFromAliasAndDescription( String alias, String description )
	{
		JsonObject json = new JsonObject();
		json.addProperty( "alias", alias );
		json.addProperty( "description", description );
		return json;
	}
	
	public static void main( String[] args ) throws MalformedURLException, IOException
	{
		
		String apiUrl = "http://vm570.int.janelia.org:8080/api";
		String uuid = "2fa87e8e61684bef9d2a92756a65d228";
		Repository repo = new Repository( apiUrl, uuid );
		
		// To create new repo, do: 
		// Repository repo = new Repository( apiUrl, uuid );
		
		System.out.println( repo.getApiUrl() );
		System.out.println( repo.getUuid() );
		
		Dataset ds = repo.create( "testing", "labelblk" );
		
		System.out.println( ds.getName() );
		System.out.println( ds.getType() );
		System.out.println( ds.getUuid() );
		System.out.println( ds.getSync() );
		
	}
	
	
	
}
