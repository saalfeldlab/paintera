package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.ViewerSetupImgLoader;
import bdv.util.ColorStream;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;


/**
 * {@link ViewerSetupImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/labels64">DVID's labels64 type</a>
 * that maps uint64 into saturated ARGB colors using {@link ColorStream}.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class LabelblkSetupImageLoader extends
	AbstractDvidSetupImageLoader< ARGBType, VolatileARGBType, VolatileIntArray >
{
	private LabelblkSetupImageLoader(
			final ConstructorParameters parameters,
			final int setupId,
			final int argbMask ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super(
				setupId,
				new ARGBType(),
				new VolatileARGBType(),
				parameters,
				new LabelblkVolatileArrayLoader(
						parameters.apiUrl,
						parameters.nodeId,
						parameters.dataInstanceId,
						parameters.cellDimensions,
						argbMask ) );
	}

	/**
	 * http://hackathon.janelia.org/api/help/labels64
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 * @param argbMask e.g. 0xffffffff for full opacity or 0x7fffffff for half opacity
	 *
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public LabelblkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId,
			final int argbMask ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( new ConstructorParameters( apiUrl, nodeId, dataInstanceId ), setupId, argbMask );
	}

	/**
	 * http://hackathon.janelia.org/api/help/labels64
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 *
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public LabelblkSetupImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int setupId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( apiUrl, nodeId, dataInstanceId, setupId, 0xffffffff );
	}
}
