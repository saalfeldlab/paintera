package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.composite.CompositeProjector;
import bdv.export.ProgressWriterConsole;
import bdv.img.cache.Cache;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.catmaid.CatmaidImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
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
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

public class BigMovie
{
	static class CombinedImgLoader implements ViewerImgLoader
	{
		private final HashMap< Integer, ViewerSetupImgLoader< ?, ? > > setupImgLoaders;

		private final VolatileGlobalCellCache cache;

		public CombinedImgLoader( final ViewerSetupImgLoader< ?, ? >... loaders )
		{
			setupImgLoaders = new HashMap< Integer, ViewerSetupImgLoader< ?, ? > >();
			int maxNumLevels = 1;
			int setupIds = 0;
			for ( final ViewerSetupImgLoader< ?, ? > loader : loaders )
			{
				maxNumLevels = Math.max( maxNumLevels, loader.numMipmapLevels() );
				final int setupId = setupIds++; // TODO: = loader.getSetupId();
				setupImgLoaders.put( setupId, loader );
			}

			cache = new VolatileGlobalCellCache( 1, loaders.length, maxNumLevels, 10 );
		}

		@Override
		public Cache getCache()
		{
			return cache;
		}

		@Override
		public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
		{
			return setupImgLoaders.get( setupId );
		}
	};


	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			final CatmaidImageLoader imgLoader = new CatmaidImageLoader(
//					272056,
//					132596,
//					5849,
//					10,
//					
//					"http://bock-s6.janelia.priv/view/flytiles_tier2/rendered_boxes/FAFB00/v9_align_tps/2048x2048/%1$d/%5$d/%8$d/%9$d.jpg",
//					2048,
//					2048,
					
					3000,
					3000,
					4385,
					10,
					
					"https://neuropil.janelia.org/public/150625_segmentation_samples/sample_A/cutout_3k/%1$d/%5$d/%8$d/%9$d.jpg",
					2048,
					2048,
					
//					"http://bock-s6.janelia.priv/view/flytiles_tier2/rendered_boxes/FAFB00/v7_align_tps/2048x2048/%1$d/%5$d/%8$d/%9$d.png",
//					2048,
//					2048,

//					"https://bocklab.hhmi.org/data/fafb/v7/disk0/v7_align_tps/%1$d/%5$d/%8$d/%9$d.jpg",
//					1024,
//					1024,

//					new int[][]{
//							{ 256, 256, 26 },
//							{ 128, 128, 26 },
//							{ 64, 64, 26 },
//							{ 64, 64, 51 },
//							{ 64, 64, 51 },
//							{ 64, 64, 68 },
//							{ 64, 64, 68 },
//							{ 64, 64, 63 },
//							{ 64, 64, 63 } } );
					new int[][]{
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 },
							{ 256, 256, 8 } },
					true );

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

			/* composites */
			final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite<ARGBType,ARGBType> >();
			composites.add( new CompositeCopy< ARGBType >() );
			final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();

			final ViewerOptions options = ViewerOptions.options();
			options.width( 1280 );
			options.height( 720 );
//			options.useVolatileIfAvailable( false );
			final String windowTitle = "bigcat";
			final BigDataViewer bdv = new BigDataViewer(
					converterSetups,
					sources,
					null,
					timepoints.size(),
					cache,
					windowTitle,
					null,
					options.accumulateProjectorFactory( projectorFactory ) );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					1, 0, 0, -136027.5,
					0, 1, 0, -66297.5,
					0, 0, 1, -2875 * 10 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			bdv.getViewerFrame().setVisible( true );
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
