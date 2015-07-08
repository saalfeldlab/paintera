package bdv.img.dvid;

import java.io.File;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

import org.jdom2.Element;

@ImgLoaderIo( format = "dvid-grayscale8", type = DvidGrayscale8ImageLoader.class )
public class XmlIoDvidGrayscale8ImageLoader
		implements XmlIoBasicImgLoader< DvidGrayscale8ImageLoader >
{
	@Override
	public Element toXml( final DvidGrayscale8ImageLoader imgLoader, final File basePath )
	{
		throw new UnsupportedOperationException( "not implemented" );
	}

	@Override
	public DvidGrayscale8ImageLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final String apiUrl = elem.getChildText( "apiUrl" );
		final String nodeId = elem.getChildText( "nodeId" );
		final String dataInstanceId = elem.getChildText( "dataInstanceId" );
		try
		{
			return new DvidGrayscale8ImageLoader( apiUrl, nodeId, dataInstanceId );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
