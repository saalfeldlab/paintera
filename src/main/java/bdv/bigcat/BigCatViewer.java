package bdv.bigcat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import bdv.BigDataViewer;
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
import bdv.bigcat.ui.AbstractARGBConvertedLabelsSource;
import bdv.bigcat.ui.ModalGoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

public class BigCatViewer< P extends BigCatViewer.Parameters >
{
	static public class Parameters
	{
		@Parameter( names = { "--infile", "-i" }, description = "Input file path" )
		public String inFile = "";

		@Parameter( names = { "--infilelabels", "-j" }, description = "Input file path for labels (if different from input file path for raw data)" )
		public String inFileLabels = null;

		@Parameter( names = { "--raw", "-r" }, description = "raw pixel datasets" )
		public List< String > raws = new ArrayList<>();

		@Parameter( names = { "--label", "-l" }, description = "label datasets" )
		public List< String > labels = new ArrayList<>();

		@Parameter( names = { "--assignment", "-a" }, description = "fragment segment assignment table" )
		public String assignment = "/fragment_segment_lut";

		@Parameter( names = { "--complete", "-f" }, description = "complete fragments" )
		public String completeFragments = "/complete_fragments";

		public void init()
		{
			if ( inFileLabels == null )
				inFileLabels = inFile;

			if ( inFile == null )
				inFile = inFileLabels;
		}
	}

	/** default cell dimensions */
	final static protected int[] cellDimensions = new int[]{ 64, 64, 8 };

	/** global ID generator */
	protected IdService idService;

	/** raw pixels (image data) */
	final protected ArrayList< H5UnsignedByteSetupImageLoader > raws = new ArrayList<>();

	/** fragment to segment assignment */
	protected FragmentSegmentAssignment assignment;

	/** complete fragments */
	protected FragmentAssignment completeFragmentsAssignment;

	/** color generator for composition of loaded segments and canvas */
	protected ModalGoldenAngleSaturatedARGBStream colorStream;

	/** loaded segments */
	final protected ArrayList< H5LabelMultisetSetupImageLoader > labels = new ArrayList<>();

	/** compositions of labels and canvas that are displayed */
	final protected ArrayList< AbstractARGBConvertedLabelsSource > convertedLabels = new ArrayList<>();

	final protected VolatileGlobalCellCache cache;

	/** main BDV instance */
	protected BigDataViewer bdv;

	/** controllers */
	protected InputTriggerConfig config;

	protected int setupId = 0;

	public static void main( final String[] args ) throws Exception
	{
		final Parameters params = new Parameters();
		new JCommander( params, args );
		params.init();
		final BigCatViewer< Parameters > bigCat = new BigCatViewer<>();
		bigCat.init( params );
		bigCat.setupBdv( params );
	}

	public BigCatViewer() throws Exception
	{
		Util.initUI();
		this.config = getInputTriggerConfig();
		cache = new VolatileGlobalCellCache( 1, 12 );

	}

