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

import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.AbstractARGBConvertedLabelsSource;
import bdv.bigcat.ui.ModalGoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.util.IdService;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

public class BigCatViewerJan< P extends BigCatViewerJan.Parameters >
{
	static public class Parameters
	{
		@Parameter( names = { "--raw-file", "-r" }, description = "Path to raw hdf5" )
		public String rawFile = "";

		public Parameters setRawFile( final String rawFile )
		{
			this.rawFile = rawFile;
			return this;
		}

		@Parameter( names = { "--raw-dataset", "-R" }, description = "Raw dataset" )
		public String rawDataset = "volumes/raw";

		public Parameters setRawDataset( final String rawDataset )
		{
			this.rawDataset = rawDataset;
			return this;
		}

		@Parameter( names = { "--ground-truth-file", "-g" }, description = "Path to ground truth hdf5" )
		public String groundTruthFile = "";

		public Parameters setGroundTruthFile( final String groundTruthFile )
		{
			this.groundTruthFile = groundTruthFile;
			return this;
		}

		@Parameter( names = { "--ground-truth-dataset", "-G" }, description = "GT dataset" )
		public String groundTruthDataset = "volumes/labels/neuron_ids_notransparency";

		public Parameters setGroundTruthDataset( final String groundTruthDataset )
		{
			this.groundTruthFile = groundTruthDataset;
			return this;
		}

		@Parameter( names = { "--prediction-file", "-p" }, description = "Path to prediction hdf5" )
		public String predictionFile = "";

		public Parameters setPredictionFile( final String predictionFile )
		{
			this.predictionFile = predictionFile;
			return this;
		}

		@Parameter ( names = { "--prediction-dataset", "-P" }, description = "Prediction dataset" )
		public String predictionDataset = "main";

		public Parameters setPredictionDataset( final String predictionDataset )
		{
			this.predictionDataset = predictionDataset;
			return this;
		}

		@Parameter( names = { "--prediction-offset", "-o" }, description = "Offset of prediction (will read from h5 if not set and available)" )
		public String offset = null;

		public double[] offsetArray = null;

		public Parameters setOffsetArray( final double[] offsetArray )
		{
			this.offsetArray = offsetArray;
			return this;
		}

		@Parameter( names = { "--resolution" }, description = "Voxel resolution" )
		public String resolution = null;

		public double[] resolutionArray = null;

		public Parameters setResolutionArray( final double[] resolutionArray )
		{
			this.resolutionArray = resolutionArray;
			return this;
		}

		public void init()
		{
			if ( offset != null )
			{
				offsetArray = Arrays.stream( offset.split( "," ) ).mapToDouble( Double::parseDouble ).toArray();
				final double tmp = offsetArray[ 0 ];
				offsetArray[ 0 ] = offsetArray[ 2 ];
				offsetArray[ 2 ] = tmp;
			}

			if ( resolution != null )
			{
				resolutionArray = Arrays.stream( resolution.split( "," ) ).mapToDouble( Double::parseDouble ).toArray();
				final double tmp = resolutionArray[ 0 ];
				resolutionArray[ 0 ] = resolutionArray[ 2 ];
				resolutionArray[ 2 ] = tmp;
			}
		}

	}

	/** default cell dimensions */
	final static protected int[] cellDimensions = { 145, 53, 5 }; // new int[]{
	// 64, 64, 8
	// };

	/** global ID generator */
	protected IdService idService;

	/** raw pixels (image data) */
	final protected ArrayList< H5UnsignedByteSetupImageLoader > raws = new ArrayList<>();

	/** fragment to segment assignment */
	protected FragmentSegmentAssignment assignment;

	/** color generator for composition of loaded segments and canvas */
	protected ModalGoldenAngleSaturatedARGBStream colorStream;

	/** loaded segments */
	final protected ArrayList< H5LabelMultisetSetupImageLoader > labels = new ArrayList<>();

	/** compositions of labels and canvas that are displayed */
	final protected ArrayList< AbstractARGBConvertedLabelsSource > convertedLabels = new ArrayList<>();

	/** main BDV instance */
	protected BigDataViewer bdv;

	/** controllers */
	protected InputTriggerConfig config;

	protected int setupId = 0;

	protected double[] resolution = null;

	protected double[] offset = null;

