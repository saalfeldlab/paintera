package bdv.bigcat.viewer.viewer3d;

import cleargl.GLVector;
import graphics.scenery.Hub;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.repl.REPL;
import graphics.scenery.utils.SceneryPanel;

public class Viewer3D extends SceneryBase
{
	/** logger */
	private final SceneryPanel scPanel;

	public Viewer3D( final String applicationName, final int windowWidth, final int windowHeight, final boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );

		scPanel = new SceneryPanel( 500, 500 );
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

		getScene().addChild(lights[0]);

		getHub().add(SceneryElement.Settings, getSettings());
		getHub().add(SceneryElement.Statistics, getStats());

		setRepl(new REPL(getRenderer(), getSettings(), getScene(), getStats(), getHub()));

		if ( getRepl() != null )
		{
			getRepl().start();
			getRepl().showConsoleWindow();
		}
	}

	public void manualCamera()
	{
		Scene scene = this.getScene();

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
