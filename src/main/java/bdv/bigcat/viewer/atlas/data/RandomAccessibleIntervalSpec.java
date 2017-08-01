package bdv.bigcat.viewer.atlas.data;

import java.util.Arrays;

import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Util;

public class RandomAccessibleIntervalSpec< T extends NumericType< T >, VT extends NumericType< VT > > implements DatasetSpec< T, VT >
{

	private final Source< T > source;

	private final Source< VT > viewerSource;

	private final Converter< VT, ARGBType > viewerConverter;

	public RandomAccessibleIntervalSpec(
			final Converter< VT, ARGBType > viewerConverter,
			final RandomAccessibleInterval< T >[] sources,
			final RandomAccessibleInterval< VT >[] viewerSources,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name )
	{

		this.source = new RandomAccessibleIntervalMipmapSource( sources, Util.getTypeFromInterval( sources[ 0 ] ), resolutions, voxelDimensions, name );
		this.viewerSource = new RandomAccessibleIntervalMipmapSource( viewerSources, Util.getTypeFromInterval( sources[ 0 ] ), resolutions, voxelDimensions, name );

		this.viewerConverter = viewerConverter;
	}

	@Override
	public Source< T > getSource()
	{
		return source;
	}

	@Override
	public Source< VT > getViewerSource()
	{
		return viewerSource;
	}

	@Override
	public Converter< VT, ARGBType > getViewerConverter()
	{
		return viewerConverter;
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT >, A > RandomAccessibleIntervalSpec< T, VT > fromCachedCellLoader(
			final Converter< VT, ARGBType > viewerConverter,
			final CacheLoader< Long, Cell< A > >[] loaders,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name,
			final CellGrid[] grids,
			final T t,
			final A accessType )
	{
		return fromCachedCellLoader( viewerConverter, loaders, resolutions, voxelDimensions, name, grids, t, accessType, null, null );
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT >, A > RandomAccessibleIntervalSpec< T, VT > fromCachedCellLoader(
			final Converter< VT, ARGBType > viewerConverter,
			final CacheLoader< Long, Cell< A > >[] loaders,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name,
			final CellGrid[] grids,
			final T t,
			final A accessType,
			final SharedQueue queue,
			final CacheHints hints )
	{
		return fromCachedCellLoader( viewerConverter, loaders, resolutions, voxelDimensions, name, grids, new SoftRefLoaderCache<>(), t, accessType, queue, hints );
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT >, A > RandomAccessibleIntervalSpec< T, VT > fromCachedCellLoader(
			final Converter< VT, ARGBType > viewerConverter,
			final CacheLoader< Long, Cell< A > >[] loaders,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name,
			final CellGrid[] grids,
			final LoaderCache< Long, Cell< A > > loaderCache,
			final T t,
			final A accessType )
	{
		return fromCachedCellLoader( viewerConverter, loaders, resolutions, voxelDimensions, name, grids, loaderCache, t, accessType, null, null );
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT >, A > RandomAccessibleIntervalSpec< T, VT > fromCachedCellLoader(
			final Converter< VT, ARGBType > viewerConverter,
			final CacheLoader< Long, Cell< A > >[] loaders,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name,
			final CellGrid[] grids,
			final LoaderCache< Long, Cell< A > > loaderCache,
			final T t,
			final A accessType,
			final SharedQueue queue,
			final CacheHints hints )
	{
		final CachedCellImg< T, A >[] sources = new CachedCellImg[ loaders.length ];
		for ( int i = 0; i < loaders.length; ++i )
		{
			final Cache< Long, Cell< A > > cache = loaderCache.withLoader( loaders[ i ] );
			sources[ i ] = new CachedCellImg<>( grids[ i ], t.getEntitiesPerPixel(), cache, accessType );
		}
		return fromCachedCellImg( viewerConverter, sources, resolutions, voxelDimensions, name, queue, hints );
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT > > RandomAccessibleIntervalSpec< T, VT > fromCachedCellImg(
			final Converter< VT, ARGBType > viewerConverter,
			final CachedCellImg< T, ? >[] sources,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name )
	{
		return fromCachedCellImg( viewerConverter, sources, resolutions, voxelDimensions, name, null, null );
	}

	public static < T extends NativeType< T > & NumericType< T >, VT extends Volatile< T > & NumericType< VT > > RandomAccessibleIntervalSpec< T, VT > fromCachedCellImg(
			final Converter< VT, ARGBType > viewerConverter,
			final CachedCellImg< T, ? >[] sources,
			final double[][] resolutions,
			final VoxelDimensions voxelDimensions,
			final String name,
			final SharedQueue queue,
			final CacheHints hints )
	{
		final RandomAccessibleInterval< VT >[] vsources = Arrays.stream( sources ).map( source -> VolatileViews.wrapAsVolatile( source, queue, hints ) ).toArray( RandomAccessibleInterval[]::new );
		return new RandomAccessibleIntervalSpec<>( viewerConverter, sources, vsources, resolutions, voxelDimensions, name );
	}

}