	/**
	 * Initialize BigCatViewer, order is important because individual initializers depend on previous members initialized.
	 *
	 * <ol>
	 * <li>Load raw,</li>
	 * <li>setup IdService,</li>
	 * <li>setup assignments,</li>
	 * <li>load labels and create label+canvas compositions.</li>
	 * </ol>
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void init( final P params ) throws IOException
	{
		initRaw( params );
		initIdService( params );
		initAssignments( params );
		initLabels( params );
	}

	/**
	 * Load raw data and labels and initialize canvas
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initRaw( final P params ) throws IOException
	{
		System.out.println( "Opening raw from " + params.inFile );
		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

		/* raw pixels */
		for ( final String raw : params.raws )
		{
			if ( reader.exists( raw ) )
			{
				final H5UnsignedByteSetupImageLoader rawLoader = new H5UnsignedByteSetupImageLoader( reader, raw, setupId++, cellDimensions, cache );
				raws.add( rawLoader );
			}
			else
				System.out.println( "no raw dataset '" + raw + "' found" );
		}
	}

	/**
	 * Initialize ID service.
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initIdService( final P params ) throws IOException
	{
		/* id */
		idService = new LocalIdService();

		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

		long maxId = 0;
		final Long nextIdObject = H5Utils.loadAttribute( reader, "/", "next_id" );

		reader.close();

		if ( nextIdObject == null )
		{
			for ( final H5LabelMultisetSetupImageLoader labelLoader : labels )
				maxId = maxId( labelLoader, maxId );
		}
		else
			maxId = nextIdObject.longValue() - 1;

		idService.invalidate( maxId );
	}

	/**
	 * Initialize assignments.
	 *
	 * @param params
	 */
	protected void initAssignments( final P params )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

		/* fragment segment assignment */
		assignment = new FragmentSegmentAssignment( idService );
		final TLongLongHashMap lut = H5Utils.loadLongLongLut( reader, params.assignment, 1024 );
		if ( lut != null )
			assignment.initLut( lut );

		/* complete fragments */
		completeFragmentsAssignment = new FragmentAssignment();
		final TLongHashSet set = new TLongHashSet();
		H5Utils.loadLongCollection( set, reader, params.completeFragments, 1024 );

		/* color stream */
		colorStream = new ModalGoldenAngleSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x20 );

		reader.close();
	}

	/**
	 * Load labels and create label+canvas compositions.
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initLabels( final P params ) throws IOException
	{
		System.out.println( "Opening labels from " + params.inFileLabels );
		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFileLabels );

		/* labels */
		for ( final String label : params.labels )
			if ( reader.exists( label ) )
				readLabels( reader, label );
		else
				System.out.println( "no label dataset '" + label + "' found" );
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
	protected void setupBdv( final P params ) throws Exception
	{
		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList<>();
		final ArrayList< SetCache > cacheLoaders = new ArrayList<>();
		for ( final H5UnsignedByteSetupImageLoader loader : raws )
		{
			composites.add( new CompositeCopy< ARGBType >() );
			cacheLoaders.add( loader );
		}
		for ( final H5LabelMultisetSetupImageLoader loader : labels )
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

	/**
	 * Creates a label loader and the converted labels and adds them to the
	 * respective lists.
	 *
	 * Depends on {@link #colorStream} being initialized.
	 *
	 * Modifies {@link #labels}, {@link #setupId}, {@link #convertedLabels}.
	 *
	 * @param reader
	 * @param labelDataset
	 * @throws IOException
	 */
	protected void readLabels(
			final IHDF5Reader reader,
			final String labelDataset ) throws IOException
	{
		/* labels */
		final H5LabelMultisetSetupImageLoader labelLoader =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelDataset,
						setupId++,
						cellDimensions,
						cache );

		/* converted labels */
		final ARGBConvertedLabelsSource convertedLabelsSource =
				new ARGBConvertedLabelsSource(
						setupId++,
						labelLoader,
						colorStream );

		labels.add( labelLoader );
		convertedLabels.add( convertedLabelsSource );
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

	final static protected long maxId(
			final H5LabelMultisetSetupImageLoader labelLoader,
			long maxId ) throws IOException
	{
		for ( final LabelMultisetType t : Views.iterable( labelLoader.getImage( 0 ) ) )
		{
			for ( final Multiset.Entry< Label > v : t.entrySet() )
			{
				final long id = v.getElement().id();
				if ( Label.regular( id ) && IdService.greaterThan( id, maxId ) )
					maxId = id;
			}
		}

		return maxId;
	}

	final static protected long maxId(
			final Iterable< LongType > labels,
			long maxId ) throws IOException
	{
		for ( final LongType t : labels )
		{
			final long id = t.get();
			if ( Label.regular( id ) && IdService.greaterThan( id, maxId ) )
				maxId = id;
		}

		return maxId;
	}
}
