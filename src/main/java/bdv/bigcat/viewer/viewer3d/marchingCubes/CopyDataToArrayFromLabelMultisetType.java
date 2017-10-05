package bdv.bigcat.viewer.viewer3d.marchingCubes;

import java.util.List;

import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class CopyDataToArrayFromLabelMultisetType implements CopyDataToArray< LabelMultisetType >
{
	@Override
	public void copyDataToArray( final RandomAccessibleInterval< LabelMultisetType > input, final List< Long > volumeArray )
	{
		final ExtendedRandomAccessibleInterval< LabelMultisetType, RandomAccessibleInterval< LabelMultisetType > > extended =
				Views.extendValue( input, new LabelMultisetType() );

		final Cursor< LabelMultisetType > cursor = Views.flatIterable( Views.interval( extended,
				new FinalInterval( new long[] { input.min( 0 ) - 1, input.min( 1 ) - 1, input.min( 2 ) - 1 },
						new long[] { input.max( 0 ) + 1, input.max( 1 ) + 1, input.max( 2 ) + 1 } ) ) )
				.localizingCursor();

		while ( cursor.hasNext() )
		{
			final LabelMultisetType iterator = cursor.next();
			int count = Integer.MIN_VALUE;

			for ( final Multiset.Entry< Label > e : iterator.entrySet() )
				if ( e.getCount() > count )
				{
					count = e.getCount();
					volumeArray.add( e.getElement().id() );
				}
		}
	}
}
