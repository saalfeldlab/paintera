/**
 * 
 */
package bdv.img.dvid;

import java.util.Map;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Info
{
	public String Root;
	public String Alias;
	public String Description;
	public String[] Log;
	public Map< String, DataInstance > DataInstances;
	public String Created;
	public String Updated;
}
