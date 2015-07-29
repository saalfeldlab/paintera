package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

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
	
	public String getRequestString( String request )
	{
		return getRequestString( request, null, null );
	}
	
	public String getRequestString( String request, String format, Map< String, String > options )
	{
		StringBuilder url = new StringBuilder( getUrl() )
			.append( "/" )
			.append( request )
			;
		if ( format != null && format.length() > 0 )
			url.append( "/" ).append( format )
				;
		
		if ( options != null && options.size() > 0 )
		{
			Iterator< Entry< String, String >> it = options.entrySet().iterator();
			Entry< String, String > firstEntry = it.next();
			appendKeyValue( url, firstEntry.getKey(), firstEntry.getValue() );
			while( it.hasNext() )
			{	
				Entry< String, String > entry = it.next();
				appendKeyValue( url, entry.getKey(), entry.getValue() );
			}
		}
		
		return url.toString();
	}
	
	public static void appendKeyValue( StringBuilder buf, String key, String value )
	{
		appendKeyValue( buf, key, value, "," );
	}

	public static void appendKeyValue( StringBuilder buf, String key, String value, String separator )
	{
		appendKeyValue( buf, key, value, separator, "=" );
	}

	public static void appendKeyValue( StringBuilder buf, String key, String value, String separator, String equal )
	{
		buf.append( separator );
		buf.append( key );
		if ( value != null && !value.isEmpty() )
		{
			buf.append( equal );
			buf.append( value );
		}
	}
	
	public JsonObject getInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = getRequestString( "info" );
		return JsonHelper.fetch( url, JsonObject.class );
	}
	
	public void get( String request, byte[] result ) throws MalformedURLException, IOException
	{
		HttpRequest.getRequest( getRequestString( request ), result );
	}
	

}
