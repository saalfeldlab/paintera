package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.BigDataViewer;
import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.AnnotationController;
import bdv.bigcat.control.MergeController;
import bdv.bigcat.control.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
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
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class BigCatAnnotations
{
	final static private int[] cellDimensions = new int[]{ 64, 64, 8 };
	
	static private H5LabelMultisetSetupImageLoader fragments = null;
	static private ARGBConvertedLabelPairSource convertedLabelPair = null;
	static private CellImg< LongType, ?, ? > paintedLabels = null;
	static private BigDataViewer bdv;
	static private GoldenAngleSaturatedARGBStream colorStream;
	static private FragmentSegmentAssignment assignment;
	static private String projectFile;

	public static void main( final String[] args ) throws Exception
	{
		Util.initUI();
		
		projectFile = args[0];

		System.out.println( "Opening " + projectFile );
		final IHDF5Reader reader = HDF5Factory.open( projectFile );

		/* raw pixels */
		// support both file_format 0.0 and >=0.1
		final String rawDataset = reader.exists("/volumes/raw") ? "/volumes/raw"  : "/raw";
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, rawDataset, 0, cellDimensions );

		/* fragments */
		final String backgroundLabelsDataset = "/volumes/labels/neuron_ids";
		final String mergedLabelsDataset = "/volumes/labels/merged_neuron_ids";
		final String paintedLabelsDataset = "/volumes/labels/painted_neuron_ids";
		final String labelsDataset = reader.exists( mergedLabelsDataset ) ? mergedLabelsDataset : backgroundLabelsDataset;
		if (reader.exists(labelsDataset))
			readFragments(args, reader, labelsDataset, paintedLabelsDataset);

		setupBdv(raw);
	}

	private static void readFragments(final String[] args, final IHDF5Reader reader, final String labelsDataset, String paintedLabelsDataset)
			throws IOException {

		fragments =
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

		/* pair labels */
		final RandomAccessiblePair< VolatileLabelMultisetType, LongType > labelPair =
				new RandomAccessiblePair<>(
						fragments.getVolatileImage( 0, 0 ),
						paintedLabels );

		assignment = new FragmentSegmentAssignment();
		colorStream = new GoldenAngleSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x30 );
		convertedLabelPair =
				new ARGBConvertedLabelPairSource(
						3,
						labelPair,
						paintedLabels, // as Interval, used just for the size
						fragments.getMipmapTransforms(),
						colorStream );
	}

	private static void setupBdv(final H5UnsignedByteSetupImageLoader raw) throws Exception {
		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
		composites.add( new CompositeCopy< ARGBType >() );

		final String windowTitle = "BigCAT";

		if (fragments != null) {

			composites.add( new ARGBCompositeAlphaYCbCr() );
			bdv = BigCat.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
				new ARGBConvertedLabelPairSource[]{ convertedLabelPair },
				new SetCache[]{ fragments },
				composites );
		} else {

			bdv = BigCat.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
				new ARGBConvertedLabelPairSource[]{ },
				new SetCache[]{ },
				composites );
		}

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set( 0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0 );
		bdv.getViewer().setCurrentViewerTransform( transform );

		bdv.getViewerFrame().setVisible( true );

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

		if (fragments != null) {

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

			final SelectionController selectionController = new SelectionController(
					bdv.getViewer(),
					colorStream,
					new InputTriggerConfig(),
					bdv.getViewerFrame().getKeybindings(),
					new InputTriggerConfig() );

			final MergeController mergeController = new MergeController(
					bdv.getViewer(),
					idPicker2,
					selectionController,
					assignment,
					new InputTriggerConfig(),
					bdv.getViewerFrame().getKeybindings(),
					new InputTriggerConfig() );
			
			final TranslateZController translateZController = new TranslateZController(
					bdv.getViewer(),
					raw.getMipmapResolutions()[0],
					new  InputTriggerConfig() );

			bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );
			bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );
		}

		final AnnotationsHdf5Store annotationsStore = new AnnotationsHdf5Store(projectFile);
		final AnnotationController annotationController = new AnnotationController(
				annotationsStore,
				bdv.getViewer(),
				new InputTriggerConfig(),
				bdv.getViewerFrame().getKeybindings(),
				new InputTriggerConfig() );

		bindings.addBehaviourMap( "annotation", annotationController.getBehaviourMap() );
		bindings.addInputTriggerMap( "annotation", annotationController.getInputTriggerMap() );

		bdv.getViewer().getDisplay().addOverlayRenderer( annotationController.getAnnotationOverlay() );
	}
}
