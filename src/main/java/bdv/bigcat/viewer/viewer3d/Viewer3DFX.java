package bdv.bigcat.viewer.viewer3d;

import bdv.util.InvokeOnJavaFXApplicationThread;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import net.imglib2.Interval;

public class Viewer3DFX extends Pane
{

	private final Group root;

	private final Group meshesGroup;

	private final SubScene scene;

	private final PerspectiveCamera camera;

	private final Group cameraGroup;

//	private final AmbientLight light = new AmbientLight( Color.WHITE );

	private final PointLight l = new PointLight( Color.WHITE );

	private final Point3D cameraNormal = new Point3D( 0, 0, 1 );

	private final Point3D xNormal = new Point3D( 1, 0, 0 );

	private final Point3D yNormal = new Point3D( 0, 1, 0 );

	public Viewer3DFX( final double width, final double height, final Interval interval )
	{
		super();
		this.root = new Group();
		this.meshesGroup = new Group();
		this.setWidth( width );
		this.setHeight( height );
		this.scene = new SubScene( root, width, height, true, SceneAntialiasing.DISABLED );
		this.scene.setFill( Color.BLACK );
		this.camera = new PerspectiveCamera( true );
		this.camera.setNearClip( 0.01 );
		this.camera.setFarClip( 2.0 );
		this.camera.setTranslateY( 0 );
		this.camera.setTranslateX( 0 );
		this.camera.setTranslateZ( 0 );
		this.scene.setCamera( this.camera );
		this.cameraGroup = new Group();

		this.getChildren().add( this.scene );
		this.root.getChildren().addAll( cameraGroup, meshesGroup );
//		this.root.getChildren().add( light );
//		this.root.getChildren().add( l );
		this.scene.widthProperty().bind( widthProperty() );
		this.scene.heightProperty().bind( heightProperty() );
		l.setTranslateZ( -0.1 );
		this.cameraGroup.getChildren().addAll( camera, l );
//		this.cameraGroup.getTransforms().addAll( translate, rotX, rotY );
		this.cameraGroup.getTransforms().add( new Translate( 0, 0, -1 ) );

		final double[] xy = new double[ 2 ];
		final Affine initialTransform = new Affine();
		final Affine affine = new Affine();
		final Affine affineCopy = new Affine();
		meshesGroup.getTransforms().addAll( affine );
		initialTransform.prependTranslation( -interval.dimension( 0 ) / 2, -interval.dimension( 1 ) / 2, -interval.dimension( 2 ) / 2 );
		final double sf = 1.0 / interval.dimension( 0 );
		initialTransform.prependScale( sf, sf, sf );
		affine.setToTransform( initialTransform );
//		affine.prependTranslation( 0, 0, 0 );

		// TODO should these events invokeAndWait instead of invoke?
		this.addEventHandler( MouseEvent.MOUSE_PRESSED, event -> {
			xy[ 0 ] = event.getX();
			xy[ 1 ] = event.getY();
			affineCopy.setToTransform( affine );
		} );
//
		this.addEventHandler( MouseEvent.MOUSE_DRAGGED, event -> {
			if ( !event.isPrimaryButtonDown() || event.isSecondaryButtonDown() )
				return;
			final double dX = event.getX() - xy[ 0 ];
			final double dY = event.getY() - xy[ 1 ];
			final Affine affineCopy2 = new Affine( affineCopy );
			affineCopy2.prependRotation( dY, 0, 0, 0, new Point3D( 1, 0, 0 ) );
			affineCopy2.prependRotation( -dX, 0, 0, 0, new Point3D( 0, 1, 0 ) );

			InvokeOnJavaFXApplicationThread.invoke( () -> {
				affine.setToTransform( affineCopy2 );
			} );
		} );
		this.addEventHandler( MouseEvent.MOUSE_DRAGGED, event -> {
			if ( event.isPrimaryButtonDown() || !event.isSecondaryButtonDown() )
				return;
			final double dX = event.getX() - xy[ 0 ];
			final double dY = event.getY() - xy[ 1 ];
			final Affine affineCopy2 = new Affine( affineCopy );
			affineCopy2.prependTranslation( 2 * dX / getHeight(), 2 * dY / getHeight() );
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				affine.setToTransform( affineCopy2 );
			} );
		} );
		camera.setFieldOfView( 90 );

		this.addEventHandler( ScrollEvent.SCROLL, event -> {
			if ( Math.abs( event.getDeltaY() ) > Math.abs( event.getDeltaX() ) )
			{
				final double scroll = event.getDeltaY();
				final double factor = scroll > 0 ? 1.05 : 1 / 1.05;
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					affine.prependScale( factor, factor, factor );
				} );
			}
		} );

		this.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.Z ) && event.isShiftDown() )
				InvokeOnJavaFXApplicationThread.invoke( () -> affine.setToTransform( initialTransform ) );
		} );

	}

	public SubScene scene()
	{
		return scene;
	}

	public Group root()
	{
		return root;
	}

	public Group meshesGroup()
	{
		return meshesGroup;
	}

}
