package bdv.bigcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

/**
 * Unit test for annotations
 * Covering: addition, removal and change in visibility of annotations
 * 
 * @author vleite
 */
public class AnnotationsTest
{
	/**
	 * Annotations in the project - presynaptic, postsynaptic and synapse
	 * annotations
	 */
	private static Annotations annotations;

	/** Controller of annotation overlay */
	private static AnnotationsController controller;

	/** Unique id for each annotation */
	private static IdService idService = new LocalIdService();

	/** Log */
	private static final Logger LOGGER = Logger.getLogger( AnnotationsTest.class.getName() );

	/**
	 * This method load the hdf file and the annotations. Initialize annotations
	 * and controller variables that will be used
	 */
	@BeforeClass
	public static void loadData()
	{

		// hdf file to use on test
		final Parameters params = new Parameters();
		params.inFile = "data/sample_B_20160708_frags_46_50_annotations.hdf";
		params.init();

		// Start the visualization
		BigCat< Parameters > bigCat;
		try
		{
			bigCat = new BigCat<>();
			bigCat.init( params );
			bigCat.setupBdv( params );
			controller = bigCat.annotationsController;
		}
		catch ( Exception e )
		{
			LOGGER.log( Level.SEVERE, "BigCat was not initialized successfully.", e );
		}

		final AnnotationsHdf5Store annotationStore = new AnnotationsHdf5Store( params.inFile, idService );

		try
		{
			annotations = annotationStore.read();
		}
		catch ( Exception e )
		{
			LOGGER.log( Level.SEVERE, "Couldn't read annotations from hdf file", e );
		}
	}

	/**
	 * This test uses two points to add a pre- and a postsynaptic annotation.
	 * Each one increases in one unity the number of annotations available
	 */
	@Test
	public void createAnnotations()
	{
		final double[] prePoint = { 532, 1799, 0 };
		final double[] postPoint = { 749, 1835, 0 };
		final RealPoint prePosition = new RealPoint( prePoint );
		final RealPoint postPosition = new RealPoint( postPoint );

		final int numAnnotations = annotations.getAnnotations().size();

		final PreSynapticSite pre = new PreSynapticSite( idService.next(), prePosition, "" );
		final PostSynapticSite post = new PostSynapticSite( idService.next(), postPosition, "" );
		pre.setPartner( post );
		post.setPartner( pre );

		annotations.add( pre );
		annotations.add( post );

		assertEquals( "The number of annotations must increase in two units", numAnnotations + 2, annotations.getAnnotations().size() );
	}

	/**
	 * This test removes one annotation (doesn't matter which type).
	 */
	@Test
	public void removeAnnotation()
	{
		final int numAnnotations = annotations.getAnnotations().size();
		final double[] point = { 618, 1528, 0 };
		final RealPoint position = new RealPoint( point );
		final List< Annotation > closest = annotations.getKNearest( position, 1 );

		for ( final Annotation a : closest )
		{
			annotations.remove( a );
		}

		assertEquals( "The number of annotations must decrease in one unit", numAnnotations - 1, annotations.getAnnotations().size() );
	}

	/**
	 * This test turns the annotation overlay visible. default visibility is
	 * true when the bigcat is initialized however once each test is
	 * independent, the hideAnnotations can be called before this one, and then
	 * change the visibility of the overlay.
	 */
	@Test
	public void showAnnotations()
	{
		final boolean visibility = controller.getAnnotationOverlay().isVisible();

		try
		{
			final Robot robot = new Robot();

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
					LOGGER.log( Level.SEVERE, "Thread sleep was interrupted", e );
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
				LOGGER.log( Level.SEVERE, "Thread sleep was interrupted", e );
			}
		}
		catch ( AWTException e )
		{
			LOGGER.log( Level.SEVERE, "Couldn't initialize robot", e );
		}

		assertTrue( "The annotations must be visible", controller.getAnnotationOverlay().isVisible() );
	}

	/**
	 * Test hiding the annotation overlay After this test the annotation will
	 * not be visible
	 */
	@Test
	public void hideAnnotations()
	{
		try
		{
			final Robot robot = new Robot();
			robot.keyPress( KeyEvent.VK_O );
			robot.keyRelease( KeyEvent.VK_O );
		}
		catch ( AWTException e )
		{
			LOGGER.log( Level.SEVERE, "Couldn't initialize robot", e );
		}
		try
		{
			Thread.sleep( 50 );
		}
		catch ( InterruptedException e )
		{
			LOGGER.log( Level.SEVERE, "Thread sleep was interrupted", e );
		}

		assertFalse( "The annotations must be invisible", controller.getAnnotationOverlay().isVisible() );
	}

	/**
	 * Add a syanpse annotation that increases the number of annotations by one.
	 */
	@Test
	public void addCommentAnnotation()
	{
		final int numAnnotations = annotations.getAnnotations().size();
		final double[] point = { 600, 1799, 0 };
		final RealPoint position = new RealPoint( point );

		final Annotation annotation = new Synapse( idService.next(), position, "comment here" );
		annotations.add( annotation );

		assertEquals( "The number of annotations must increase in one unit", numAnnotations + 1, annotations.getAnnotations().size() );
	}

}
