package bdv.bigcat.viewer.viewer3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.controls.InputHandler;
import graphics.scenery.controls.behaviours.MovementCommand;
import graphics.scenery.utils.SceneryPanel;
import net.imglib2.RealLocalizable;

public class Viewer3D extends SceneryBase
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( Viewer3D.class );

	private final SceneryPanel scPanel;

	private final Camera cam = new Camera();
	
	public Viewer3D( final String applicationName, final int windowWidth, final int windowHeight, final boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );

		scPanel = new SceneryPanel( 500, 500 );
	}

	public void setCameraPosition( final RealLocalizable position )
	{
		cam.setPosition( new GLVector( position.getFloatPosition( 0 ), position.getFloatPosition( 1 ), position.getFloatPosition( 2 ) ) );
	}

	@Override
	public void init()
	{
		System.out.println( "init... " );
		setRenderer(
				Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), scPanel ) );
		getHub().add( SceneryElement.Renderer, getRenderer() );

		// TODO: box with the size of the data
		final Box hull = new Box( new GLVector( 30000, 30000, 30000 ), true );
		hull.getMaterial().setOpacity( 0.5f );
		hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
		hull.getMaterial().setDoubleSided( true );
		getScene().addChild( hull );

		final InputHandler handler = new InputHandler( getScene(), getRenderer(), getHub() );
		handler.removeBehaviour( "move_forward" );
		handler.removeBehaviour( "move_left" );
		handler.removeBehaviour( "move_back" );
		handler.removeBehaviour( "move_right" );
		handler.removeBehaviour( "move_forward_fast" );
		handler.removeBehaviour( "move_left_fast" );
		handler.removeBehaviour( "move_back_fast" );
		handler.removeBehaviour( "move_right_fast" );

		handler.removeKeyBinding( "move_forward" );
		handler.removeKeyBinding( "move_left" );
		handler.removeKeyBinding( "move_back" );
		handler.removeKeyBinding( "move_right" );
		handler.removeKeyBinding( "move_forward_fast" );
		handler.removeKeyBinding( "move_left_fast" );
		handler.removeKeyBinding( "move_back_fast" );
		handler.removeKeyBinding( "move_right_fast" );

		final Scene scene = getScene();

		handler.addBehaviour( "move_forward", new MovementCommand( "move_forward", "forward", scene::findObserver, 1.0f ) );
		handler.addBehaviour( "move_left", new MovementCommand( "move_left", "left", scene::findObserver, 1.0f ) );
		handler.addBehaviour( "move_back", new MovementCommand( "move_back", "back", scene::findObserver, 1.0f ) );
		handler.addBehaviour( "move_right", new MovementCommand( "move_right", "right", scene::findObserver, 1.0f ) );
		handler.addBehaviour( "move_forward_fast", new MovementCommand( "move_forward_fast", "forward", scene::findObserver, 20.0f ) );
		handler.addBehaviour( "move_left_fast", new MovementCommand( "move_left_fast", "left", scene::findObserver, 20.0f ) );
		handler.addBehaviour( "move_back_fast", new MovementCommand( "move_back_fast", "back", scene::findObserver, 20.0f ) );
		handler.addBehaviour( "move_right_fast", new MovementCommand( "move_right_fast", "right", scene::findObserver, 20.0f ) );

		handler.addKeyBinding( "move_forward", "W" );
		handler.addKeyBinding( "move_left", "A" );
		handler.addKeyBinding( "move_back", "S" );
		handler.addKeyBinding( "move_right", "D" );
		handler.addKeyBinding( "move_forward_fast", "shift W" );
		handler.addKeyBinding( "move_left_fast", "shift A" );
		handler.addKeyBinding( "move_back_fast", "shift S" );
		handler.addKeyBinding( "move_right_fast", "shift D" );

		System.out.println( "camera... " );

		setInputHandler( handler );

		cam.perspectiveCamera( 50f, getWindowWidth(), getWindowHeight(), 0.1f, 40000.0f );
		cam.setActive( true );
		getScene().addChild( cam );

		final PointLight[] lights = new PointLight[ 4 ];
		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 100.2f * 5 );
			lights[ i ].setLinear( 0.001f );
			lights[ i ].setQuadratic( 0.0f );
//			lights[ i ].showLightBox();
		}
		lights[ 0 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

		getScene().addChild( lights[ 0 ] );
	}

	public void addChild( final Mesh child )
	{
		getScene().addChild( child );
		final MovementCommand movement = new MovementCommand( "move_forward", "forward", () -> child );
	}

	@Override
	public void inputSetup()
	{
		setupCameraModeSwitching( "C" );
	}

	public void removeChild( final Mesh child )
	{
		getScene().removeChild( child );
	}

	public SceneryPanel getPanel()
	{
		return scPanel;
	}

	public Scene scene()
	{
		return this.getScene();
	}

}
