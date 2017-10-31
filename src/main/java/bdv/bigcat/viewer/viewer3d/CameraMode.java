package bdv.bigcat.viewer.viewer3d;

import com.jogamp.opengl.math.Quaternion;

import cleargl.GLVector;
import graphics.scenery.Camera;
import graphics.scenery.Hub;
import graphics.scenery.Scene;
import graphics.scenery.backends.Renderer;

interface NeuronRendererListener
{
	void updateCamera( float[] boundingBox );
}

public class CameraMode implements NeuronRendererListener
{
	private final Scene scene;
//
//	private final Renderer renderer;
//
//	private final Hub hub;

	private final Camera manualCamera;

	private final Camera automaticCamera;

	private float[] completeBoundingBox = null;

	public enum Mode
	{
		MANUAL,
		AUTOMATIC
	}

	public CameraMode( final Scene scene )
	{
		this.scene = scene;
//		this.renderer = renderer;
//		this.hub = hub;

		manualCamera = new Camera();
		automaticCamera = new Camera();

		scene.addChild( manualCamera );
		scene.addChild( automaticCamera );

	}

	public void manual( final Renderer renderer, final Hub hub )
	{
		System.out.println( "manual camera... " );
		manualCamera.setActive( true );
	}

	public void automatic()
	{
		System.out.println( "automatic camera... " );
		automaticCamera.setActive( true );
	}

	public Mode getCameraMode()
	{
		if ( manualCamera.getActive() )
			return Mode.MANUAL;

		return Mode.AUTOMATIC;
	}

	public void perspectiveCamera( final float fov, final float width, final float height, final float nearPlaneLocation, final float farPlaneLocation )
	{
		manualCamera.perspectiveCamera( fov, width, height, nearPlaneLocation, farPlaneLocation );
		automaticCamera.perspectiveCamera( fov, width, height, nearPlaneLocation, farPlaneLocation );
	}

	public void setPosition( final GLVector position )
	{
		manualCamera.setPosition( position );
		automaticCamera.setPosition( position );
		completeBoundingBox = null;
	}

//	private void updateCameraPosition()
//	{
//		// TODO: get the bb of the objects on scene
//		float[] sceneBB = scene.getBoundingBoxCoords();
//		System.out.println( "sceneBB " + sceneBB[ 0 ] + " " + sceneBB[ 2 ] + " " + sceneBB[ 4 ] + " " + sceneBB[ 1 ] + " " + sceneBB[ 3 ] + " " + sceneBB[ 5 ] );
//
//		float[] completeBoundingBox = sceneBB;
//
//		// set the camera position to the center of the complete bounding box
//		float[] cameraPosition = new float[ 3 ];
//		float[] centerBB = new float[ 3 ];
//		for ( int i = 0; i < completeBoundingBox.length / 2; i++ )
//		{
//			cameraPosition[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
//			centerBB[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
//		}
//
//		System.out.println( "test" );
//
//		// calculate the distance to the center
//		float FOV = ( float ) ( automaticCamera.getFov() * ( Math.PI / 180 ) );
//		float height = completeBoundingBox[ 1 ] - completeBoundingBox[ 0 ];
//		float width = completeBoundingBox[ 3 ] - completeBoundingBox[ 2 ];
//		float depth = completeBoundingBox[ 5 ] - completeBoundingBox[ 4 ];
//
//		float dist = Math.max( height, Math.max( height, depth ) );
//		float distanceToCenter = ( float ) Math.abs( ( dist / 2 ) / Math.tan( FOV / 2 ) ) + width / 2;
//		cameraPosition[ 0 ] += distanceToCenter;
//		// walk with the camera in the x-axis
//		automaticCamera.setPosition( new GLVector( cameraPosition[ 0 ], cameraPosition[ 1 ], cameraPosition[ 2 ] ) );
//
//		System.out.println( "test2" );
//		// TODO: work with any rotation degree
//		// rotate the camera 90 degrees to look to the neuron
//		float angle = ( float ) ( -90 * ( Math.PI / 180 ) );
//		GLVector rotationVector = new GLVector( 0, 0, 0 );
//		rotationVector.set( 1, angle );
//		Quaternion rotation = new Quaternion();
//		rotation.setFromEuler( rotationVector.get( 0 ), rotationVector.get( 1 ), rotationVector.get( 2 ) );
//		automaticCamera.setRotation( rotation );
//
//		System.out.println( "test3" );
//	}

