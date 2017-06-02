package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.ConfirmSegmentController;
import bdv.bigcat.control.MergeController;
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.FragmentAssignment;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.LabelMultiSetIdPicker;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.ModalGoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.dvid.LabelblkMultisetSetupImageLoader;
import bdv.img.dvid.Uint8blkImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.LocalIdService;
import bdv.util.dvid.DatasetKeyValue;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;

public class BigCatDvidViewer extends BigCatViewer< BigCatDvidViewer.Parameters >
{
	static public class Parameters extends BigCatViewer.Parameters
	{
		@Parameter( names = { "--url" }, description = "URL" )
		public String url = "";

		@Parameter( names = { "--uuid" }, description = "UUID" )
		public String uuid = "";

		public Parameters()
		{
			raws = Arrays.asList( new String[] { "grayscale" } );
			labels = null;
			assignment = null;
		}

	}

	/** raw pixels (image data) */
	final protected ArrayList< Uint8blkImageLoader > raws = new ArrayList<>();

	/** loaded segments */
	final protected ArrayList< LabelblkMultisetSetupImageLoader > labels = new ArrayList<>();

	public static void main( final String[] args ) throws Exception
	{
		final Parameters params = new Parameters();
		new JCommander( params, args );
		params.init();
		final BigCatDvidViewer bigCat = new BigCatDvidViewer();
		bigCat.init( params );
		bigCat.setupBdv( params );
	}

	public BigCatDvidViewer() throws Exception
	{
		Util.initUI();
		this.config = getInputTriggerConfig();
	}


