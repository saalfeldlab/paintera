package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import bdv.viewer.Source;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
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

		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );

		// populate everything

		final Optional< Properties > loadedProperties = loadPropertiesIfPresent(
				painteraArgs.project(),
				GsonHelpers.builderWithAllRequiredAdapters( baseView ).setPrettyPrinting() );

		final Properties properties = new Properties( baseView.sourceInfo() );
		baseView.manager().addListener( properties );

		if ( loadedProperties.isPresent() )
		{

			final SourceInfo loadedSourceInfo = loadedProperties.get().sources;

			for ( final Source< ? > source : loadedSourceInfo.trackSources() )
			{
				final SourceState< ?, ? > state = loadedSourceInfo.getState( source );
				LOG.debug( "Adding source {} and state {}", source, state );
				if ( state instanceof LabelSourceState< ?, ? > )
				{
					final LabelSourceState< ?, ? > lstate = ( LabelSourceState< ?, ? > ) state;
					baseView.addLabelSource( ( LabelSourceState ) lstate );
					final long[] selIds = lstate.selectedIds().getActiveIds();
					final long lastId = lstate.selectedIds().getLastSelection();
					lstate.selectedIds().deactivateAll();
					lstate.selectedIds().activate( selIds );
					lstate.selectedIds().activateAlso( lastId );
				}
				else
				{
					baseView.addRawSource( ( SourceState ) state );
				}
			}
			baseView.sourceInfo().currentSourceIndexProperty().set( loadedProperties.get().sources.currentSourceIndexProperty().get() );
		}

		LOG.debug( "Adding {} raw sources: {}", painteraArgs.rawSources().length, painteraArgs.rawSources() );
		for ( final String source : painteraArgs.rawSources() )
			addRawFromString( baseView, source );

		LOG.debug( "Adding {} label sources: {}", painteraArgs.labelSources().length, painteraArgs.labelSources() );
		for ( final String source : painteraArgs.labelSources() )
			addLabelFromString( baseView, source );

		loadedProperties.map( Properties::globalTransformCopy ).ifPresent( baseView.manager()::setTransform );

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
						persistProperties( painteraArgs.project(), properties, GsonHelpers.builderWithAllRequiredAdapters( baseView ).setPrettyPrinting() );
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

	public static Optional< Properties > loadPropertiesIfPresent( final String root, final GsonBuilder builder )
	{
		try
		{
			final Properties properties = N5Helpers.n5Reader( root, builder, 64, 64, 64 ).getAttribute( "", "paintera", Properties.class );
			return Optional.of( properties );
		}
		catch ( final IOException | NullPointerException e )
		{
			return Optional.empty();
		}
	}

	public static void persistProperties( final String root, final Properties properties, final GsonBuilder builder ) throws IOException
	{
		N5Helpers.n5Writer( root, builder, 64, 64, 64 ).setAttribute( "", PAINTERA_KEY, properties );
	}

}
