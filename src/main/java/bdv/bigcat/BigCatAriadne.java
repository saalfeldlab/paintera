package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.DrawProjectAndIntersectController;
import bdv.bigcat.control.LabelBrushController;
import bdv.bigcat.control.LabelFillController;
import bdv.bigcat.control.LabelPersistenceController;
import bdv.bigcat.control.LabelRestrictToSegmentController;
import bdv.bigcat.control.MergeController;
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.LabelMultiSetIdPicker;
import bdv.bigcat.label.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.bigcat.util.DirtyInterval;
import bdv.img.SetCache;
import bdv.img.h5.AbstractH5SetupImageLoader;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

public class BigCatAriadne
{
	final static private int[] cellDimensions = new int[]{ 8, 64, 64 };
	final static private double[] resolutions = new double[]{ 10, 1, 1 };
	final static private String rawDataset = "/em_raw";
	final static private String backgroundLabelsDataset = "/labels";
	final static private String paintedLabelsDataset = "/paintedLabels";
	final static private String mergedLabelsDataset = "/mergedLabels";
	final static private String fragmentSegmentLutDataset = "/fragment_segment_lut";
	final static private IdService idService = new LocalIdService();

	static protected InputTriggerConfig getInputTriggerConfig() throws IllegalArgumentException {

		final String[] filenames = {
				"bigcatkeyconfig.yaml",
				System.getProperty( "user.home" ) + "/.bdv/bigcatkeyconfig.yaml"
		};

		for (final String filename : filenames) {

			try {
				if (new File(filename).isFile()) {
					System.out.println("reading key config from file " + filename);
					return new InputTriggerConfig(YamlConfigIO.read(filename));
				}
			} catch (final IOException e) {
				System.err.println("Error reading " + filename);
			}
		}

		System.out.println("creating default input trigger config");

		// default input trigger config, disables "control button1" drag in bdv
		// (collides with default of "move annotation")
		final InputTriggerConfig config = new InputTriggerConfig(
				Arrays.asList(
						new InputTriggerDescription[] {
								new InputTriggerDescription( new String[] { "not mapped" }, "drag rotate slow", "bdv" )
						}
				)
		);

		return config;
	}

	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		Util.initUI();

		System.out.println( "Opening " + args[ 0 ] );
		final IHDF5Reader reader = HDF5Factory.open( args[ 0 ] );

		/* raw pixels */
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, rawDataset, 0, cellDimensions, resolutions );

		/* fragments */
		final String labelsDataset = reader.exists( mergedLabelsDataset ) ? mergedLabelsDataset : backgroundLabelsDataset;
		final H5LabelMultisetSetupImageLoader fragments =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelsDataset,
						1,
						cellDimensions,
						resolutions );
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
		final FragmentSegmentAssignment assignment = new FragmentSegmentAssignment( idService );
		final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream( assignment );