	public BigDataViewer getBigDataViewer()
	{
		return bdv;
	}

	public static void main( final String[] args ) throws Exception
	{
		final String[] argv = new String[] {
				"-r", "/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B.augmented.0.hdf",
				"-g", "/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B.augmented.0.hdf",
				"-p", "/groups/saalfeld/home/hanslovskyp/from_funkej/phil/sample_B_median_aff_cf_hq_dq_au00.87.hdf",
				"-o", "560,424,424", "--resolution", "40,4,4"
//				"-o", "14,106,106" // "560,424,424" resolution = 1?
		};
		final Parameters params = new Parameters();
		new JCommander( params, argv );
		params.init();
		run( params );
	}

	public static BigCatViewerJan< Parameters > run( final Parameters params ) throws Exception
	{

		final BigCatViewerJan< Parameters > bigCat = new BigCatViewerJan<>();
		bigCat.init( params );
		bigCat.setupBdv( params );
		return bigCat;
	}

	public BigCatViewerJan() throws Exception
	{
		Util.initUI();
		this.config = getInputTriggerConfig();
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
//		initIdService( params );
		initAssignments( params );
		initGroundTruth( params );
		initPrediction( params );
	}

	/**
	 * Load raw data and labels and initialize canvas
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initRaw( final P params ) throws IOException
	{
		System.out.println( "Opening raw from " + params.rawFile );
		final IHDF5Reader reader = HDF5Factory.open( params.rawFile );

		/* raw pixels */
		final String raw = params.rawDataset;
		this.resolution = params.resolutionArray == null ? readResolution( reader, raw ) : params.resolutionArray;
		if ( reader.exists( raw ) )
		{
			final H5UnsignedByteSetupImageLoader rawLoader = new H5UnsignedByteSetupImageLoader( reader, raw, setupId++, cellDimensions, this.resolution, new double[ this.resolution.length ] );
			raws.add( rawLoader );
		}
		else
			System.out.println( "no raw dataset '" + raw + "' found" );
	}

	/**
	 * Initialize ID service.
	 *
	 * @param params
	 * @throws IOException
	 */
//	protected void initIdService( final P params ) throws IOException
//	{
//		/* id */
//		idService = new LocalIdService();
//
//		final IHDF5Reader reader = HDF5Factory.open( params.inFile );
//
//		long maxId = 0;
//		final Long nextIdObject = H5Utils.loadAttribute( reader, "/", "next_id" );
//
//		reader.close();
//
//		if ( nextIdObject == null )
//			for ( final H5LabelMultisetSetupImageLoader labelLoader : labels )
//				maxId = maxId( labelLoader, maxId );
//		else
//			maxId = nextIdObject.longValue() - 1;
//
//		idService.invalidate( maxId );
//	}

	/**
	 * Initialize assignments.
	 *
	 * @param params
	 */
	protected void initAssignments( final P params )
	{
//		final IHDF5Reader reader = HDF5Factory.open( params.groundTruthFile );

		/* fragment segment assignment */
		assignment = new FragmentSegmentAssignment( idService );
//		final TLongLongHashMap lut = H5Utils.loadLongLongLut( reader, "", 1024 );
//		if ( lut != null )
//			assignment.initLut( lut );

		/* color stream */
		colorStream = new ModalGoldenAngleSaturatedARGBStream( assignment );
		colorStream.setAlpha( 0x20 );

//		reader.close();
	}

	/**
	 * Load labels and create label+canvas compositions.
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initPrediction( final P params ) throws IOException
	{
		System.out.println( "Opening labels from " + params.predictionFile );
		final IHDF5Reader reader = HDF5Factory.open( params.predictionFile );

		/* labels */
		final String label = params.predictionDataset;
		if ( reader.exists( label ) ) {
			offset = params.offsetArray == null ? readOffset( reader, label ) : params.offsetArray;
			System.out.println( "Using offset " + Arrays.toString( offset ) );
			readLabels( reader, label, resolution, offset );
		}
		else
			System.out.println( "no label dataset '" + label + "' found" );
	}

	/**
	 * Load labels and create label+canvas compositions.
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initGroundTruth( final P params ) throws IOException
	{
		System.out.println( "Opening labels from " + params.groundTruthFile );
		final IHDF5Reader reader = HDF5Factory.open( params.groundTruthFile );


		/* labels */
		final String groundTruth = params.groundTruthDataset;
		if ( reader.exists( groundTruth ) )
			readLabels( reader, groundTruth, this.resolution, new double[ this.resolution.length ] );
		else
			System.out.println( "no ground truth dataset '" + groundTruth + "' found" );
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

