package bdv.labels.labelset;

import java.io.IOException;
import java.util.ArrayList;

import bdv.export.ExportMipmapInfo;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import ch.systemsx.cisd.base.mdarray.MDIntArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5IntStorageFeatures;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.iterator.LocalizingIntervalIterator;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class DownscaleToHdf5
{
	public static void main( final String[] args ) throws IOException
	{
		final String fn = "/Users/pietzsch/workspace/data/bigcat/davi_v7_4k_refix_export.h5";
		final String fnscaled = "/Users/pietzsch/Desktop/downscale-test.h5";

		final int[][] resolutions = new int[][] {
			{ 1, 1, 1 },
			{ 2, 2, 1 },
			{ 4, 4, 1 },
			{ 8, 8, 1 },
			{ 16, 16, 2 },
			{ 32, 32, 3 },
			{ 64, 64, 6 }
		};
		final int[][] subdivisions = new int[][] {
			{ 32, 32, 32 },
			{ 32, 32, 32 },
			{ 32, 32, 32 },
			{ 32, 32, 32 },
			{ 32, 32, 32 },
			{ 32, 32, 32 },
			{ 32, 32, 32 },
		};
		final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( resolutions, subdivisions );
		final int numLevels = resolutions.length;

		for ( int level = 1; level < numLevels; ++level )
		{
			final IHDF5Reader reader = HDF5Factory.openForReading( fn );
			final IHDF5Writer writer = HDF5Factory.open( fnscaled );
			final H5LabelMultisetSetupImageLoader fragments = new H5LabelMultisetSetupImageLoader(
					reader,
					level == 1 ? null : writer,
					"/bodies",
					1,
					new int[] {64, 64, 8},
					new VolatileGlobalCellCache( 1, 10 ) );


			final ArrayList< RandomAccessibleInterval< LabelMultisetType > > imgs = new ArrayList<>();
			for ( int i = 0; i < level; ++i )
				imgs.add( fragments.getImage( 0, i ) );

			writer.uint32().write( "levels", level + 1 );
			final int l = level;
			final BlockWriter hdfBlockWriter = new BlockWriter()
			{
				@Override
				public void writeBlock( final VolatileLabelMultisetArray data, final long[] min, final long[] blocksize )
				{
//					System.out.println( Util.printCoordinates( min ) );
					final LongMappedAccess access = data.getListData().createAccess();
					final int intSize = ( int ) ( data.getListDataUsedSizeInBytes() / 4 );
					final int[] lists = new int[ intSize ];
					for ( int i = 0; i < intSize; ++i )
						lists[ i ] = access.getInt( i * 4 );
					final MDIntArray block = new MDIntArray( data.getCurrentStorageArray(), bdv.img.hdf5.Util.reorder( blocksize ) );

					final String listsPath = String.format( "l%02d/z%05d/y%05d/x%05d/lists", l, min[ 2 ], min[ 1 ], min[ 0 ] );
					final String dataPath = String.format( "l%02d/z%05d/y%05d/x%05d/data", l, min[ 2 ], min[ 1 ], min[ 0 ] );
					writer.uint32().writeArray( listsPath, lists, HDF5IntStorageFeatures.INT_AUTO_SCALING_UNSIGNED_DELETE );
					writer.uint32().writeMDArray( dataPath, block, HDF5IntStorageFeatures.INT_AUTO_SCALING_UNSIGNED_DELETE );
				}
			};
			final LevelInfoWriter hdfLevelInfoWriter = new LevelInfoWriter()
			{
				@Override
				public void writeLevelInfo( final long[] dimensions, final long[] factors, final long[] blocksize )
				{
					System.out.println( "writing level " + l );
					System.out.println( "dimensions = " + Util.printCoordinates( dimensions ) );
					System.out.println( "factors = " + Util.printCoordinates( factors ) );
					System.out.println( "blocksize = " + Util.printCoordinates( blocksize ) );

					final String dimensionsPath = String.format( "l%02d/dimensions", l );
					final String factorsPath = String.format( "l%02d/factors", l );
					final String blocksizePath = String.format( "l%02d/blocksize", l );
					writer.uint64().writeArray( dimensionsPath, dimensions );
					writer.uint64().writeArray( factorsPath, factors );
					writer.uint64().writeArray( blocksizePath, blocksize );
				}
			};
			writeLevelToHdf5File( imgs, mipmapInfo, level, hdfBlockWriter, hdfLevelInfoWriter );
			writer.close();
			reader.close();
		}
	}

	public interface BlockWriter
	{
		public void writeBlock( VolatileLabelMultisetArray data, final long[] min, final long[] blocksize );
	}

	public interface LevelInfoWriter
	{
		public void writeLevelInfo( final long[] dimensions, final long[] factors, final long[] blocksize );
	}

	public static void writeLevelToHdf5File(
			final ArrayList< RandomAccessibleInterval< LabelMultisetType > > imgs,
			final ExportMipmapInfo mipmapInfo,
			final int level,
			final BlockWriter writer,
			final LevelInfoWriter levelInfoWriter )
	{
		final int n = imgs.get( 0 ).numDimensions();
		final int[][] resolutions = mipmapInfo.getExportResolutions();

		// Are downsampling factors a multiple of a level that we have
		// already written?
		int[] factorsToPreviousLevel = null;
		int previousLevel = -1;
		A: for ( int l = level - 1; l >= 0; --l )
		{
			final int[] f = new int[ n ];
			for ( int d = 0; d < n; ++d )
			{
				f[ d ] = resolutions[ level ][ d ] / resolutions[ l ][ d ];
				if ( f[ d ] * resolutions[ l ][ d ] != resolutions[ level ][ d ] )
					continue A;
			}
			factorsToPreviousLevel = f;
			previousLevel = l;
			break;
		}

		final RandomAccessibleInterval< LabelMultisetType > sourceImg = imgs.get( previousLevel );
		final long[] factors = Util.int2long( factorsToPreviousLevel );

		final long[] dimensions = new long[ n ];
		sourceImg.dimensions( dimensions );
		for ( int d = 0; d < n; ++d )
			dimensions[ d ] = Math.max( dimensions[ d ] / factors[ d ], 1 );

		levelInfoWriter.writeLevelInfo( dimensions, Util.int2long( resolutions[ level ] ), Util.int2long( mipmapInfo.getSubdivisions()[ level ] ) );

		final long[] minRequiredInput = new long[ n ];
		final long[] maxRequiredInput = new long[ n ];
		sourceImg.min( minRequiredInput );
		for ( int d = 0; d < n; ++d )
			maxRequiredInput[ d ] = minRequiredInput[ d ] + dimensions[ d ] * factors[ d ] - 1;
		final RandomAccessibleInterval< LabelMultisetType > extendedImg = Views.interval( Views.extendBorder( sourceImg ), new FinalInterval( minRequiredInput, maxRequiredInput ) );

		final int[] cellDimensions = mipmapInfo.getSubdivisions()[ level ];
		final long[] numCells = new long[ n ];
		final int[] borderSize = new int[ n ];
		final long[] minCell = new long[ n ];
		final long[] maxCell = new long[ n ];
		for ( int d = 0; d < n; ++d )
		{
			numCells[ d ] = ( dimensions[ d ] - 1 ) / cellDimensions[ d ] + 1;
			maxCell[ d ] = numCells[ d ] - 1;
			borderSize[ d ] = ( int ) ( dimensions[ d ] - ( numCells[ d ] - 1 ) * cellDimensions[ d ] );
		}

		final LocalizingIntervalIterator i = new LocalizingIntervalIterator( minCell, maxCell );
		final long[] currentCellMin = new long[ n ];
		final long[] currentCellDim = new long[ n ];
		final long[] currentCellPos = new long[ n ];
		while ( i.hasNext() )
		{
			i.fwd();
			i.localize( currentCellPos );
			for ( int d = 0; d < n; ++d )
			{
				currentCellMin[ d ] = currentCellPos[ d ] * cellDimensions[ d ];
				final boolean isBorderCellInThisDim = ( currentCellPos[ d ] + 1 == numCells[ d ] );
				currentCellDim[ d ] = isBorderCellInThisDim ? borderSize[ d ] : cellDimensions[ d ];
			}
			final VolatileLabelMultisetArray downscaled = Downscale.downscale( extendedImg, factors, currentCellDim, currentCellMin );
			writer.writeBlock( downscaled, currentCellMin, currentCellDim );
		}
	}
}
