package bdv.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Convenience class for creating post/get URLs for dvid labelblk type.
 *
 */
public class DvidLabelBlkURL
{

	public static String makeRawString(
			String apiUrl,
			String uuid,
			String dataName,
			int[] dims,
			int[] size,
			int[] offset )
	{
		return makeRawString( apiUrl, uuid, dataName, dims, size, offset, "" );
	}

	public static String makeRawString(
			String apiUrl,
			String uuid,
			String dataName,
			int[] dims,
			int[] size,
			int[] offset,
			String format )
	{
		return makeRawString( apiUrl, uuid, dataName, dims, size, offset, format, null );
	}

	public static String makeRawString(
			String apiUrl,
			String uuid,
			String dataName,
			int[] dims,
			int[] size,
			int[] offset,
			String format,
			Map< String, String > opts )
	{
		assert dims.length > 0 && dims.length == size.length;

		final StringBuffer buf = new StringBuffer( apiUrl );

		buf.append( "/node/" );
		buf.append( uuid );
		buf.append( "/" );
		buf.append( dataName );
		buf.append( "/raw/" );
		addArray( buf, dims );
		buf.append( "/" );
		addArray( buf, size );
		buf.append( "/" );
		addArray( buf, offset );

		if ( !format.isEmpty() )
		{
			buf.append( "/" );
			buf.append( format );
		}

		if ( opts != null && opts.size() > 0 )
		{
			buf.append( "?" );
			Set< Entry< String, String >> es = opts.entrySet();
			Iterator< Entry< String, String >> it = es.iterator();
			Entry< String, String > first = it.next();

			appendKeyValue( buf, first.getKey(), first.getValue(), "" );

			{
				while ( it.hasNext() )
				{
					Entry< String, String > entry = it.next();
					appendKeyValue( buf, entry.getKey(), entry.getValue() );
				}
			}
		}

		return buf.toString();
	}

	public static void addArray( StringBuffer buf, final int[] array )
	{
		buf.append( array[ 0 ] );
		for ( int i = 1; i < array.length; ++i )
		{
			buf.append( "_" );
			buf.append( array[ i ] );
		}
	}

	public static void appendKeyValue( StringBuffer buf, String key, String value )
	{
		appendKeyValue( buf, key, value, "," );
	}

	public static void appendKeyValue( StringBuffer buf, String key, String value, String separator )
	{
		appendKeyValue( buf, key, value, separator, "=" );
	}

	public static void appendKeyValue( StringBuffer buf, String key, String value, String separator, String equal )
	{
		buf.append( separator );
		buf.append( key );
		if ( value != null && !value.isEmpty() )
		{
			buf.append( equal );
			buf.append( value );
		}

	}

	public static void main( String[] args )
	{
		HashMap< String, String > opts = new HashMap< String, String >();
		opts.put( "opt1", "val1" );
		opts.put( "opt2", "" );
		System.out.println( makeRawString( "<api URL>", "3f8c", "segmentation", new int[] { 0, 1 }, new int[] { 512, 256 }, new int[] { 0, 0, 100 }, "fmt", opts ) );
	}

}
