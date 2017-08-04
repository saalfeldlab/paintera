package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.ViewerSetupImgLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

/**
 * {@link ViewerSetupImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/grayscale8">DVID's grayscale8 type</a>.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Uint8blkSetupImageLoader
	extends AbstractDvidSetupImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray >
{
	private Uint8blkSetupImageLoader(
			final ConstructorParameters parameters,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super(
				setupId,
				new UnsignedByteType(),
				new VolatileUnsignedByteType(),
				parameters,
				new Uint8blkVolatileArrayLoader(
						parameters.apiUrl,
						parameters.nodeId,
						parameters.dataInstanceId,
						parameters.cellDimensions ) );
	}

	/**
	 * http://emdata.janelia.org/api/help/grayscale8
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "grayscale"
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public Uint8blkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( new ConstructorParameters( apiUrl, nodeId, dataInstanceId ), setupId );
	}
}
