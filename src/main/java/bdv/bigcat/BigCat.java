package bdv.bigcat;

import java.awt.Cursor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JOptionPane;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.io.yaml.YamlConfigIO;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import bdv.BigDataViewer;
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
import bdv.bigcat.control.SelectionController;
import bdv.bigcat.control.TranslateZController;
import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.IdPicker;
import bdv.bigcat.label.LabelMultiSetIdPicker;
import bdv.bigcat.label.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.ui.ARGBConvertedLabelPairSource;
import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.AbstractARGBConvertedLabelsSource;
import bdv.bigcat.ui.GoldenAngleSaturatedARGBStream;
import bdv.bigcat.ui.GoldenAngleSaturatedConfirmSwitchARGBStream;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.AbstractH5SetupImageLoader;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.RandomAccessibleInterval;
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

public class BigCat
{
	public static class Parameters {

		@Parameter(names={"--file", "-f"}, description = "CREMI-format HDF5 file name")
		public String file;

		@Parameter(names={"--raw", "-r"}, description = "Name of raw volume to show in overlay (defaults to 'raw')")
		public String rawDataset = "raw";

		@Parameter(names={"--labels", "-l"}, description = "Name of label volume to show in overlay (defaults to 'neuron_ids')")
		public String labelsDataset = "neuron_ids";

		@Parameter(names={"--lut"}, description = "Name of fragment-segment LUT dataset (defaults to 'fragment_segment_lut')")
		public String fragmentSegmentLutDataset = "fragment_segment_lut";

		@Parameter(names={"--readonly", "-ro"}, description = "Do not load label editing extensions. Faster, consumes less memory.")
		public boolean readOnly;
	}

	final static private int[] cellDimensions = new int[]{ 64, 64, 8 };

	private H5LabelMultisetSetupImageLoader fragments = null;
	private AbstractARGBConvertedLabelsSource convertedLabels = null;
	private CellImg< LongType, ?, ? > paintedLabels = null;
	private BigDataViewer bdv;
	private GoldenAngleSaturatedConfirmSwitchARGBStream colorStream;
	private FragmentSegmentAssignment assignment;
	private final String projectFile;
	private final String paintedLabelsDataset;
	private final String mergedLabelsDataset;
	private String fragmentSegmentLutDataset;
	private LabelPersistenceController persistenceController;
	private AnnotationsController annotationsController;
	private final InputTriggerConfig config;

	private final IdService idService = new LocalIdService();

	private boolean loadPaintedLabels = false;

	public static void main( final String[] args ) throws Exception
	{

		final Parameters params = new Parameters();
		new JCommander(params, args);
		new BigCat(params);
	}

	public BigCat(Parameters params) throws Exception
	{
		Util.initUI();

		this.config = getInputTriggerConfig();

		projectFile = params.file;

		String labelsDataset = params.labelsDataset;
		String rawDataset = params.rawDataset;
		loadPaintedLabels = !params.readOnly;

		fragmentSegmentLutDataset = "/" + params.fragmentSegmentLutDataset;

		System.out.println( "Opening " + projectFile );
		final IHDF5Reader reader = HDF5Factory.open( projectFile );

		// support both file_format 0.0 and >=0.1
		final String volumesPath = reader.isGroup( "/volumes" ) ? "/volumes" : "";
		final String labelsPath = reader.isGroup( volumesPath + "/labels" ) ? volumesPath + "/labels" : "";

		/* raw pixels */
		final String rawPath = volumesPath + "/" + rawDataset;
		final H5UnsignedByteSetupImageLoader raw = new H5UnsignedByteSetupImageLoader( reader, rawPath, 0, cellDimensions );

		/* fragments */
		String fragmentsPath = labelsPath + "/" + labelsDataset;
		mergedLabelsDataset = labelsPath + "/merged_" + labelsDataset;
		paintedLabelsDataset = labelsPath + "/painted_" + labelsDataset;
		fragmentsPath = reader.object().isDataSet( mergedLabelsDataset ) ? mergedLabelsDataset : fragmentsPath;
		if ( reader.exists( fragmentsPath ) )
			readFragments( params, reader, fragmentsPath, paintedLabelsDataset, fragmentSegmentLutDataset );
		else
			System.out.println( "no labels found cooresponding to requested dataset '" + labelsDataset + "' (searched in '" + labelsPath + "')" );

		setupBdv( raw );
	}

