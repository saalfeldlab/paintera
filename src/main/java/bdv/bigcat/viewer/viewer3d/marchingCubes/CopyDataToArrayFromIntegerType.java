package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class CopyDataToArrayFromIntegerType< I extends IntegerType< I > > implements CopyDataToArray< I >
{
	@Override
	public void copyDataToArray( RandomAccessibleInterval< I > input, List< Long > volumeArray )
	{
		final ExtendedRandomAccessibleInterval< I, RandomAccessibleInterval< I > > extended =
				Views.extendValue( input, Util.getTypeFromInterval( input ).createVariable() );

		final Cursor< I > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		while ( cursor.hasNext() )
		{
			Long label = cursor.get().getIntegerLong();
			volumeArray.add( label );
		}
	}
}
