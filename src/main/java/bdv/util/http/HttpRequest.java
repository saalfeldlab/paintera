package bdv.util.http;

import java.io.ByteArrayOutputStream;
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
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 * Convenience methods for requests and responses.
 *
 */
public class HttpRequest
{
	
	public static final String POST = "POST";

	public static final String GET = "GET";
	
	public static final String DELETE = "DELETE";
	
	public static final String HEAD = "HEAD";
	
	public static final String CHARSET_UTF8 = "UTF-8";
	
	/**
	 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
	 *
	 * Interface for delegating management of the input stream to the caller.
	 *
	 */
	public static interface ResponseHandler
	{
		/**
		 * 
		 * Any class implementing this can handle the {@link InputStream} according
		 * to its needs.
		 * 
		 * @param in 
		 * @throws IOException
		 */
		public void handle( InputStream in ) throws IOException;
	}
	
	/**
	 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
	 *
	 * Interface for delegating writing to a {@link DataOutputStream} to
	 * the caller. This allows for writing of data of arbitrary type T as
	 * long as the caller provides an implementation of {@link Writer<T>}.
	 * 
	 */
	public static interface Writer< T >
	{
		public void write( DataOutputStream out, T data ) throws IOException;
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
	
	public static class ByteArrayWriter implements Writer< byte[] >
	{
		@Override
		public void write( DataOutputStream out, byte[] data ) throws IOException
		{
			out.write( data );
		}
	}
	
	public static class LongArrayWriter implements Writer< long[] >
	{

		@Override
		public void write( DataOutputStream out, long[] data ) throws IOException
		{
			for ( long d : data )
				out.writeLong( d );
		}
		
	}
	
	
	public static class ChunkedByteArrayResponseHandler implements ResponseHandler
	{
		
		private byte[] array;
		private final int bufferSize;
		
		public ChunkedByteArrayResponseHandler( int bufferSize )
		{
			super();
			this.bufferSize = bufferSize;
		}
		
		public byte[] getArray()
		{
			return array;
		}

		public static void copy(InputStream in, OutputStream out, int bufferSize)
				throws IOException
		{
			// Read bytes and write to destination until eof
			byte[] buf = new byte[bufferSize];
			int len = 0;
			while ((len = in.read(buf)) >= 0)
			{
				out.write(buf, 0, len);
			}
		}


		@Override
		public void handle( InputStream in ) throws IOException
		{
			ByteArrayOutputStream sink = new ByteArrayOutputStream();
			
			copy( in, sink, bufferSize );
			
			this.array = sink.toByteArray();
		}
		
	}
	
	/**
	 * 
	 * HTTP Get request:
	 * GET url
	 * 
	 * If the HTTP status code is not 200, this method throws
	 * {@link HTTPException}.
	 * 
	 * @param url Url for GET request. 
	 * @return Data sent by the server in response as byte[].
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static byte[] getRequest( String url ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		int response = connection.getResponseCode();
		if ( response != 200 )
			throw new HTTPException( response );
		
		byte[] bytes;
		String transferEncoding = connection.getHeaderField( "Transfer-Encoding" );
		if ( transferEncoding != null && transferEncoding.compareToIgnoreCase( "chunked" ) == 0 )
		{
			ChunkedByteArrayResponseHandler handler = new ChunkedByteArrayResponseHandler( 1024 );
			getRequest( connection, handler );
			bytes = handler.getArray();
		} else
		{
			int contentLength = Integer.parseInt( connection.getHeaderField( "Content-Length" ) );
			bytes = new byte[ contentLength ];
			getRequest( connection, new ByteArrayResponseHandler( bytes ) );
		}
		connection.disconnect();
		return bytes;
	}
	
	/**
	 * 
	 * HTTP Get request:
	 * GET url
	 * 
	 * Only use this, if you know the data size beforehand and
	 * you are sure that the header field "Transfer-Encoding" does
	 * not exist or is not "chunked".
	 * 
	 * If the HTTP status code is not 200, this method throws
	 * {@link HTTPException}.
	 * 
	 * @param url Url for GET request.
	 * @param bytes Array (byte[]) for storing result. 
	 * @return Data sent by the server in response as byte[].
	 * @throws MalformedURLException
	 * @throws IOException
	 */
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
	
