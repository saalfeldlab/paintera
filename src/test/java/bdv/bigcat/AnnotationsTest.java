package bdv.bigcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import bdv.bigcat.BigCat.Parameters;
import bdv.bigcat.annotation.Annotation;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.annotation.PostSynapticSite;
import bdv.bigcat.annotation.PreSynapticSite;
import bdv.bigcat.annotation.Synapse;
import bdv.bigcat.control.AnnotationsController;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import net.imglib2.RealPoint;

public class AnnotationsTest
{
	static Annotations annotations = null;

	static AnnotationsController controller = null;

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
		controller = bigCat.annotationsController;
	}

	@Test
	public void createAnnotations() throws Exception
	{
		double[] prePoint = { 532, 1799, 0 };
		double[] postPoint = { 749, 1835, 0 };
		final RealPoint prePosition = new RealPoint( prePoint );
		final RealPoint postPosition = new RealPoint( postPoint );

		final int numAnnotations = annotations.getAnnotations().size();

		PreSynapticSite pre = new PreSynapticSite( idService.next(), prePosition, "" );
		PostSynapticSite post = new PostSynapticSite( idService.next(), postPosition, "" );
		pre.setPartner( post );
		post.setPartner( pre );

		annotations.add( pre );
		annotations.add( post );

		assertEquals( "The number of annotations must increase in two units", numAnnotations + 2, annotations.getAnnotations().size() );
	}

	@Test
	public void removeAnnotation() throws Exception
	{
		final int numAnnotations = annotations.getAnnotations().size();

		double[] point = { 618, 1528, 0 };
		final RealPoint position = new RealPoint( point );
		final List< Annotation > closest = annotations.getKNearest( position, 1 );

		for ( final Annotation a : closest )
		{
			annotations.remove( a );
		}

		assertEquals( "The number of annotations must decrease in one unit", numAnnotations - 1, annotations.getAnnotations().size() );
	}

	@Test
	public void showAnnotations() throws Exception
	{
		// default visibility is true
		// however the hideAnnotations test can be called before this one, and
		// then change the visibility
		boolean visibility = controller.getAnnotationOverlay().isVisible();

		Robot robot = new Robot();

		if ( visibility )
		{
			// hiding annotations
			robot.keyPress( KeyEvent.VK_O );
			robot.keyRelease( KeyEvent.VK_O );
			try
			{
				Thread.sleep( 50 );
			}
			catch ( InterruptedException e )
			{
				System.out.println( "sleep was interrupted" );
			}
		}

		// showing annotations
		robot.keyPress( KeyEvent.VK_O );
		robot.keyRelease( KeyEvent.VK_O );
		try
		{
			Thread.sleep( 50 );
		}
		catch ( InterruptedException e )
		{
			System.out.println( "sleep was interrupted" );
		}

		assertTrue( "The annotations must be visible", controller.getAnnotationOverlay().isVisible() );
	}

	@Test
	public void hideAnnotations() throws Exception
	{
		Robot robot = new Robot();
		robot.keyPress( KeyEvent.VK_O );
		robot.keyRelease( KeyEvent.VK_O );
		try
		{
			Thread.sleep( 50 );
		}
		catch ( InterruptedException e )
		{
			System.out.println( "sleep was interrupted" );
		}

		assertFalse( "The annotations must be invisible", controller.getAnnotationOverlay().isVisible() );
	}

	@Test
	public void addCommentAnnotation() throws Exception
	{
		final int numAnnotations = annotations.getAnnotations().size();

		double[] point = { 600, 1799, 0 };
		final RealPoint position = new RealPoint( point );

		Annotation annotation = new Synapse( idService.next(), position, "comment here" );
		annotations.add( annotation );

		assertEquals( "The number of annotations must increase in one unit", numAnnotations + 1, annotations.getAnnotations().size() );
	}

}
