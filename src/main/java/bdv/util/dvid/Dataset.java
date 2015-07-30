package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import bdv.util.JsonHelper;
import bdv.util.http.HttpRequest;

public class Dataset
{
	
	public static final String PROPERTY_TYPENAME = "typename";
	public static final String POPERTY_DATANAME = "dataname";
	public static final String PROPERTY_SYNC = "sync";

	private final Node node;
	private final String name;

	public Node getNode()
	{
		return node;
	}
	
	public String getName()
	{
		return name;
	}

	public Dataset( Node node, String name )
	{
		super();
		this.node = node;
		this.name = name;
	}
	
	public String getUrl()
	{
		return node.getUrl() + "/" + name;
	}
	
	public String mergeUrlAndRequest( String request )
	{
		return new StringBuilder( getUrl() )
				.append( "/" )
				.append( request )
				.toString()
				;
	}
	
	public String getRequestString( String request )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ) );
	}
	
	public String getRequestString( String request, String format )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ), format );
	}
	
	public String getRequestString( String request, String format, Map< String, String > options )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ), format, options );
	}
	
	public JsonObject getInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = getRequestString( "info" );
		return JsonHelper.fetch( url, JsonObject.class );
	}
	
	public byte[] get( String request ) throws MalformedURLException, IOException
	{
		return HttpRequest.getRequest( getRequestString( request ) );
	}
	
	public void get( String request, byte[] result ) throws MalformedURLException, IOException
	{
		HttpRequest.getRequest( getRequestString( request ), result );
	}
	
	public void post( String request, byte[] data ) throws MalformedURLException, IOException
	{
		post( request, data, "application/octet-stream" );
	}
	
	public void post( String request, byte[] data, String contentType ) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( getRequestString( request ), data, contentType );
	}

}
