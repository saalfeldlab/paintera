package bdv.util.http;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.ws.http.HTTPException;

import com.google.gson.JsonElement;

import net.imglib2.type.numeric.IntegerType;

public class HttpRequest
{
	
	public static final String POST = "POST";

	public static final String GET = "GET";
	
	public static final String CHARSET_UTF8 = "UTF-8";
	
	public static interface ResponseHandler
	{
		public void handle( InputStream in ) throws IOException;
	}
	
	public static class ByteArrayResponseHandler implements ResponseHandler
	{
		
		private final byte[] array;

		public ByteArrayResponseHandler( byte[] array )
		{
			this.array = array;
		}

		@Override
		public void handle( InputStream in ) throws IOException
		{
			int off = 0, l = 0;
			do
			{
				l = in.read( array, off, array.length - off );
				off += l;
			}
			while ( l > 0 );
		}
		
	}
	
	public static byte[] getRequest( String url ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		int response = connection.getResponseCode();
		if ( response != 200 )
			throw new HTTPException( response );
		String contentLength = connection.getHeaderField( "content-length" );
		byte[] bytes = new byte[ Integer.parseInt( contentLength ) ];
		getRequest( connection, new ByteArrayResponseHandler( bytes ) );
		connection.disconnect();
		return bytes;
	}
	
	public static byte[] getRequest( String url, byte[] bytes ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		int response = connection.getResponseCode();
		if ( response != 200 )
			throw new HTTPException( response );
		getRequest( connection, new ByteArrayResponseHandler( bytes ) );
		connection.disconnect();
		return bytes;
	}
	
	public static void getRequest( HttpURLConnection connection, ResponseHandler handler ) throws IOException
	{
		InputStream in = connection.getInputStream();
		handler.handle( in );
		in.close();
	}
	
	public static void postRequest( String url, byte[] postData, String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, postData, contentType );
		connection.disconnect();
	}
	
	public static HttpURLConnection postRequestWithResponse( String url, byte[] postData, String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		connection.setDoOutput( true );
		connection.setRequestMethod( POST );
		connection.setRequestProperty( "Content-Type", contentType );
		
		// Write data.
		OutputStream stream = connection.getOutputStream();
		DataOutputStream writer = new DataOutputStream( stream );
		writer.write( postData );
		writer.flush();
		writer.close();
		
		int response = connection.getResponseCode();
		if ( response != 200 )
			throw new HTTPException( response );
		
		String contentLength = connection.getHeaderField( "content-length" );
		if ( contentLength == null )
			return null;
		
		return connection;
	}
	
	public static < T extends IntegerType< T > > void postRequest( 
			String url,
			Iterable< T > iterable,
			String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, iterable, contentType );
		connection.disconnect();
	}
	
	public static < T extends IntegerType< T > > HttpURLConnection postRequestWithResponse( 
			String url,
			Iterable< T > iterable,
			String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		connection.setDoOutput( true );
		connection.setRequestMethod( POST );
		connection.setRequestProperty( "Content-Type", contentType );

		// Write data.
		OutputStream stream = connection.getOutputStream();
		DataOutputStream writer = new DataOutputStream( stream );
		for ( T i : iterable )
			writer.writeLong( i.getIntegerLong() );
		writer.flush();
		writer.close();

		int response = connection.getResponseCode();
		if ( response != 200 )
			throw new HTTPException( response );
		
		String contentLength = connection.getHeaderField( "content-length" );
		if ( contentLength == null )
			return null;
		
		return connection;
	}
	
	
	public static HttpURLConnection postRequestJSON( String url, JsonElement json ) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		return postRequestWithResponse( url, json.toString().getBytes( CHARSET_UTF8 ), "application/json; charset=UTF-8" );
	}
	
}
