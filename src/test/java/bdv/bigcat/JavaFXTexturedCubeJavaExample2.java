package bdv.bigcat;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.sun.javafx.application.PlatformImpl;

import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Material;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class JavaFXTexturedCubeJavaExample2
{

	@Test
	public void testExample() throws Exception
	{
		final JavaFXTexturedCubeApplication viewer = new JavaFXTexturedCubeApplication( "scenery - JavaFXTexturedCubeExample",
				512, 512 );
		viewer.main();
	}

	private class JavaFXTexturedCubeApplication extends SceneryBase
	{

		public JavaFXTexturedCubeApplication( final String applicationName, final int windowWidth, final int windowHeight )
		{
			super( applicationName, windowWidth, windowHeight, false );
		}

		@Override
		public void init()
		{

			final CountDownLatch latch = new CountDownLatch( 1 );
			final SceneryPanel[] imagePanel = { null };

			PlatformImpl.startup( () -> {} );

			Platform.runLater( () -> {

				final Stage stage = new Stage();
				stage.setTitle( getApplicationName() );

				final StackPane stackPane = new StackPane();
				stackPane.setBackground(
						new Background( new BackgroundFill( Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY ) ) );

				final GridPane pane = new GridPane();
				final Label label = new Label( getApplicationName() );

				imagePanel[ 0 ] = new SceneryPanel( getWindowWidth(), getWindowHeight() );

				GridPane.setHgrow( imagePanel[ 0 ], Priority.ALWAYS );
				GridPane.setVgrow( imagePanel[ 0 ], Priority.ALWAYS );

				GridPane.setFillHeight( imagePanel[ 0 ], true );
				GridPane.setFillWidth( imagePanel[ 0 ], true );

				GridPane.setHgrow( label, Priority.ALWAYS );
				GridPane.setHalignment( label, HPos.CENTER );
				GridPane.setValignment( label, VPos.BOTTOM );

				label.maxWidthProperty().bind( pane.widthProperty() );

				pane.setStyle( "-fx-background-color: rgb(20, 255, 20);" + "-fx-font-family: Consolas;"
						+ "-fx-font-weight: 400;" + "-fx-font-size: 1.2em;" + "-fx-text-fill: white;"
						+ "-fx-text-alignment: center;" );

				label.setStyle( "-fx-padding: 0.2em;" + "-fx-text-fill: black;" );

				label.setTextAlignment( TextAlignment.CENTER );

				pane.add( imagePanel[ 0 ], 1, 1 );
				pane.add( label, 1, 2 );
				stackPane.getChildren().addAll( pane );

				final Scene scene = new Scene( stackPane );
				stage.setScene( scene );
				stage.setOnCloseRequest( event -> {
					getRenderer().setShouldClose( true );

					Platform.runLater( Platform::exit );
				} );
				stage.show();

				latch.countDown();
			} );

			try
			{
				latch.await();
			}
			catch ( final InterruptedException e1 )
			{
				e1.printStackTrace();
			}

			setRenderer(
					Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), imagePanel[ 0 ] ) );
			getHub().add( SceneryElement.Renderer, getRenderer() );

			final Material boxmaterial = new Material();
			boxmaterial.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
			boxmaterial.setDiffuse( new GLVector( 0.0f, 1.0f, 0.0f ) );
			boxmaterial.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );

			boxmaterial.getTextures().put( "diffuse",
					JavaFXTexturedCubeApplication.class.getResource( "textures/helix.png" ).getFile() );

			final Box box = new Box( new GLVector( 1.0f, 1.0f, 1.0f ), false );
			box.setName( "le box du win" );
			box.setMaterial( boxmaterial );
			getScene().addChild( box );

			final PointLight[] lights = new PointLight[ 2 ];

			for ( int i = 0; i < lights.length; i++ )
			{
				lights[ i ] = new PointLight();
				lights[ i ].setPosition( new GLVector( 2.0f * i, 2.0f * i, 2.0f * i ) );
				lights[ i ].setEmissionColor( new GLVector( 1.0f, 0.0f, 1.0f ) );
				lights[ i ].setIntensity( 500.2f * ( i + 1 ) );
				getScene().addChild( lights[ i ] );
			}

			final Camera cam = new DetachedHeadCamera();
			cam.setPosition( new GLVector( 0.0f, 0.0f, 5.0f ) );
			cam.perspectiveCamera( 50.0f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.0f );
			cam.setActive( true );
			getScene().addChild( cam );

			new Thread( () -> {
				while ( true )
				{
					box.getRotation().rotateByAngleY( 0.01f );
					box.setNeedsUpdate( true );

					try
					{
						Thread.sleep( 20 );
					}
					catch ( final InterruptedException e )
					{
						e.printStackTrace();
					}
				}
			} ).start();
		}
	}
}
