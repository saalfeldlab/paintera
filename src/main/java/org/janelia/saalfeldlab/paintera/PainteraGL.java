package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.viewer.ViewerOptions;
import cuchaz.jfxgl.CalledByEventsThread;
import cuchaz.jfxgl.JFXGL;
import cuchaz.jfxgl.JFXGLLauncher;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import picocli.CommandLine;

public class PainteraGL
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static void setFocusTraversable(
			final OrthogonalViews< ? > view,
			final boolean isTraversable )
	{
		view.topLeft().viewer().setFocusTraversable( isTraversable );
		view.topRight().viewer().setFocusTraversable( isTraversable );
		view.bottomLeft().viewer().setFocusTraversable( isTraversable );
		view.grid().getBottomRight().setFocusTraversable( isTraversable );
	}

	public static void main( final String[] args )
	{
		JFXGLLauncher.launchMain( PainteraGL.class, args );
	}

	public static void jfxglmain( final String[] args )
			throws Exception
	{

		// create a window using GLFW (with a core OpenGL context)
		GLFW.glfwInit();
		GLFW.glfwWindowHint( GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3 );
		GLFW.glfwWindowHint( GLFW.GLFW_CONTEXT_VERSION_MINOR, 2 );
		GLFW.glfwWindowHint( GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE );
		GLFW.glfwWindowHint( GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE );
		final long hwnd = GLFW.glfwCreateWindow( 300, 169, "JFXGL", MemoryUtil.NULL, MemoryUtil.NULL );

		// init OpenGL
		GLFW.glfwMakeContextCurrent( hwnd );
		GL.createCapabilities();

		try
		{

			// start the JavaFX app
			JFXGL.start( hwnd, args, new PainteraApp() );

			// render loop
			while ( !GLFW.glfwWindowShouldClose( hwnd ) )
			{

				// render the JavaFX UI
				JFXGL.render();

				GLFW.glfwSwapBuffers( hwnd );
				GLFW.glfwPollEvents();
			}

		}
		finally
		{

			// cleanup
			JFXGL.terminate();
			Callbacks.glfwFreeCallbacks( hwnd );
			GLFW.glfwDestroyWindow( hwnd );
			GLFW.glfwTerminate();
		}
	}

	public static class PainteraApp extends Application
	{

		@Override
		@CalledByEventsThread
		public void start( final Stage stage ) throws Exception
		{

			final PainteraBaseView baseView = new PainteraBaseView(
					Math.min( 8, Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 ) ),
					ViewerOptions.options().screenScales( new double[] { 1.0, 0.5, 0.25 } ),
					si -> s -> si.getState( s ).interpolationProperty().get() );

			final OrthogonalViews< Viewer3DFX > orthoViews = baseView.orthogonalViews();

			final KeyTracker keyTracker = new KeyTracker();

			final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
					baseView,
					keyTracker );

			final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers( baseView, keyTracker, paneWithStatus );

			final Parameters parameters = getParameters();
			final String[] args = parameters.getRaw().stream().toArray( String[]::new );
			final PainteraCommandLineArgs painteraArgs = new PainteraCommandLineArgs();
			final boolean parsedSuccessfully = Optional.ofNullable( CommandLine.call( painteraArgs, System.err, args ) ).orElse( false );
			Platform.setImplicitExit( true );

			if ( !parsedSuccessfully )
			{
				baseView.stop();
				Platform.exit();
				return;
			}

			final Scene scene = new Scene( paneWithStatus.getPane() );
			if ( LOG.isDebugEnabled() )
			{
				scene.focusOwnerProperty().addListener( ( obs, oldv, newv ) -> LOG.debug( "Focus changed: old={} new={}", oldv, newv ) );
			}

			setFocusTraversable( orthoViews, false );

			stage.setOnCloseRequest( event -> baseView.stop() );

			keyTracker.installInto( scene );
			stage.setScene( scene );
			stage.setWidth( painteraArgs.width() );
			stage.setHeight( painteraArgs.height() );
			stage.show();
		}
	}

}
