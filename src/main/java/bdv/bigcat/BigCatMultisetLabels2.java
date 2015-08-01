package bdv.bigcat;

import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import mpicbg.spim.data.generic.sequence.BasicViewSetup;
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
import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector;
import bdv.export.ProgressWriterConsole;
import bdv.img.cache.Cache;
import bdv.img.dvid.DvidGrayscale8ImageLoader;
import bdv.labels.labelset.ARGBConvertedLabelsSource;
import bdv.labels.labelset.DvidLabels64MultisetSetupImageLoader;
import bdv.labels.labelset.VolatileSuperVoxelMultisetType;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.render.AccumulateProjectorFactory;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class BigCatMultisetLabels2
{
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			final DvidGrayscale8ImageLoader dvidGrayscale8ImageLoader = new DvidGrayscale8ImageLoader(
					"http://emrecon100.janelia.priv/api",
					"2a3fd320aef011e4b0ce18037320227c",
					"grayscale" );
			final DvidLabels64MultisetSetupImageLoader dvidLabelsMultisetImageLoader = new DvidLabels64MultisetSetupImageLoader(
					1,
					"http://emrecon100.janelia.priv/api",
					"2a3fd320aef011e4b0ce18037320227c",
					"bodies" );
//			final ARGBConvertedLabelsSetupImageLoader dvidLabelsARGBImageLoader = new ARGBConvertedLabelsSetupImageLoader(
//					2,
//					dvidLabelsMultisetImageLoader );

			final ARGBConvertedLabelsSource convertedLabels = new ARGBConvertedLabelsSource( 2, dvidLabelsMultisetImageLoader );

			final CombinedImgLoader imgLoader = new CombinedImgLoader(
//					setupIdAndLoader( 0, dvidGrayscale8ImageLoader ),
//					setupIdAndLoader( 2, dvidLabelsARGBImageLoader ) );
					setupIdAndLoader( 0, dvidGrayscale8ImageLoader ) );
			dvidGrayscale8ImageLoader.setCache( imgLoader.cache );
			dvidLabelsMultisetImageLoader.setCache( imgLoader.cache );
//			dvidLabelsARGBImageLoader.setCache( imgLoader.cache );

			final TimePoints timepoints = new TimePoints( Arrays.asList( new TimePoint( 0 ) ) );
			final Map< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
			setups.put( 0, new BasicViewSetup( 0, null, null, null ) );
//			setups.put( 2, new BasicViewSetup( 2, null, null, null ) );
			final ViewRegistrations reg = new ViewRegistrations( Arrays.asList(
//					new ViewRegistration( 0, 0 ),
//					new ViewRegistration( 0, 2 ) ) );
					new ViewRegistration( 0, 0 ) ) );

			final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
			final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

			final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
			final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

			BigDataViewer.initSetups( spimData, converterSetups, sources );

			final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final ScaledARGBConverter.VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

			final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter< VolatileARGBType >( convertedLabels, vconverter );
			final SourceAndConverter< ARGBType > soc = new SourceAndConverter< ARGBType >( convertedLabels.nonVolatile(), converter, vsoc );
			sources.add( soc );
			converterSetups.add( new RealARGBColorConverterSetup( 2, converter, vconverter ) );





			/* composites */
			final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite<ARGBType,ARGBType> >();
			composites.add( new CompositeCopy< ARGBType >() );
			composites.add( new ARGBCompositeAlphaYCbCr() );
			final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			sourceCompositesMap.put( sources.get( 1 ).getSpimSource(), composites.get( 1 ) );
			final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();
			final String windowTitle = "bigcat";
			final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), cache, windowTitle, null,
					ViewerOptions.options()
						.accumulateProjectorFactory( projectorFactory ) );

			final AffineTransform3D transform = new AffineTransform3D();
//			transform.set(
//					4.3135842398185575, -1.0275561336713027E-16, 1.1102230246251565E-16, -14207.918453952327,
//					-1.141729037412541E-17, 4.313584239818558, 1.0275561336713028E-16, -9482.518144778587,
//					1.1102230246251565E-16, -1.141729037412541E-17, 4.313584239818559, -17181.48737890195 );
//		    transform.set(
//		    		3.2188629744417074, -7.667782078539283E-17, 8.284655146757744E-17, -11182.72490198403,
//		    		-8.519757865043506E-18, 3.2188629744417074, 7.667782078539283E-17, -12970.526605903613,
//		    		8.284655146757744E-17, -8.519757865043506E-18, 3.2188629744417074, -12915.433001086165 );
			transform.set(
					30.367584357121462, -7.233983582120427E-16, 7.815957561302E-16, -103163.46077512865,
					-8.037759535689243E-17, 30.367584357121462, 7.233983582120427E-16, -68518.45769918368,
					7.815957561302E-16, -8.037759535689243E-17, 30.36758435712147, -120957.47720498207 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			bdv.getViewerFrame().setVisible( true );

			bdv.getViewer().getDisplay().addHandler(
					new MergeModeMouseListener(
							bdv.getViewer(),
							Views.interpolate(
									Views.extendValue(
											dvidLabelsMultisetImageLoader.getVolatileImage( 0, 0 ),
											new VolatileSuperVoxelMultisetType() ),
									new NearestNeighborInterpolatorFactory< VolatileSuperVoxelMultisetType >() )
							 ) );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	public static void main_OLD( final String[] args )
	{
		final String fn = "src/main/resources/dvid-flyem-graytiles.xml";
//		final String fn = "src/main/resources/dvid-flyem-grayscale.xml";
//		final String fn = "src/main/resources/dvid-flyem-bodies.xml";
//		final String fn = "src/main/resources/dvid-flyem-superpixels.xml";
		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );
			final BigDataViewer bdv = BigDataViewer.open( fn, new File( fn ).getName(), new ProgressWriterConsole(), ViewerOptions.options() );
			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					4.3135842398185575, -1.0275561336713027E-16, 1.1102230246251565E-16, -14207.918453952327,
					-1.141729037412541E-17, 4.313584239818558, 1.0275561336713028E-16, -9482.518144778587,
					1.1102230246251565E-16, -1.141729037412541E-17, 4.313584239818559, -17181.48737890195 );
			bdv.getViewer().setCurrentViewerTransform( transform );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
