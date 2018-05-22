package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import bdv.viewer.ViewerOptions;
import gnu.trove.set.hash.TLongHashSet;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import picocli.CommandLine;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String PAINTERA_KEY = "paintera";

	@Override
	public void start( final Stage stage ) throws Exception
	{

		final Parameters parameters = getParameters();
		final String[] args = parameters.getRaw().stream().toArray( String[]::new );
		final PainteraCommandLineArgs painteraArgs = new PainteraCommandLineArgs();
		final boolean parsedSuccessfully = Optional.ofNullable( CommandLine.call( painteraArgs, System.err, args ) ).orElse( false );
		Platform.setImplicitExit( true );

		if ( !parsedSuccessfully )
		{
			Platform.exit();
			return;
		}

		final PainteraBaseView baseView = new PainteraBaseView(
				Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) ),
				ViewerOptions.options().screenScales( screenScales() ),
				si -> s -> si.getState( s ).interpolationProperty().get() );

		final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

		final KeyTracker keyTracker = new KeyTracker();

		final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
				baseView,
				keyTracker );

		@SuppressWarnings( "unused" )
		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );

		// populate everything

		final Optional< JsonObject > loadedProperties = loadPropertiesIfPresent( painteraArgs.project() );

		// TODO this can probably be hidden in
		// Properties.fromSerializedProperties
		final Map< Integer, SourceState< ?, ? > > indexToState = new HashMap<>();

		final Properties properties = loadedProperties.map( lp -> Properties.fromSerializedProperties( lp, baseView, true, painteraArgs::project, indexToState ) ).orElse( new Properties( baseView ) );

		// TODO this should probably happen in the properties.populate:
		properties.sourceInfo
		.trackSources()
		.stream()
		.map( properties.sourceInfo::getState )
		.filter( state -> state instanceof LabelSourceState< ?, ? > )
		.map( state -> ( LabelSourceState< ?, ? > ) state )
		.forEach( state -> {
			final long[] selIds = state.selectedIds().getActiveIds();
			final long lastId = state.selectedIds().getLastSelection();
			state.selectedIds().deactivateAll();
			state.selectedIds().activate( selIds );
			state.selectedIds().activateAlso( lastId );
		} );
		properties.clean();

		LOG.debug( "Adding {} raw sources: {}", painteraArgs.rawSources().length, painteraArgs.rawSources() );
		for ( final String source : painteraArgs.rawSources() )
		{
			addRawFromString( baseView, source );
		}

		LOG.debug( "Adding {} label sources: {}", painteraArgs.labelSources().length, painteraArgs.labelSources() );
		for ( final String source : painteraArgs.labelSources() )
		{
			addLabelFromString( baseView, source );
		}

		properties.windowProperties.widthProperty.set( painteraArgs.width( properties.windowProperties.widthProperty.get() ) );
		properties.windowProperties.heightProperty.set( painteraArgs.height( properties.windowProperties.heightProperty.get() ) );

		properties.clean();

		final Scene scene = new Scene( paneWithStatus.getPane() );
		if ( LOG.isDebugEnabled() )
		{
			scene.focusOwnerProperty().addListener( ( obs, oldv, newv ) -> LOG.debug( "Focus changed: old={} new={}", oldv, newv ) );
		}

		setFocusTraversable( orthoViews, false );
		stage.setOnCloseRequest( event -> {

			if ( properties.isDirty() )
			{
				final Dialog< ButtonType > d = new Dialog<>();
				d.setHeaderText( "Save before exit?" );
				final ButtonType saveButton = new ButtonType( "Yes", ButtonData.OK_DONE );
				final ButtonType discardButton = new ButtonType( "No", ButtonData.NO );
				final ButtonType cancelButton = new ButtonType( "Cancel", ButtonData.CANCEL_CLOSE );
				d.getDialogPane().getButtonTypes().setAll( saveButton, discardButton, cancelButton );
				final ButtonType response = d.showAndWait().orElse( ButtonType.CANCEL );

				if ( cancelButton.equals( response ) )
				{
					LOG.debug( "Canceling close request." );
					event.consume();
					return;
				}

				if ( saveButton.equals( response ) )
				{
					LOG.debug( "Saving project before exit" );
					try
					{
						persistProperties( painteraArgs.project(), properties, GsonHelpers.builderWithAllRequiredSerializers( baseView, painteraArgs::project ).setPrettyPrinting() );
					}
					catch ( final IOException e )
					{
						LOG.error( "Unable to write project! Select NO in dialog to close." );
						event.consume();
						return;
					}
				}
				else if ( discardButton.equals( response ) )
				{
					LOG.debug( "Discarding project changes" );
				}

			}

			baseView.stop();
		} );

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.setWidth( properties.windowProperties.widthProperty.get() );
		stage.setHeight( properties.windowProperties.heightProperty.get() );
		properties.windowProperties.widthProperty.bind( stage.widthProperty() );
		properties.windowProperties.heightProperty.bind( stage.heightProperty() );
		properties.setGlobalTransformClean();

		stage.show();
	}

	public static void main( final String[] args )
	{
		launch( args );
	}

	private static void setFocusTraversable(
			final OrthogonalViews< ? > view,
			final boolean isTraversable )
	{
		view.topLeft().viewer().setFocusTraversable( isTraversable );
		view.topRight().viewer().setFocusTraversable( isTraversable );
		view.bottomLeft().viewer().setFocusTraversable( isTraversable );
		view.grid().getBottomRight().setFocusTraversable( isTraversable );
	}

	private static < D extends NativeType< D > & RealType< D >, T extends Volatile< D > & NativeType< T > & RealType< T > > Optional< DataSource< D, T > > addRawFromString(
			final PainteraBaseView pbv,
			final String identifier ) throws UnableToAddSource
	{
		if ( !Pattern.matches( "^[a-z]+://.+", identifier ) ) { return addRawFromString( pbv, "file://" + identifier ); }

		if ( Pattern.matches( "^file://.+", identifier ) )
		{
			try
			{
				final String[] split = identifier.replaceFirst( "file://", "" ).split( ":" );
				final N5Reader reader = N5Helpers.n5Reader( split[ 0 ], 64, 64, 64 );
				final String dataset = split[ 1 ];
				final String name = N5Helpers.lastSegmentOfDatasetPath( dataset );

				final DataSource< D, T > source = N5Helpers.openRawAsSource(
						reader,
						dataset,
						N5Helpers.getTransform( reader, dataset ),
						pbv.getQueue(),
						0,
						name );

				final RawSourceState< D, T > state = new RawSourceState<>(
						source,
						new ARGBColorConverter.Imp1<>(),
						new CompositeCopy<>(),
						name );

				pbv.addRawSource( state );
				return Optional.of( state.getDataSource() );
			}
			catch ( final Exception e )
			{
				throw e instanceof UnableToAddSource ? ( UnableToAddSource ) e : new UnableToAddSource( e );
			}
		}

		LOG.warn( "Unable to generate raw source from {}", identifier );
		return Optional.empty();
	}

	private static < D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > void addLabelFromString( final PainteraBaseView pbv, final String identifier ) throws UnableToAddSource
	{
		if ( !Pattern.matches( "^[a-z]+://.+", identifier ) )
		{
			addLabelFromString( pbv, "file://" + identifier );
			return;
		}

		if ( Pattern.matches( "^file://.+", identifier ) )
		{
			try
			{
				final String[] split = identifier.replaceFirst( "file://", "" ).split( ":" );
				final N5Writer n5 = N5Helpers.n5Writer( split[ 0 ], 64, 64, 64 );
				final String dataset = split[ 1 ];
				final double[] resolution = Optional.ofNullable( n5.getAttribute( dataset, "resolution", double[].class ) ).orElse( new double[] { 1.0, 1.0, 1.0 } );
				final double[] offset = Optional.ofNullable( n5.getAttribute( dataset, "offset", double[].class ) ).orElse( new double[] { 0.0, 0.0, 0.0 } );
				final AffineTransform3D transform = N5Helpers.fromResolutionAndOffset( resolution, offset );
				final TmpDirectoryCreator nextCanvasDir = new TmpDirectoryCreator( null, null );
				final String name = N5Helpers.lastSegmentOfDatasetPath( dataset );
				final SelectedIds selectedIds = new SelectedIds();
				final FragmentSegmentAssignmentState assignment = N5Helpers.assignments( n5, dataset );
				final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );
				final DataSource< D, T > dataSource = N5Helpers.openAsLabelSource(
						n5,
						dataset,
						transform,
						pbv.getQueue(),
						0,
						name );

				final DataSource< D, T > maskedSource = Masks.mask(
						dataSource,
						nextCanvasDir.get(),
						nextCanvasDir,
						new CommitCanvasN5( n5, dataset ),
						pbv.getPropagationQueue() );

				final InterruptibleFunction< Long, Interval[] >[] blockListCache =
						PainteraBaseView.generateLabelBlocksForLabelCache( maskedSource );

				final InterruptibleFunction< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCache =
						CacheUtils.segmentMeshCacheLoaders(
								maskedSource,
								SegmentMaskGenerators.forType( dataSource.getDataType() ),
								CacheUtils::toCacheSoftRefLoaderCache );

				final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );

				final MeshManagerWithAssignmentForSegments meshManager = new MeshManagerWithAssignmentForSegments(
						maskedSource,
						blockListCache,
						meshCache,
						pbv.viewer3D().meshesGroup(),
						assignment,
						selectedSegments,
						stream,
						new SimpleIntegerProperty(),
						new SimpleDoubleProperty(),
						new SimpleIntegerProperty(),
						pbv.getMeshManagerExecutorService(),
						pbv.getMeshWorkerExecutorService() );

				final MeshInfos< TLongHashSet > meshInfos = new MeshInfos< >(
						selectedSegments,
						assignment,
						meshManager,
						maskedSource.getNumMipmapLevels() );

				final LabelSourceState< D, T > state = new LabelSourceState<>(
						maskedSource,
						HighlightingStreamConverter.forType( stream, dataSource.getType() ),
						new ARGBCompositeAlphaYCbCr(),
						name,
						PainteraBaseView.equalsMaskForType( dataSource.getDataType() ),
						SegmentMaskGenerators.forType( dataSource.getDataType() ),
						assignment,
						ToIdConverter.fromType( dataSource.getDataType() ),
						selectedIds,
						N5Helpers.idService( n5, dataset ),
						meshManager,
						meshInfos );
				pbv.addLabelSource( state );
			}
			catch ( final Exception e )
			{
				throw e instanceof UnableToAddSource ? ( UnableToAddSource ) e : new UnableToAddSource( e );
			}
		}
	}

	private static double[] screenScales()
	{

		final int maxSize = ( int ) Screen
				.getScreens()
				.stream()
				.map( Screen::getVisualBounds )
				.mapToDouble( b -> Math.max( b.getWidth(), b.getHeight() ) )
				.max()
				.orElse( 0.0 );

		LOG.debug( "max screen size = {}", maxSize );

		final double[] screenScales = maxSize < 2500
				? new double[] { 1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0 }
		: new double[] { 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0, 1.0 / 16.0 };
				return screenScales;
	}

	public static Optional< JsonObject > loadPropertiesIfPresent( final String root )
	{
		return loadPropertiesIfPresent( root, new GsonBuilder() );
	}

	public static Optional< JsonObject > loadPropertiesIfPresent( final String root, final GsonBuilder builder )
	{
		try
		{
			final JsonObject properties = N5Helpers.n5Reader( root, builder, 64, 64, 64 ).getAttribute( "", "paintera", JsonObject.class );
			return Optional.of( properties );
		}
		catch ( final IOException | NullPointerException e )
		{
			return Optional.empty();
		}
	}

	public static void persistProperties( final String root, final Properties properties, final GsonBuilder builder ) throws IOException
	{
		builder.create().getAdapter( SourceInfo.class );
		LOG.debug( "Persisting properties {} into {}", properties, root );
		N5Helpers.n5Writer( root, builder, 64, 64, 64 ).setAttribute( "", PAINTERA_KEY, properties );
	}

}
