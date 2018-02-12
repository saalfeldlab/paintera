package bdv.bigcat.viewer.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.util.HashWrapper;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class UniqueLabelListCacheLoader< T > implements CacheLoader< HashWrapper< long[] >, long[] >
{

	private final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final RandomAccessibleInterval< T > data;

	private final CellGrid grid;

	private final BiConsumer< T, TLongHashSet > collectLabels;

	public UniqueLabelListCacheLoader( final RandomAccessibleInterval< T > data, final CellGrid grid, final BiConsumer< T, TLongHashSet > collectLabels )
	{
		super();
		this.data = data;
		this.grid = grid;
		this.collectLabels = collectLabels;
	}

	/**
	 * @param key
	 *            Position of block in cell grid coordinates (instead of min of
	 *            represented block).
	 */
	@Override
	public long[] get( final HashWrapper< long[] > key ) throws Exception
	{
		final long[] cellGridPosition = key.getData();

		if ( !Intervals.contains( new FinalInterval( grid.getGridDimensions() ), Point.wrap( cellGridPosition ) ) )
		{
			LOG.error( "Trying to retrieve unique labels at invalid position {} for cell grid dimensions: {}", Point.wrap( cellGridPosition ), Point.wrap( grid.getGridDimensions() ) );
			throw new IllegalArgumentException( String.format( "%s does not contain position: %s", grid.toString(), Point.wrap( cellGridPosition ).toString() ) );
		}

		final long[] min = new long[ grid.numDimensions() ];
		final long[] max = new long[ grid.numDimensions() ];
		for ( int d = 0; d < min.length; ++d )
		{
			final int s = grid.cellDimension( d );
			final long m = cellGridPosition[ d ] * s;
			min[ d ] = m;
			max[ d ] = Math.min( m + s, grid.imgDimension( d ) ) - 1;
		}
		final TLongHashSet containedLabels = new TLongHashSet();
		for ( final T t : Views.interval( data, new FinalInterval( min, max ) ) )
			collectLabels.accept( t, containedLabels );

		return containedLabels.toArray();
	}

	public static void main( final String[] args ) throws Exception
	{

		final long[] res1 = new long[] {
				1, 1, 1, 2, 3, 3, 4, 4
		};

		final long[] res2 = new long[] {
				1, 2, 3, 4
		};

		final ArrayImg< LongType, LongArray > img1 = ArrayImgs.longs( res1, res1.length );
		final ArrayImg< LongType, LongArray > img2 = ArrayImgs.longs( res2, res2.length );

		final CellGrid grid1 = new CellGrid( Intervals.dimensionsAsLongArray( img1 ), IntStream.generate( () -> 2 ).limit( img1.numDimensions() ).toArray() );
		final CellGrid grid2 = new CellGrid( Intervals.dimensionsAsLongArray( img2 ), IntStream.generate( () -> 2 ).limit( img2.numDimensions() ).toArray() );

		final UniqueLabelListCacheLoader< LongType > uniqueLabelsLoader1 = new UniqueLabelListCacheLoader<>( img1, grid1, ( l, hs ) -> hs.add( l.get() ) );
		final UniqueLabelListCacheLoader< LongType > uniqueLabelsLoader2 = new UniqueLabelListCacheLoader<>( img2, grid2, ( l, hs ) -> hs.add( l.get() ) );

		System.out.println( Arrays.toString( uniqueLabelsLoader1.get( HashWrapper.longArray( 0 ) ) ) );
		System.out.println( Arrays.toString( uniqueLabelsLoader1.get( HashWrapper.longArray( 1 ) ) ) );
		System.out.println( Arrays.toString( uniqueLabelsLoader1.get( HashWrapper.longArray( 2 ) ) ) );
		System.out.println( Arrays.toString( uniqueLabelsLoader1.get( HashWrapper.longArray( 3 ) ) ) );
		System.out.println( Arrays.toString( uniqueLabelsLoader2.get( HashWrapper.longArray( 0 ) ) ) );
		System.out.println( Arrays.toString( uniqueLabelsLoader2.get( HashWrapper.longArray( 1 ) ) ) );
		// will throw IllegalArgumentException:
//		System.out.println( Arrays.toString( uniqueLabelsLoader2.get( HashWrapper.longArray( 2 ) ) ) );

	}

}
