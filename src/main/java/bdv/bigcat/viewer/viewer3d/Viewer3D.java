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
import net.imglib2.RealLocalizable;

public class Viewer3D extends SceneryBase
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( Viewer3D.class );

	private final SceneryPanel scPanel;

	private final Camera cam = new DetachedHeadCamera();

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

		final Box hull = new Box( new GLVector( 20000, 20000, 20000 ), true );
		hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
		hull.getMaterial().setDoubleSided( true );
		getScene().addChild( hull );

		System.out.println( "camera... " );

		cam.perspectiveCamera( 50f, getWindowWidth(), getWindowHeight(), 0.1f, 40000.0f );
		cam.setActive( true );
		// TODO: camera position must be related with the mesh not with the
		// whole volume
		getScene().addChild( cam );

		// TODO: camera position must be related with the object
		cam.setPosition( new GLVector( 0f, 0f, 0f ) );
		getScene().addChild( cam );

		final PointLight[] lights = new PointLight[ 4 ];

		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 100.2f * 5 );
			lights[ i ].setLinear( 0.001f );
			lights[ i ].setQuadratic( 0.0f );
		}
		lights[ 0 ].setPosition( new GLVector( 1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

		getScene().addChild( lights[ 0 ] );
//		for ( int i = 0; i < lights.length; i++ )
//			getScene().addChild( lights[ i ] );
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

	public void removeAllNeurons()
	{
		final CopyOnWriteArrayList< Node > children = getScene().getChildren();
		final Iterator< Node > iterator = children.iterator();
		while ( iterator.hasNext() )
		{
			final Node child = iterator.next();
			if ( child.getName().compareTo( "Mesh" ) == 0 )
				getScene().removeChild( child );
		}
	}

}
