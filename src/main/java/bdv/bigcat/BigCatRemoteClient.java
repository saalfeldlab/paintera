package bdv.bigcat;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import org.zeromq.ZContext;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

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
import bdv.bigcat.label.PairLabelMultiSetLongIdPicker;
import bdv.bigcat.ui.Util;
import bdv.img.SetCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.img.h5.H5UnsignedByteSetupImageLoader;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.LocalIdService;
import bdv.util.RemoteIdService;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

public class BigCatRemoteClient extends BigCat< BigCatRemoteClient.Parameters >
{
	static public class Parameters extends BigCat.Parameters
	{
		@Parameter( names = { "--broker", "-b" }, description = "URL to configuration broker" )
		public String config = "";

		private Config brokerConfig;

		public void loadConfig() throws FileNotFoundException, IOException
		{
			try ( final FileReader reader = new FileReader( new File( config ) ) )
			{
				brokerConfig = new Gson().fromJson( reader, Config.class );
			}
		}
	}

	final private ZContext ctx;

	public static void main( final String[] args ) throws Exception
	{
		final Parameters params = new Parameters();
		new JCommander( params, args );
		params.init();
		params.loadConfig();
		final BigCatRemoteClient bigCat = new BigCatRemoteClient();
		bigCat.init( params );
		bigCat.setupBdv( params );
	}

	@Override
	protected void initIdService( final Parameters params ) throws IOException
	{
		/* id service */
		if ( params.brokerConfig.id_service_url != null )
			idService = new RemoteIdService( ctx, params.brokerConfig.id_service_url );
		else
			idService = new LocalIdService();
	}

	public BigCatRemoteClient() throws Exception
	{
		super();
		ctx = new ZContext();
	}

	@Override
	protected void setupBdv( final Parameters params ) throws Exception
	{
		/* composites */
		final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< >();
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
		final LabelBrushController brushController;
		final PairLabelMultiSetLongIdPicker idPicker;

		if ( labels.size() > 0 )
		{
			/* TODO fix ID picker to pick from the top most label canvas pair */
			idPicker = new PairLabelMultiSetLongIdPicker(
					bdv.getViewer(),
					RealViews.affineReal(
							Views.interpolate(
									new RandomAccessiblePair< >(
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
					params.brokerConfig.solver_url,
					config,
					bdv.getViewerFrame().getKeybindings(),
					config);

			/* TODO fix to deal with correct transform */
			brushController = new LabelBrushController(
					bdv.getViewer(),
					canvas,
					dirtyLabelsInterval,
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
					labels.get( 0 ).getMipmapResolutions()[ 0 ],
					dirtyLabelsInterval,
					assignment,
					completeFragmentsAssignment,
					idService,
					params.inFile,
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
					labels.get( 0 ).getImage(0),
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
}