	/**
	 * Handle GET request as specified by handler.
	 * 
	 * @param connection Open connection. Closing this connection is the responsibility of the caller.
	 * @param handler Caller specifies how to handle the data.
	 * @throws IOException
	 */
	public static void getRequest( HttpURLConnection connection, ResponseHandler handler ) throws IOException
	{
		InputStream in = connection.getInputStream();
		handler.handle( in );
		in.close();
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static void postRequest( String url, byte[] postData, String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, postData, contentType );
		connection.disconnect();
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static void postRequest( String url, long[] postData, String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, postData, contentType );
		connection.disconnect();
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * The connection is returned to allow the caller to handle a potential response.
	 * The caller is responsible for closing the connection.
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @return HTTP Connection for response handling
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static HttpURLConnection postRequestWithResponse( String url, byte[] postData, String contentType ) throws MalformedURLException, IOException
	{
		return postRequestWithResponse( url, postData, contentType, new ByteArrayWriter() );
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * The connection is returned to allow the caller to handle a potential response.
	 * The caller is responsible for closing the connection.
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @return HTTP Connection for response handling
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static HttpURLConnection postRequestWithResponse( String url, long[] postData, String contentType ) throws MalformedURLException, IOException
	{
		return postRequestWithResponse( url, postData, contentType, new LongArrayWriter() );
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @param dataWriter Caller specified 
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static < T > void postRequest(
			String url,
			T postData,
			String contentType,
			Writer< T > dataWriter ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, postData, contentType, dataWriter );
		connection.disconnect();
	}
	
	/**
	 * 
	 * HTTP POST request:
	 * POST url
	 * 
	 * The connection is returned to allow the caller to handle a potential response.
	 * The caller is responsible for closing the connection.
	 * 
	 * @param url Url for request. 
	 * @param postData Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @param dataWriter Caller needs to specify how to write data.
	 * @return HTTP Connection for response handling
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static < T > HttpURLConnection postRequestWithResponse( 
			String url, 
			T postData, 
			String contentType,
			Writer< T > dataWriter ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		connection.setDoOutput( true );
		connection.setRequestMethod( POST );
		connection.setRequestProperty( "Content-Type", contentType );
		
		// Write data.
		OutputStream stream = connection.getOutputStream();
		DataOutputStream writer = new DataOutputStream( stream );
		dataWriter.write( writer, postData );
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
	
	
	/**
	 * 
	 * HTTP POST request for {@link Iterable} on {@link IntegerType}:
	 * POST url
	 * 
	 * @param url Url for request. 
	 * @param iterable Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static < T extends IntegerType< T > > void postRequest( 
			String url,
			Iterable< T > iterable,
			String contentType ) throws MalformedURLException, IOException
	{
		HttpURLConnection connection = postRequestWithResponse( url, iterable, contentType );
		connection.disconnect();
	}
	
	
	/**
	 * 
	 * HTTP POST request for {@link Iterable} on {@link IntegerType}:
	 * POST url
	 * 
	 * The connection is returned to allow the caller to handle a potential response.
	 * The caller is responsible for closing the connection.
	 * 
	 * @param url Url for request. 
	 * @param iterable Data to be posted
	 * @param contentType Value of the header field "Content-Type"
	 * @return HTTP Connection for response handling
	 * @throws MalformedURLException
	 * @throws IOException
	 */
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
		if ( iterable.iterator().next() instanceof UnsignedLongType || iterable.iterator().next() instanceof LongType )
		{
			for ( T i : iterable )
				writer.writeLong( i.getIntegerLong() );
		}
		else if ( iterable.iterator().next() instanceof UnsignedByteType || iterable.iterator().next() instanceof ByteType )
		{
			for ( T i : iterable )
			{
				writer.writeByte( i.getInteger() & 0xff );
			}
		}
		else
		{
			for ( T i : iterable )
				writer.writeInt( i.getInteger() );
		}
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
	
	/**
	 * 
	 * HTTP POST request for {@link JsonElement}:
	 * POST url
	 * 
	 * @param url Url for request.
	 * @param json Data to be posted.
	 * @return HTTP Connection for response handling
	 * @throws MalformedURLException
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static HttpURLConnection postRequestJSON( String url, JsonElement json ) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		return postRequestWithResponse( url, json.toString().getBytes( CHARSET_UTF8 ), "application/json; charset=UTF-8" );
	}
	
	/**
	 * 
	 * HTTP DELETE request:
	 * DELETE url
	 * 
	 * @param url Url for request.
	 * @return HTTP status code of the DELETE request.
	 * @throws IOException
	 */
	public static int delete ( String url ) throws IOException
	{
		HttpURLConnection connection = ( HttpURLConnection ) new URL( url ).openConnection();
		connection.setDoOutput( true );
		connection.setRequestMethod( DELETE );
		connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
		connection.connect();
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}
	
}