//		final SelectionController selectionController;
//		final LabelMultiSetIdPicker idPicker;

//		if ( labels.size() > 0 )
//		{
//			/* TODO fix ID picker to pick from the top most label canvas pair */
//			idPicker = new LabelMultiSetIdPicker(
//					bdv.getViewer(),
//					RealViews.affineReal(
//							Views.interpolate(
//									Views.extendValue(
//											labels.get( 0 ).getImage( 0 ),
//											new LabelMultisetType() ),
//									new NearestNeighborInterpolatorFactory< LabelMultisetType >() ),
//							labels.get( 0 ).getMipmapTransforms()[ 0 ] ) );
//
////			selectionController = new SelectionController(
////					bdv.getViewer(),
////					idPicker,
////					colorStream,
////					idService,
////					assignment,
////					config,
////					bdv.getViewerFrame().getKeybindings(),
////					config);
////
////			final MergeController mergeController = new MergeController(
////					bdv.getViewer(),
////					idPicker,
////					selectionController,
////					assignment,
////					config,
////					bdv.getViewerFrame().getKeybindings(),
////					config);
////
////			/* mark segments as finished */
////			final ConfirmSegmentController confirmSegment = new ConfirmSegmentController(
////					bdv.getViewer(),
////					selectionController,
////					assignment,
////					colorStream,
////					colorStream,
////					config,
////					bdv.getViewerFrame().getKeybindings() );
//
//			bindings.addBehaviourMap( "select", selectionController.getBehaviourMap() );
//			bindings.addInputTriggerMap( "select", selectionController.getInputTriggerMap() );
//
//			bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
//			bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );
//
//		}
//		else
//		{
//			selectionController = null;
//			idPicker = null;
//		}

		/* override navigator z-step size with raw[ 0 ] z resolution */
		final TranslateZController translateZController = new TranslateZController(
				bdv.getViewer(),
				raws.get( 0 ).getMipmapResolutions()[ 0 ],
				config );
		bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );

//		if ( selectionController != null )
//			bdv.getViewer().getDisplay().addOverlayRenderer( selectionController.getSelectionOverlay() );
	}

	protected void readLabels(
			final IHDF5Reader reader,
			final String labelDataset,
			final double[] resolution,
			final double[] offset ) throws IOException
	{
		/* labels */
		final H5LabelMultisetSetupImageLoader labelLoader =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelDataset,
						setupId++,
						cellDimensions,
						resolution,
						offset );

		/* converted labels */
		final ARGBConvertedLabelsSource convertedLabelsSource =
				new ARGBConvertedLabelsSource(
						setupId++,
						labelLoader,
						colorStream );

		labels.add( labelLoader );
		convertedLabels.add( convertedLabelsSource );
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
						cellDimensions );

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
			for ( final Multiset.Entry< Label > v : t.entrySet() )
			{
				final long id = v.getElement().id();
				if ( Label.regular( id ) && IdService.greaterThan( id, maxId ) )
					maxId = id;
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

	static public double[] readResolution( final IHDF5Reader reader, final String dataset )
	{
		final double[] resolution;
		if ( reader.object().hasAttribute( dataset, "resolution" ) )
		{
			final double[] h5res = reader.float64().getArrayAttr( dataset, "resolution" );
			resolution = new double[] { h5res[ 2 ], h5res[ 1 ], h5res[ 0 ], };
		}
		else
			resolution = new double[] { 1, 1, 1 };

		return resolution;
	}

	static public double[] readOffset( final IHDF5Reader reader, final String dataset )
	{
		final double[] offset;
		if ( reader.object().hasAttribute( dataset, "offset" ) )
		{
			final double[] h5offset = reader.float64().getArrayAttr( dataset, "offset" );
			offset = new double[] { h5offset[ 2 ], h5offset[ 1 ], h5offset[ 0 ], };
		}
		else
			offset = new double[] { 0, 0, 0 };

		return offset;
	}
}
