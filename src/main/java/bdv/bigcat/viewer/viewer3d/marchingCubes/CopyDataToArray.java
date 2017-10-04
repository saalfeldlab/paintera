package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.List;

import net.imglib2.RandomAccessibleInterval;

public interface CopyDataToArray< T >
{
	/**
	 * Copy the information on input to volumeArray
	 * 
	 * @param source
	 *            the data that will be copied
	 * @param target
	 *            the array that will contain the information from source
	 */
	public void copyDataToArray( RandomAccessibleInterval< T > source, List< Long > target );
}
