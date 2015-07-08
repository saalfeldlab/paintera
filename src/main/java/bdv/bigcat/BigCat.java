package bdv.bigcat;

import java.io.File;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;

public class BigCat
{
	public static void main( final String[] args )
	{
//		final String fn = "http://tomancak-mac-17.mpi-cbg.de:8080/openspim/";
//		final String fn = "/Users/Pietzsch/Desktop/openspim/datasetHDF.xml";
//		final String fn = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String fn = "/Users/Pietzsch/Desktop/spimrec2/dataset.xml";
//		final String fn = "/Users/pietzsch/Desktop/HisYFP-SPIM/dataset.xml";
//		final String fn = "/Users/Pietzsch/Desktop/bdv example/drosophila 2.xml";
//		final String fn = "/Users/pietzsch/Desktop/data/clusterValia/140219-1/valia-140219-1.xml";
//		final String fn = "/Users/Pietzsch/Desktop/data/catmaid.xml";
//		final String fn = "src/main/resources/openconnectome-bock11-neariso.xml";
//		final String fn = "src/main/resources/dvid-flyem-graytiles.xml";
//		final String fn = "src/main/resources/dvid-flyem-grayscale.xml";
//		final String fn = "src/main/resources/dvid-flyem-bodies.xml";
		final String fn = "src/main/resources/dvid-flyem-superpixels.xml";
//		final String fn = "/Users/Pietzsch/Desktop/data/catmaid-confocal.xml";
//		final String fn = "/Users/pietzsch/desktop/data/BDV130418A325/BDV130418A325_NoTempReg.xml";
//		final String fn = "/Users/pietzsch/Desktop/data/valia2/valia.xml";
//		final String fn = "/Users/pietzsch/workspace/data/fast fly/111010_weber/combined.xml";
//		final String fn = "/Users/pietzsch/workspace/data/mette/mette.xml";
//		final String fn = "/Users/tobias/Desktop/openspim.xml";
//		final String fn = "/Users/pietzsch/Desktop/data/fibsem.xml";
//		final String fn = "/Users/pietzsch/Desktop/data/fibsem-remote.xml";
//		final String fn = "/Users/pietzsch/Desktop/url-valia.xml";
//		final String fn = "/Users/pietzsch/Desktop/data/clusterValia/140219-1/valia-140219-1.xml";
//		final String fn = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String fn = "/Volumes/projects/tomancak_lightsheet/Mette/ZeissZ1SPIM/Maritigrella/021013_McH2BsGFP_CAAX-mCherry/11-use/hdf5/021013_McH2BsGFP_CAAX-mCherry-11-use.xml";
		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );
			BigDataViewer.open( fn, new File( fn ).getName(), new ProgressWriterConsole() );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
