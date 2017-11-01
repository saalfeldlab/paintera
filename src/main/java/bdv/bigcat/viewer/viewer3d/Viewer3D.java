package bdv.bigcat.viewer.viewer3d;

import cleargl.GLVector;
import graphics.scenery.Hub;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.controls.InputHandler;
import graphics.scenery.controls.behaviours.MovementCommand;
import graphics.scenery.repl.REPL;
import graphics.scenery.utils.SceneryPanel;

public class Viewer3D extends SceneryBase
{
	/** logger */
	private final SceneryPanel scPanel;

	public Viewer3D( final String applicationName, final int windowWidth, final int windowHeight, final boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );

		scPanel = new SceneryPanel( windowWidth, windowHeight );
	}

	@Override
	public void init()
	{
		System.out.println( "init... " );
		setRenderer(
				Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), scPanel ) );
		getHub().add( SceneryElement.Renderer, getRenderer() );

		final PointLight[] lights = new PointLight[ 4 ];
		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 100.2f * 5 );
			lights[ i ].setLinear( 0.01f );
			lights[ i ].setQuadratic( 0.00f );
//			lights[ i ].showLightBox();
		}
		lights[ 0 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

		getScene().addChild( lights[ 0 ] );

		getHub().add( SceneryElement.Settings, getSettings() );
		getHub().add( SceneryElement.Statistics, getStats() );

		setRepl( new REPL( getRenderer(), getSettings(), getScene(), getStats(), getHub() ) );

		if ( getRepl() != null )
		{
			getRepl().start();
			getRepl().showConsoleWindow();
		}
	}

	public void manualCamera()
	{
		final Scene scene = this.getScene();
		final InputHandler handler = new InputHandler( scene, this.getRenderer(), this.getHub() );
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

		setInputHandler( handler );
	}

	public SceneryPanel getPanel()
	{
		return scPanel;
	}

	public Scene scene()
	{
		return this.getScene();
	}

	public Renderer renderer()
	{
		return this.getRenderer();
	}

	public Hub hub()
	{
		return this.getHub();
	}
}
