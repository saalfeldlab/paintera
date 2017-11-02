package bdv.bigcat.viewer.viewer3d;

import com.jogamp.opengl.math.Quaternion;

import cleargl.GLVector;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.transform.Rotate;

interface NeuronRendererListener
{
	void updateCamera( double[] boundingBox );
}

public class CameraModeFX implements NeuronRendererListener
{
	private final SubScene scene;

	private final PerspectiveCamera manualCamera;

	private final PerspectiveCamera automaticCamera;

	private double[] completeBoundingBox = null;

	public enum Mode
	{
		MANUAL,
		AUTOMATIC
	}

	public CameraModeFX( final SubScene scene )
	{
		this.scene = scene;

		manualCamera = new PerspectiveCamera( true );
		automaticCamera = new PerspectiveCamera( true );

//		scene.addChild( manualCamera );
//		scene.addChild( automaticCamera );

//		final InputHandler handler = ( InputHandler ) hub.get( SceneryElement.Input );
//		System.out.println( "======camera handler: " + handler );
	}

	public void manual()
	{
		System.out.println( "manual camera... " );
		this.scene.setCamera( manualCamera );
	}

	public void automatic()
	{
		System.out.println( "automatic camera... " );
		this.scene.setCamera( automaticCamera );
	}

	public Mode getCameraMode()
	{
		if ( this.scene.getCamera() == manualCamera )
			return Mode.MANUAL;

		return Mode.AUTOMATIC;
	}

	public void perspectiveCamera( final float fov, final float nearPlaneLocation, final float farPlaneLocation )
	{
		setPerspectiveCamera( manualCamera, fov, nearPlaneLocation, farPlaneLocation );
		setPerspectiveCamera( automaticCamera, fov, nearPlaneLocation, farPlaneLocation );
	}

	private void setPerspectiveCamera( PerspectiveCamera camera, final float fov, final float nearPlaneLocation, final float farPlaneLocation )
	{
		camera.setFieldOfView( fov );
		camera.setNearClip( nearPlaneLocation );
		camera.setFarClip( farPlaneLocation );
		camera.setTranslateY( 0 );
		camera.setTranslateX( 0 );
		camera.setTranslateZ( 0 );
	}

	public void setPosition( final GLVector position )
	{
		System.out.println( "initial camera position: " + position.get( 0 ) + " " + position.get( 1 ) + " " + position.get( 2 ) );

		manualCamera.setTranslateX( position.get( 0 ) );
		manualCamera.setTranslateY( position.get( 1 ) );
		manualCamera.setTranslateZ( position.get( 2 ) );

		automaticCamera.setTranslateX( position.get( 0 ) );
		automaticCamera.setTranslateY( position.get( 1 ) );
		automaticCamera.setTranslateZ( position.get( 2 ) );

		completeBoundingBox = null;
	}

	@Override
	public void updateCamera( final double[] boundingBox )
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
		final double[] cameraPosition = new double[ 3 ];
		final double[] centerBB = new double[ 3 ];
		for ( int i = 0; i < completeBoundingBox.length / 2; i++ )
		{
			cameraPosition[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
			centerBB[ i ] = ( completeBoundingBox[ i * 2 ] + completeBoundingBox[ i * 2 + 1 ] ) / 2;
		}

		// calculate the distance to the center
		final double FOV = ( automaticCamera.getFieldOfView() * ( Math.PI / 180 ) );
		final double height = completeBoundingBox[ 1 ] - completeBoundingBox[ 0 ];
		final double width = completeBoundingBox[ 3 ] - completeBoundingBox[ 2 ];
		final double depth = completeBoundingBox[ 5 ] - completeBoundingBox[ 4 ];
		final double dist = Math.max( height, Math.max( height, depth ) );
		final double distanceToCenter = ( float ) Math.abs( dist / 2 / Math.tan( FOV / 2 ) ) + width / 2;

		// walk with the camera in the x-axis
		cameraPosition[ 0 ] += distanceToCenter;
		automaticCamera.setTranslateX( cameraPosition[ 0 ] );
		automaticCamera.setTranslateY( cameraPosition[ 1 ] );
		automaticCamera.setTranslateZ( cameraPosition[ 2 ] );

		System.out.println( "cameraPosition: " + cameraPosition[ 0 ] + " " + cameraPosition[ 1 ] + " " + cameraPosition[ 2 ] );
//		automaticCamera.setPosition( new GLVector( cameraPosition[ 0 ], cameraPosition[ 1 ], cameraPosition[ 2 ] ) );

		final float angle = ( float ) ( -90 * ( Math.PI / 180 ) );
		final GLVector rotationVector = new GLVector( 0, 0, 0 );
		rotationVector.set( 1, angle );
		final Quaternion rotation = new Quaternion();
		rotation.setFromEuler( rotationVector.get( 0 ), rotationVector.get( 1 ), rotationVector.get( 2 ) );
		automaticCamera.setRotate( angle );
		automaticCamera.setRotationAxis( Rotate.X_AXIS );

		System.out.println( "rotate: " + angle );
	}

	/**
	 * Return true if completeBoundingBox contains boundingBox, false otherwise.
	 *
	 * @param completeBoundingBox
	 * @param boundingBox
	 * @return
	 */
	private boolean contains( final double[] completeBoundingBox, final double[] boundingBox )
	{
		boolean contains = true;
		System.out.println( "contains" );
		System.out.println( "complete bb: " + completeBoundingBox[ 0 ] + " " + completeBoundingBox[ 2 ] + " " + completeBoundingBox[ 4 ] +
				" " + completeBoundingBox[ 1 ] + " " + completeBoundingBox[ 3 ] + " " + completeBoundingBox[ 5 ] );

		System.out.println( "bb: " + boundingBox[ 0 ] + " " + boundingBox[ 2 ] + " " + boundingBox[ 4 ] +
				" " + boundingBox[ 1 ] + " " + boundingBox[ 3 ] + " " + boundingBox[ 5 ] );

		for ( int i = 0; i < completeBoundingBox.length; i++ )
			if ( i % 2 == 0 && completeBoundingBox[ i ] > boundingBox[ i ] )
			{
				completeBoundingBox[ i ] = boundingBox[ i ];
				contains = false;
			}

			else if ( i % 2 != 0 && completeBoundingBox[ i ] < boundingBox[ i ] )
			{
				completeBoundingBox[ i ] = boundingBox[ i ];
				contains = false;
			}
		return contains;
	}
}
