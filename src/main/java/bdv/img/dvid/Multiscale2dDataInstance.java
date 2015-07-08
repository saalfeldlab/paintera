/**
 * 
 */
package bdv.img.dvid;

import java.util.TreeMap;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Multiscale2dDataInstance extends DataInstance
{
	static public class Extended
	{
		static public class Level
		{
			public double[] Resolution;
			public int[] TileSize;
		}
		
		public String Source;
		public boolean Placeholder;
		public int Encoding;
		public int Quality;
		public TreeMap<Integer, Level> Levels;
		
		final Level getLevel( final Integer s )
		{
			return Levels.get( s );
		}
	}
	
	public Extended Extended;
}
