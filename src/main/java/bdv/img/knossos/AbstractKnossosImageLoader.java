package bdv.img.knossos;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;

import bdv.AbstractCachedViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.util.Util;
/**
 * Loader for uint8 volumes stored in the KNOSSOS format
 *
 * http://knossostool.org/
 *
 * Blocks of 128^3 voxels (fill with zero if smaller) uint64 voxels in network
 * byte order from left top front to right bottom rear,
 * index = z 128<sup>2</sup> + y 128 + x
 * naming convention
 *
 * x%1$d/y%2$d/z%3$d/%4$s_x%1$d_y%2$d_z%3$d.raw
 * with arguments
 *
 * (1) x / 128
 * (2) y / 128
 * (3) z / 128
 * (4) name
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 *
 */
abstract public class AbstractKnossosImageLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A extends VolatileAccess > extends AbstractCachedViewerSetupImgLoader< T, V, A > implements ViewerImgLoader
{
	static public class KnossosConfig
	{
		final public String experimentName;
		final public double scaleX;
		final public double scaleY;
		final public double scaleZ;
		final public long width;
		final public long height;
		final public long depth;
		final public int numScales;
		final public String baseUrl;
		final public String format;

		public KnossosConfig(
				final String experimentName,
				final double scaleX,
				final double scaleY,
				final double scaleZ,
				final long width,
				final long height,
				final long depth,
				final int numScales,
				final String baseUrl,
				final String format)
		{
			this.experimentName = experimentName;
			this.scaleX = scaleX;
			this.scaleY = scaleY;
			this.scaleZ = scaleZ;
			this.width = width;
			this.height = height;
			this.depth = depth;
			this.numScales = numScales;
			this.baseUrl = baseUrl;
			this.format = format;
		}

		public double[][] createMipmapResolutions()
		{
			final double[][] mipmapResolutions = new double[ numScales ][];
			for ( int l = 0; l < numScales; ++l )
			{
				final int si = 1 << l;
				mipmapResolutions[ l ] = new double[] { si, si, si };
			}
			return mipmapResolutions;
		}

		public long[][] createDimensions()
		{
			final long[][] dimensions = new long[ numScales ][];
			for ( int l = 0; l < numScales; ++l )
				dimensions[ l ] = new long[] { width >> l, height >> l, depth >> l };

			return dimensions;
		}

		public int[][] createCellDimensions()
		{
			final int[][] cellDimensions = new int[ numScales ][];
			for ( int l = 0; l < numScales; ++l )
				cellDimensions[ l ] = new int[] { 128, 128, 128 };

			return cellDimensions;
		}

		public  AffineTransform3D[] createMipmapTransforms()
		{
			final AffineTransform3D[] mipmapTransforms = new AffineTransform3D[ numScales ];
			for ( int l = 0; l < numScales; ++l )
			{
				final int si = 1 << l;

				final AffineTransform3D mipmapTransform = new AffineTransform3D();

				mipmapTransform.set( si * scaleX, 0, 0 );
				mipmapTransform.set( si * scaleY, 1, 1 );
				mipmapTransform.set( si * scaleZ, 2, 2 );

				final double offset = 0.5 * ( si - 1 );

				mipmapTransform.set( offset * scaleX, 0, 3 );
				mipmapTransform.set( offset * scaleY, 1, 3 );
				mipmapTransform.set( offset * scaleZ, 2, 3 );

				mipmapTransforms[ l ] = mipmapTransform;
			}

			return mipmapTransforms;
		}
	}

