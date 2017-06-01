package bdv.bigcat.ui;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	/** Log */
	private static final Logger LOGGER = Logger.getLogger( MarchingCubesTest.class.getName() );

	Handler handlerObj = new ConsoleHandler();

	@Test
	public void testExample() throws Exception
	{
		handlerObj.setLevel( Level.ALL );
		LOGGER.addHandler( handlerObj );
		LOGGER.setLevel( Level.ALL );
		LOGGER.setUseParentHandlers( false );

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

			final Material material = new Material();
			material.setAmbient( new GLVector( 0.1f, 1.0f, 1.0f ) );
			material.setDiffuse( new GLVector( 0.1f, 0.0f, 0.0f ) );
			material.setSpecular( new GLVector( 0.1f, 0f, 0f ) );

			int[] volMin = {0,0,0};
			MarchingCubes< Integer > mc = new MarchingCubes< Integer>(volMin);
			MarchingCubes2< Integer > mc2 = new MarchingCubes2< Integer >();
			MarchingCubes4< Integer > mc4 = new MarchingCubes4< Integer>();
			ArrayList< float[] > vertices = new ArrayList<>();

			Integer[] values = new Integer[ 120 * 120 * 34 ];
			DataInputStream input;

			int i = 0;
			try
			{
				/** lobster dataset: http://cns-alumni.bu.edu/~lavanya/Graphics/cs580/p5/web-page/p5.html*/
				input = new DataInputStream( new FileInputStream( "data/lobster.dat" ) );
				while ( input.available() > 0 )
				{
					values[ i ] = input.read();
					i++;
				}
				input.close();

			}
			catch ( IOException e )
			{
				LOGGER.log( Level.SEVERE, "Couldn't load dat file.", e );
			}
			
			Integer[] voxels = {
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								1, 1, 1, 1, 1,
								1, 2, 2, 2, 1,
								1, 2, 3, 2, 1,
								1, 2, 2, 2, 1,
								1, 1, 1, 1, 1,
								};

			int[] volDim = { 120, 120, 34 };
			float[] voxDim = { 10f, 10f, 10f };
			int isoLevel = 125;
			int offset = 0;
			vertices = mc2.marchingCubes( values, volDim, voxDim, isoLevel, offset );

			float[] verticesArray = new float[ vertices.size() * 3 ];
			i = 0;
			for ( float[] floatV : vertices )
			{
				for ( float f : floatV )
				{
					verticesArray[ i++ ] = f;
				}
			}

//			MarchingCubes4< Integer >.Mesh mesh4 = mc4.new Mesh();
//			mesh4 = mc4.march( values, 120, 120, 34, isoLevel );
			
//			MarchingCubes< Integer >.Mesh mesh_ = mc.new Mesh();
//			mesh_ = mc.generateSurface( values, voxDim, volDim, false, isoLevel );
//			
//			System.out.println("# of vertices: " + mesh_.vertexCount);
//			System.out.println("# of faces: " + mesh_.faceCount);
//			
//			float[] arrayv = new float[ mesh_.faceCount * 3 * 3];
//			float[] normalv = new float[ mesh_.faceCount *3 * 3];
//			i = 0;
//			for ( i = 0; i < mesh_.faceCount; i ++ )
//			{
//				int id0 = mesh_.faces[ i * 3 ];
//				int id1 = mesh_.faces[ i * 3 + 1 ];
//				int id2 = mesh_.faces[ i * 3 + 2 ];
//
//				arrayv[ i++ ] = mesh_.vertices[id0][0];
//				arrayv[ i++ ] = mesh_.vertices[id0][1];
//				arrayv[ i++ ] = mesh_.vertices[id0][2];
//
//				arrayv[ i++ ] = mesh_.vertices[id1][0];
//				arrayv[ i++ ] = mesh_.vertices[id1][1];
//				arrayv[ i++ ] = mesh_.vertices[id1][2];
//
//				arrayv[ i++ ] = mesh_.vertices[id2][0];
//				arrayv[ i++ ] = mesh_.vertices[id2][1];
//				arrayv[ i++ ] = mesh_.vertices[id2][2];
//			}
//
//			i = 0;
//			for ( i = 0; i < mesh_.faceCount; i ++ )
//			{
//				int id0 = mesh_.faces[ i * 3 ];
//				int id1 = mesh_.faces[ i * 3 + 1 ];
//				int id2 = mesh_.faces[ i * 3 + 2 ];
//
//				normalv[ i++ ] = mesh_.normals[id0][0];
//				normalv[ i++ ] = mesh_.normals[id0][1];
//				normalv[ i++ ] = mesh_.normals[id0][2];
//
//				normalv[ i++ ] = mesh_.normals[id1][0];
//				normalv[ i++ ] = mesh_.normals[id1][1];
//				normalv[ i++ ] = mesh_.normals[id1][2];
//
//				normalv[ i++ ] = mesh_.normals[id2][0];
//				normalv[ i++ ] = mesh_.normals[id2][1];
//				normalv[ i++ ] = mesh_.normals[id2][2];
//			}
//			i = 0;
//			for ( float[] floatV : mesh_.vertices )
//			{
//				for ( float f : floatV )
//				{
//					arrayv[ i++ ] = f/100;
//				}
//			}
//			
//			i = 0;
//			for ( float[] floatV : mesh_.normals )
//			{
//				for ( float f : floatV )
//				{
//					normalv[ i++ ] = f;
//				}
//			}

			
			Mesh neuron = new Mesh();
			neuron.setMaterial( material );
			neuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
			neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
			neuron.setNormals( FloatBuffer.wrap( verticesArray) );
//			neuron.setIndices( IntBuffer.wrap( mesh_.faces ) );

			getScene().addChild( neuron );

			PointLight[] lights = new PointLight[ 2 ];

			for ( i = 0; i < lights.length; i++ )
			{
				lights[ i ] = new PointLight();
				lights[ i ].setPosition( new GLVector( 2.0f * i, 2.0f * i, 2.0f * i ) );
				lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
				lights[ i ].setIntensity( 100.2f * ( i + 1 ) );
				lights[ i ].setLinear( 0.0f );
				lights[ i ].setQuadratic( 0f );
				lights[ i ].setRadius( 1000 );
//				lights[ i ].showLightBox();
				getScene().addChild( lights[ i ] );
			}

			final Camera cam = new DetachedHeadCamera();
			cam.setPosition( new GLVector( 0.0f, 0.0f, 5.0f ) );

			cam.perspectiveCamera( 50.0f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.0f );
			cam.setActive( true );
			getScene().addChild( cam );

			final Thread rotator = new Thread()
			{
				public void run()
				{
					while ( true )
					{
						neuron.setNeedsUpdate( true );

						float diff = cam.getPosition().minus( neuron.getPosition() ).magnitude();
						LOGGER.log( Level.FINE, "distance from camera: " + diff );
						
						if (diff < 1)
							neuron.getRotation().rotateByAngleY( 0.0001f );
						else
							neuron.getRotation().rotateByAngleY( 0.01f );
						try
						{
							Thread.sleep( 20 );
						}
						catch ( InterruptedException e )
						{
							LOGGER.log( Level.SEVERE, "Interruption on rotator.", e );
						}
					}
				}
			};
			rotator.start();
		}
	}
}
