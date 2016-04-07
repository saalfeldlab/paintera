package bdv.bigcat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.LabelPaintController;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.AbstractH5SetupImageLoader;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.labelpair.RandomAccessiblePair;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class BigCATAriadne
{
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		Util.initUI();

		System.out.println( "Opening " + args[ 0 ] );
		final IHDF5Reader reader = HDF5Factory.openForReading( args[ 0 ] );

		/* raw pixels */
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, "/em_raw", 0, new int[] { 64, 64, 8 } );

		/* fragments */
		final H5LabelMultisetSetupImageLoader fragments = new H5LabelMultisetSetupImageLoader( reader, "/labels", 1, new int[] { 64, 64, 8 } );
		final RandomAccessibleInterval< VolatileLabelMultisetType > fragmentsPixels = fragments.getVolatileImage( 0, 0 );
		final long[] fragmentsDimensions = Intervals.dimensionsAsLongArray( fragmentsPixels );
		
		/* painted labels */
		final long[] paintedLabelsArray = new long[ ( int )Intervals.numElements( fragmentsPixels ) ];
		Arrays.fill( paintedLabelsArray, Long.MIN_VALUE );
		final ArrayImg< LongType, LongArray > paintedLabels = ArrayImgs.longs( paintedLabelsArray, fragmentsDimensions );
		
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
		
		final MergeController mergeController = new MergeController(
				bdv.getViewer(),
				RealViews.affineReal(
						Views.interpolate(
								Views.extendValue(
										fragments.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
										new VolatileLabelMultisetType() ),
								new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
						fragments.getMipmapTransforms()[ 0 ] ),
				colorStream,
				assignment,
				new InputTriggerConfig(),
				bdv.getViewerFrame().getKeybindings(),
				new InputTriggerConfig() );
		
		final LabelPaintController paintController = new LabelPaintController(
				bdv.getViewer(),
				paintedLabels,
				fragments.getMipmapTransforms()[ 0 ],
				colorStream,
				assignment,
				mergeController,
				new InputTriggerConfig() );
		
		bindings.addBehaviourMap( "paint", paintController.getBehaviourMap() );
		bindings.addInputTriggerMap( "paint", paintController.getInputTriggerMap() );
		
		bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
		bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );
		
		
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
