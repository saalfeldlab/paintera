package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.viewer.ViewerOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final PainteraBaseView baseView = new PainteraBaseView(
			Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) ),
			ViewerOptions.options().screenScales( new double[] { 1.0, 0.5, 0.25 } ),
			si -> s -> si.getState( s ).interpolationProperty().get() );

	private final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

	private final KeyTracker keyTracker = new KeyTracker();

	final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
			baseView,
			keyTracker );

	final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );


	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		final Stage stage = new Stage();
		final Scene scene = new Scene( paneWithStatus.getPane() );
		if ( LOG.isDebugEnabled() )
		{
			scene.focusOwnerProperty().addListener( ( obs, oldv, newv ) -> LOG.debug( "Focus changed: old={} new={}", oldv, newv ) );
		}
		Platform.setImplicitExit( true );

		setFocusTraversable( orthoViews, false );

		stage.setOnCloseRequest( event -> baseView.stop() );

		keyTracker.installInto( scene );
		stage.setScene( scene );
		stage.setWidth( 800 );
		stage.setHeight( 600 );
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

}
