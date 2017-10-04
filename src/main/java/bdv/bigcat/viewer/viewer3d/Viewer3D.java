package bdv.bigcat.viewer.viewer3d;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Mesh;
import graphics.scenery.Node;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.controls.behaviours.MovementCommand;
import graphics.scenery.utils.SceneryPanel;

public class Viewer3D extends SceneryBase
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( Viewer3D.class );

	private double[] volumeResolution = null;

	private final SceneryPanel scPanel;

	public Viewer3D( String applicationName, int windowWidth, int windowHeight, boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );

		scPanel = new SceneryPanel( 500, 500 );
	}

	public void setVolumeResolution( double[] resolution )
	{
		this.volumeResolution = resolution;
	}

	public void init()
	{
		System.out.println( "init... " );
		setRenderer(
				Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), scPanel ) );
		getHub().add( SceneryElement.Renderer, getRenderer() );

		final Box hull = new Box( new GLVector( 50.0f, 50.0f, 50.0f ), true );
		hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
		hull.getMaterial().setDoubleSided( true );
		getScene().addChild( hull );

		final Camera cam = new DetachedHeadCamera();

		System.out.println( "camera... " );

		cam.perspectiveCamera( 50f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.0f );
		cam.setActive( true );
		// TODO: camera position must be related with the mesh not with the
		// whole volume
		if ( volumeResolution == null )
		{
			cam.setPosition( new GLVector( 0, 0, 5 ) );
		}
		else
		{
			cam.setPosition( new GLVector( ( float ) ( volumeResolution[ 0 ] / 2 ), ( float ) ( volumeResolution[ 1 ] / 2 ), 2 ) );
		}
		getScene().addChild( cam );

		// TODO: camera position must be related with the object
		cam.setPosition( new GLVector( 2f, 2f, 10 ) );
		getScene().addChild( cam );

		final PointLight[] lights = new PointLight[ 4 ];

		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 100.2f * 5 );
			lights[ i ].setLinear( 0.0f );
			lights[ i ].setQuadratic( 0.1f );
		}

		lights[ 0 ].setPosition( new GLVector( 1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

		for ( int i = 0; i < lights.length; i++ )
			getScene().addChild( lights[ i ] );
	}

	public void addChild( Mesh child )
	{
		getScene().addChild( child );
		MovementCommand movement = new MovementCommand( "move_forward", "forward", () -> child );
	}

	@Override
	public void inputSetup()
	{
		setupCameraModeSwitching( "C" );
	}

	public void removeChild( Mesh child )
	{
		getScene().removeChild( child );
	}

	public SceneryPanel getPanel()
	{
		return scPanel;
	}
	
	public void removeAllNeurons()
	{
		CopyOnWriteArrayList< Node > children = getScene().getChildren();
		Iterator< Node > iterator = children.iterator();
		while (iterator.hasNext()) {
			Node child = iterator.next();
			if ( child.getName().compareTo( "Mesh" ) == 0 )
			{
				getScene().removeChild( child );
			}
		}
	}

}
