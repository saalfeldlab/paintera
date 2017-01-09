package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.ColorStream;

/**
 * {@link ViewerImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/labels64">DVID's labels64 type</a>
 * that maps uint64 into saturated ARGB colors using {@link ColorStream}.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class LabelblkImageLoader
	extends LabelblkSetupImageLoader
	implements ViewerImgLoader
{
	/**
	 * http://emdata.janelia.org/api/help/labels64
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
	public LabelblkImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId,
			final int argbMask ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( apiUrl, nodeId, dataInstanceId, 0, argbMask );
	}

	/**
	 * http://hackathon.janelia.org/api/help/grayscale8
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "bodies"
	 *
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public LabelblkImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( apiUrl, nodeId, dataInstanceId, 0xffffffff );
	}

	@Override
	public VolatileGlobalCellCache getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}
}
