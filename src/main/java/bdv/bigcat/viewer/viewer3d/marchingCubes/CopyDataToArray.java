package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.List;

import net.imglib2.RandomAccessibleInterval;

public interface CopyDataToArray< T >
{
	public void copyDataToArray( RandomAccessibleInterval< T > input, List< Long > volumeArray );
}
