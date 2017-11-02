package bdv.bigcat.viewer.viewer3d;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class Viewer3DFX extends Pane
{

	private final Group root;

	private final SubScene scene;

	private final PerspectiveCamera camera;

//	private final AmbientLight light = new AmbientLight( Color.WHITE );

	private final PointLight l = new PointLight( Color.WHITE );

	public Viewer3DFX( final double width, final double height )
	{
		super();
		this.root = new Group();
		this.setWidth( width );
		this.setHeight( height );
		this.scene = new SubScene( root, width, height );
		this.scene.setFill( Color.BLACK );
		this.camera = new PerspectiveCamera( true );
		this.camera.setNearClip( 0.1 );
		this.camera.setFarClip( 10000.0 );
		this.camera.setTranslateY( 0 );
		this.camera.setTranslateX( 0 );
		this.camera.setTranslateZ( 0 );
		this.scene.setCamera( this.camera );

		this.getChildren().add( this.scene );
//		this.root.getChildren().add( light );
		this.root.getChildren().add( l );
		this.scene.widthProperty().bind( widthProperty() );
		this.scene.heightProperty().bind( heightProperty() );
		this.l.translateXProperty().bind( this.camera.translateXProperty() );
		this.l.translateYProperty().bind( this.camera.translateYProperty() );
		this.l.translateZProperty().bind( this.camera.translateZProperty() );

	}

	public SubScene scene()
	{
		return scene;
	}

	public Group root()
	{
		return root;
	}

}
