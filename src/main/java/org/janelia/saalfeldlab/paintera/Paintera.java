package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.viewer.ViewerOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.imglib2.Volatile;
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

		int maxSize = 0;
		for ( final Screen screen : Screen.getScreens() )
		{
			Rectangle2D bounds = screen.getVisualBounds();
			maxSize = Math.max( ( int )bounds.getWidth(), maxSize );
			maxSize = Math.max( ( int )bounds.getHeight(), maxSize );
		}

		LOG.debug( "max screen size = {}", maxSize );

		final double[] screenScales = maxSize < 2500 ?
				new double [] { 1.0 / 1.0, 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0}:
				new double [] { 1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0, 1.0 / 16.0};

		final PainteraBaseView baseView = new PainteraBaseView(
				Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) ),
				ViewerOptions.options().screenScales( screenScales ),
				si -> s -> si.getState( s ).interpolationProperty().get() );

		final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

		final KeyTracker keyTracker = new KeyTracker();

		final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
				baseView,
				keyTracker );

		@SuppressWarnings( "unused" )
		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );

		final Scene scene = new Scene( paneWithStatus.getPane() );
		if ( LOG.isDebugEnabled() )
		{
			scene.focusOwnerProperty().addListener( ( obs, oldv, newv ) -> LOG.debug( "Focus changed: old={} new={}", oldv, newv ) );
		}

		for ( final String source : painteraArgs.rawSources() )
			addRawFromString( baseView, source );

		for ( final String source : painteraArgs.labelSources() )
			addLabelFromString( baseView, source );

		setFocusTraversable( orthoViews, false );

		stage.setOnCloseRequest( event -> baseView.stop() );

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.setWidth( painteraArgs.width() );
		stage.setHeight( painteraArgs.height() );
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

	private static < D extends RealType< D > & NativeType< D >, T extends Volatile< D > & RealType< T > & NativeType< T > > Optional< DataSource< D, T > > addRawFromString(
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

}
