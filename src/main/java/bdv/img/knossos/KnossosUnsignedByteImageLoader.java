package bdv.img.knossos;

import java.io.IOException;

import bdv.img.cache.VolatileGlobalCellCache;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
/**
 * Loader for uint8 volumes stored in the KNOSSOS format
 *
 * http://knossostool.org/
 *
 * Volumes are stored as 128<sup>3</sup> 8bit datacubes either
 * jpeg-compressed or raw in a nested file structure.
 *
 * Files names follow a convention that is typically
 * <baseURL>/mag%d/x%04d/y%04d/z%04d/<experiment>_x%04d_y%04d_z%04d_mag%d.raw


x y z are %04d block coordinates
mag in {1, 2, 4, 8, 16, ...}



example

experiment name "070317_e1088";
scale x 22.0;
scale y 22.0;
scale z 30.0;
boundary x 2048;
boundary y 1792;
boundary z 2048;
magnification 1;



#optional

compression_ratio 1000; # means jpg
ftp_mode <server> <root_directory> <http_user> <http_passwd> 30000
                                                             timeout ?



 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 *
 */
public class KnossosUnsignedByteImageLoader extends AbstractKnossosImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray >
{
	public KnossosUnsignedByteImageLoader(
			final KnossosConfig config,
			final String urlFormat,
			final VolatileGlobalCellCache cache )
	{
		super(
				config,
				urlFormat,
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				new KnossosUnsignedByteVolatileArrayLoader(
						config.baseUrl,
						urlFormat,
						config.experimentName,
						config.format ),
				cache );
	}


	public KnossosUnsignedByteImageLoader(
			final String configUrl,
			final String urlFormat,
			final VolatileGlobalCellCache cache ) throws IOException
	{
		this( fetchConfig( configUrl ), urlFormat, cache );
	}
}
