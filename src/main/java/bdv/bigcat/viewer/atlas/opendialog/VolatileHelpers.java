package bdv.bigcat.viewer.atlas.opendialog;

import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileRandomAccessibleIntervalView;
import bdv.util.volatiles.VolatileViewData;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.AccessFlags;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetEntryList;
import net.imglib2.type.label.LongMappedAccessData;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.util.Intervals;

public class VolatileHelpers
{

	public static < T extends NativeType< T >, A > VolatileCachedCellImg< T, A > createVolatileCachedCellImg(
			final CellGrid grid,
			final T type,
			final AccessFlags[] accessFlags,
			final Cache< Long, Cell< A > > cache,
			final CreateInvalid< Long, Cell< A > > createInvalid,
			final SharedQueue queue,
			final CacheHints hints )
	{
		final VolatileCache< Long, Cell< A > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );
		final VolatileCachedCellImg< T, A > volatileImg = new VolatileCachedCellImg<>( grid, type, hints, volatileCache.unchecked()::get );
		return volatileImg;
	}

	public static < T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A > VolatileRandomAccessibleIntervalView< T, V > wrapCachedCellImg(
			final CachedCellImg< T, A > cachedCellImg,
			final CreateInvalid< Long, Cell< A > > createInvalid,
			final SharedQueue queue,
			final CacheHints hints,
			final V vtype )
	{
		final T type = cachedCellImg.createLinkedType();
		final CellGrid grid = cachedCellImg.getCellGrid();
		final Cache< Long, Cell< A > > cache = cachedCellImg.getCache();

		final AccessFlags[] flags = AccessFlags.of( cachedCellImg.getAccessType() );
		if ( !AccessFlags.isVolatile( flags ) )
			throw new IllegalArgumentException( "underlying " + CachedCellImg.class.getSimpleName() + " must have volatile access type" );
		@SuppressWarnings( "rawtypes" )
		final VolatileCachedCellImg< V, ? > img = createVolatileCachedCellImg( grid, vtype, flags, ( Cache ) cache, createInvalid, queue, hints );

		final VolatileViewData< T, V > vvd = new VolatileViewData<>( img, queue, type, vtype );
		return new VolatileRandomAccessibleIntervalView<>( vvd );
	}

	public static class CreateInvalidVolatileLabelMultisetArray implements CreateInvalid< Long, Cell< VolatileLabelMultisetArray > >
	{

		private final CellGrid grid;

		public CreateInvalidVolatileLabelMultisetArray( final CellGrid grid )
		{
			super();
			this.grid = grid;
		}

		@Override
		public Cell< VolatileLabelMultisetArray > createInvalid( final Long key ) throws Exception
		{
			final long[] cellPosition = new long[ grid.numDimensions() ];
			grid.getCellGridPositionFlat( key, cellPosition );
			final long[] cellMin = new long[ cellPosition.length ];
			final int[] cellDims = new int[ cellPosition.length ];
			grid.getCellDimensions( cellPosition, cellMin, cellDims );

			final LabelMultisetEntry e = new LabelMultisetEntry( Label.INVALID, 1 );
			final int numEntities = ( int ) Intervals.numElements( cellDims );

			final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );
			final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
			list.createListAt( listData, 0 );
			list.add( e );
			final int[] data = new int[ numEntities ];
			final VolatileLabelMultisetArray array = new VolatileLabelMultisetArray( data, listData, false );
			return new Cell<>( cellDims, cellMin, array );
		}

	}

}
