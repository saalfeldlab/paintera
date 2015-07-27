package bdv.labels.labelset;

import net.imglib2.IterableInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.DvidLabels64MultisetSetupImageLoader.MultisetSource;
import bdv.labels.labelset.Multiset.Entry;
import bdv.util.ColorStream;

public class ARGBConvertedLabelsArrayLoader implements CacheArrayLoader< VolatileIntArray >
{
	private VolatileIntArray theEmptyArray;

	private final MultisetSource multisetSource;

	public ARGBConvertedLabelsArrayLoader( final MultisetSource multisetSource )
	{
		this.multisetSource = multisetSource;
		theEmptyArray = new VolatileIntArray( 1, false );
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}

	@Override
	public VolatileIntArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
//		System.out.println( "ARGBConvertedLabelsArrayLoader.loadArray(\n"
//				+ "   timepoint = " + timepoint + "\n"
//				+ "   setup = " + setup + "\n"
//				+ "   level = " + level + "\n"
//				+ "   dimensions = " + Util.printCoordinates( dimensions ) + "\n"
//				+ "   min = " + Util.printCoordinates( min ) + "\n"
//				+ ")"
//				);
		final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];

		final IterableInterval< SuperVoxelMultisetType > source =
		Views.flatIterable(
				Views.interval(
						multisetSource.getSource( timepoint, level ),
						Intervals.createMinSize(
								min[ 0 ], min[ 1 ], min[ 2 ],
								dimensions[ 0 ], dimensions[ 1 ], dimensions[ 0 ] )
						)
				);
		int i = 0;
		for ( final SuperVoxelMultisetType t : source )
		{
			double r = 0;
			double g = 0;
			double b = 0;
			int size = 0;
			for ( final Entry< SuperVoxel > entry : t.entrySet() )
			{
				final long superVoxelId = entry.getElement().id();
				final int count = entry.getCount();
				final int argb = ColorStream.get( superVoxelId );
				r += count * ARGBType.red( argb );
				g += count * ARGBType.green( argb );
				b += count * ARGBType.blue( argb );
				size += count;
			}
			r = Math.min( 255, r / size );
			g = Math.min( 255, g / size );
			b = Math.min( 255, b / size );
			data[ i++ ] = ARGBType.rgba( r, g, b, 255 );
		}

//		System.out.println( "done: ARGBConvertedLabelsArrayLoader.loadArray(\n"
//				+ "      timepoint = " + timepoint + "\n"
//				+ "      setup = " + setup + "\n"
//				+ "      level = " + level + "\n"
//				+ "      dimensions = " + Util.printCoordinates( dimensions ) + "\n"
//				+ "      min = " + Util.printCoordinates( min ) + "\n"
//				+ "   )"
//				);
		return new VolatileIntArray( data, true );
	}

	@Override
	public VolatileIntArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileIntArray( numEntities, false );
		return theEmptyArray;
	}
}
