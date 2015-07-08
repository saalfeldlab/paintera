/**
 * 
 */
package bdv.img.dvid;

import java.util.Map;
import bdv.util.JsonHelper;
import com.google.gson.Gson;
import com.googlecode.gentyref.TypeToken;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class DataInstance
{
	static public class Base
	{
		public String TypeName;
		public String TypeURL;
		public String TypeVersion;
		public String Name;
		public String RepoUUID;
		public String Compression;
		public String Checksum;
		public String Persistence;
		public boolean Versioned;
	}
	
	public Base Base;
	
	static public void main( final String... args )
	{
		final Map< String, Info > info =
				JsonHelper.tryFetch(
						"http://hackathon.janelia.org/api/repos/info",
						new TypeToken< Map< String, Info > >(){}.getType(),
						20 );
		final Map< String, DataInstance > dataInstances = info.values().iterator().next().DataInstances;
		
		System.out.println( new Gson().toJson( dataInstances ) );
	}
}
