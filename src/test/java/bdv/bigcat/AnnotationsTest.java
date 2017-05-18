package bdv.bigcat;

import static org.junit.Assert.assertEquals;

import java.text.Annotation;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;
import bdv.bigcat.BigCat;
import bdv.bigcat.BigCat.Parameters;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.annotation.PostSynapticSite;
import bdv.bigcat.annotation.PreSynapticSite;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import net.imglib2.RealPoint;

public class AnnotationsTest
{
	static Annotations annotations = null;
	static IdService idService = new LocalIdService();

	@BeforeClass
	public static void loadData() throws Exception
	{

		// hdf file to use on test
		final Parameters params = new Parameters();
		params.inFile = "data/sample_B_20160708_frags_46_50_annotations.hdf";
		params.init();

		// Start the visualization
		final BigCat< Parameters > bigCat = new BigCat<>();
		bigCat.init( params );
		bigCat.setupBdv( params );
		AnnotationsHdf5Store annotationStore = new AnnotationsHdf5Store( params.inFile, idService );
		
		annotations = annotationStore.read();
	}

	@Test
	public void createAnnotations() throws Exception
	{
		double[] prePoint = {532, 1799,0};
		double[] postPoint = {749,1835,0};
		final RealPoint prePosition = new RealPoint(prePoint);
		final RealPoint postPosition = new RealPoint(postPoint);
		
		final int numAnnotations = annotations.getAnnotations().size();
		
		PreSynapticSite pre = new PreSynapticSite( idService.next(), prePosition, "" );
		PostSynapticSite post = new PostSynapticSite( idService.next(), postPosition, "" );
		pre.setPartner( post );
		post.setPartner( pre );
		
		annotations.add( pre );
		annotations.add( post );
		
		assertEquals("The number of annotations must increase in two units", numAnnotations+2, annotations.getAnnotations().size());
	}

	@Test
	public void showAnnotations() throws Exception
	{
	}

	@Test
	public void hideAnnotations() throws Exception
	{
	}

	@Test
	public void removeAnnotations() throws Exception
	{
		double[] prePoint = {532, 1799,0};
		double[] postPoint = {749,1835,0};
		final RealPoint prePosition = new RealPoint(prePoint);
		final RealPoint postPosition = new RealPoint(postPoint);
		
		final int numAnnotations = annotations.getAnnotations().size();
		
		PreSynapticSite pre = new PreSynapticSite( idService.next(), prePosition, "" );
		PostSynapticSite post = new PostSynapticSite( idService.next(), postPosition, "" );
		pre.setPartner( post );
		post.setPartner( pre );
		
		annotations.add( pre );
		annotations.add( post );
		
		assertEquals("The number of annotations must increase in two units", numAnnotations+2, annotations.getAnnotations().size());
	}

}
