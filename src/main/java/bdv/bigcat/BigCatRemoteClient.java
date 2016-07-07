package bdv.bigcat;

import java.awt.Cursor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO;
import org.zeromq.ZContext;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import bdv.BigDataViewer;
import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.AgglomerationClientController;
import bdv.bigcat.control.AnnotationsController;
import bdv.bigcat.control.ConfirmSegmentController;
import bdv.bigcat.control.DrawProjectAndIntersectController;
import bdv.bigcat.control.LabelBrushController;
import bdv.bigcat.control.LabelFillController;
import bdv.bigcat.control.LabelPersistenceController;
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.GoldenAngleSaturatedConfirmSwitchARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.img.labelpair.RandomAccessiblePair;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.util.RemoteIdService;
import bdv.viewer.TriggerBehaviourBindings;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.algorithm.neighborhood.DiamondShape;
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

public class BigCatRemoteClient
{
	static private class Parameters
	{
		@Parameter( names = { "--broker", "-b" }, description = "URL to configuration broker" )
		public String config = "";

		@Parameter( names = { "--infile", "-i" }, description = "Input file path" )
		public String inFile = "";

		@Parameter( names = { "--raw", "-r" }, description = "raw pixel datasets" )
		public List< String > raws = Arrays.asList( new String[] { "/volumes/raw" } );

		@Parameter( names = { "--label", "-l" }, description = "label datasets" )
		public List< String > labels = Arrays.asList( new String[] { "/volumes/labels/neuron_ids" } );

		@Parameter( names = { "--assignment", "-a" }, description = "fragment segment assignment table" )
		public String assignment = "/fragment_segment_lut";

		@Parameter( names = { "--canvas", "-c" }, description = "canvas dataset" )
		public String canvas = "/volumes/labels/painted_neuron_ids";

		@Parameter( names = { "--export", "-e" }, description = "export dataset" )
		public String export = "/volumes/labels/merged_neuron_ids";
	}

	final static private int[] cellDimensions = new int[]{ 64, 64, 8 };

	final private ArrayList< H5UnsignedByteSetupImageLoader > raws = new ArrayList<>();
	final private ArrayList< H5LabelMultisetSetupImageLoader > labels = new ArrayList<>();
	final private ArrayList< ARGBConvertedLabelPairSource > convertedLabelCanvasPairs = new ArrayList<>();

	final private ZContext ctx;

	/* TODO this has to change into a virtual container with temporary storage */
	private CellImg< LongType, ?, ? > canvas = null;

	private BigDataViewer bdv;
	private final GoldenAngleSaturatedConfirmSwitchARGBStream colorStream;
	private final FragmentSegmentAssignment assignment;

	private LabelPersistenceController persistenceController;
	private AnnotationsController annotationsController;
	private final InputTriggerConfig config;

	private final IdService idService;

	/**
	 * Writes max(a,b) into a
	 *
	 * @param a
	 * @param b
	 */
	final static private void max( final long[] a, final long[] b )
	{
		for ( int i = 0; i < a.length; ++i )
			if ( b[ i ] > a[ i ] )
				a[ i ] = b[ i ];
	}

	public static void main( final String[] args ) throws Exception
	{
		final Parameters params = new Parameters();
		new JCommander( params, args );

		final Config config;
		try ( final FileReader reader = new FileReader( new File( params.config ) ) )
		{
			config = new Gson().fromJson( reader, Config.class );
			new BigCatRemoteClient( params, config );
		}
	}

