package bdv.bigcat.viewer.viewer3d;

import graphics.scenery.Hub;
import graphics.scenery.Scene;
import graphics.scenery.SceneryElement;
import graphics.scenery.Settings;
import graphics.scenery.backends.Renderer;
import graphics.scenery.controls.InputHandler;
import graphics.scenery.repl.REPL;
import graphics.scenery.utils.SceneryPanel;
import graphics.scenery.utils.Statistics;

public class Viewer3D
{
	/** logger */
	private final SceneryPanel scPanel;

	private final String applicationName;

	private final int windowWidth;

	private final int windowHeight;

	private final boolean wantREPL;

	private final Scene scene;

	private Hub hub;

	public Viewer3D( final String applicationName, final int windowWidth, final int windowHeight, final boolean wantREPL )
	{
		this.applicationName = applicationName;
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.wantREPL = wantREPL;

		this.scene = new Scene();

		scPanel = new SceneryPanel( windowWidth, windowHeight );
	}

	public void init()
	{
		System.out.println( "init... " );

		hub = new Hub();
		final Settings settings = new Settings();
		hub.add( SceneryElement.Settings, settings );

		final Statistics statistics = new Statistics( hub );
		hub.add( SceneryElement.Statistics, statistics );

		final Renderer renderer = Renderer.createRenderer( hub, applicationName, scene, windowWidth, windowHeight, scPanel );
		hub.add( SceneryElement.Renderer, renderer );
		
		InputHandler inputHandler = new InputHandler(scene, renderer, hub);
		inputHandler.useDefaultBindings( System.getProperty( "user.home" ) + "/.$applicationName.bindings" );
		
		if ( wantREPL )
		{
			REPL repl = new REPL( renderer, settings, scene, statistics, hub );

			if ( repl != null )
			{
				repl.start();
				repl.showConsoleWindow();
			}
		}
	}

	public SceneryPanel getPanel()
	{
		return scPanel;
	}

	public Scene scene()
	{
		return this.scene;
	}

	public int getWindowWidth()
	{
		return this.windowWidth;
	}

	public int getWindowHeight()
	{
		return this.windowHeight;
	}

	public Hub getHub()
	{
		return this.hub;
	}
}
