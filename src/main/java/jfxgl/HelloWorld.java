package jfxgl;

import java.io.IOException;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import cuchaz.jfxgl.CalledByEventsThread;
import cuchaz.jfxgl.JFXGL;
import cuchaz.jfxgl.JFXGLLauncher;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class HelloWorld
{

	public static void main( final String[] args )
	{
		JFXGLLauncher.launchMain( HelloWorld.class, args );
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
			JFXGL.start( hwnd, args, new HelloWorldApp() );

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

	public static class HelloWorldApp extends Application
	{

		@Override
		@CalledByEventsThread
		public void start( final Stage stage )
				throws IOException
		{

			// create the UI
			final Label label = new Label( "Hello World!" );
			final TextField tf1 = new TextField();
			final TextField tf2 = new TextField();
			final VBox root = new VBox( label, tf1, tf2 );
//			label.setAlignment( Pos.CENTER );
			stage.setScene( new Scene( root ) );
		}
	}
}