	public BigCatRemoteClient( final Parameters params, final Config brokerConfig ) throws Exception
	{
		Util.initUI();

		this.config = getInputTriggerConfig();

		ctx = new ZContext();

		System.out.println( "Opening " + params.inFile );
		final IHDF5Reader reader = HDF5Factory.open( params.inFile );

		/* raw pixels */
		final long[] maxRawDimensions = new long[]{ 0, 0, 0 };
		for ( final String raw : params.raws )
		{
			if ( reader.exists( raw ) )
			{
				final H5UnsignedByteSetupImageLoader rawLoader = new H5UnsignedByteSetupImageLoader( reader, raw, 0, cellDimensions );
				raws.add( rawLoader );
				max( maxRawDimensions, Intervals.dimensionsAsLongArray( rawLoader.getVolatileImage( 0, 0 ) ) );
			}
			else
				System.out.println( "no raw dataset '" + raw + "' found" );
		}

		/* canvas (to which the brush paints) */
		if ( reader.exists( params.canvas ) )
			canvas = H5Utils.loadUnsignedLong( reader, params.canvas, cellDimensions );
		else
		{
			canvas = new CellImgFactory< LongType >( cellDimensions ).create( maxRawDimensions, new LongType() );
			for ( final LongType t : canvas )
				t.set( Label.TRANSPARENT );
		}

		/* id service */
		if ( brokerConfig.id_service_url != null )
			idService = new RemoteIdService( ctx, brokerConfig.id_service_url );
		else
			idService = new LocalIdService();

		/* fragment segment assignment */
		assignment = new FragmentSegmentAssignment( idService );
		final TLongLongHashMap lut = H5Utils.loadLongLongLut( reader, params.assignment, 1024 );
		if ( lut != null )
			assignment.initLut( lut );

		/* color stream */
		colorStream = new GoldenAngleSaturatedConfirmSwitchARGBStream( assignment );
		colorStream.setAlpha( 0x20 );

		/* labels */
		for ( final String label : params.labels )
			if ( reader.exists( label ) )
				readLabels( reader, label );
		else
				System.out.println( "no label dataset '" + label + "' found" );

		/* id */
		long maxId = 0;
		final Long nextIdObject = H5Utils.loadAttribute( reader, "/", "next_id" );
		if ( nextIdObject == null )
		{
			for ( final H5LabelMultisetSetupImageLoader labelLoader : labels )
				maxId = maxId( labelLoader, maxId );

			if ( reader.exists( params.canvas ) )
					maxId = maxId( canvas, maxId );
		}
		else
			maxId = nextIdObject.longValue() - 1;

		idService.invalidate( maxId );

		setupBdv( params, brokerConfig );
	}

	/**
	 * Creates a label loader, a label canvas pair and the converted
	 * pair and adds them to the respective lists.
	 *
	 * @param reader
	 * @param labelDataset
	 * @throws IOException
	 */
	private void readLabels(
			final IHDF5Reader reader,
			final String labelDataset ) throws IOException
	{
		/* labels */
		final H5LabelMultisetSetupImageLoader labelLoader =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelDataset,
						1,
						cellDimensions );

		/* pair labels */
		final RandomAccessiblePair< VolatileLabelMultisetType, LongType > labelCanvasPair =
				new RandomAccessiblePair<>(
						labelLoader.getVolatileImage( 0, 0 ),
						canvas );

		/* converted pair */
		final ARGBConvertedLabelPairSource convertedLabelCanvasPair =
				new ARGBConvertedLabelPairSource(
						3,
						labelCanvasPair,
						canvas, // as Interval, used just for the size
						labelLoader.getMipmapTransforms(),
						colorStream );