//		final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x20 );
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

		final ArrayList< SetCache > cacheLoaders = new ArrayList<>();
		cacheLoaders.add( raw );
		cacheLoaders.add( fragments );

		@SuppressWarnings( "unchecked" )
		final BigDataViewer bdv = Util.createViewer(
				windowTitle,
				Arrays.asList( new AbstractH5SetupImageLoader[]{ raw } ),
//				new ARGBConvertedLabelsSource[]{ convertedFragments },
				Arrays.asList( new ARGBConvertedLabelPairSource[]{ convertedLabelPair } ),
				cacheLoaders,
				composites,
				null );

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set( 0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0 );
		bdv.getViewer().setCurrentViewerTransform( transform );

		bdv.getViewerFrame().setVisible( true );

		final DirtyInterval dirtyLabelsInterval = new DirtyInterval();

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

		final LabelMultiSetIdPicker idPicker = new LabelMultiSetIdPicker(
				bdv.getViewer(),
				RealViews.affineReal(
						Views.interpolate(
								Views.extendValue(
										fragments.getImage( 0 ),
										new LabelMultisetType() ),
								new NearestNeighborInterpolatorFactory< LabelMultisetType >() ),
						fragments.getMipmapTransforms()[ 0 ] )
				);

		final PairLabelMultiSetLongIdPicker idPicker2 = new PairLabelMultiSetLongIdPicker(
				bdv.getViewer(),
				RealViews.affineReal(
						Views.interpolate(
								new RandomAccessiblePair< LabelMultisetType, LongType >(
										Views.extendValue(
											fragments.getImage( 0 ),
											new LabelMultisetType() ),
										Views.extendValue(
												paintedLabels,
												new LongType( Label.TRANSPARENT ) ) ),
								new NearestNeighborInterpolatorFactory< Pair< LabelMultisetType, LongType > >() ),
						fragments.getMipmapTransforms()[ 0 ] )
				);

		final InputTriggerConfig config = getInputTriggerConfig();

		final SelectionController selectionController = new SelectionController(
				bdv.getViewer(),
				idPicker2,
				colorStream,
				idService,
				assignment,
				config,
				bdv.getViewerFrame().getKeybindings(),
				config );

		final MergeController mergeController = new MergeController(
				bdv.getViewer(),
				idPicker2,
				selectionController,
				assignment,
				config,
				bdv.getViewerFrame().getKeybindings(),
				config );

		final TranslateZController translateZController = new TranslateZController(
				bdv.getViewer(),
				raw.getMipmapResolutions()[0],
				config );

		final LabelBrushController brushController = new LabelBrushController(
				bdv.getViewer(),
				paintedLabels,
				dirtyLabelsInterval,
				fragments.getMipmapTransforms()[ 0 ],
				assignment,
				selectionController,
				cellDimensions,
				config,
				0 );

		final LabelPersistenceController persistenceController = new LabelPersistenceController(
				bdv.getViewer(),
				fragments.getImage( 0 ),
				paintedLabels,
				fragments.getMipmapResolutions()[ 0 ],
				dirtyLabelsInterval,
				assignment,
				idService,
				paintedLabelsFilePath,
				paintedLabelsDataset,
				mergedLabelsDataset,
				cellDimensions,
				fragmentSegmentLutDataset,
				config,
				bdv.getViewerFrame().getKeybindings() );

		final LabelFillController fillController = new LabelFillController(
				bdv.getViewer(),
				fragments.getImage( 0 ),
				paintedLabels,
				dirtyLabelsInterval,
				fragments.getMipmapTransforms()[ 0 ],
				assignment,
				selectionController,
				new DiamondShape( 1 ),
				idPicker2,
				config );

		final LabelRestrictToSegmentController intersectController = new LabelRestrictToSegmentController(
				bdv.getViewer(),
				fragments.getImage( 0 ),
				paintedLabels,
				fragments.getMipmapTransforms()[ 0 ],
				assignment,
				selectionController,
				new DiamondShape( 1 ),
				config );

		final DrawProjectAndIntersectController dpi = new DrawProjectAndIntersectController(
				bdv,
				idService,
				transform,
				new InputTriggerConfig(),
				fragments.getImage(0),
				paintedLabels,
				dirtyLabelsInterval,
				fragments.getMipmapTransforms()[0],
				assignment,
				colorStream,
				selectionController,
				bdv.getViewerFrame().getKeybindings(),
				bindings,
				"shift S"
		);
//		Annotations annotations = new Annotations();
//		final AnnotationController annotationController = new AnnotationController(
//				bdv.getViewer(),
//				annotations,
//				new InputTriggerConfig(),
//				bdv.getViewerFrame().getKeybindings(),
//				new InputTriggerConfig() );

//		bindings.addBehaviourMap( "annotation", annotationController.getBehaviourMap() );
//		bindings.addInputTriggerMap( "annotation", annotationController.getInputTriggerMap() );

		bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
		bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );

		bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );

		bindings.addBehaviourMap( "brush", brushController.getBehaviourMap() );
		bindings.addInputTriggerMap( "brush", brushController.getInputTriggerMap() );

		bindings.addBehaviourMap( "fill", fillController.getBehaviourMap() );
		bindings.addInputTriggerMap( "fill", fillController.getInputTriggerMap() );

		bindings.addBehaviourMap( "restrict", intersectController.getBehaviourMap() );
		bindings.addInputTriggerMap( "restrict", intersectController.getInputTriggerMap() );

//		bdv.getViewer().getDisplay().addOverlayRenderer( annotationController.getAnnotationOverlay() );

		System.out.println( "inputTriggerMap: " );
		System.out.println( bindings.getConcatenatedInputTriggerMap().getAllBindings() );

		System.out.println( "behaviourMap: " );
		System.out.println( bindings.getConcatenatedBehaviourMap().getAllBindings() );


		bdv.getViewer().getDisplay().addOverlayRenderer( brushController.getBrushOverlay() );
//		bdv.getViewer().getDisplay().addOverlayRenderer( dpi.brushOverlay );


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

	}
}
