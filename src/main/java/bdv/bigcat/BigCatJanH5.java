package bdv.bigcat;
import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector;
import bdv.bigcat.control.ClientController;
import bdv.bigcat.control.Message;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.img.cache.Cache;
import bdv.img.janh5.JanH5FloatSetupImageLoader;
import bdv.img.janh5.JanH5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.render.AccumulateProjectorFactory;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.view.Views;

public class BigCatJanH5
{
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			final IHDF5Reader reader = HDF5Factory.openForReading( "/groups/saalfeld/saalfeldlab/pedunculus/davi_v7_4k_refix_export.h5" );

			/* raw pixels */
			final JanH5FloatSetupImageLoader raw = new JanH5FloatSetupImageLoader(
					reader,
					"/raw",
					0,
					new int[]{64, 64, 8} );

			final CombinedImgLoader imgLoader = new CombinedImgLoader(
					setupIdAndLoader( 0, raw ) );
			raw.setCache( imgLoader.cache );

			final TimePoints timepoints = new TimePoints( Arrays.asList( new TimePoint( 0 ) ) );
			final Map< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
			setups.put( 0, new BasicViewSetup( 0, null, null, null ) );
			final ViewRegistrations reg = new ViewRegistrations( Arrays.asList( new ViewRegistration( 0, 0 ) ) );
			final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
			final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

			final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
			final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

			BigDataViewer.initSetups( spimData, converterSetups, sources );



			/* fragments */
			final JanH5LabelMultisetSetupImageLoader fragments = new JanH5LabelMultisetSetupImageLoader(
					reader,
					"/bodies",
					1,
					new int[]{64, 64, 8} );

			fragments.setCache( imgLoader.cache );

			final FragmentSegmentAssignment assignment = new FragmentSegmentAssignment();
			final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream( assignment );
//			final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
			colorStream.setAlpha( 0x30 );
			final ARGBConvertedLabelsSource convertedFragments =
					new ARGBConvertedLabelsSource(
							2,
							fragments,
							colorStream );

			final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final ScaledARGBConverter.VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

			final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter< VolatileARGBType >( convertedFragments, vconverter );
			final SourceAndConverter< ARGBType > soc = new SourceAndConverter< ARGBType >( convertedFragments.nonVolatile(), converter, vsoc );
			sources.add( soc );

			final RealARGBColorConverterSetup fragmentsConverterSetup = new RealARGBColorConverterSetup( 2, converter, vconverter );
			converterSetups.add( fragmentsConverterSetup );

			/* composites */
			final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite<ARGBType,ARGBType> >();
			composites.add( new CompositeCopy< ARGBType >() );
			composites.add( new ARGBCompositeAlphaYCbCr() );
//			composites.add( new ARGBCompositeAlphaMultiply() );
//			composites.add( new ARGBCompositeAlphaAdd() );
//			composites.add( new ARGBCompositeAlpha() );
			final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			sourceCompositesMap.put( sources.get( 1 ).getSpimSource(), composites.get( 1 ) );
			final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();
			final String windowTitle = "bigcat";
			final BigDataViewer bdv = new BigDataViewer(
					converterSetups,
					sources,
					null,
					timepoints.size(),
					cache,
					windowTitle,
					null,
					ViewerOptions.options()
						.accumulateProjectorFactory( projectorFactory )
						.numRenderingThreads( 16 ) );


			final AffineTransform3D transform = new AffineTransform3D();
//			transform.set(
//					4.3135842398185575, -1.0275561336713027E-16, 1.1102230246251565E-16, -14207.918453952327,
//					-1.141729037412541E-17, 4.313584239818558, 1.0275561336713028E-16, -9482.518144778587,
//					1.1102230246251565E-16, -1.141729037412541E-17, 4.313584239818559, -17181.48737890195 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			/* separate source min max */
			bdv.getSetupAssignments().removeSetupFromGroup(
					fragmentsConverterSetup,
					bdv.getSetupAssignments().getMinMaxGroups().get( 0 ) );

			converterSetups.get( 0 ).setDisplayRange( 0, 1 );
			fragmentsConverterSetup.setDisplayRange( 0, 255 );


			bdv.getViewerFrame().setVisible( true );

//			bdv.getViewer().getDisplay().addHandler(
//					new MergeModeController(
//							bdv.getViewer(),
//							RealViews.affineReal(
//								Views.interpolate(
//										Views.extendValue(
//												fragments.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
//												new VolatileLabelMultisetType() ),
//										new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
//								fragments.getMipmapTransforms()[ 0 ] ),
//							colorStream,
//							assignment ) );

			final ZContext ctx = new ZContext();
			final Socket socket = ctx.createSocket( ZMQ.REQ );
			socket.connect( "tcp://10.103.40.190:8128" );

			final ClientController controller = new ClientController(
					bdv.getViewer(),
					Views.interpolate(
							Views.extendValue(
									fragments.getVolatileImage( 0, 0, ImgLoaderHints.LOAD_COMPLETELY ),
									new VolatileLabelMultisetType() ),
							new NearestNeighborInterpolatorFactory< VolatileLabelMultisetType >() ),
					colorStream,
					assignment,
					socket );

			bdv.getViewer().getDisplay().addHandler( controller );

			controller.sendMessage( new Message() );

		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
