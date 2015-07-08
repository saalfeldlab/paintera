package bdv.img.dvid;

import java.io.File;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

import org.jdom2.Element;

@ImgLoaderIo( format = "dvid-labels64", type = DvidLabels64ImageLoader.class )
public class XmlIoDvidLabels64ImageLoader
		implements XmlIoBasicImgLoader< DvidLabels64ImageLoader >
{
	@Override
	public Element toXml( final DvidLabels64ImageLoader imgLoader, final File basePath )
	{
		throw new UnsupportedOperationException( "not implemented" );
	}

	@Override
	public DvidLabels64ImageLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final String apiUrl = elem.getChildText( "apiUrl" );
		final String nodeId = elem.getChildText( "nodeId" );
		final String dataInstanceId = elem.getChildText( "dataInstanceId" );
		try
		{
			return new DvidLabels64ImageLoader( apiUrl, nodeId, dataInstanceId );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
