package bdv.img.dvid;

import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import bdv.img.cache.CacheArrayLoader;

public class DvidMultiscale2dVolatileArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	private VolatileByteArray theEmptyArray;

	private final String apiUrl;
	private final String nodeId;
	private final String dataInstanceId;
	final private int[] zScales;
	final private int[][] blockDimensions;

	public DvidMultiscale2dVolatileArrayLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int[] zScales,
			final int[][] blockDimensions )
	{
		theEmptyArray = new VolatileByteArray( 1, false );
		this.apiUrl = apiUrl;
		this.nodeId = nodeId;
		this.dataInstanceId = dataInstanceId;
		this.zScales = zScales;
		this.blockDimensions = blockDimensions;
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}
	
	static private void readImage(
			final String urlString,
			final int w,
			final int h,
			final int[] data ) throws IOException, InterruptedException
	{
			final URL url = new URL( urlString );
			final BufferedImage jpg = ImageIO.read( url );
			/* This gymnastic is necessary to get reproducible gray
			* values, just opening a JPG or PNG, even when saved by
			* ImageIO, and grabbing its pixels results in gray values
			* with a non-matching gamma transfer function, I cannot tell
			* why... */
			final BufferedImage image =
					new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
			image.createGraphics().drawImage( jpg, 0, 0, null );
			final PixelGrabber pg = new PixelGrabber( image, 0, 0, w, h, data, 0, w );
			pg.grabPixels();
	}
	
	private String makeUrl(
			final int level,
			final long col,
			final long row,
			final long z )
	{
		final StringBuffer buf = new StringBuffer( apiUrl );
		
		// <api URL>/node/3f8c/mymultiscale2d/tile/xy/0/10_10_20
		
		buf.append( "/node/" );
		buf.append( nodeId );
		buf.append( "/" );
		buf.append( dataInstanceId );
		buf.append( "/tile/xy/" );
		buf.append( level );
		buf.append( "/" );
		buf.append( col );
		buf.append( "_" );
		buf.append( row );
		buf.append( "_" );
		buf.append( z );
		
		return buf.toString();
	}
	

	@Override
	public VolatileByteArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		final long c = min[ 0 ] / blockDimensions[ level ][ 0 ];
		final long r = min[ 1 ] / blockDimensions[ level ][ 1 ];
		final double scale = 1.0 / ( 1 << level );

		final int w = dimensions[ 0 ];
		final int h = dimensions[ 1 ];
		final int[] data = new int[ w * h ];
		final byte[] bytes = new byte[ data.length ];

		try
		{
			if ( zScales[ level ] > 1 )
			{
				final long[] values = new long[ data.length ];
				for ( long z = min[ 2 ] * zScales[ level ], dz = 0; dz < zScales[ level ]; ++dz )
				{
					final String urlString = makeUrl( level, c, r, z );
					readImage( urlString, w, h, data );
					
					/* use blue channel from gray scale image */
					for ( int i = 0; i < data.length; ++i )
						values[ i ] += data[ i ] & 0xff;
				}
				for ( int i = 0; i < data.length; ++i )
					bytes[ i ] = ( byte )( values[ i ] / zScales[ level ] );
			}
			else
			{
				final String urlString = makeUrl( level, c, r, min[ 2 ] );
				readImage( urlString, w, h, data );
				
				/* use blue channel from gray scale image */
				for ( int i = 0; i < data.length; ++i )
					bytes[ i ] += ( byte )( data[ i ] & 0xff );

//				System.out.println( "success loading r=" + entry.key.r + " c=" + entry.key.c + " url(" + urlString + ")" );
			}

		}
		catch (final IOException e)
		{
			System.out.println( "failed loading r=" + r + " c=" + c );
		}
		catch (final InterruptedException e)
		{
			e.printStackTrace();
		}
		return new VolatileByteArray( bytes, true );
	}

	@Override
	public VolatileByteArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileByteArray( numEntities, false );
		return theEmptyArray;
	}
}
