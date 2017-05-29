package bdv.bigcat.ui;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

import cleargl.GLVector;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryDefaultApplication;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;

public class MarchingCubesTest
{
	@Test
	public void testExample() throws Exception
	{
		MarchingCubeJavaApplication viewer = new MarchingCubeJavaApplication( "Marching cube", 800, 600 );
		viewer.main();
	}

	private class MarchingCubeJavaApplication extends SceneryDefaultApplication
	{
		public MarchingCubeJavaApplication( String applicationName, int windowWidth, int windowHeight )
		{
			super( applicationName, windowWidth, windowHeight, true );
		}

		public void init()
		{

			setRenderer( Renderer.Factory.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight() ) );
			getHub().add( SceneryElement.RENDERER, getRenderer() );

			Material material = new Material();
			material.setAmbient( new GLVector( 0.1f, 0.0f, 0.0f ) );
			material.setDiffuse( new GLVector( 0.1f, 0.0f, 0.0f ) );
			material.setSpecular( new GLVector( 0.1f, 0f, 0f ) );
			
			MarchingCubes2< Integer > mc = new MarchingCubes2< Integer >();
			ArrayList< float[] > vertices = new ArrayList<>();

			Integer[] values = new Integer[ 120 * 120 * 34 ];
			DataInputStream input;

			int i = 0;
			try
			{
				input = new DataInputStream( new FileInputStream( "/home/vleite/Downloads/lobster.dat" ) );
				System.out.println(input);
				
				while ( input.available() > 0 )
				{
					values[ i ] = input.read();
					i++;
				}
				input.close();

			}
			catch ( IOException e1 )
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			int[] volDim = { 120, 120, 34 };
			float[] voxDim = { 5, 5, 5 };
			int isoLevel = 125;
			int offset = 0;
			vertices = mc.marchingCubes( values, volDim, voxDim, isoLevel, offset );

			float[] verticesArray = new float[vertices.size()*3];
			i = 0;
			for (float[] floatV : vertices) {
				for (float f : floatV) {
					verticesArray[i++] = f;
				}
			}
			
			Mesh neuron = new Mesh();
			neuron.setMaterial( material );
			neuron.setPosition( new GLVector(0.0f, 0.0f, 0.0f) );
			neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
			neuron.setNormals( FloatBuffer.wrap( verticesArray )  );

			getScene().addChild( neuron );

			PointLight[] lights = new PointLight[ 9 ];

			System.out.println(lights.length);
			for ( i = 0; i < lights.length; i++ )
			{
				lights[ i ] = new PointLight();
				lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
				lights[ i ].setIntensity( 100.2f * ( i + 1 ) );
				lights[ i ].setLinear( 0.0f );
				lights[ i ].setQuadratic( 0f );
				lights[ i ].setRadius( 1000 );
//				lights[ i ].showLightBox();
			}
			lights[ 0 ].setPosition( new GLVector( 0.0f, 0.0f, 1.0f) );
			lights[ 1 ].setPosition( new GLVector( 0.0f, 1.0f, 0.0f) );
			lights[ 3 ].setPosition( new GLVector( 1.0f, 0.0f, 0.0f) );
			lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f) );
			lights[ 4 ].setPosition( new GLVector( 1.0f, 1.0f, 0.0f) );
			lights[ 5 ].setPosition( new GLVector( 1.0f, 0.0f, 1.0f) );
			lights[ 6 ].setPosition( new GLVector( 1.0f, 1.0f, 1.0f) );
			lights[ 7 ].setPosition( new GLVector( 0.0f, -1.0f, 0.0f) );
			lights[ 8 ].setPosition( new GLVector( -1.0f, 0.0f, 0.0f) );

			getScene().addChild( lights[ 0 ] );
			getScene().addChild( lights[ 1 ] );
			getScene().addChild( lights[ 2 ] );
			getScene().addChild( lights[ 3 ] );
			getScene().addChild( lights[ 4 ] );
			getScene().addChild( lights[ 5 ] );
			getScene().addChild( lights[ 6 ] );
			getScene().addChild( lights[ 7 ] );
			getScene().addChild( lights[ 8 ] );

			
			Camera cam = new DetachedHeadCamera();
			cam.setPosition( new GLVector( 0.0f, 0.0f, 5.0f ) );
			cam.perspectiveCamera( 50.0f, getRenderer().getWindow().getWidth(), getRenderer().getWindow().getHeight(),
					0.1f, 1000.0f );
			cam.setActive( true );
			getScene().addChild( cam );

			Thread rotator = new Thread()
			{
				public void run()
				{
					while ( true )
					{
						neuron.getRotation().rotateByAngleY( 0.01f );
						neuron.setNeedsUpdate( true );

						try
						{
							Thread.sleep( 20 );
						}
						catch ( InterruptedException e )
						{
							e.printStackTrace();
						}
					}
				}
			};
			rotator.start();
			
			
			float farPlane = cam.getFarPlaneDistance();
		}

	}

}
