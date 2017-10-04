package bdv.bigcat.viewer.viewer3d.marchingCubes;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;

public interface NextVertexValues< T >
{
	public Cursor< T > createCursor( RandomAccessibleInterval< T > input );

	public void getVerticesValues( int cursorX, int cursorY, int cursorZ, int[] cubeSize, double[] vertices );

}
