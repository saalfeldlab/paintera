package bdv.bigcat;

import java.awt.Cursor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import bdv.bigcat.annotation.AnnotationsHdf5Store;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.control.AnnotationsController;
import bdv.bigcat.control.ConfirmSegmentController;
import bdv.bigcat.control.DrawProjectAndIntersectController;
import bdv.bigcat.control.LabelBrushController;
import bdv.bigcat.control.LabelFillController;
import bdv.bigcat.control.LabelPersistenceController;
import bdv.bigcat.control.MergeController;
import bdv.bigcat.control.NeuronIdsToFileController;
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.Util;
import bdv.bigcat.util.DirtyInterval;
import bdv.img.SetCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.LocalIdService;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
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
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

public class BigCat< P extends BigCat.Parameters > extends BigCatViewer< P >
{
	static public class Parameters extends BigCatViewer.Parameters
	{
		@Parameter( names = { "--canvas", "-c" }, description = "canvas dataset" )
		public String canvas = "/volumes/labels/canvas";

		@Parameter( names = { "--export", "-e" }, description = "export dataset" )
		public String export = "/volumes/labels/merged_ids";

		@Parameter( names = { "--outfile", "-o" }, description = "Output file path" )
		public String outFile;

		@Override
		public void init()
		{
			super.init();

			if ( outFile == null )
				outFile = inFile;
		}
	}

	/** max raw dimensions */
	final protected long[] maxRawDimensions = new long[ 3 ];

	/**
	 * canvas that gets modified by brush TODO this has to change into a virtual
	 * container with temporary storage
	 */
	protected CellImg< LongType, ? > canvas = null;

	/** interval in which pixels were modified */
	final protected DirtyInterval dirtyLabelsInterval = new DirtyInterval();

	/** controllers */
	protected LabelPersistenceController persistenceController;

	protected AnnotationsController annotationsController;

	/**
	 * Writes max(a,b) into a
	 *
	 * @param a
	 * @param b
	 */
	final static protected void max( final long[] a, final long[] b )
	{
		for ( int i = 0; i < a.length; ++i )
			if ( b[ i ] > a[ i ] )
				a[ i ] = b[ i ];
	}

	public static void main( final String[] args ) throws Exception
	{
		final Parameters params = new Parameters();
		new JCommander( params, args );
		params.init();
		final BigCat< Parameters > bigCat = new BigCat<>();
		bigCat.init( params );
		bigCat.setupBdv( params );
	}

	public BigCat() throws Exception
	{
		super();
	}

