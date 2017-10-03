package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class NextVertexValuesFromIntegerType< I extends IntegerType< I > > implements NextVertexValues< I >
{
	ExtendedRandomAccessibleInterval< I, RandomAccessibleInterval< I > > extended;

	@Override
	public Cursor< I > createCursor( RandomAccessibleInterval< I > input )
	{
		extended = Views.extendValue( ( RandomAccessibleInterval< I > ) input, Util.getTypeFromInterval( input ).createVariable() );

		Cursor< I > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		return cursor;
	}

	@Override
	public void verticesValues( int cursorX, int cursorY, int cursorZ, int[] cubeSize, double[] vertices )
	{
		// get the 8 vertices of the cube taking into account the cube size
		List< Cursor< I > > verticesCursor = new ArrayList< Cursor< I > >();
		verticesCursor.add( getVertexLabelMultisetType( cursorX, cursorY, cursorZ ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX + cubeSize[ 0 ], cursorY, cursorZ ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX, cursorY + cubeSize[ 1 ], cursorZ ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX, cursorY, cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX + cubeSize[ 0 ], cursorY, cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX, cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );
		verticesCursor.add( getVertexLabelMultisetType( cursorX + cubeSize[ 0 ], cursorY + cubeSize[ 1 ], cursorZ + cubeSize[ 2 ] ) );

		for ( int i = 0; i < verticesCursor.size(); i++ )
		{
			Cursor< I > vertex = verticesCursor.get( i );
			while ( vertex.hasNext() )
			{
				vertices[ i ] = vertex.get().getIntegerLong();
			}
		}
	}

	private Cursor< I > getVertexLabelMultisetType( final int cursorX, final int cursorY, final int cursorZ )
	{
		final long[] begin = new long[] { cursorX, cursorY, cursorZ };
		final long[] end = new long[] { cursorX, cursorY, cursorZ };

		return Views.flatIterable( Views.interval( extended, new FinalInterval( begin, end ) ) ).cursor();
	}

}