	private void readFragments(
			final Parameters params,
			final IHDF5Reader reader,
			final String labelsDataset,
			final String paintedLabelsDataset,
			final String fragmentSegmentLutDataset ) throws IOException
	{
		System.out.println("Reading fragments...");
		fragments =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelsDataset,
						1,
						cellDimensions );
		final RandomAccessibleInterval< VolatileLabelMultisetType > fragmentsPixels = fragments.getVolatileImage( 0, 0 );
		final long[] fragmentsDimensions = Intervals.dimensionsAsLongArray( fragmentsPixels );

		assignment = new FragmentSegmentAssignment( idService );
		final TLongLongHashMap lut = H5Utils.loadLongLongLut( reader, fragmentSegmentLutDataset, 1024 );
		if ( lut != null )
			assignment.initLut( lut );

		colorStream = new GoldenAngleSaturatedConfirmSwitchARGBStream( assignment );
		colorStream.setAlpha( 0x20 );

		if (!loadPaintedLabels) {

			final GoldenAngleSaturatedARGBStream cs = new GoldenAngleSaturatedARGBStream( assignment );
			cs.setAlpha( 0x20 );

			convertedLabels =
					new ARGBConvertedLabelsSource(
							3,
							fragments,
							cs);
			return;
		}

		System.out.println("Finding max ID");

		final Long nextIdObject = H5Utils.loadAttribute( reader, "/", "next_id" );
		long maxId = 0;
		if ( nextIdObject == null )
		{
			maxId = maxId( reader, labelsDataset, maxId );
			if ( reader.exists( paintedLabelsDataset ) )
				maxId = maxId( reader, paintedLabelsDataset, maxId );
		}
		else
			maxId = nextIdObject.longValue() - 1;

		idService.invalidate( maxId );

		final String paintedLabelsFilePath = params.file;
		final File paintedLabelsFile = new File( paintedLabelsFilePath );
		if ( paintedLabelsFile.exists() && reader.exists( paintedLabelsDataset ) ) {
			System.out.println("Reading painted labels...");
			paintedLabels = H5Utils.loadUnsignedLong( new File( paintedLabelsFilePath ), paintedLabelsDataset, cellDimensions );
		} else {
			System.out.println("Creating painted labels...");
			paintedLabels = new CellImgFactory< LongType >( cellDimensions ).create( fragmentsDimensions, new LongType() );
			for ( final LongType t : paintedLabels )
				t.set( Label.TRANSPARENT );
		}

