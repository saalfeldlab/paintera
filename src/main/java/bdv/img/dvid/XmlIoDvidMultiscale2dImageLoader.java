package bdv.img.dvid;

import java.io.File;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

import org.jdom2.Element;

@ImgLoaderIo( format = "dvid-multiscale2d", type = DvidMultiscale2dImageLoader.class )
public class XmlIoDvidMultiscale2dImageLoader
		implements XmlIoBasicImgLoader< DvidMultiscale2dImageLoader >
{
	@Override
	public Element toXml( final DvidMultiscale2dImageLoader imgLoader, final File basePath )
	{
		throw new UnsupportedOperationException( "not implemented" );
	}

	@Override
	public DvidMultiscale2dImageLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final String apiUrl = elem.getChildText( "apiUrl" );
		final String nodeId = elem.getChildText( "nodeId" );
		final String dataInstanceId = elem.getChildText( "dataInstanceId" );
		try
		{
			return new DvidMultiscale2dImageLoader( apiUrl, nodeId, dataInstanceId );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