	final static public KnossosConfig fetchConfig( final String configUrl ) throws IOException
	{
		final URL url = new URL( configUrl );
		System.out.println( "Fetching config from " + url );

		final String config = IOUtils.toString( url.openStream() );

		final String experimentName = config.replaceAll( "(?s).*experiment name \"([^\"]*)\";.*", "$1" );

		final String scaleXString = config.replaceAll( "(?s).*scale x ([^;]*);.*", "$1" );
		final String scaleYString = config.replaceAll( "(?s).*scale y ([^;]*);.*", "$1" );
		final String scaleZString = config.replaceAll( "(?s).*scale z ([^;]*);.*", "$1" );

		final String boundaryXString = config.replaceAll( "(?s).*boundary x ([^;]*);.*", "$1" );
		final String boundaryYString = config.replaceAll( "(?s).*boundary y ([^;]*);.*", "$1" );
		final String boundaryZString = config.replaceAll( "(?s).*boundary z ([^;]*);.*", "$1" );

		final String magnificationString = config.replaceAll( "(?s).*magnification ([^;]*);.*", "$1" );

		final String compressionRatioString = config.replaceAll( "(?s).*compression_ratio ([^;]*);.*", "$1" );

		String baseUrl = "";
		if ( config.contains( "ftp_mode" ) )
		{
			baseUrl = "http://";
			final String server = config.replaceAll( "(?s).*ftp_mode ([^ ]*) .*", "$1" );
			if ( !server.equals( config ) )
				baseUrl += server;
			final String httpUser = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* ([^ ]*).*", "$1" );
			final String httpPasswd = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* [^ ]* ([^ ]*).*", "$1" );
			if ( !( httpUser.equals( config ) || httpPasswd.equals( config ) ) )
				baseUrl += httpUser + ":" + httpPasswd + "@";
			final String rootDirectory = config.replaceAll( "(?s).*ftp_mode [^ ]* ([^ ]*).*", "$1" );
			if ( !rootDirectory.equals( config ) )
				baseUrl += "/" + rootDirectory;
			String timeout = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* [^ ]* [^ ]* ([^ ;]*);.*", "$1" );
			if ( timeout.equals( config ) ) timeout = "";
		}
		else
			baseUrl = configUrl.replaceAll( "^(.*)/[^/]*/[^/]*$", "$1" );

		final int numScales = magnificationString.equals( config ) ? 1 : Util.ldu( Integer.parseInt( magnificationString ) );

		final KnossosConfig knossosConfig = new KnossosConfig(
				experimentName,
				Double.parseDouble( scaleXString ),
				Double.parseDouble( scaleYString ),
				Double.parseDouble( scaleZString ),
				Long.parseLong( boundaryXString ),
				Long.parseLong( boundaryYString ),
				Long.parseLong( boundaryZString ),
				numScales,
				baseUrl,
				compressionRatioString.equals( "1000" ) ? "jpg" : "raw" );

		System.out.println( new Gson().toJson( knossosConfig ) );

		return knossosConfig;
	}

	final static public KnossosConfig tryFetchConfig( final String configUrl, final int maxNumTrials )
	{
		KnossosConfig config = null;
		for ( int i = 0; i < maxNumTrials && config == null; ++i )
		{
			try
			{
				config = fetchConfig( configUrl );
				break;
			}
			catch ( final Exception e )
			{
				e.printStackTrace( System.err );
			}
			try
			{
				Thread.sleep( 100 );
			}
			catch ( final InterruptedException e )
			{}
		}
		return config;
	}

	final protected KnossosConfig config;

	public AbstractKnossosImageLoader(
			final KnossosConfig config,
			final String urlFormat,
			final T type,
			final V volatileType,
			final CacheArrayLoader< A > loader,
			final VolatileGlobalCellCache cache )
	{
		super(
				0,
				config.createDimensions(),
				config.createCellDimensions(),
				config.createMipmapResolutions(),
				type,
				volatileType,
				loader,
				cache );

		this.config = config;

	}

	public AbstractKnossosImageLoader(
			final String configUrl,
			final String urlFormat,
			final T type,
			final V volatileType,
			final CacheArrayLoader< A > loader,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		this(
				tryFetchConfig( configUrl, 20 ),
				urlFormat,
				type,
				volatileType,
				loader,
				cache );
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( @SuppressWarnings( "hiding" ) final int setupId )
	{
		return this;
	}
}
