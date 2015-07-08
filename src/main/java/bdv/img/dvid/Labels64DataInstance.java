/**
 * 
 */
package bdv.img.dvid;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 * 
 * TODO incomplete
 */
public class Labels64DataInstance extends DataInstance
{
	static public class Extended
	{
		public boolean Interpolable;
		public int[] BlockSize;
		public double[] VoxelSize;
		public String[] VoxelUnits;
		public long[] MinPoint;
		public long[] MaxPoint;
		public long[] MinIndex;
		public long[] MaxIndex;
		public int Background;
	}
	
	public Extended Extended;
}
