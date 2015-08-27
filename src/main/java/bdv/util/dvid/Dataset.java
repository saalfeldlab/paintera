package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import bdv.util.JsonHelper;
import bdv.util.http.HttpRequest;
import bdv.util.http.HttpRequest.Writer;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 * Dataset class holds convenience methods, such as getMethod and
 * subclasses of Dataset hold datatype specific get and post methods.
 *
 */
public class Dataset
{
	
	public static final String PROPERTY_TYPENAME = "typename";
	public static final String POPERTY_DATANAME = "dataname";
	public static final String PROPERTY_SYNC = "sync";

	protected final Node node;
	protected final String name;
	protected final String type;

	/**
	 * @return The currnent checkout.
	 */
	public Node getNode()
	{
		return node;
	}
	
	/**
	 * @return The name of the dataset.
	 */
	public String getName()
	{
		return name;
	}

	public Dataset( Node node, String name, String type )
	{
		super();
		this.node = node;
		this.name = name;
		this.type = type;
	}
	
	/**
	 * @return the base url of this data set, i.e.
	 * <server-url>:<port>/api/node/<uuid>/<name>
	 */
	public String getUrl()
	{
		return node.getUrl() + "/" + name;
	}
	
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @return <{@link Dataset#getURL}>/<request>
	 */
	public String mergeUrlAndRequest( String request )
	{
		return new StringBuilder( getUrl() )
				.append( "/" )
				.append( request )
				.toString()
				;
	}
	
	/**
	 * Call {@link Dataset#getRequestString(String, String)} without specifying
	 *  format.
	 */
	public String getRequestString( String request )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ) );
	}
	
	
	/**
	 * Call {@link Dataset#getRequestString(String, String, Map)} without options.
	 */
	public String getRequestString( String request, String format )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ), format );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param format If non-empty {@link String}, do retrieve data as specified by format.
	 * @param options Key-Value pairs will be appended to the URL as comma separated list.
	 * Key-only options can be passed with empty-string or null Value.
	 * @return <url><fmt><opts> with:
	 * <fmt>  := "" if format is null or empty {@link String}, "/format" otherwise
	 * <opts> := "" if options is empty map or null, "?key1=value1,key2,key3=value3,..." otherwise
	 */
	public String getRequestString( String request, String format, Map< String, String > options )
	{
		return DvidUrlOptions.getRequestString( mergeUrlAndRequest( request ), format, options );
	}
	
	/**
	 * @return Dataset info as {@link JsonObject}.
	 * @throws JsonSyntaxException
	 * @throws JsonIOException
	 * @throws IOException
	 */
	public JsonObject getInfo() throws JsonSyntaxException, JsonIOException, IOException
	{
		String url = getRequestString( "info" );
		return JsonHelper.fetch( url, JsonObject.class );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @return byte[] containig the data provded by the dvid server in the response.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public byte[] get( String request ) throws MalformedURLException, IOException
	{
		return HttpRequest.getRequest( getRequestString( request ) );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param result byte[] for storing the data provded by the dvid server in the response.
	 * Picking the correct size is left to the user.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void get( String request, byte[] result ) throws MalformedURLException, IOException
	{
		HttpRequest.getRequest( getRequestString( request ), result );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 * The content type of the request will be set to "application/octet-stream".
	 * 
	 */
	public void post( String request, byte[] data ) throws MalformedURLException, IOException
	{
		post( request, data, "application/octet-stream" );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @param contentType Specifies the content type of the http request.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void post( String request, byte[] data, String contentType ) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( getRequestString( request ), data, contentType );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 * The content type of the request will be set to "application/octet-stream".
	 * 
	 */
	public void post( String request, long[] data ) throws MalformedURLException, IOException
	{
		post( request, data, "application/octet-stream" );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @param contentType Specifies the content type of the http request.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void post( String request, long[] data, String contentType ) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( getRequestString( request ), data, contentType );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @param writer {@link Writer} that handles sending data of type T to the dvid server.
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 * The content type of the request will be set to "application/octet-stream".
	 * 
	 */
	public <T> void post( String request, T data, Writer< T > writer ) throws MalformedURLException, IOException
	{
		post( request, data, "application/octet-stream", writer );
	}
	
	/**
	 * @param request Request to dvid server that gets appended to the url.
	 * @param data Data to be sent to the dvid server.
	 * @param contentType Specifies the content type of the http request.
	 * @param writer {@link Writer} that handles sending data of type T to the dvid server.
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public <T> void post( String request, T data, String contentType, Writer< T > writer ) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( getRequestString( request ), data, contentType, writer );
	}
	
	/**
	 * @return Remove itself from the dvid server.
	 * @throws IOException
	 */
	public int deleteSelf() throws IOException
	{
		return this.getNode().deleteDatset( this );
	}
	
	/**
	 * @return The type of the dataset.
	 */
	public String getType()
	{
		return type;
	}

}
