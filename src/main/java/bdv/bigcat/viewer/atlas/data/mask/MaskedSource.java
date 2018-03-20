package bdv.bigcat.viewer.atlas.data.mask;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.label.Label;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.mask.PickOne.PickAndConvert;
import bdv.net.imglib2.view.RandomAccessibleTriple;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DelegateAccessIo;
import net.imglib2.cache.img.DelegateAccessWrappers;
import net.imglib2.cache.img.DelegateAccesses;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.basictypeaccess.constant.ConstantLongAccess;
import net.imglib2.img.basictypeaccess.delegate.dirty.DirtyVolatileDelegateLongAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg.LazyCells;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class MaskedSource< D extends Type< D >, T extends Type< T > > implements DataSource< D, T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int NUM_DIMENSIONS = 3;

	private final DataSource< D, T > source;

	private final CachedCellImg< UnsignedLongType, DirtyVolatileDelegateLongAccess >[] dataCanvases;

	private final RandomAccessibleInterval< VolatileUnsignedLongType >[] canvases;

	private final HashMap< RandomAccessibleInterval< UnsignedByteType >, MaskInfo< UnsignedLongType > > masks;

	private final RandomAccessible< UnsignedLongType >[] dMasks;

	private final RandomAccessible< VolatileUnsignedLongType >[] tMasks;

	private final ImgFactory< UnsignedByteType > maskFactory;

	private final PickAndConvert< D, UnsignedLongType, UnsignedLongType, D > pacD;

	private final PickAndConvert< T, VolatileUnsignedLongType, VolatileUnsignedLongType, T > pacT;

	private final D extensionD;

	private final T extensionT;

	private final ExecutorService propagationExecutor = Executors.newFixedThreadPool( 1 );

	// TODO make sure that BB is handled properly in multi scale case!!!
	private final TLongSet affectedBlocks = new TLongHashSet();

	private final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasToBackground;

	@SuppressWarnings( "unchecked" )
	public MaskedSource(
			final DataSource< D, T > source,
			final DiskCachedCellImgOptions opts,
			final IntFunction< String > mipmapCanvasCacheDirs,
			final PickAndConvert< D, UnsignedLongType, UnsignedLongType, D > pacD,
			final PickAndConvert< T, VolatileUnsignedLongType, VolatileUnsignedLongType, T > pacT,
			final D extensionD,
			final T extensionT,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasToBackground )
	{
		super();
		this.source = source;
		this.dataCanvases = new CachedCellImg[ source.getNumMipmapLevels() ];
		this.canvases = new RandomAccessibleInterval[ source.getNumMipmapLevels() ];
		this.dMasks = new RandomAccessible[ this.canvases.length ];
		this.tMasks = new RandomAccessible[ this.canvases.length ];

		for ( int i = 0; i < canvases.length; ++i )
		{
			final DiskCachedCellImgOptions o = opts
					.volatileAccesses( true )
					.dirtyAccesses( true )
					.cacheDirectory( Paths.get( mipmapCanvasCacheDirs.apply( i ) ) );
			final DiskCachedCellImgFactory< UnsignedLongType > f = new DiskCachedCellImgFactory<>(
					o,
					( pt, af ) -> DelegateAccessIo.get( pt, af ),
					( pt, af ) -> n -> DelegateAccesses.getSingleValue( pt, af ),
					DelegateAccessWrappers::getWrapper );
			final CellLoader< UnsignedLongType > loader = img -> img.forEach( t -> t.set( Label.INVALID ) );
			final CachedCellImg< UnsignedLongType, DirtyVolatileDelegateLongAccess > store =
					( CachedCellImg< UnsignedLongType, DirtyVolatileDelegateLongAccess > ) f.create( Intervals.dimensionsAsLongArray( source.getDataSource( 0, i ) ), new UnsignedLongType(), loader, o );
			final RandomAccessibleInterval< VolatileUnsignedLongType > vstore = VolatileViews.wrapAsVolatile( store );
			this.dataCanvases[ i ] = store;
			this.canvases[ i ] = vstore;
			this.dMasks[ i ] = ConstantUtils.constantRandomAccessible( new UnsignedLongType( Label.INVALID ), NUM_DIMENSIONS );
			final VolatileUnsignedLongType vult = new VolatileUnsignedLongType();
			vult.get().set( Label.INVALID );
			this.tMasks[ i ] = ConstantUtils.constantRandomAccessible( vult, NUM_DIMENSIONS );
		}

		final DiskCachedCellImgOptions maskOpts = opts.cacheDirectory( null ).deleteCacheDirectoryOnExit( true );

		this.masks = new HashMap<>();
		this.maskFactory = new DiskCachedCellImgFactory<>( maskOpts );
		this.pacD = pacD;
		this.pacT = pacT;
		this.extensionT = extensionT;
		this.extensionD = extensionD;
		this.mergeCanvasToBackground = mergeCanvasToBackground;
	}

	public RandomAccessibleInterval< UnsignedByteType > generateMask( final int t, final int level, final UnsignedLongType value ) throws MaskInUse
	{
		return generateMask( new MaskInfo<>( t, level, value ) );
	}

	public RandomAccessibleInterval< UnsignedByteType > generateMask( final MaskInfo< UnsignedLongType > mask ) throws MaskInUse
	{
		if ( this.masks.size() > 0 )
			throw new MaskInUse( "Cannot generate new mask: current mask has not been submitted yet." );
		final RandomAccessibleInterval< UnsignedByteType > store = maskFactory.create( source.getSource( 0, mask.level ), new UnsignedByteType() );
		final RandomAccessibleInterval< VolatileUnsignedByteType > vstore = VolatileViews.wrapAsVolatile( store );
		final UnsignedLongType INVALID = new UnsignedLongType( Label.INVALID );
		this.dMasks[ mask.level ] = Converters.convert( Views.extendZero( store ), ( input, output ) -> output.set( input.get() == 1 ? mask.value : INVALID ), new UnsignedLongType() );
		this.tMasks[ mask.level ] = Converters.convert( Views.extendZero( vstore ), ( input, output ) -> {
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
				output.get().set( input.get().get() == 1 ? mask.value : INVALID );
		}, new VolatileUnsignedLongType() );
		this.masks.put( store, mask );
		return store;
	}

	public void applyMask( final RandomAccessibleInterval< UnsignedByteType > mask, final Interval... paintedIntervals )
	{
		synchronized ( this.masks )
		{
			LOG.debug( "Applying mask: {}", mask, paintedIntervals );
			final MaskInfo< UnsignedLongType > maskInfo = this.masks.get( mask );
			if ( maskInfo == null )
			{
				LOG.warn( "Did not pass valid mask {}", mask );
				return;
			}
			final CachedCellImg< UnsignedLongType, ? > canvas = dataCanvases[ maskInfo.level ];
			final CellGrid grid = canvas.getCellGrid();
			final TLongSet affectedBlocks = MaskedSource.affectedBlocks( grid, paintedIntervals );

			final long[] gridDimensions = grid.getGridDimensions();
			final int[] blockSize = new int[ grid.numDimensions() ];
			grid.cellDimensions( blockSize );
			final TLongSet completelyPaintedBlocks = new TLongHashSet();
			final long[] gridPosition = new long[ grid.numDimensions() ];
			final RandomAccess< Cell< DirtyVolatileDelegateLongAccess > > cellsAccess = dataCanvases[ maskInfo.level ].getCells().randomAccess();
			final long[] blockMin = new long[ grid.numDimensions() ];
			final long[] blockMax = new long[ grid.numDimensions() ];
			for ( final TLongIterator blockIterator = affectedBlocks.iterator(); blockIterator.hasNext(); )
			{
				final long blockId = blockIterator.next();
				grid.getCellGridPositionFlat( blockId, gridPosition );
				cellsAccess.setPosition( gridPosition );
				final Cell< DirtyVolatileDelegateLongAccess > cell = cellsAccess.get();
				final DirtyVolatileDelegateLongAccess access = cell.getData();
				final LongAccess previousAccess = access.getDelegate();
				final LongArray updatedAccess = new LongArray( ( int ) cell.size() );
				cell.min( blockMin );
				Arrays.setAll( blockMax, d -> blockMin[ d ] + cell.dimension( d ) - 1 );
				LOG.warn( "Painting: level={}, min={}, max={}", maskInfo.level, blockMin, blockMax );
				final Cursor< UnsignedByteType > cursor = Views.flatIterable( Views.interval( mask, blockMin, blockMax ) ).cursor();
				boolean paintedAllPixelsInBlock = true;
				for ( int i = 0; cursor.hasNext(); ++i )
				{
					final boolean wasPainted = cursor.next().get() > 0;
					paintedAllPixelsInBlock &= wasPainted;
					updatedAccess.setValue( i, wasPainted ? maskInfo.value.get() : previousAccess.getValue( i ) );
				}
				access.setDelegate( updatedAccess );
				if ( paintedAllPixelsInBlock )
					completelyPaintedBlocks.add( blockId );
			}

//			LOG.debug( "For mask info {} and interval {}, painting into canvas {}", maskInfo, paintedIntervalAtPaintedScale, dataCanvases );
//			final Cursor< UnsignedLongType > canvasCursor = Views.flatIterable( Views.interval( canvas, paintedIntervalAtPaintedScale ) ).cursor();
//			final Cursor< UnsignedByteType > maskCursor = Views.flatIterable( Views.interval( mask, paintedIntervalAtPaintedScale ) ).cursor();
//			while ( canvasCursor.hasNext() )
//			{
//				canvasCursor.fwd();
//				maskCursor.fwd();
//				if ( maskCursor.get().get() == 1 )
//					canvasCursor.get().set( maskInfo.value );
//			}

			this.dMasks[ maskInfo.level ] = ConstantUtils.constantRandomAccessible( new UnsignedLongType( Label.INVALID ), NUM_DIMENSIONS );
			this.dMasks[ maskInfo.level ] = ConstantUtils.constantRandomAccessible( new UnsignedLongType( Label.INVALID ), NUM_DIMENSIONS );

			forgetMasks();

			final TLongSet paintedBlocksAtHighestResolution = this.scaleBlocksToLevel( affectedBlocks, maskInfo.level, 0 );

			propagationExecutor.submit( () -> propagateMask( mask, affectedBlocks, maskInfo.level, maskInfo.value ) );

			// TODO make correct painted bounding box for multi scale
			// TODO update canvases in other scale levels
			this.affectedBlocks.addAll( paintedBlocksAtHighestResolution );
		}

	}

	private Interval scaleIntervalToLevel( final Interval interval, final int intervalLevel, final int targetLevel )
	{
		if ( intervalLevel == targetLevel )
			return interval;

		final double[] min = LongStream.of( Intervals.minAsLongArray( interval ) ).asDoubleStream().toArray();
		final double[] max = LongStream.of( Intervals.maxAsLongArray( interval ) ).asDoubleStream().toArray();

		final Scale3D toTargetScale = new Scale3D( DataSource.getRelativeScales( this, 0, intervalLevel, targetLevel ) ).inverse();

		toTargetScale.apply( min, min );
		toTargetScale.apply( max, max );

		return Intervals.smallestContainingInterval( new FinalRealInterval( min, max ) );

	}

	private TLongSet scaleBlocksToLevel( final TLongSet blocks, final int blocksLevel, final int targetLevel )
	{
		if ( blocksLevel == targetLevel )
			return blocks;

		final CellGrid grid = this.dataCanvases[ blocksLevel ].getCellGrid();
		final CellGrid targetGrid = this.dataCanvases[ targetLevel ].getCellGrid();

		final long[] blockPos = new long[ grid.numDimensions() ];
		final int[] blockSize = new int[ grid.numDimensions() ];
		final double[] blockMin = new double[ grid.numDimensions() ];
		final double[] blockMax = new double[ grid.numDimensions() ];
		final long[] blockMinLong = new long[ grid.numDimensions() ];
		final long[] blockMaxLong = new long[ grid.numDimensions() ];
		grid.cellDimensions( blockSize );

		final int[] targetBlockSize = new int[ targetGrid.numDimensions() ];
		targetGrid.cellDimensions( targetBlockSize );
		final int[] ones = IntStream.generate( () -> 1 ).limit( targetGrid.numDimensions() ).toArray();
		final long[] targetGridDimensions = targetGrid.getGridDimensions();

		final Scale3D toTargetScale = new Scale3D( DataSource.getRelativeScales( this, 0, blocksLevel, targetLevel ) ).inverse();

		final TLongSet targetBlocks = new TLongHashSet();
		for ( final TLongIterator blockIt = blocks.iterator(); blockIt.hasNext(); )
		{
			final long blockId = blockIt.next();
			grid.getCellGridPositionFlat( blockId, blockPos );
			Arrays.setAll( blockMin, d -> blockPos[ d ] * blockSize[ d ] );
			Arrays.setAll( blockMax, d -> Math.min( blockMin[ d ] + blockSize[ d ], targetGrid.imgDimension( d ) ) );
			toTargetScale.apply( blockMin, blockMin );
			toTargetScale.apply( blockMax, blockMax );
			Arrays.setAll( blockMinLong, d -> ( long ) blockMin[ d ] / targetBlockSize[ d ] );
			Arrays.setAll( blockMaxLong, d -> ( long ) ( blockMax[ d ] - 1 ) / targetBlockSize[ d ] );
			Grids.forEachOffset( blockMinLong, blockMaxLong, ones, block -> targetBlocks.add( IntervalIndexer.positionToIndex( block, targetGridDimensions ) ) );
		}

		return targetBlocks;
	}

	private void scalePositionToLevel( final long[] position, final int intervalLevel, final int targetLevel, final long[] targetPosition )
	{
		Arrays.setAll( targetPosition, d -> position[ d ] );
		if ( intervalLevel == targetLevel )
			return;

		final double[] positionDouble = LongStream.of( position ).asDoubleStream().toArray();

		final Scale3D toTargetScale = new Scale3D( DataSource.getRelativeScales( this, 0, intervalLevel, targetLevel ) );

		toTargetScale.apply( positionDouble, positionDouble );

		Arrays.setAll( targetPosition, d -> ( long ) Math.ceil( positionDouble[ d ] ) );

	}

	public void forgetMasks()
	{
		synchronized ( this.masks )
		{
			this.masks.clear();
		}
	}

	public void mergeCanvasIntoBackground()
	{
		if ( this.mergeCanvasToBackground != null )
		{
			LOG.debug( "Merging canvas into background for blocks {}", this.affectedBlocks );
			{
				final CachedCellImg< UnsignedLongType, DirtyVolatileDelegateLongAccess > canvas = this.dataCanvases[ 0 ];
				final long[] affectedBlocks = this.affectedBlocks.toArray();
				this.affectedBlocks.clear();
				this.mergeCanvasToBackground.accept( canvas, affectedBlocks );
			}

			for ( int i = 0; i < dataCanvases.length; ++i )
			{
				final RandomAccessibleInterval< UnsignedLongType > canvas = dataCanvases[ i ];
				if ( canvas instanceof DiskCachedCellImg< ?, ? > )
				{
					final DiskCachedCellImg< ?, ? > cachedImg = ( DiskCachedCellImg< ?, ? > ) canvas;
					LOG.debug( "Invalidating all for canvas {}", cachedImg );
					// TODO invalidate and delete everything!
//					cachedImg.getCache().invalidateAll();
				}
			}
		}
		else
			LOG.debug( "No canvas painted -- won't merge into background." );
	}

	@Override
	public boolean isPresent( final int t )
	{
		return source.isPresent( t );
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		final RandomAccessibleInterval< T > source = this.source.getSource( t, level );
		final RandomAccessibleInterval< VolatileUnsignedLongType > canvas = this.canvases[ level ];
		final RandomAccessibleInterval< VolatileUnsignedLongType > mask = Views.interval( this.tMasks[ level ], source );
		final RandomAccessibleTriple< T, VolatileUnsignedLongType, VolatileUnsignedLongType > composed = new RandomAccessibleTriple<>( source, canvas, mask );
		return new PickOne<>( Views.interval( composed, source ), pacT.copy() );
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		final RandomAccessibleInterval< T > source = getSource( t, level );
		return Views.interpolate( Views.extendValue( source, this.extensionT.copy() ), new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		source.getSourceTransform( t, level, transform );
	}

	@Override
	public T getType()
	{
		return source.getType();
	}

	@Override
	public String getName()
	{
		return source.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval< D > getDataSource( final int t, final int level )
	{
		final RandomAccessibleInterval< D > source = this.source.getDataSource( t, level );
		final RandomAccessibleInterval< UnsignedLongType > canvas = this.dataCanvases[ level ];
		final RandomAccessibleInterval< UnsignedLongType > mask = Views.interval( this.dMasks[ level ], source );
		final RandomAccessibleTriple< D, UnsignedLongType, UnsignedLongType > composed = new RandomAccessibleTriple<>( source, canvas, mask );
		return new PickOne<>( Views.interval( composed, source ), pacD.copy() );
	}

	@Override
	public RealRandomAccessible< D > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		final RandomAccessibleInterval< D > source = getDataSource( t, level );
		return Views.interpolate( Views.extendValue( source, this.extensionD.copy() ), new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public D getDataType()
	{
		return source.getDataType();
	}

	public RandomAccessibleInterval< UnsignedLongType > getReadOnlyDataCanvas( final int t, final int level )
	{
		return Converters.convert( ( RandomAccessibleInterval< UnsignedLongType > ) this.dataCanvases[ level ], new TypeIdentity<>(), new UnsignedLongType() );
	}

	public RandomAccessibleInterval< D > getReadOnlyDataBackground( final int t, final int level )
	{
		return Converters.convert( this.source.getDataSource( t, level ), new TypeIdentity<>(), this.source.getDataType().createVariable() );
	}

	public static < A extends DirtyVolatileDelegateLongAccess > void downsampleBlocks(
			final RandomAccessible< UnsignedLongType > source,
			final CachedCellImg< UnsignedLongType, A > img,
			final TLongSet affectedBlocks,
			final int[] steps )
	{
		final LazyCells< Cell< A > > cells = img.getCells();
		final CellGrid grid = img.getCellGrid();
		final int[] blockSize = new int[ grid.numDimensions() ];
		grid.cellDimensions( blockSize );

		final RandomAccess< Cell< A > > cellsAccess = cells.randomAccess();
		final long[] cellPosition = new long[ grid.numDimensions() ];
		final long[] cellMin = new long[ grid.numDimensions() ];
		final long[] cellMax = new long[ grid.numDimensions() ];
		final long[] cellDim = new long[ grid.numDimensions() ];

		final TLongLongHashMap counts = new TLongLongHashMap();
		LOG.debug( "Initializing affected blocks: {}", affectedBlocks );
		for ( final TLongIterator it = affectedBlocks.iterator(); it.hasNext(); )
		{
			counts.clear();
			final long blockId = it.next();
			grid.getCellGridPositionFlat( blockId, cellPosition );
			cellsAccess.setPosition( cellPosition );
			final Cell< A > cell = cellsAccess.get();
			cell.min( cellMin );
			Arrays.setAll( cellMax, d -> cellMin[ d ] + cell.dimension( d ) - 1 );
			Arrays.setAll( cellDim, cell::dimension );
			final A access = cell.getData();
			final int size = ( int ) cell.size();
			final LongAccess currentAccess = access.getDelegate();
			final LongAccess updatedAccess = currentAccess instanceof ConstantLongAccess ? new LongArray( size ) : currentAccess;
			final IntervalView< UnsignedLongType > currentView = Views.translate( ArrayImgs.unsignedLongs( currentAccess, cellDim ), cellMin );
			final IntervalView< UnsignedLongType > updatedView = Views.translate( ArrayImgs.unsignedLongs( updatedAccess, cellDim ), cellMin );
			downsample( source, updatedView, steps );
			access.setDelegate( updatedAccess );
		}
	}

	public static < T extends IntegerType< T > > void downsample( final RandomAccessible< T > source, final RandomAccessibleInterval< T > target, final int[] steps )
	{
		LOG.debug( "Downsampling ({} {}) with steps {}", Intervals.minAsLongArray( target ), Intervals.maxAsLongArray( target ), steps );
		final TLongLongHashMap counts = new TLongLongHashMap();
		final long[] start = new long[ source.numDimensions() ];
		final long[] stop = new long[ source.numDimensions() ];
		final RandomAccess< T > sourceAccess = source.randomAccess();
		for ( final Cursor< T > targetCursor = Views.flatIterable( target ).cursor(); targetCursor.hasNext(); )
		{
			final T t = targetCursor.next();
			counts.clear();

			Arrays.setAll( start, d -> targetCursor.getLongPosition( d ) * steps[ d ] );
			Arrays.setAll( stop, d -> start[ d ] + steps[ d ] );
			sourceAccess.setPosition( start );

			for ( int dim = 0; dim < start.length; )
			{
				final long id = sourceAccess.get().getIntegerLong();
//				if ( id != Label.INVALID )
				counts.put( id, counts.get( id ) + 1 );

				for ( dim = 0; dim < start.length; ++dim )
				{
					sourceAccess.fwd( dim );
					if ( sourceAccess.getLongPosition( dim ) < stop[ dim ] )
						break;
					else
						sourceAccess.setPosition( start[ dim ], dim );
				}
			}

			long maxCount = 0;
			for ( final TLongLongIterator countIt = counts.iterator(); countIt.hasNext(); )
			{
				countIt.advance();
				final long count = countIt.value();
				final long id = countIt.key();
				if ( count > maxCount )
				{
					maxCount = count;
					t.setInteger( id );
				}
			}

		}
	}

	public static < T extends IntegerType< T > > void upsample(
			final RandomAccessible< ? extends BooleanType< ? > > source,
					final RandomAccessibleInterval< T > target,
					final double[] scaleSourceToTarget, final T value )
	{
		LOG.debug( "Upsampling ({} {}) with scale from source to target {}", Intervals.minAsLongArray( target ), Intervals.maxAsLongArray( target ), scaleSourceToTarget );
		final RandomAccessibleInterval< ? extends BooleanType< ? > > scaledSource = Views.interval( Views.raster( RealViews.transform( Views.interpolate( source, new NearestNeighborInterpolatorFactory<>() ), new Scale3D( scaleSourceToTarget ) ) ), target );
		final Cursor< T > targetCursor = Views.flatIterable( target ).cursor();
		final Cursor< ? extends BooleanType< ? > > sourceCursor = Views.flatIterable( scaledSource ).cursor();
		for ( ; targetCursor.hasNext(); )
		{
			targetCursor.fwd();
			final BooleanType< ? > s = sourceCursor.next();
			if ( s.get() )
				targetCursor.get().set( value );
		}
	}

	private void propagateMask(
			final RandomAccessibleInterval< UnsignedByteType > mask,
			final TLongSet paintedBlocksAtPaintedScale,
			final int paintedLevel,
			final UnsignedLongType label )
	{

		for ( int level = paintedLevel + 1; level < getNumMipmapLevels(); ++level )
		{
			final RandomAccessibleInterval< UnsignedLongType > atLowerLevel = dataCanvases[ level - 1 ];
			final CachedCellImg< UnsignedLongType, DirtyVolatileDelegateLongAccess > atHigherLevel = dataCanvases[ level ];
			final double[] relativeScales = DataSource.getRelativeScales( this, 0, level - 1, level );

			if ( DoubleStream.of( relativeScales ).filter( d -> Math.round( d ) != d ).count() > 0 )
			{
				LOG.warn(
						"Non-integer relative scales found for levels {} and {}: {} -- this does not make sense for label data -- aborting.",
						level - 1,
						level,
						relativeScales );
				throw new RuntimeException( "Non-integer relative scales: " + Arrays.toString( relativeScales ) );
			}
			final TLongSet affectedBlocksAtHigherLevel = this.scaleBlocksToLevel( paintedBlocksAtPaintedScale, paintedLevel, level );

			// downsample
			final int[] steps = DoubleStream.of( relativeScales ).mapToInt( d -> ( int ) d ).toArray();
			downsampleBlocks(
					Views.extendValue( atLowerLevel, new UnsignedLongType( Label.INVALID ) ),
					atHigherLevel,
					affectedBlocksAtHigherLevel,
					steps );
//			downsample( Views.extendValue( atLowerLevel, new UnsignedLongType( Label.INVALID ) ), Views.interval( atHigherLevel, intervalAtHigherLevel ), steps );
		}

		final RealRandomAccessible< UnsignedByteType > interpolatedMask = Views.interpolate( Views.extendZero( mask ), new NearestNeighborInterpolatorFactory<>() );

		for ( int level = paintedLevel - 1; level >= 0; --level )
		{
			final TLongSet affectedBlocksAtLowerLevel = this.scaleBlocksToLevel( paintedBlocksAtPaintedScale, paintedLevel, level );
			final double[] currentRelativeScaleFromTargetToPainted = DataSource.getRelativeScales( this, 0, level, paintedLevel );

			// upsample
			final CellGrid gridAtTargetLevel = dataCanvases[ level ].getCellGrid();
			final int[] blockSize = new int[ gridAtTargetLevel.numDimensions() ];
			gridAtTargetLevel.cellDimensions( blockSize );

			final long[] cellPosTarget = new long[ gridAtTargetLevel.numDimensions() ];
			final long[] minTarget = new long[ gridAtTargetLevel.numDimensions() ];
			final long[] maxTarget = new long[ gridAtTargetLevel.numDimensions() ];
			final long[] stopTarget = new long[ gridAtTargetLevel.numDimensions() ];
			final long[] minPainted = new long[ minTarget.length ];
			final long[] maxPainted = new long[ minTarget.length ];
			final RandomAccess< Cell< DirtyVolatileDelegateLongAccess > > targetCellsAccess = dataCanvases[ level ].getCells().randomAccess();
			final Scale3D scaleTransform = new Scale3D( currentRelativeScaleFromTargetToPainted );
			final RealRandomAccessible< UnsignedByteType > scaledMask = RealViews.transformReal( interpolatedMask, scaleTransform );
			for ( final TLongIterator blockIterator = affectedBlocksAtLowerLevel.iterator(); blockIterator.hasNext(); )
			{
				final long blockId = blockIterator.next();
				gridAtTargetLevel.getCellGridPositionFlat( blockId, cellPosTarget );
				Arrays.setAll( minTarget, d -> cellPosTarget[ d ] * blockSize[ d ] );
				Arrays.setAll( maxTarget, d -> Math.min( minTarget[ d ] + blockSize[ d ], gridAtTargetLevel.imgDimension( d ) ) - 1 );
				Arrays.setAll( stopTarget, d -> maxTarget[ d ] + 1 );
				this.scalePositionToLevel( minTarget, level, paintedLevel, minPainted );
				this.scalePositionToLevel( stopTarget, level, paintedLevel, maxPainted );
				Arrays.setAll( maxPainted, d -> maxPainted[ d ] - 1 );
				final IntervalView< BoolType > relevantBlockAtPaintedResolution = Views.interval( Converters.convert( mask, ( s, t ) -> t.set( s.get() > 0 ), new BoolType() ), minPainted, maxPainted );
				final boolean isConstantValueBlock = isAllTrue( relevantBlockAtPaintedResolution );

				LOG.trace(
						"Upsampling block: level={}, block min (target)={}, block max (target)={}, block min={}, block max={}, scale={}",
						paintedLevel,
						minTarget,
						maxTarget,
						minPainted,
						maxPainted,
						currentRelativeScaleFromTargetToPainted );

				final LongAccess updatedAccess;
				targetCellsAccess.setPosition( cellPosTarget );
				final DirtyVolatileDelegateLongAccess currentAccess = targetCellsAccess.get().getData();
				if ( isConstantValueBlock )
					updatedAccess = new ConstantLongAccess( label.getIntegerLong() );
				else
				{
					final Interval targetInterval = new FinalInterval( minTarget, maxTarget );
					final int numElements = ( int ) Intervals.numElements( targetInterval );
					updatedAccess = new LongArray( numElements );
					final Cursor< UnsignedByteType > maskCursor = Views.flatIterable( Views.interval( Views.raster( scaledMask ), targetInterval ) ).cursor();
					for ( int i = 0; maskCursor.hasNext(); ++i )
					{
						final boolean wasPainted = maskCursor.next().get() > 0;
						updatedAccess.setValue( i, wasPainted ? label.getIntegerLong() : currentAccess.getValue( i ) );
					}

				}
//				targetCellsAccess.get().getData().setDelegate( updatedAccess );
				currentAccess.setDelegate( updatedAccess );
//				currentAccess.setValid( true );
			}

		}
	}

	public static TLongSet affectedBlocks( final CellGrid grid, final Interval... intervals )
	{
		final long[] gridDimensions = grid.getGridDimensions();
		final int[] blockSize = new int[ grid.numDimensions() ];
		grid.cellDimensions( blockSize );
		return affectedBlocks( gridDimensions, blockSize, intervals );
	}

	public static TLongSet affectedBlocks( final long[] gridDimensions, final int[] blockSize, final Interval... intervals )
	{
		final TLongHashSet blocks = new TLongHashSet();
		final int[] ones = IntStream.generate( () -> 1 ).limit( blockSize.length ).toArray();
		final long[] relevantIntervalMin = new long[ blockSize.length ];
		final long[] relevantIntervalMax = new long[ blockSize.length ];
		for ( final Interval interval : intervals )
		{
			Arrays.setAll( relevantIntervalMin, d -> ( interval.min( d ) / blockSize[ d ] ) );
			Arrays.setAll( relevantIntervalMax, d -> ( interval.max( d ) / blockSize[ d ] ) );
			Grids.forEachOffset(
					relevantIntervalMin,
					relevantIntervalMax,
					ones,
					offset -> blocks.add( IntervalIndexer.positionToIndex( offset, gridDimensions ) ) );
		}

		return blocks;

	}

	public static TLongSet affectedBlocksInHigherResolution(
			final TLongSet blocksInLowRes,
			final CellGrid lowResGrid,
			final CellGrid highResGrid,
			final int[] relativeScalingFactors )
	{

		assert lowResGrid.numDimensions() == highResGrid.numDimensions();

		final int[] blockSizeLowRes = new int[ lowResGrid.numDimensions() ];
		Arrays.setAll( blockSizeLowRes, lowResGrid::cellDimension );

		final long[] gridDimHighRes = highResGrid.getGridDimensions();
		final int[] blockSizeHighRes = new int[ highResGrid.numDimensions() ];
		Arrays.setAll( blockSizeHighRes, highResGrid::cellDimension );

		final long[] blockMin = new long[ lowResGrid.numDimensions() ];
		final long[] blockMax = new long[ lowResGrid.numDimensions() ];

		final TLongHashSet blocksInHighRes = new TLongHashSet();

		final int[] ones = IntStream.generate( () -> 1 ).limit( highResGrid.numDimensions() ).toArray();

		for ( final TLongIterator it = blocksInLowRes.iterator(); it.hasNext(); )
		{
			final long index = it.next();
			lowResGrid.getCellGridPositionFlat( index, blockMin );
			for ( int d = 0; d < blockMin.length; ++d )
			{
				final long m = blockMin[ d ] * blockSizeLowRes[ d ];
				blockMin[ d ] = m * relativeScalingFactors[ d ] / blockSizeHighRes[ d ];
				blockMax[ d ] = ( m + blockSizeLowRes[ d ] - 1 ) * relativeScalingFactors[ d ] / blockSizeHighRes[ d ];
			}

			Grids.forEachOffset( blockMin, blockMax, ones, offset -> blocksInHighRes.add( IntervalIndexer.positionToIndex( offset, gridDimHighRes ) ) );

		}
		return blocksInHighRes;
	}

	public static boolean blockWasAllPainted(
			final RandomAccessible< ? extends BooleanType< ? > > mask,
					final long[] min,
					final long[] max,
					final int[] scaleFactors )
	{
		final long[] scaledMin = min.clone();
		final long[] scaledMax = max.clone();
		Arrays.setAll( scaledMin, d -> min[ d ] * scaleFactors[ d ] );
		Arrays.setAll( scaledMax, d -> ( max[ d ] + 1 ) * scaleFactors[ d ] - 1 );
		return isAllTrue( Views.interval( mask, scaledMin, scaledMax ) );
	}

	public static boolean isAllTrue(
			final RandomAccessibleInterval< ? extends BooleanType< ? > > interval )
	{
		for ( final BooleanType< ? > val : Views.iterable( interval ) )
			if ( !val.get() )
				return false;
		return true;
	}

}
