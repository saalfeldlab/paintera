package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.project.AffineTransform3DJsonAdapter;
import org.janelia.saalfeldlab.paintera.project.CompositeSerializer;
import org.janelia.saalfeldlab.paintera.project.PainteraProject;
import org.janelia.saalfeldlab.paintera.project.ProjectDirectoryNotSetException;
import org.janelia.saalfeldlab.paintera.project.SelectedIdsSerializer;
import org.janelia.saalfeldlab.paintera.project.SourceInfoSerializer;
import org.janelia.saalfeldlab.paintera.project.SourceStateSerializer;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.SourceInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.viewer.ViewerOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import picocli.CommandLine;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

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

		final PainteraProject project = loadOrInitProject( painteraArgs.project() );
		final AffineTransform3D initialTransform = project.globalTransformCopy();
		baseView.manager().setTransform( project.globalTransformCopy() );

		final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

		final KeyTracker keyTracker = new KeyTracker();

		final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
				baseView,
				keyTracker );

		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );

		for ( final String source : painteraArgs.rawSources() )
			addRawFromString( baseView, source );

		for ( final String source : painteraArgs.labelSources() )
			addLabelFromString( baseView, source );

		if ( !Arrays.equals( new AffineTransform3D().getRowPackedCopy(), initialTransform.getRowPackedCopy() ) )
			baseView.manager().setTransform( initialTransform );

		project.setViewer( baseView );
		project.setWidth( painteraArgs.width( project.width() ) );
		project.setHeight( painteraArgs.width( project.height() ) );

		final Scene scene = new Scene( paneWithStatus.getPane() );
		if ( LOG.isDebugEnabled() )
		{
			scene.focusOwnerProperty().addListener( ( obs, oldv, newv ) -> LOG.debug( "Focus changed: old={} new={}", oldv, newv ) );
		}

		setFocusTraversable( orthoViews, false );
		stage.setOnCloseRequest( event -> {

			if ( project.isDirty() )
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
						project.persist();
					}
					catch ( final ProjectDirectoryNotSetException e )
					{
						LOG.error( "Project directory not set, prompt not implemented yet. Select NO in dialog." );
						event.consume();
						return;
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

		final int initialWidth = project.width();
		final int initialHeight = project.height();

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.setWidth( initialWidth );
		stage.setHeight( initialHeight );

		new Thread( () -> {
			try
			{
				final Gson gson = new GsonBuilder()
						.registerTypeHierarchyAdapter( SourceInfo.class, new SourceInfoSerializer() )
						.registerTypeAdapter( SourceState.class, new SourceStateSerializer(
								baseView.getPropagationQueue(),
								baseView.getMeshManagerExecutorService(),
								baseView.getMeshWorkerExecutorService(),
								baseView.getQueue(),
								0,
								baseView.viewer3D().meshesGroup() ) )
						.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() )
						.registerTypeHierarchyAdapter( Composite.class, new CompositeSerializer() )
						.registerTypeAdapter( SelectedIds.class, new SelectedIdsSerializer() )
//						.setPrettyPrinting()
						.create();
				System.out.println( "WAT" );
				final String json = gson.toJson( baseView.sourceInfo().getState( baseView.sourceInfo().currentSourceProperty().get() ), org.janelia.saalfeldlab.paintera.state.SourceState.class );
				System.out.println( "serialized:  " + json );
				final SourceState< ?, ? > state = gson.fromJson( json, SourceState.class );
				System.out.println( "state " + state + " " + ( state == null ) );
				System.out.println( "serialized2: " + gson.toJson( state ) );
//				baseView.addRawSource( ( SourceState ) state );
				final LabelSourceState< ?, ? > labelState = ( LabelSourceState< ?, ? > ) baseView.sourceInfo().getState( baseView.sourceInfo().trackSources().get( 1 ) );
				labelState.selectedIds().activate( 1, 2, 3 );
				labelState.selectedIds().activateAlso( 8173 );
				final String jsonLabel = gson.toJson( labelState, SourceState.class );
				System.out.println( "serialized3: " + jsonLabel );
				final LabelSourceState< ?, ? > labelState2 = ( LabelSourceState< ?, ? > ) gson.fromJson( jsonLabel, SourceState.class );
				final String jsonLabel2 = gson.toJson( labelState2, SourceState.class );
				System.out.println( "serialized4: " + jsonLabel2 );
				final long lastSelection = labelState2.selectedIds().getLastSelection();
				final long[] selectedIds = labelState2.selectedIds().getActiveIds();
				baseView.addLabelSource( ( LabelSourceState ) labelState2 );
				labelState2.selectedIds().deactivateAll();
				labelState2.selectedIds().activate( selectedIds );
				labelState2.selectedIds().activateAlso( lastSelection );
			}
			catch ( final Exception e )
			{
				System.out.println( e.getMessage() );
				e.printStackTrace();
			}
		} ).start();

		stage.widthProperty().addListener( ( obs, oldv, newv ) -> project.setWidth( newv.intValue() ) );
		stage.heightProperty().addListener( ( obs, oldv, newv ) -> project.setHeight( newv.intValue() ) );
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

	private static < D extends RealType< D > & NativeType< D >, T extends Volatile< D > & RealType< T > > Optional< DataSource< D, T > > addRawFromString(
			final PainteraBaseView pbv,
			final String identifier ) throws IOException
	{
		if ( !Pattern.matches( "^[a-z]+://.+", identifier ) )
			return addRawFromString( pbv, "file://" + identifier );

		if ( Pattern.matches( "^file://.+", identifier ) )
		{
			final String[] split = identifier.replaceFirst( "file://", "" ).split( ":" );
			final N5Writer n5 = N5Helpers.n5Writer( split[ 0 ], 64, 64, 64 );
			return pbv.addRawSource( n5, split[ 1 ] );
		}

		LOG.warn( "Unable to generate raw source from {}", identifier );
		return Optional.empty();
	}

	private static void addLabelFromString( final PainteraBaseView pbv, final String identifier ) throws IOException
	{
		if ( !Pattern.matches( "^[a-z]+://.+", identifier ) )
		{
			addLabelFromString( pbv, "file://" + identifier );
			return;
		}

		if ( Pattern.matches( "^file://.+", identifier ) )
		{
			final String[] split = identifier.replaceFirst( "file://", "" ).split( ":" );
			final N5Writer n5 = N5Helpers.n5Writer( split[ 0 ], 64, 64, 64 );
			final String dataset = split[ 1 ];
			final double[] resolution = Optional.ofNullable( n5.getAttribute( dataset, "resolution", double[].class ) ).orElse( new double[] { 1.0, 1.0, 1.0 } );
			final double[] offset = Optional.ofNullable( n5.getAttribute( dataset, "offset", double[].class ) ).orElse( new double[] { 0.0, 0.0, 0.0 } );
			pbv.addLabelSource( n5, dataset, resolution, offset );
		}
	}

	private static double[] screenScales()
	{

		int maxSize = 0;
		for ( final Screen screen : Screen.getScreens() )
		{
			final Rectangle2D bounds = screen.getVisualBounds();
			maxSize = Math.max( ( int ) bounds.getWidth(), maxSize );
			maxSize = Math.max( ( int ) bounds.getHeight(), maxSize );
		}

		LOG.debug( "max screen size = {}", maxSize );

		final double[] screenScales = maxSize < 2500
				? new double[] { 1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0 }
				: new double[] { 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0, 1.0 / 16.0 };
		return screenScales;
	}

	PainteraProject loadOrInitProject( final String root )
	{
		try
		{
			final PainteraProject project = N5Helpers.n5Reader( root, PainteraProject.builder, 64, 64, 64 ).getAttribute( "", "paintera", PainteraProject.class );
			project.setProjectDirectory( root, false );
			return project;
		}
		catch ( final IOException | NullPointerException e )
		{
			final PainteraProject project = new PainteraProject();
			project.setProjectDirectory( root );
			return project;
		}
	}

}
