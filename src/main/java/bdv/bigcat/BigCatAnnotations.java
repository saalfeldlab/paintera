package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.annotation.AnnotationController;
import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.AbstractH5SetupImageLoader;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.img.labelpair.RandomAccessiblePair;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;

public class BigCatAnnotations
{
	final static private int[] cellDimensions = new int[]{ 8, 64, 64 };
	final static private String rawDataset = "/raw";
	final static private String backgroundLabelsDataset = "/labels";
	final static private String paintedLabelsDataset = "/paintedLabels";
	final static private String mergedLabelsDataset = "/mergedLabels";

	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		Util.initUI();

		System.out.println( "Opening " + args[ 0 ] );
		final IHDF5Reader reader = HDF5Factory.open( args[ 0 ] );

		/* raw pixels */
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, rawDataset, 0, cellDimensions );

		/* fragments */
		final String labelsDataset = reader.exists( mergedLabelsDataset ) ? mergedLabelsDataset : backgroundLabelsDataset;
		final H5LabelMultisetSetupImageLoader fragments =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelsDataset,
						1,
						cellDimensions );
		final RandomAccessibleInterval< VolatileLabelMultisetType > fragmentsPixels = fragments.getVolatileImage( 0, 0 );
		final long[] fragmentsDimensions = Intervals.dimensionsAsLongArray( fragmentsPixels );

//		// TODO does not work for uint64
//		long maxId = 0;
//		for (final LabelMultisetType t : Views.iterable(fragments.getImage(0))) {
//			for (final Multiset.Entry<SuperVoxel> v : t.entrySet()) {
//				long id = v.getElement().id();
//				if (id != PairVolatileLabelMultisetLongARGBConverter.TRANSPARENT_LABEL)
//					// TODO does not work for uint64
//					maxId = Math.max(maxId, id);
//			}
//		}
//		// TODO does not work for uint64
//		IdService.invalidate(0, maxId);

		/* painted labels */
//		final long[] paintedLabelsArray = new long[ ( int )Intervals.numElements( fragmentsPixels ) ];
//		Arrays.fill( paintedLabelsArray, PairVolatileLabelMultisetLongARGBConverter.TRANSPARENT_LABEL );
//		final ArrayImg< LongType, LongArray > paintedLabels = ArrayImgs.longs( paintedLabelsArray, fragmentsDimensions );

		final CellImg< LongType, ?, ? > paintedLabels;
		final String paintedLabelsFilePath = args[ 0 ];
		final File paintedLabelsFile = new File( paintedLabelsFilePath );
		if ( paintedLabelsFile.exists() && reader.exists( paintedLabelsDataset ) )
				paintedLabels = H5Utils.loadUnsignedLong( new File( paintedLabelsFilePath ), paintedLabelsDataset, cellDimensions );
		else
		{
			paintedLabels = new CellImgFactory< LongType >( cellDimensions ).create( fragmentsDimensions, new LongType() );
			for ( final LongType t : paintedLabels )
				t.set( Label.TRANSPARENT );
		}

//		H5Utils.saveUnsignedLong( paintedLabels, new File( args[ 0 ] + ".labels.h5" ), "paintedLabels", cellDimensions );

		/* pair labels */
		final RandomAccessiblePair< VolatileLabelMultisetType, LongType > labelPair =
				new RandomAccessiblePair<>(
						fragments.getVolatileImage( 0, 0 ),
						paintedLabels );

		/* converters and controls */
		final FragmentSegmentAssignment assignment = new FragmentSegmentAssignment();
		final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream( assignment );
//		final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x30 );
		//final ARGBConvertedLabelsSource convertedFragments = new ARGBConvertedLabelsSource( 2, fragments, colorStream );
		final ARGBConvertedLabelPairSource convertedLabelPair =
				new ARGBConvertedLabelPairSource(
						3,
						labelPair,
						paintedLabels, // as Interval, used just for the size
						fragments.getMipmapTransforms(),
						colorStream );

		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
		composites.add( new CompositeCopy< ARGBType >() );
		composites.add( new ARGBCompositeAlphaYCbCr() );

		final String windowTitle = "BigCAT";

		final BigDataViewer bdv = BigCat.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
//				new ARGBConvertedLabelsSource[]{ convertedFragments },
				new ARGBConvertedLabelPairSource[]{ convertedLabelPair },
				new SetCache[]{ fragments },
				composites );

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set( 0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0 );
		bdv.getViewer().setCurrentViewerTransform( transform );

		bdv.getViewerFrame().setVisible( true );

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

//		final MergeController mergeController = new MergeController(
//				bdv.getViewer(),
//				RealViews.affineReal(
//						Views.interpolate(
//								Views.extendValue(
//										fragments.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
//										new VolatileLabelMultisetType() ),
//								new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
//						fragments.getMipmapTransforms()[ 0 ] ),
//				colorStream,
//				assignment,
//				new InputTriggerConfig(),
//				bdv.getViewerFrame().getKeybindings(),
//				new InputTriggerConfig() );

//		final LabelBrushController brushController = new LabelBrushController(
//				bdv.getViewer(),
//				paintedLabels,
//				fragments.getMipmapTransforms()[ 0 ],
//				colorStream,
//				assignment,
//				mergeController,
//				paintedLabelsFilePath,
//				paintedLabelsDataset,
//				cellDimensions,
//				new InputTriggerConfig() );
//
//		final LabelPersistenceController persistenceController = new LabelPersistenceController(
//				bdv.getViewer(),
//				fragments.getImage( 0 ),
//				paintedLabels,
//				paintedLabelsFilePath,
//				paintedLabelsDataset,
//				mergedLabelsDataset,
//				cellDimensions,
//				new InputTriggerConfig(),
//				bdv.getViewerFrame().getKeybindings() );

		final AnnotationsHdf5Store annotationsStore = new AnnotationsHdf5Store(args[0], "/");
		final AnnotationController annotationController = new AnnotationController(
				annotationsStore,
				bdv.getViewer(),
				new InputTriggerConfig(),
				bdv.getViewerFrame().getKeybindings(),
				new InputTriggerConfig() );

		bindings.addBehaviourMap( "annotation", annotationController.getBehaviourMap() );
		bindings.addInputTriggerMap( "annotation", annotationController.getInputTriggerMap() );

//		bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
//		bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );

//		bindings.addBehaviourMap( "brush", brushController.getBehaviourMap() );
//		bindings.addInputTriggerMap( "brush", brushController.getInputTriggerMap() );

		bdv.getViewer().getDisplay().addOverlayRenderer( annotationController.getAnnotationOverlay() );

//		bdv.getViewer().getDisplay().addOverlayRenderer( brushController.getBrushOverlay() );


//			final ZContext ctx = new ZContext();
//			final Socket socket = ctx.createSocket( ZMQ.REQ );
//			socket.connect( "tcp://10.103.40.190:8128" );
//
//			final ClientController controller = new ClientController(
//					bdv.getViewer(),
//					Views.interpolate(
//							Views.extendValue(
//									fragments.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
//									new VolatileLabelMultisetType() ),
//							new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
//					colorStream,
//					assignment,
//					socket );

//			bdv.getViewer().getDisplay().addHandler( controller );

//			controller.sendMessage( new Message() );

		while ( true ) {

			annotationController.changeCommentOfCurrentSelection();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}
}
