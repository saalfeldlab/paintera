package bdv.bigcat.viewer;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.sun.javafx.application.PlatformImpl;

import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.util.volatiles.SharedQueue;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.view.Views;

public class ExampleApplicationSampleE
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static void main( final String[] args ) throws Exception
	{
		PlatformImpl.startup( () -> {} );

		final String USER_HOME = System.getProperty( "user.home" );

		String n5Path = "/nrs/saalfeld/sample_E/sample_E.n5";
		String rawGroup = "/volumes/raw";
		String labelsN5Path = "/groups/saalfeld/saalfeldlab/sampleE/multicut_segmentation.n5";
		String labelsDataset = "/multicut";

		double[] resolution = { 4, 4, 40 };

		final Parameters params = getParameters( args );
		if ( params != null )
		{
			LOG.info( "parameters are not null" );
			n5Path = params.filePath;
			rawGroup = params.rawDatasetPath;
			labelsN5Path = n5Path;
			labelsDataset = params.labelDatasetPath;
			resolution = new double[] { params.resolution.get( 0 ), params.resolution.get( 1 ), params.resolution.get( 2 ) };

			System.out.println( Arrays.toString( resolution ) );
		}

		final int numPriorities = 20;
		final SharedQueue sharedQueue = new SharedQueue( 12, 20 );

		// TODO remove
		final VolatileGlobalCellCache cellCache = new VolatileGlobalCellCache( 1, 12 );

		final RandomAccessibleIntervalDataSource< UnsignedByteType, VolatileUnsignedByteType > rawSource =
				DataSource.createN5MipmapSource( "raw", new N5FSReader( n5Path ), rawGroup, resolution, sharedQueue, UnsignedByteType::new, VolatileUnsignedByteType::new );

		final LabelDataSource labelSource =
				LabelDataSource.createLabelSource(
						"labels",
						( RandomAccessibleInterval )N5Utils.openVolatile(
								new N5FSReader( labelsN5Path ),
								labelsDataset ),
						resolution,
						sharedQueue,
						0,
						new FragmentSegmentAssignmentOnlyLocal() );

		final IdService idService = new LocalIdService();

		final Atlas viewer = new Atlas( sharedQueue );



		final CountDownLatch latch = new CountDownLatch( 3 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}

			viewer.addRawSource( rawSource, 0., ( 1 << 8 ) - 1. );
			latch.countDown();

			viewer.addLabelSource( labelSource, labelSource.getAssignment(), v -> v.get().getIntegerLong(), idService );
			latch.countDown();

			stage.show();
			latch.countDown();
		} );

		latch.await();
	}

	private static Parameters getParameters( final String[] args )
	{
		// get the parameters
		final Parameters params = new Parameters();
		JCommander.newBuilder()
				.addObject( params )
				.build()
				.parse( args );

		final boolean success = validateParameters( params );
		if ( !success )
			return null;

		return params;
	}

	private static boolean validateParameters( final Parameters params )
	{
		return params.filePath != "";
	}

	public static class LabelIntersectionCellLoader implements CellLoader<UnsignedLongType> {

		private final RandomAccessible<UnsignedLongType> label1;
		private final RandomAccessible<UnsignedLongType> label2;

		public LabelIntersectionCellLoader(
				final RandomAccessible<UnsignedLongType> label1,
				final RandomAccessible<UnsignedLongType> label2) {

			this.label1 = label1;
			this.label2 = label2;
		}

		@Override
		public void load(final SingleCellArrayImg<UnsignedLongType, ?> cell) throws Exception {

			final Cursor<UnsignedLongType> label1Cursor = Views.flatIterable(Views.interval(label1, cell)).localizingCursor();
			final Cursor<UnsignedLongType> label2Cursor = Views.flatIterable(Views.interval(label2, cell)).localizingCursor();

//			while (label1Cursor.hasNext())

//			FloodFill.fill(
//					source.getDataSource( time, level ),
//					accessTracker,
//					seed,
//					new UnsignedByteType( 1 ),
//					new DiamondShape( 1 ),
//					makeFilter() );
		}

	}
}