		/* pair labels */
		final RandomAccessiblePair< VolatileLabelMultisetType, LongType > labelPair =
				new RandomAccessiblePair<>(
						fragments.getVolatileImage( 0, 0 ),
						paintedLabels );
		convertedLabels =
				new ARGBConvertedLabelPairSource(
						3,
						labelPair,
						paintedLabels, // as Interval, used just for the size
						fragments.getMipmapTransforms(),
						colorStream );
	}

	@SuppressWarnings( "unchecked" )
	private void setupBdv( final H5UnsignedByteSetupImageLoader raw ) throws Exception
	{
		System.out.println("Setting up BDV");

		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite< ARGBType, ARGBType > >();
		composites.add( new CompositeCopy< ARGBType >() );

		final String windowTitle = "BigCAT";

		if ( fragments != null )
		{
			composites.add( new ARGBCompositeAlphaYCbCr() );
			//composites.add( new ARGBCompositeAlphaMultiply() );
			bdv = Util.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
				new AbstractARGBConvertedLabelsSource[]{ convertedLabels },
				new SetCache[]{ fragments },
				composites,
				config );
		}
		else
		{
			bdv = Util.createViewer(
				windowTitle,
				new AbstractH5SetupImageLoader[]{ raw },
				new ARGBConvertedLabelPairSource[]{},
				new SetCache[]{},
				composites,
				config );
		}

		bdv.getViewerFrame().setVisible( true );

		final TriggerBehaviourBindings bindings = bdv.getViewerFrame().getTriggerbindings();

		final SelectionController selectionController;
		final LabelBrushController brushController;
		final IdPicker idPicker;

		if ( fragments != null )
		{
			if (paintedLabels != null) {

				idPicker = new PairLabelMultiSetLongIdPicker(
						bdv.getViewer(),
						RealViews.affineReal(
								Views.interpolate(
										new RandomAccessiblePair< LabelMultisetType, LongType >(
												Views.extendValue(
													fragments.getImage( 0 ),
													new LabelMultisetType() ),
												Views.extendValue(
														paintedLabels,
														new LongType( Label.OUTSIDE) ) ),
										new NearestNeighborInterpolatorFactory< Pair< LabelMultisetType, LongType > >() ),
								fragments.getMipmapTransforms()[ 0 ] )
						);

			} else {

				idPicker = new LabelMultiSetIdPicker(
						bdv.getViewer(),
						RealViews.affineReal(
								Views.interpolate(
										Views.extendValue(
												fragments.getImage( 0 ),
												new LabelMultisetType() ),
										new NearestNeighborInterpolatorFactory< LabelMultisetType >() ),
								fragments.getMipmapTransforms()[ 0 ] )
						);
			}

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

			if (loadPaintedLabels) {

				brushController = new LabelBrushController(
						bdv.getViewer(),
						paintedLabels,
						fragments.getMipmapTransforms()[ 0 ],
						assignment,
						selectionController,
						projectFile,
						paintedLabelsDataset,
						cellDimensions,
						config);

				final double[] labelResolution = fragments.getMipmapResolutions()[0];

				persistenceController = new LabelPersistenceController(
						bdv.getViewer(),
						fragments.getImage( 0 ),
						paintedLabels,
						labelResolution,
						assignment,
						idService,
						projectFile,
						paintedLabelsDataset,
						mergedLabelsDataset,
						cellDimensions,
						fragmentSegmentLutDataset,
						config,
						bdv.getViewerFrame().getKeybindings() );

				final LabelFillController fillController = new LabelFillController(
						bdv.getViewer(),
						fragments.getImage( 0 ),
						paintedLabels,
						fragments.getMipmapTransforms()[ 0 ],
						assignment,
						selectionController,
						new DiamondShape( 1 ),
						idPicker,
						config);

				/* splitter (and more) */
				final DrawProjectAndIntersectController dpi = new DrawProjectAndIntersectController(
						bdv,
						idService,
						new AffineTransform3D(),
						new InputTriggerConfig(),
						fragments.getImage(0),
						paintedLabels,
						fragments.getMipmapTransforms()[0],
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

				bindings.addBehaviourMap( "brush", brushController.getBehaviourMap() );
				bindings.addInputTriggerMap( "brush", brushController.getInputTriggerMap() );

				bindings.addBehaviourMap( "fill", fillController.getBehaviourMap() );
				bindings.addInputTriggerMap( "fill", fillController.getInputTriggerMap() );

			} else {

				brushController = null;
			}

			bindings.addBehaviourMap( "select", selectionController.getBehaviourMap() );
			bindings.addInputTriggerMap( "select", selectionController.getInputTriggerMap() );

			bindings.addBehaviourMap( "merge", mergeController.getBehaviourMap() );
			bindings.addInputTriggerMap( "merge", mergeController.getInputTriggerMap() );

			if (loadPaintedLabels) {
				bdv.getViewerFrame().addWindowListener( new WindowAdapter()
				{
					@Override
					public void windowClosing( final WindowEvent we )
					{
						saveBeforeClosing();
						System.exit( 0 );
					}
				} );
			}
		}
		else
		{
			selectionController = null;
			brushController = null;
			idPicker = null;
		}

		final TranslateZController translateZController = new TranslateZController(
				bdv.getViewer(),
				raw.getMipmapResolutions()[ 0 ],
				config );
		bindings.addBehaviourMap( "translate_z", translateZController.getBehaviourMap() );

		final AnnotationsHdf5Store annotationsStore = new AnnotationsHdf5Store( projectFile, idService );
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

	protected InputTriggerConfig getInputTriggerConfig() throws IllegalArgumentException {

		final String[] filenames = {
				"bigcatkeyconfig.yaml",
				System.getProperty( "user.home" ) + "/.bdv/bigcatkeyconfig.yaml"
		};

		for (final String filename : filenames) {

			try {
				if (new File(filename).isFile()) {
					System.out.println("reading key config from file " + filename);
					return new InputTriggerConfig(YamlConfigIO.read(filename));
				}
			} catch (final IOException e) {
				System.err.println("Error reading " + filename);
			}
		}

		System.out.println("creating default input trigger config");

		// default input trigger config, disables "control button1" drag in bdv
		// (collides with default of "move annotation")
		final InputTriggerConfig config = new InputTriggerConfig(
				Arrays.asList(
						new InputTriggerDescription[] {
								new InputTriggerDescription( new String[] { "not mapped" }, "drag rotate slow", "bdv" )
						}
				)
		);

		return config;
	}

	public void saveBeforeClosing()
	{
		final String message = "Save changes to " + projectFile + " before closing?";

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
			final IHDF5Reader reader,
			final String labelsDataset,
			long maxId ) throws IOException
	{
		final H5LabelMultisetSetupImageLoader labelLoader =
				new H5LabelMultisetSetupImageLoader(
						reader,
						null,
						labelsDataset,
						1,
						cellDimensions );

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
}
