package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.ArrayList;
import java.util.List;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class NextVertexValuesFromLabelMultisetType implements NextVertexValues< LabelMultisetType >
{

	ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended;

	@Override
	public Cursor< LabelMultisetType > createCursor( RandomAccessibleInterval< LabelMultisetType > input )
	{
		extended = Views.extendValue( ( RandomAccessibleInterval< LabelMultisetType > ) input, new LabelMultisetType() );

		Cursor< LabelMultisetType > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		return cursor;
	}

	@Override
	public void getVerticesValues( int cursorX, int cursorY, int cursorZ, int[] cubeSize, double[] vertices )
	{
		// get the 8 vertices of the cube taking into account the cube size
		List< Cursor< LabelMultisetType > > verticesCursor = new ArrayList< Cursor< LabelMultisetType > >();
		verticesCursor.add( getVertex( cursorX, cursorY, cursorZ ) );
		verticesCursor.add( getVertex( cursorX + cubeSize[ 0 ], cursorY, cursorZ ) );
		verticesCursor.add( getVertex( cursorX, cursorY + cubeSize[ 1 ], cursorZ ) );
		verticesCursor.add( getVertex( cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ ) );
		verticesCursor.add( getVertex( cursorX, cursorY, cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertex( cursorX + cubeSize[ 0 ], cursorY, cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertex( cursorX, cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertex( cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );

		for ( int i = 0; i < verticesCursor.size(); i++ )
		{
			Cursor< LabelMultisetType > vertex = verticesCursor.get( i );
			while ( vertex.hasNext() )
			{
				LabelMultisetType it = vertex.next();

				for ( final Multiset.Entry< Label > e : it.entrySet() )
				{
					vertices[ i ] = e.getElement().id();
				}
			}
		}
	}

	/**
	 * Get a cursor of a cube vertex from RAI
	 *
	 * @param cursorX
	 *            position on x
	 * @param cursorY
	 *            position on y
	 * @param cursorZ
	 *            position on z
	 * @return Cursor<LabelMultisetType> with the one position
	 */
	private Cursor< LabelMultisetType > getVertex( final int cursorX, final int cursorY, final int cursorZ )
	{
		final long[] begin = new long[] { cursorX, cursorY, cursorZ };
		final long[] end = new long[] { cursorX, cursorY, cursorZ };

		return Views.flatIterable( Views.interval( extended, new FinalInterval( begin, end ) ) ).cursor();
	}

}