	/**
	 * Load raw data and labels and initialize canvas
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void initRaw( final Parameters params ) throws IOException
	{
		System.out.println( "Opening raw from " + params.url );

		/* raw pixels */
		for ( final String raw : params.raws )
		{
			final Uint8blkImageLoader rawLoader = new Uint8blkImageLoader(
					params.url,
					params.uuid,
					raw);

			raws.add( rawLoader );
		}
	}

	/**
	 * Initialize ID service.
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void initIdService( final Parameters params ) throws IOException
	{
		/* id */
		idService = new LocalIdService();
	}

	/**
	 * Initialize assignments.
	 *
	 * @param params
	 */
	@Override
	protected void initAssignments( final Parameters params )
	{
		/* fragment segment assignment */
		assignment = new FragmentSegmentAssignment( idService );

		/* complete fragments */
		completeFragmentsAssignment = new FragmentAssignment();

		/* color stream */
		colorStream = new ModalGoldenAngleSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x20 );
	}

	/**
	 * Load labels and create label+canvas compositions.
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void initLabels( final Parameters params ) throws IOException
	{
		if ( params.labels != null && params.labels.size() > 0 )
		{
			System.out.println( "Opening labels from " + params.url );

//			final Server server = new Server( params.url );
//			final Repository repo = new Repository( server, params.uuid );

			final double[][] resolutions = new double[][]{ { 1, 1, 1 } };

			/* labels */
			for ( final String label : params.labels )
			{
				/* labels */
//				final DatasetKeyValue datasetKeyValue = new DatasetKeyValue( repo.getRootNode(), label );
				final LabelblkMultisetSetupImageLoader labelLoader = new LabelblkMultisetSetupImageLoader(
						setupId++,
						params.url,
						params.uuid,
						label,
						resolutions,
//						new DatasetKeyValue[]{ datasetKeyValue } );
						new DatasetKeyValue[ 0 ] );

				/* converted labels */
				final ARGBConvertedLabelsSource convertedLabelsSource =
						new ARGBConvertedLabelsSource(
								setupId++,
								labelLoader,
								colorStream );

				labels.add( labelLoader );
				convertedLabels.add( convertedLabelsSource );
			}
		}
	}

	/**
	 * Create tool.
	 *
	 * Depends on {@link #raws}, {@link #labels},
	 * {@link #convertedLabelCanvasPairs}, {@link #colorStream},
	 * {@link #idService}, {@link #assignment},
	 * {@link #config}, {@link #dirtyLabelsInterval},
	 * {@link #completeFragmentsAssignment}, {@link #canvas} being initialized.
	 *
	 * Modifies {@link #bdv}, {@link #convertedLabelCanvasPairs},
	 * {@link #persistenceController},
	 *
	 * @param params
	 * @throws Exception
	 */
	@Override
	protected void setupBdv( final Parameters params ) throws Exception
	{
		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList<>();
		final ArrayList< SetCache > cacheLoaders = new ArrayList<>();
		for ( final Uint8blkImageLoader loader : raws )
		{
			composites.add( new CompositeCopy< ARGBType >() );
			cacheLoaders.add( loader );
		}
		for ( final LabelblkMultisetSetupImageLoader loader : labels )
		{
			composites.add( new ARGBCompositeAlphaYCbCr() );
			cacheLoaders.add( loader );
		}

		final String windowTitle = "BigCAT";

		bdv = Util.createViewer(
				windowTitle,
				raws,
				convertedLabels,
				cacheLoaders,
				composites,
				config );

		bdv.getViewerFrame().setVisible( true );

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

		final SelectionController selectionController;
		final LabelMultiSetIdPicker idPicker;

		if ( labels.size() > 0 )
		{
			/* TODO fix ID picker to pick from the top most label canvas pair */
			idPicker = new LabelMultiSetIdPicker(
					bdv.getViewer(),
					RealViews.affineReal(
							Views.interpolate(
									Views.extendValue(
											labels.get( 0 ).getImage( 0 ),
											new LabelMultisetType() ),
									new NearestNeighborInterpolatorFactory< LabelMultisetType >() ),
							labels.get( 0 ).getMipmapTransforms()[ 0 ] ) );

			selectionController = new SelectionController(
					bdv.getViewer(),
					idPicker,
					colorStream,
					idService,
					assignment,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config);

			final MergeController mergeController = new MergeController(
					bdv.getViewer(),
					idPicker,
					selectionController,
					assignment,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config);

			/* mark segments as finished */
			final ConfirmSegmentController confirmSegment = new ConfirmSegmentController(
					bdv.getViewer(),
					selectionController,
					assignment,
					colorStream,
					colorStream,
					config,
					bdv.getViewerFrame().getKeybindings() );

			bindings.addBehaviourMap( "select", selectionController.getBehaviourMap() );
			bindings.addInputTriggerMap( "select", selectionController.getInputTriggerMap() );

			bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );

		}
		else
		{
			selectionController = null;
			idPicker = null;
		}

		/* override navigator z-step size with raw[ 0 ] z resolution */
		final TranslateZController translateZController = new TranslateZController(
				bdv.getViewer(),
				raws.get( 0 ).getMipmapResolutions()[ 0 ],
				config );
		bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );

		if ( selectionController != null )
			bdv.getViewer().getDisplay().addOverlayRenderer( selectionController.getSelectionOverlay() );
	}

	static protected InputTriggerConfig getInputTriggerConfig() throws IllegalArgumentException
	{
		final String[] filenames = { "bigcatkeyconfig.yaml", System.getProperty( "user.home" ) + "/.bdv/bigcatkeyconfig.yaml" };

		for ( final String filename : filenames )
		{
			try
			{
				if ( new File( filename ).isFile() )
				{
					System.out.println( "reading key config from file " + filename );
					return new InputTriggerConfig( YamlConfigIO.read( filename ) );
				}
			}
			catch ( final IOException e )
			{
				System.err.println( "Error reading " + filename );
			}
		}

		System.out.println( "creating default input trigger config" );

		// default input trigger config, disables "control button1" drag in bdv
		// (collides with default of "move annotation")
		final InputTriggerConfig config =
				new InputTriggerConfig(
						Arrays.asList(
								new InputTriggerDescription[] { new InputTriggerDescription( new String[] { "not mapped" }, "drag rotate slow", "bdv" ) } ) );

		return config;
	}
}
