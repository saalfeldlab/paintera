package bdv.util.dvid;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public class DvidUrlOptions
{
	
	private final static TreeMap< String, String > defaultOptions = generateDefaultOptions();
	
	private final static TreeMap< String, String > generateDefaultOptions()
	{
		TreeMap< String, String > tm = new TreeMap< String, String >();
		tm.put( "interactive", "false" );
		return tm;
	}
	
	public static String getRequestString( String request )
	{
		return getRequestString( request, null );
	}
	
	public static String getRequestString( String request, String format )
	{
		return getRequestString( request, format, defaultOptions );
	}
	
	public static String getRequestString( String urlString, String format, Map< String, String > options )
	{
		StringBuilder url = new StringBuilder( urlString );
		if ( format != null && format.length() > 0 )
			url.append( "/" ).append( format )
		;
		
		if ( options != null && options.size() > 0 )
		{
			Iterator< Entry< String, String >> it = options.entrySet().iterator();
			Entry< String, String > firstEntry = it.next();
			appendKeyValue( url, firstEntry.getKey(), firstEntry.getValue(), "?" );
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
}
