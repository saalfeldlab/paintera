package bdv.bigcat.viewer.viewer3d.marchingCubes;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;

public interface NextVertexValues< T >
{
	/**
	 * creates a cursor to walk through the data
	 *
	 * @param input
	 *            the data that will be manipulated
	 * @return Cursor<T>
	 */
	public Cursor< T > createCursor( RandomAccessibleInterval< T > input );

	/**
	 * Return the eight vertices of the cube in a given position
	 *
	 * @param cursorX
	 *            position on x
	 * @param cursorY
	 *            position on y
	 * @param cursorZ
	 *            position on z
	 * @param cubeSize
	 *            size of the cube (in voxels)
	 * @param vertices
	 *            array with the values of the eight vertices
	 */
	public void getVerticesValues( int cursorX, int cursorY, int cursorZ, int[] cubeSize, double[] vertices );

}
