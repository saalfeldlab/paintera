package bdv.labels.labelset;

import gnu.trove.list.array.TLongArrayList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;

// load full resolution arrays and create list representation
	public class VolatileSuperVoxelMultisetArrayLoader implements CacheArrayLoader< VolatileSuperVoxelMultisetArray >
	{
		private VolatileSuperVoxelMultisetArray theEmptyArray;

		private final String apiUrl;
		private final String nodeId;
		private final String dataInstanceId;

		public VolatileSuperVoxelMultisetArrayLoader(
				final String apiUrl,
				final String nodeId,
				final String dataInstanceId,
				final int[] blockDimensions )
		{
			theEmptyArray = new VolatileSuperVoxelMultisetArray( 1, false );
			this.apiUrl = apiUrl;
			this.nodeId = nodeId;
			this.dataInstanceId = dataInstanceId;
		}

		// TODO: unused -- remove.
		@Override
		public int getBytesPerElement()
		{
			return 8;
		}

		static private void readBlock(
				final String urlString,
				final int[] data,
				final LongMappedAccessData listData  ) throws IOException
		{
			final byte[] bytes = new byte[ data.length * 8 ];
			final URL url = new URL( urlString );
			final InputStream in = url.openStream();
			final byte[] header = new byte[1];
			in.read( header, 0, 1 );
			if ( header[ 0 ] == 0 )
				return;

			in.skip( 3 );
			int off = 0;
			for (
					int l = in.read( bytes, off, bytes.length );
					l > 0 || off + l < bytes.length;
					off += l, l = in.read( bytes, off, bytes.length - off ) );
			in.close();

			final TLongArrayList idAndOffsetList = new TLongArrayList();
			long nextListOffset = 0;
A:			for ( int i = 0, j = -1; i < data.length; ++i )
			{
				final long id =
						bytes[ ++j ] |
						( ( long )bytes[ ++j ] << 8 ) |
						( ( long )bytes[ ++j ] << 16 ) |
						( ( long )bytes[ ++j ] << 24 ) |
						( ( long )bytes[ ++j ] << 32 ) |
						( ( long )bytes[ ++j ] << 40 ) |
						( ( long )bytes[ ++j ] << 48 ) |
						( ( long )bytes[ ++j ] << 56 );

				// does the list [id x 1] already exist?
				for ( int k = 0; k < idAndOffsetList.size(); k += 2 )
				{
					if ( idAndOffsetList.getQuick( k ) == id )
					{
						final long offset = idAndOffsetList.getQuick( k + 1 );
						data[ i ] = ( int ) offset;
						continue A;
					}
				}

				final MappedObjectArrayList< SuperVoxelMultisetEntry, ? > list = new MappedObjectArrayList<>( SuperVoxelMultisetEntry.type, listData, nextListOffset );
				list.add( new SuperVoxelMultisetEntry( id, 1 ) );
				idAndOffsetList.add( id );
				idAndOffsetList.add( nextListOffset );
				data[ i ] = ( int ) nextListOffset;
				nextListOffset += list.getSizeInBytes();
			}
		}

		private String makeUrl(
				final long[] min,
				final int[] dimensions )
		{
			final StringBuffer buf = new StringBuffer( apiUrl );

			buf.append( "/node/" );
			buf.append( nodeId );
			buf.append( "/" );
			buf.append( dataInstanceId );
			buf.append( "/blocks/" );
			buf.append( min[ 0 ] / dimensions[ 0 ] );
			buf.append( "_" );
			buf.append( min[ 1 ] / dimensions[ 1 ] );
			buf.append( "_" );
			buf.append( min[ 2 ] / dimensions[ 2 ] );
			buf.append( "/1" );

			return buf.toString();
		}

		@Override
		public VolatileSuperVoxelMultisetArray loadArray(
				final int timepoint,
				final int setup,
				final int level,
				final int[] dimensions,
				final long[] min ) throws InterruptedException
		{
			final int[] data = new int[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
			final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );

			try
			{
				final String urlString = makeUrl( min, dimensions );
//				System.out.println( urlString + " " + data.length );
				readBlock( urlString, data, listData );
			}
			catch (final IOException e)
			{
				System.out.println(
						"failed loading min = " +
						Arrays.toString( min ) +
						", dimensions = " +
						Arrays.toString( dimensions ) );
				return emptyArray( dimensions );
			}

			return new VolatileSuperVoxelMultisetArray( data, listData, true );
		}

		@Override
		public VolatileSuperVoxelMultisetArray emptyArray( final int[] dimensions )
		{
			int numEntities = 1;
			for ( int i = 0; i < dimensions.length; ++i )
				numEntities *= dimensions[ i ];
			if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
				theEmptyArray = new VolatileSuperVoxelMultisetArray( numEntities, false );
			return theEmptyArray;
		}
	}