		labels.add( labelLoader );
		convertedLabelCanvasPairs.add( convertedLabelCanvasPair );
	}

	@SuppressWarnings( "unchecked" )
	private void setupBdv( final Parameters params, final Config brokerConfig ) throws Exception
	{
		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
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
				convertedLabelCanvasPairs,
				cacheLoaders,
				composites,
				config );

		bdv.getViewerFrame().setVisible( true );

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

		final SelectionController selectionController;
		final LabelBrushController brushController;
		final PairLabelMultiSetLongIdPicker idPicker;

		if ( labels.size() > 0 )
		{
			/* TODO fix ID picker to pick from the top most label canvas pair */
			idPicker = new PairLabelMultiSetLongIdPicker(
					bdv.getViewer(),
					RealViews.affineReal(
							Views.interpolate(
									new RandomAccessiblePair< LabelMultisetType, LongType >(
											Views.extendValue(
												labels.get( 0 ).getImage( 0 ),
												new LabelMultisetType() ),
											Views.extendValue(
													canvas,
													new LongType( Label.OUTSIDE) ) ),
									new NearestNeighborInterpolatorFactory< Pair< LabelMultisetType, LongType > >() ),
							labels.get( 0 ).getMipmapTransforms()[ 0 ] )
					);

			selectionController = new SelectionController(
					bdv.getViewer(),
					idPicker,
					colorStream,
					idService,
					assignment,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config);

//			final MergeController mergeController = new MergeController(
//					bdv.getViewer(),
//					idPicker,
//					selectionController,
//					assignment,
//					config,
//					bdv.getViewerFrame().getKeybindings(),
//					config);

			final AgglomerationClientController mergeController = new AgglomerationClientController(
					bdv.getViewer(),
					idPicker,
					selectionController,
					assignment,
					ctx,
					brokerConfig.solver_url,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config);

			/* TODO fix to deal with correct transform */
			brushController = new LabelBrushController(
					bdv.getViewer(),
					canvas,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					selectionController,
					cellDimensions,
					config);

			/* TODO fix to deal with more than one label set */
			persistenceController = new LabelPersistenceController(
					bdv.getViewer(),
					labels.get( 0 ).getImage( 0 ),
					canvas,
					assignment,
					idService,
					params.inFile,
					params.canvas,
					params.export,
					cellDimensions,
					params.assignment,
					config,
					bdv.getViewerFrame().getKeybindings() );

			/* TODO fix to deal with more than one label set */
			final LabelFillController fillController = new LabelFillController(
					bdv.getViewer(),
					labels.get( 0 ).getImage( 0 ),
					canvas,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					selectionController,
					new DiamondShape( 1 ),
					idPicker,
					config);

			/* splitter (and more) */
			/* TODO fix to deal with more than one label set */
			final DrawProjectAndIntersectController dpi = new DrawProjectAndIntersectController(
					bdv,
					idService,
					new AffineTransform3D(),
					new InputTriggerConfig(),
					labels.get( 0 ).getImage(0),
					canvas,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					colorStream,
					selectionController,
					bdv.getViewerFrame().getKeybindings(),
					bindings,
					"shift T" );

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

			bindings.addBehaviourMap( "brush", brushController.getBehaviourMap() );
			bindings.addInputTriggerMap( "brush", brushController.getInputTriggerMap() );

			bindings.addBehaviourMap( "fill", fillController.getBehaviourMap() );
			bindings.addInputTriggerMap( "fill", fillController.getInputTriggerMap() );

			bdv.getViewerFrame().addWindowListener( new WindowAdapter()
			{
				@Override
				public void windowClosing( final WindowEvent we )
				{
					saveBeforeClosing( params );
					System.exit( 0 );
				}
			} );
		}
		else
		{
			selectionController = null;
			brushController = null;
			idPicker = null;
		}

		/* override navigator z-step size with raw[ 0 ] z resolution */
		final TranslateZController translateZController = new TranslateZController(
				bdv.getViewer(),
				raws.get( 0 ).getMipmapResolutions()[ 0 ],
				config );
		bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );

		final AnnotationsHdf5Store annotationsStore = new AnnotationsHdf5Store( params.inFile, idService );
		annotationsController = new AnnotationsController(
				annotationsStore,
				bdv,
				idService,
				config,
				bdv.getViewerFrame().getKeybindings(),
				config );

		bindings.addBehaviourMap( "annotation", annotationsController.getBehaviourMap() );
		bindings.addInputTriggerMap( "annotation", annotationsController.getInputTriggerMap() );

		/* overlays */
		bdv.getViewer().getDisplay().addOverlayRenderer( annotationsController.getAnnotationOverlay() );

		if ( brushController != null )
			bdv.getViewer().getDisplay().addOverlayRenderer( brushController.getBrushOverlay() );

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

	public void saveBeforeClosing( final Parameters params )
	{
		final String message = "Save changes to " + params.inFile + " before closing?";

		if (
				JOptionPane.showConfirmDialog(
						bdv.getViewerFrame(),
						message,
						bdv.getViewerFrame().getTitle(),
						JOptionPane.YES_NO_OPTION ) == JOptionPane.YES_OPTION )
		{
			bdv.getViewerFrame().setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
			annotationsController.saveAnnotations();
			persistenceController.saveNextId();
			persistenceController.saveFragmentSegmentAssignment();
			persistenceController.savePaintedLabels();
		}
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
				if ( id != Label.TRANSPARENT && IdService.greaterThan( id, maxId ) )
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
			if ( id != Label.TRANSPARENT && IdService.greaterThan( id, maxId ) )
				maxId = id;
		}

		return maxId;
	}
}
