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
 * <a href= "http://emdata.janelia.org/api/help/multiscale2d">DVID's multiscale2d type</a>
 * that maps uint64 into saturated ARGB colors using {@link ColorStream}.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class DvidMultiscale2dImageLoader extends DvidMultiscale2dSetupImageLoader
	implements ViewerImgLoader
{
	/**
	 * http://hackathon.janelia.org/api/help/multiscale2d
	 *
	 * @param apiUrl e.g. "http://hackathon.janelia.org/api"
	 * @param nodeId e.g. "2a3fd320aef011e4b0ce18037320227c"
	 * @param dataInstanceId e.g. "graytiles"
	 * @throws IOException
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 */
	public DvidMultiscale2dImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( apiUrl, nodeId, dataInstanceId, 0 );
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
