package bdv.bigcat;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import com.sun.javafx.application.PlatformImpl;

import cleargl.GLTypeEnum;
import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.GenericTexture;
import graphics.scenery.Material;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class SimpleSceneryExampleGradientTextureJavaFX extends SceneryBase
{

	private final SceneryPanel[] panel = new SceneryPanel[ 1 ];

	public SimpleSceneryExampleGradientTextureJavaFX( final String applicationName, final int windowWidth, final int windowHeight, final boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );
	}

	@Override
	public void init()
	{
		PlatformImpl.startup( () -> {} );
		final CountDownLatch latch = new CountDownLatch( 1 );
		Platform.runLater( () -> {

			panel[ 0 ] = new SceneryPanel( getWindowWidth(), getWindowHeight() );
			final HBox root = new HBox();
			HBox.setHgrow( panel[ 0 ], Priority.ALWAYS );
			root.getChildren().add( panel[ 0 ] );

			final Stage stage = new Stage();
			stage.setTitle( getApplicationName() );
			final Scene scene = new Scene( root );

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
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		setRenderer(
				Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), panel[ 0 ] ) );
		getHub().add( SceneryElement.Renderer, getRenderer() );

		final int numChannels = 4;
		final int w = 300, h = 300;
		final byte[] texture = new byte[ w * h * numChannels ];
		for ( int y = 0, i = 0; y < h; ++y )
			for ( int x = 0; x < w; ++x, i += numChannels )
			{
				final int r = ( int ) ( x * y * 255.0 / h / w );
				final int g = r;
				final int b = r;
				final int a = 255;
				System.out.println( r + " " + g + " " + b + " " + a );
				texture[ i + 0 ] = ( byte ) Math.min( r, 127 );
				texture[ i + 1 ] = ( byte ) Math.min( g, 127 );
				texture[ i + 2 ] = ( byte ) Math.min( b, 127 );
				texture[ i + 3 ] = ( byte ) Math.min( a, 127 );
			}

		final Box box = new Box( new GLVector( 1.0f, 1.0f, 1.0f ), true );
		final Material m = new Material();
		m.setAmbient( new GLVector( 1.0f, 1.0f, 1.0f ) );
		m.setDiffuse( new GLVector( 1.0f, 1.0f, 1.0f ) );
		m.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
		m.getTransferTextures().put( "texture", new GenericTexture( "texture", new GLVector( w, h, 1.0f ), numChannels, GLTypeEnum.Byte, ByteBuffer.wrap( texture ), true, true ) );
		m.getTextures().put( "diffuse", "fromBuffer:texture" );
		box.setMaterial( m );
		getScene().addChild( box );

		System.out.println( "camera... " );

		final Camera cam = new DetachedHeadCamera();
		cam.setPosition( new GLVector( 0f, 0f, 5.0f ) );
		cam.perspectiveCamera( 50.0f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.f );
		cam.setActive( true );
		getScene().addChild( cam );

		final PointLight[] lights = new PointLight[ 10 ];

		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setPosition( new GLVector( 2.0f * i, 2.0f * i, 2.0f * i ) );
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 500.2f * ( i + 1 ) );
			getScene().addChild( lights[ i ] );
		}

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

	public static void main( final String[] args )
	{
		final SimpleSceneryExampleGradientTextureJavaFX example = new SimpleSceneryExampleGradientTextureJavaFX( "texture example", 500, 500, false );
		example.main();
	}

}
