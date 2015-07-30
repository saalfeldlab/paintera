package bdv.img.dvid;

import java.io.IOException;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.Cache;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * {@link ViewerImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/grayscale8">DVID's grayscale8 type</a>.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class DvidGrayscale8ImageLoader extends DvidGrayscale8SetupImageLoader
		implements ViewerImgLoader
{
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
	public DvidGrayscale8ImageLoader(
			final String apiUrl,
			final String nodeId,
			final String dataInstanceId) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( apiUrl, nodeId, dataInstanceId, 0 );
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public Cache getCache()
	{
		return cache;
	}

}
