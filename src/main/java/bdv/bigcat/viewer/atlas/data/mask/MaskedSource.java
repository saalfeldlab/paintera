package bdv.bigcat.viewer.atlas.data.mask;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.mask.PickOne.PickAndConvert;
import bdv.labels.labelset.Label;
import bdv.net.imglib2.view.RandomAccessibleTriple;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class MaskedSource< D extends Type< D >, T extends Type< T > > implements DataSource< D, T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int NUM_DIMENSIONS = 3;

	private final DataSource< D, T > source;

	private final RandomAccessibleInterval< UnsignedLongType >[] dataCanvases;

	private final RandomAccessibleInterval< VolatileUnsignedLongType >[] canvases;

	private final HashMap< RandomAccessibleInterval< UnsignedByteType >, MaskInfo< UnsignedLongType > > masks;

	private final RandomAccessible< UnsignedLongType >[] dMasks;

	private final RandomAccessible< VolatileUnsignedLongType >[] tMasks;

	private final ImgFactory< UnsignedByteType > maskFactory;

	private final PickAndConvert< D, UnsignedLongType, UnsignedLongType, D > pacD;

	private final PickAndConvert< T, VolatileUnsignedLongType, VolatileUnsignedLongType, T > pacT;

	private final D extensionD;

	private final T extensionT;

	// TODO make sure that BB is handled properly in multi scale case!!!
	private Interval paintedBoundingBox = null;

	private final Consumer< RandomAccessibleInterval< UnsignedLongType > > mergeCanvasToBackground;

	@SuppressWarnings( "unchecked" )
	public MaskedSource(
			final DataSource< D, T > source,
			final DiskCachedCellImgOptions opts,
			final IntFunction< String > mipmapCanvasCacheDirs,
			final PickAndConvert< D, UnsignedLongType, UnsignedLongType, D > pacD,
			final PickAndConvert< T, VolatileUnsignedLongType, VolatileUnsignedLongType, T > pacT,
			final D extensionD,
			final T extensionT,
			final Consumer< RandomAccessibleInterval< UnsignedLongType > > mergeCanvasToBackground )
	{
		super();
		this.source = source;
		this.dataCanvases = new RandomAccessibleInterval[ source.getNumMipmapLevels() ];
		this.canvases = new RandomAccessibleInterval[ source.getNumMipmapLevels() ];
		this.dMasks = new RandomAccessible[ this.canvases.length ];
		this.tMasks = new RandomAccessible[ this.canvases.length ];

		for ( int i = 0; i < canvases.length; ++i )
		{
			final DiskCachedCellImgOptions o = opts.cacheDirectory( Paths.get( mipmapCanvasCacheDirs.apply( i ) ) );
			final DiskCachedCellImgFactory< UnsignedLongType > f = new DiskCachedCellImgFactory<>( o );
			final CellLoader< UnsignedLongType > loader = img -> img.forEach( t -> t.set( Label.INVALID ) );
			final RandomAccessibleInterval< UnsignedLongType > store = f.create( Intervals.dimensionsAsLongArray( source.getDataSource( 0, i ) ), new UnsignedLongType(), loader, o );
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

	public void applyMask( final RandomAccessibleInterval< UnsignedByteType > mask, final Interval paintedInterval )
	{
		synchronized ( this.masks )
		{
			final MaskInfo< UnsignedLongType > maskInfo = this.masks.get( mask );
			if ( maskInfo == null )
			{
				LOG.debug( "Did not pass valid mask {}", mask );
				return;
			}
			final RandomAccessibleInterval< UnsignedLongType > canvas = dataCanvases[ maskInfo.level ];
			final long[] min = Intervals.minAsLongArray( canvas );
			final long[] max = Intervals.maxAsLongArray( canvas );
			for ( int d = 0; d < min.length; ++d )
			{
				min[ d ] = Math.max( paintedInterval.min( d ), min[ d ] );
				max[ d ] = Math.min( paintedInterval.max( d ), max[ d ] );
			}

			final FinalInterval interval = new FinalInterval( min, max );

			LOG.debug( "For mask info {} and interval {}, painting into canvas {}", maskInfo, interval, dataCanvases );
			final Cursor< UnsignedLongType > canvasCursor = Views.flatIterable( Views.interval( canvas, interval ) ).cursor();
			final Cursor< UnsignedByteType > maskCursor = Views.flatIterable( Views.interval( mask, interval ) ).cursor();
			while ( canvasCursor.hasNext() )
			{
				canvasCursor.fwd();
				maskCursor.fwd();
				if ( maskCursor.get().get() == 1 )
					canvasCursor.get().set( maskInfo.value );
			}

			this.dMasks[ maskInfo.level ] = ConstantUtils.constantRandomAccessible( new UnsignedLongType( Label.INVALID ), NUM_DIMENSIONS );
			this.dMasks[ maskInfo.level ] = ConstantUtils.constantRandomAccessible( new UnsignedLongType( Label.INVALID ), NUM_DIMENSIONS );

			forgetMasks();

			// TODO make correct painted bounding box for multi scale
			// TODO update canvases in other scale levels
			if ( this.paintedBoundingBox == null )
				this.paintedBoundingBox = interval;
			else
			{
				final long[] m = min.clone();
				final long[] M = max.clone();
				for ( int d = 0; d < this.paintedBoundingBox.numDimensions(); ++d )
				{
					m[ d ] = Math.min( this.paintedBoundingBox.min( d ), m[ d ] );
					M[ d ] = Math.max( this.paintedBoundingBox.max( d ), M[ d ] );
				}
				this.paintedBoundingBox = new FinalInterval( m, M );
			}
		}

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
		if ( this.paintedBoundingBox != null && this.mergeCanvasToBackground != null )
		{
			LOG.debug( "Merging canvas into background for interval {}", this.paintedBoundingBox );
			final IntervalView< UnsignedLongType > rai = Views.interval( this.dataCanvases[ 0 ], this.paintedBoundingBox );
			this.paintedBoundingBox = null;
			this.mergeCanvasToBackground.accept( rai );
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
		return Converters.convert( this.dataCanvases[ level ], new TypeIdentity<>(), new UnsignedLongType() );
	}

	public RandomAccessibleInterval< D > getReadOnlyDataBackground( final int t, final int level )
	{
		return Converters.convert( this.source.getDataSource( t, level ), new TypeIdentity<>(), this.source.getDataType().createVariable() );
	}

}
