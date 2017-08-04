package bdv.img.dvid;

import java.io.File;

import org.jdom2.Element;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

@ImgLoaderIo( format = "dvid-grayscale8", type = Uint8blkImageLoader.class )
public class XmlIoDvidGrayscale8ImageLoader
		implements XmlIoBasicImgLoader< Uint8blkImageLoader >
{
	@Override
	public Element toXml( final Uint8blkImageLoader imgLoader, final File basePath )
	{
		throw new UnsupportedOperationException( "not implemented" );
	}

	@Override
	public Uint8blkImageLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final String apiUrl = elem.getChildText( "apiUrl" );
		final String nodeId = elem.getChildText( "nodeId" );
		final String dataInstanceId = elem.getChildText( "dataInstanceId" );
		try
		{
			return new Uint8blkImageLoader( apiUrl, nodeId, dataInstanceId );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
