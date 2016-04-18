package bdv.bigcat;

import java.io.IOException;
import java.util.ArrayList;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.AbstractH5SetupImageLoader;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;

public class BigCATJuan
{
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		Util.initUI();

		System.out.println( "Opening " + args[ 0 ] );
		final IHDF5Reader reader = HDF5Factory.openForReading( args[ 0 ] );

		/* raw pixels */
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, "/raw", 0, new int[] { 64, 64, 8 } );

		/* fragments */
		final H5LabelMultisetSetupImageLoader fragments = new H5LabelMultisetSetupImageLoader( reader, null, "/labels", 1, new int[] { 64, 64, 8 } );

		/* converters and controls */
		final FragmentSegmentAssignment assignment = new FragmentSegmentAssignment();
		final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream( assignment );
//			final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x30 );
		final ARGBConvertedLabelsSource convertedFragments = new ARGBConvertedLabelsSource( 2, fragments, colorStream );

		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
		composites.add( new CompositeCopy< ARGBType >() );
		composites.add( new ARGBCompositeAlphaYCbCr() );

		final String windowTitle = "BigCAT";

		final BigDataViewer bdv = BigCat.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
				new ARGBConvertedLabelsSource[]{ convertedFragments },
				new SetCache[]{ fragments },
				composites );

		bdv.getViewerFrame().setVisible( true );

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

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();
		bindings.addBehaviourMap( "bigcat", mergeController.getBehaviourMap() );
		bindings.addInputTriggerMap( "bigcat", mergeController.getInputTriggerMap() );
		
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
