package bdv.bigcat.viewer.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.util.HashWrapper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.cache.CacheLoader;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;

public class BlocksForLabelCacheLoader implements CacheLoader< Long, Interval[] >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final CellGrid grid;

	private final Function< Long, Interval[] > getRelevantIntervalsFromLowerResolution;

	private final Function< Interval, List< Interval > > getRelevantBlocksIntersectingWithLowResInterval;

	private final Function< long[], long[] > getUniqueLabelListForBlock;

	private final ExecutorService es;

	/**
	 *
	 * @param grid
	 * @param getRelevantIntervalsFromLowerResolution
	 *            Get all blocks in lower resolution that contain requested
	 *            labels. Blocks are defined by min and max in the lower
	 *            resolution coordinate system.
	 * @param getRelevantBlocksIntersectingWithLowResInterval
	 *            for a block defined by min and max in lower resolution
	 *            coordinate system, find all blocks that intersect with it at
	 *            this resolution.
	 * @param getUniqueLabelListForBlock
	 *            Given a block for this resolution defined by its position in
	 *            the cell grid, retrieve a unique list of labels present in
	 *            this block.
	 * @param es
	 *            {@link ExecutorService} for parallel execution of retrieval of
	 *            lists of unique labels. The task is parallelized over blocks.
	 */
	public BlocksForLabelCacheLoader(
			final CellGrid grid,
			final Function< Long, Interval[] > getRelevantIntervalsFromLowerResolution,
			final Function< Interval, List< Interval > > getRelevantBlocksIntersectingWithLowResInterval,
			final Function< long[], long[] > getUniqueLabelListForBlock,
			final ExecutorService es )
	{
		super();
		this.grid = grid;
		this.getRelevantIntervalsFromLowerResolution = getRelevantIntervalsFromLowerResolution;
		this.getRelevantBlocksIntersectingWithLowResInterval = getRelevantBlocksIntersectingWithLowResInterval;
		this.getUniqueLabelListForBlock = getUniqueLabelListForBlock;
		this.es = es;
	}

	@Override
	public Interval[] get( final Long key ) throws Exception
	{
		final Interval[] relevantLowResBlocks = getRelevantIntervalsFromLowerResolution.apply( key );
		final HashSet< HashWrapper< Interval > > blocks = new HashSet<>();
		Arrays
				.stream( relevantLowResBlocks )
				.map( getRelevantBlocksIntersectingWithLowResInterval::apply )
				.flatMap( List::stream )
				.map( HashWrapper::interval )
				.forEach( blocks::add );
		LOG.debug( "{} -- got {} block candidates: {}", grid, blocks.size(), toString( blocks ) );

		final List< Future< Interval > > futures = new ArrayList<>();
		blocks.forEach( block -> {
			final Future< Interval > future = es.submit( () -> {
				final long[] cellPos = new long[ grid.numDimensions() ];
				grid.getCellPosition( Intervals.minAsLongArray( block.getData() ), cellPos );
				final long[] uniqueLabels = getUniqueLabelListForBlock.apply( cellPos );
				final long unboxedKey = key;
				return Arrays.stream( uniqueLabels ).filter( l -> l == unboxedKey ).count() > 0 ? block.getData() : null;
			} );
			futures.add( future );
		} );

		final List< Interval > results = new ArrayList<>();
		for ( int i = 0; i < futures.size(); ++i )
		{
			final Interval result = futures.get( i ).get();
			if ( result != null )
			{
				LOG.trace( "Adding interval: " + Point.wrap( Intervals.minAsLongArray( result ) ) + " " + Point.wrap( Intervals.maxAsLongArray( result ) ) );
				results.add( result );
			}
		}

		LOG.debug( "Found a total of {} blocks", results.size() );

		return results.toArray( new Interval[ results.size() ] );
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

		final CellGrid grid1 = new CellGrid( Intervals.dimensionsAsLongArray( img1 ), IntStream.generate( () -> 2 ).limit( res1.length ).toArray() );
		final CellGrid grid2 = new CellGrid( Intervals.dimensionsAsLongArray( img2 ), IntStream.generate( () -> 2 ).limit( res2.length ).toArray() );

		final UniqueLabelListCacheLoader< LongType > uniqueLabelsLoader1 = new UniqueLabelListCacheLoader<>( img1, grid1, ( l, hs ) -> hs.add( l.get() ) );
		final UniqueLabelListCacheLoader< LongType > uniqueLabelsLoader2 = new UniqueLabelListCacheLoader<>( img2, grid2, ( l, hs ) -> hs.add( l.get() ) );

		final Function< long[], long[] > uncheckedGet1 = pos -> {
			try
			{
				return uniqueLabelsLoader1.get( HashWrapper.longArray( pos ) );
			}
			catch ( final Exception e )
			{
				throw new RuntimeException( e );
			}
		};
		final Function< long[], long[] > uncheckedGet2 = pos -> {
			try
			{
				return uniqueLabelsLoader2.get( HashWrapper.longArray( pos ) );
			}
			catch ( final Exception e )
			{
				throw new RuntimeException( e );
			}
		};

		final ExecutorService es = Executors.newFixedThreadPool( 3 );

		final BlocksForLabelCacheLoader blocksForLabelLoader2 = new BlocksForLabelCacheLoader(
				grid2,
				val -> new Interval[] { new FinalInterval( 3 ) },
				i -> IntStream.range( 0, res2.length / 2 ).mapToObj( pos -> Intervals.translate( new FinalInterval( 2 ), pos * 2, 0 ) ).collect( Collectors.toList() ),
				uncheckedGet2,
				es );

		System.out.println( toString( blocksForLabelLoader2.get( 0l ) ) );
		System.out.println( toString( blocksForLabelLoader2.get( 1l ) ) );
		System.out.println( toString( blocksForLabelLoader2.get( 2l ) ) );
		System.out.println( toString( blocksForLabelLoader2.get( 3l ) ) );
		System.out.println( toString( blocksForLabelLoader2.get( 4l ) ) );
		System.out.println( toString( blocksForLabelLoader2.get( 5l ) ) );

		final Function< Long, Interval[] > uncheckedGetIntervals2 = id -> {
			try
			{
				return blocksForLabelLoader2.get( id );
			}
			catch ( final Exception e )
			{
				throw new RuntimeException( e );
			}
		};

		final BlocksForLabelCacheLoader blocksForLabelLoader1 = new BlocksForLabelCacheLoader(
				grid1,
				val -> uncheckedGetIntervals2.apply( val ),
				i -> doubleStep( i ),
				uncheckedGet1,
				es );

		System.out.println();

		System.out.println( toString( blocksForLabelLoader1.get( 0l ) ) );
		System.out.println( toString( blocksForLabelLoader1.get( 1l ) ) );
		System.out.println( toString( blocksForLabelLoader1.get( 2l ) ) );
		System.out.println( toString( blocksForLabelLoader1.get( 3l ) ) );
		System.out.println( toString( blocksForLabelLoader1.get( 4l ) ) );
		System.out.println( toString( blocksForLabelLoader1.get( 5l ) ) );
		es.shutdown();

	}

	private static List< String > toString( final Interval[] intervals )
	{
		final List< String > strings = Arrays
				.stream( intervals )
				.map( ival -> String.format( "(%s %s)", Point.wrap( Intervals.minAsLongArray( ival ) ), Point.wrap( Intervals.maxAsLongArray( ival ) ) ) )
				.collect( Collectors.toList() );
		return strings;

	}

	private static List< Interval > doubleStep( final Interval interval )
	{
		final long[] min = Intervals.minAsLongArray( interval );
		return Arrays.asList(
				new FinalInterval( Arrays.stream( min ).map( m -> m * 2 + 0 ).toArray(), Arrays.stream( min ).map( m -> m * 2 + 1 ).toArray() ),
				new FinalInterval( Arrays.stream( min ).map( m -> m * 2 + 2 ).toArray(), Arrays.stream( min ).map( m -> m * 2 + 3 ).toArray() ) );
	}

	public static String toString( final Collection< HashWrapper< Interval > > list )
	{
		return list
				.stream()
				.map( HashWrapper::getData )
				.map( i -> "(" + Point.wrap( Intervals.minAsLongArray( i ) ) + " " + Point.wrap( Intervals.maxAsLongArray( i ) ) + ")" )
				.collect( Collectors.toList() ).toString();
	}

}
