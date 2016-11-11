package bdv.img.knossos;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
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
abstract public class AbstractKnossosImageLoader< T, V extends Volatile< T > > extends AbstractViewerSetupImgLoader< T, V > implements ViewerImgLoader
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
	}
	
	final protected KnossosConfig config;
	
	final protected double[][] mipmapResolutions;

	final protected long[][] imageDimensions;

	final protected AffineTransform3D[] mipmapTransforms;

	final protected VolatileGlobalCellCache cache;

	public AbstractKnossosImageLoader(
			T type,
			V volatileType,
			final String configUrl, final String urlFormat )
	{
		super( type, volatileType );
		
		config = tryFetchConfig( configUrl, 20 );

		mipmapResolutions = new double[ config.numScales ][];
		imageDimensions = new long[ config.numScales ][];
		mipmapTransforms = new AffineTransform3D[ config.numScales ];
		for ( int l = 0; l < config.numScales; ++l )
		{
			final int si = 1 << l;
			
			mipmapResolutions[ l ] = new double[] { si, si, si };
			imageDimensions[ l ] = new long[] { config.width >> l, config.height >> l, config.depth >> l };
			
			final AffineTransform3D mipmapTransform = new AffineTransform3D();

			mipmapTransform.set( si * config.scaleX, 0, 0 );
			mipmapTransform.set( si * config.scaleY, 1, 1 );
			mipmapTransform.set( si * config.scaleZ, 2, 2 );
			
			final double offset = 0.5 * ( si - 1 );

			mipmapTransform.set( offset * config.scaleX, 0, 3 );
			mipmapTransform.set( offset * config.scaleY, 1, 3 );
			mipmapTransform.set( offset * config.scaleZ, 2, 3 );

			mipmapTransforms[ l ] = mipmapTransform;
		}

		cache = new VolatileGlobalCellCache( config.numScales, 10 );
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
			String server = config.replaceAll( "(?s).*ftp_mode ([^ ]*) .*", "$1" );
			if ( !server.equals( config ) )
				baseUrl += server;
			String httpUser = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* ([^ ]*).*", "$1" );
			String httpPasswd = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* [^ ]* ([^ ]*).*", "$1" );
			if ( !( httpUser.equals( config ) || httpPasswd.equals( config ) ) )
				baseUrl += httpUser + ":" + httpPasswd + "@";
			String rootDirectory = config.replaceAll( "(?s).*ftp_mode [^ ]* ([^ ]*).*", "$1" );
			if ( !rootDirectory.equals( config ) )
				baseUrl += "/" + rootDirectory;
			String timeout = config.replaceAll( "(?s).*ftp_mode [^ ]* [^ ]* [^ ]* [^ ]* ([^ ;]*);.*", "$1" );
			if ( timeout.equals( config ) ) timeout = "";
		}
		else
			baseUrl = configUrl.replaceAll( "^(.*)/[^/]*/[^/]*$", "$1" );
		
		int numScales = magnificationString.equals( config ) ? 1 : Util.ldu( Integer.parseInt( magnificationString ) );
		
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

	@Override
	public double[][] getMipmapResolutions()
	{
		return mipmapResolutions;
	}

	@Override
	public int numMipmapLevels()
	{
		return config.numScales;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}
}