	/**
	 * Initialize BigCat, order is important because individual initializers
	 * depend on previous members initialized.
	 *
	 * <ol>
	 * <li>Load raw and canvas,</li>
	 * <li>setup IdService,</li>
	 * <li>setup assignments,</li>
	 * <li>load labels and create label+canvas compositions.</li>
	 * </ol>
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void init( final P params ) throws IOException
	{
		initRaw( params );
		initCanvas( params );
		initIdService( params );
		initAssignments( params );
		initLabels( params );
	}

	/**
	 * Load raw data, find maximum raw dimensions
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void initRaw( final P params ) throws IOException
	{
		System.out.println( "Opening raw from " + params.inFile );
		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

		/* raw pixels */
		Arrays.fill( maxRawDimensions, 0 );
		for ( final String raw : params.raws )
			if ( reader.exists( raw ) )
			{
				final H5UnsignedByteSetupImageLoader rawLoader = new H5UnsignedByteSetupImageLoader( reader, raw, setupId++, cellDimensions, cache );
				raws.add( rawLoader );
				max( maxRawDimensions, Intervals.dimensionsAsLongArray( rawLoader.getVolatileImage( 0, 0 ) ) );
			}
			else
				System.out.println( "no raw dataset '" + raw + "' found" );
	}

	/**
	 * Load or initialize canvas
	 *
	 * @param params
	 * @throws IOException
	 */
	protected void initCanvas( final P params ) throws IOException
	{
		System.out.println( "Opening canvas from " + params.inFile );
		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

		/* canvas (to which the brush paints) */
		if ( reader.exists( params.canvas ) )
			canvas = H5Utils.loadUnsignedLong( reader, params.canvas, cellDimensions );
		else
		{
			canvas = new CellImgFactory< LongType >( cellDimensions ).create( maxRawDimensions, new LongType() );
			for ( final LongType t : canvas )
				t.set( Label.TRANSPARENT );
		}

		reader.close();
	}

	/**
	 * Initialize ID service, load max id from file or find max id in labels and
	 * canvas.
	 *
	 * @param params
	 * @throws IOException
	 */
	@Override
	protected void initIdService( final P params ) throws IOException
	{
		/* id */
		idService = new LocalIdService();

		final IHDF5Reader reader = HDF5Factory.openForReading( params.inFile );

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

		reader.close();
	}

	/**
	 * Create tool.
	 *
	 * Depends on {@link #raws}, {@link #labels},
	 * {@link #convertedLabelCanvasPairs}, {@link #colorStream},
	 * {@link #idService}, {@link #assignment}, {@link #config},
	 * {@link #dirtyLabelsInterval}, {@link #completeFragmentsAssignment},
	 * {@link #canvas} being initialized.
	 *
	 * Modifies {@link #bdv}, {@link #convertedLabelCanvasPairs},
	 * {@link #persistenceController},
	 *
	 * @param params
	 * @throws Exception
	 */
	@Override
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
		bdv.getViewerFrame().setSize( 1248, 656 );

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
									new RandomAccessiblePair<>(
											Views.extendValue(
													labels.get( 0 ).getImage( 0 ),
													new LabelMultisetType() ),
											Views.extendValue(
													canvas,
													new LongType( Label.OUTSIDE ) ) ),
									new NearestNeighborInterpolatorFactory< Pair< LabelMultisetType, LongType > >() ),
							labels.get( 0 ).getMipmapTransforms()[ 0 ] ) );

			selectionController = new SelectionController(
					bdv.getViewer(),
					idPicker,
					colorStream,
					idService,
					assignment,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config );

			final MergeController mergeController = new MergeController(
					bdv.getViewer(),
					idPicker,
					selectionController,
					assignment,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config );

			/* TODO fix to deal with correct transform */
			brushController = new LabelBrushController(
					bdv.getViewer(),
					canvas,
					dirtyLabelsInterval,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					selectionController,
					cellDimensions,
					config );

			/* TODO fix to deal with more than one label set */
			persistenceController = new LabelPersistenceController(
					bdv.getViewer(),
					labels.get( 0 ).getImage( 0 ),
					canvas,
					labels.get( 0 ).getMipmapResolutions()[ 0 ],
					labels.get( 0 ).getOffset(),
					dirtyLabelsInterval,
					assignment,
					completeFragmentsAssignment,
					idService,
					params.outFile,
					params.canvas,
					params.export,
					cellDimensions,
					params.assignment,
					params.completeFragments,
					config,
					bdv.getViewerFrame().getKeybindings() );

			/* TODO fix to deal with more than one label set */
			final LabelFillController fillController = new LabelFillController(
					bdv.getViewer(),
					labels.get( 0 ).getImage( 0 ),
					canvas,
					dirtyLabelsInterval,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					selectionController,
					new DiamondShape( 1 ),
					idPicker,
					config );

			/* splitter (and more) */
			/* TODO fix to deal with more than one label set */
			final DrawProjectAndIntersectController dpi = new DrawProjectAndIntersectController(
					bdv,
					idService,
					new AffineTransform3D(),
					new InputTriggerConfig(),
					labels.get( 0 ).getImage( 0 ),
					canvas,
					dirtyLabelsInterval,
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

			final NeuronIdsToFileController storeController = new NeuronIdsToFileController(
					bdv.getViewer(),
					canvas,
					labels.get( 0 ).getMipmapTransforms()[ 0 ],
					assignment,
					selectionController,
					cellDimensions,
					config );

			bindings.addBehaviourMap( "select", selectionController.getBehaviourMap() );
			bindings.addInputTriggerMap( "select", selectionController.getInputTriggerMap() );

			bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );

			bindings.addBehaviourMap( "brush", brushController.getBehaviourMap() );
			bindings.addInputTriggerMap( "brush", brushController.getInputTriggerMap() );

			bindings.addBehaviourMap( "fill", fillController.getBehaviourMap() );
			bindings.addInputTriggerMap( "fill", fillController.getInputTriggerMap() );

			bindings.addBehaviourMap( "store", storeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "store", storeController.getInputTriggerMap() );

			bdv.getViewerFrame().setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );

			// TODO I hate this, but we need to prevent the ViewerFrame's
			// listener from calling stop on the viewer
			final WindowListener[] listeners = bdv.getViewerFrame().getWindowListeners();
			for ( final WindowListener wl : listeners )
				bdv.getViewerFrame().removeWindowListener( wl );

			bdv.getViewerFrame().addWindowListener( new WindowAdapter()
			{
				@Override
				public void windowClosing( final WindowEvent we )
				{
					final boolean reallyClose = saveBeforeClosing( params );
					if ( reallyClose )
					{
						bdv.getViewerFrame().getViewerPanel().stop();
						bdv.getViewerFrame().setVisible( false );
						// TODO really shouldn't kill the whole jvm in case some
						// other process (e.g. fiji eventually) calls bigcat
						System.exit( 0 );
					}
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

	/**
	 * Creates a label loader, a label canvas pair and the converted pair and
	 * adds them to the respective lists.
	 *
	 * Depends on {@link #canvas} and {@link #colorStream} being initialized.
	 *
	 * Modifies {@link #labels}, {@link #setupId},
	 * {@link #convertedLabelCanvasPairs}.
	 *
	 * @param reader
	 * @param labelDataset
	 * @throws IOException
	 */
	@Override
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

		/* pair labels */
		final RandomAccessiblePair< VolatileLabelMultisetType, LongType > labelCanvasPair =
				new RandomAccessiblePair<>(
						labelLoader.getVolatileImage( 0, 0 ),
						canvas );

		/* converted pair */
		final ARGBConvertedLabelPairSource convertedLabelCanvasPair =
				new ARGBConvertedLabelPairSource(
						setupId++,
						labelCanvasPair,
						canvas, // as Interval, used just for the size
						labelLoader.getMipmapTransforms(),
						colorStream );

		labels.add( labelLoader );
		convertedLabels.add( convertedLabelCanvasPair );
	}

	/**
	 * Create dialog for before shutdown saving, and save if confirmed.
	 *
	 * @param params
	 * @return
	 */
	public boolean saveBeforeClosing( final Parameters params )
	{
		final String message = "Save changes to " + params.inFile + " before closing?";

		final int option = JOptionPane.showConfirmDialog(
				bdv.getViewerFrame(),
				message,
				bdv.getViewerFrame().getTitle(),
				JOptionPane.YES_NO_CANCEL_OPTION );

		final boolean save = option == JOptionPane.YES_OPTION;
		final boolean reallyClose = save || option == JOptionPane.NO_OPTION;

		if ( save )
		{
			bdv.getViewerFrame().setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
			annotationsController.saveAnnotations();
			persistenceController.saveNextId();
			persistenceController.saveFragmentSegmentAssignment();
			persistenceController.savePaintedLabels();
		}
		return reallyClose;
	}
}
