/**
 * 
 */
package bdv.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JsonHelper
{
	final static public <T> T fetch( final String url, final Type type )
			throws JsonSyntaxException, JsonIOException, IOException
	{
		final Gson gson = new Gson();
		final T t =
				gson.fromJson(
						new InputStreamReader( new URL( url ).openStream() ),
						type );
		return t;
	}

	final static public <T> T tryFetch(
			final String url,
			final Type type,
			final int maxNumTrials )
	{
		T t = null;
		for ( int i = 0; i < maxNumTrials && t == null; ++i )
		{
			try
			{
				t = fetch( url, type );
				break;
			}
			catch ( final Exception e )
			{}
			try
			{
				Thread.sleep( 100 );
			}
			catch ( final InterruptedException e )
			{}
		}
		return t;
	}
}
