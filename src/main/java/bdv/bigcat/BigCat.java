package bdv.bigcat;

import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.http.HTTPException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.RandomSaturatedARGBStream;
import bdv.img.cache.Cache;
import bdv.img.dvid.DvidGrayscale8ImageLoader;
import bdv.labels.labelset.DvidLabels64MultisetSetupImageLoader;
import bdv.labels.labelset.VolatileSuperVoxelMultisetType;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.util.dvid.DatasetKeyValue;
import bdv.util.dvid.Repository;
import bdv.util.dvid.Server;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.render.AccumulateProjectorFactory;
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

public class BigCat
{
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		final String url = "http://vm570.int.janelia.org:8080";
		final String labelsBase = "multisets-labels-downscaled-zero-extended-2"; //"multisets-labels-downscaled";
		final String uuid = "4668221206e047648f622dc4690ff7dc";

		final Server server = new Server( url );
		final Repository repo = new Repository( server, uuid );

//		final DatasetKeyValue[] stores = new DatasetKeyValue[ 4 ];
		final DatasetKeyValue[] stores = new DatasetKeyValue[ 0 ];

		for ( int i = 0; i < stores.length; ++i )
		{
			stores[ i ] = new DatasetKeyValue( repo.getRootNode(), labelsBase + "-" + ( 1 << ( i + 1 ) ) );

			try
			{
				repo.getRootNode().createDataset( stores[ i ].getName(), DatasetKeyValue.TYPE );
			}
			catch ( final HTTPException e )
			{
				e.printStackTrace( System.err );
			}
		}

//		final double[][] resolutions = new double[][]{
//				{ 1, 1, 1 },
//				{ 2, 2, 2 },
//				{ 4, 4, 4 },
//				{ 8, 8, 8 },
//				{16, 16, 16 } };

		final double[][] resolutions = new double[][]{
				{ 1, 1, 1 } };

		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			/* data sources */
			final DvidGrayscale8ImageLoader dvidGrayscale8ImageLoader = new DvidGrayscale8ImageLoader(
					"http://emrecon100.janelia.priv/api",
					"2a3fd320aef011e4b0ce18037320227c",
					"grayscale" );
			final DvidLabels64MultisetSetupImageLoader dvidLabelsMultisetImageLoader = new DvidLabels64MultisetSetupImageLoader(
					1,
					"http://emrecon100.janelia.priv/api",
					"2a3fd320aef011e4b0ce18037320227c",
					"bodies",
					resolutions,
					stores );
//			final ARGBConvertedLabelsSetupImageLoader dvidLabelsARGBImageLoader = new ARGBConvertedLabelsSetupImageLoader(
//					2,
//					dvidLabelsMultisetImageLoader );

			final SegmentBodyAssignment assignment = new SegmentBodyAssignment();

//			final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream();
			final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
			colorStream.setAlpha( 0x30 );
			final ARGBConvertedLabelsSource convertedLabels =
					new ARGBConvertedLabelsSource(
							2,
							dvidLabelsMultisetImageLoader,
							colorStream );

			final CombinedImgLoader imgLoader = new CombinedImgLoader(
					setupIdAndLoader( 0, dvidGrayscale8ImageLoader ) );
			dvidGrayscale8ImageLoader.setCache( imgLoader.cache );
			dvidLabelsMultisetImageLoader.setCache( imgLoader.cache );

			final TimePoints timepoints = new TimePoints( Arrays.asList( new TimePoint( 0 ) ) );
			final Map< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
			setups.put( 0, new BasicViewSetup( 0, null, null, null ) );
			final ViewRegistrations reg = new ViewRegistrations( Arrays.asList(
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
//			composites.add( new ARGBCompositeAlpha() );
			final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			sourceCompositesMap.put( sources.get( 1 ).getSpimSource(), composites.get( 1 ) );
			final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();
			final String windowTitle = "bigcat";
			final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), cache, windowTitle, null,
					ViewerOptions.options()
						.accumulateProjectorFactory( projectorFactory )
						.numRenderingThreads( 16 ) );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					30.367584357121462, -7.233983582120427E-16, 7.815957561302E-16, -103163.46077512865,
					-8.037759535689243E-17, 30.367584357121462, 7.233983582120427E-16, -68518.45769918368,
					7.815957561302E-16, -8.037759535689243E-17, 30.36758435712147, -120957.47720498207 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			bdv.getViewerFrame().setSize( 1280 - 32, 720 - 28 - 32 );

			bdv.getViewerFrame().setVisible( true );

			bdv.getViewer().getDisplay().addHandler(
					new MergeModeController(
							bdv.getViewer(),
							Views.interpolate(
									Views.extendValue(
											dvidLabelsMultisetImageLoader.getVolatileImage( 0, 0 ),
											new VolatileSuperVoxelMultisetType() ),
									new NearestNeighborInterpolatorFactory< VolatileSuperVoxelMultisetType >() ),
							colorStream,
							assignment ) );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