	@Override
	public void updateCamera( float[] boundingBox )
	{
		System.out.println( "updating camera" );
		// if completeBoundingBox contains boundingbox, it is not necessary
		// update the camera
		if ( completeBoundingBox != null && contains( completeBoundingBox, boundingBox ) )
			return;

		if ( completeBoundingBox == null )
			completeBoundingBox = boundingBox.clone();

		System.out.println( "complete bb: " + completeBoundingBox[ 0 ] + " " + completeBoundingBox[ 2 ] + " " + completeBoundingBox[ 4 ] +
				" " + completeBoundingBox[ 1 ] + " " + completeBoundingBox[ 3 ] + " " + completeBoundingBox[ 5 ] );

		// set the camera position to the center of the complete bounding box
		float[] cameraPosition = new float[ 3 ];
		float[] centerBB = new float[ 3 ];
		for ( int i = 0; i < completeBoundingBox.length / 2; i++ )
		{
			cameraPosition[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
			centerBB[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
		}

		// calculate the distance to the center
		float FOV = ( float ) ( automaticCamera.getFov() * ( Math.PI / 180 ) );
		float height = completeBoundingBox[ 1 ] - completeBoundingBox[ 0 ];
		float width = completeBoundingBox[ 3 ] - completeBoundingBox[ 2 ];
		float depth = completeBoundingBox[ 5 ] - completeBoundingBox[ 4 ];
		float dist = Math.max( height, Math.max( height, depth ) );
		float distanceToCenter = ( float ) Math.abs( ( dist / 2 ) / Math.tan( FOV / 2 ) ) + width / 2;

		// walk with the camera in the x-axis
		cameraPosition[ 0 ] += distanceToCenter;
		automaticCamera.setPosition( new GLVector( cameraPosition[ 0 ], cameraPosition[ 1 ], cameraPosition[ 2 ] ) );

		float angle = ( float ) ( -90 * ( Math.PI / 180 ) );
		GLVector rotationVector = new GLVector( 0, 0, 0 );
		rotationVector.set( 1, angle );
		Quaternion rotation = new Quaternion();
		rotation.setFromEuler( rotationVector.get( 0 ), rotationVector.get( 1 ), rotationVector.get( 2 ) );
		automaticCamera.setRotation( rotation );
	}

	/**
	 * Return true if completeBoundingBox contains boundingBox, false otherwise.
	 * 
	 * @param completeBoundingBox
	 * @param boundingBox
	 * @return
	 */
	private boolean contains( float[] completeBoundingBox, float[] boundingBox )
	{
		boolean contains = true;
		System.out.println( "contains" );
		System.out.println( "complete bb: " + completeBoundingBox[ 0 ] + " " + completeBoundingBox[ 2 ] + " " + completeBoundingBox[ 4 ] +
				" " + completeBoundingBox[ 1 ] + " " + completeBoundingBox[ 3 ] + " " + completeBoundingBox[ 5 ] );

		System.out.println( "bb: " + boundingBox[ 0 ] + " " + boundingBox[ 2 ] + " " + boundingBox[ 4 ] +
				" " + boundingBox[ 1 ] + " " + boundingBox[ 3 ] + " " + boundingBox[ 5 ] );

		for ( int i = 0; i < completeBoundingBox.length; i++ )
		{
			if ( ( i % 2 == 0 ) && completeBoundingBox[ i ] > boundingBox[ i ] )
			{
				completeBoundingBox[ i ] = boundingBox[ i ];
				contains = false;
			}

			else if ( ( i % 2 != 0 ) && completeBoundingBox[ i ] < boundingBox[ i ] )
			{
				completeBoundingBox[ i ] = boundingBox[ i ];
				contains = false;
			}
		}

		return contains;
	}
}
