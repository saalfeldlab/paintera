package bdv.bigcat.viewer.viewer3d;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class Viewer3DFX extends Pane
{

	private final Group root;

	private final SubScene scene;

	private final PerspectiveCamera camera;

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
		this.scene.widthProperty().bind( widthProperty() );
		this.scene.heightProperty().bind( heightProperty() );

		final Timeline tl = new Timeline( 30, new KeyFrame( Duration.seconds( 0.01 ), event -> {
			camera.setTranslateZ( camera.getTranslateZ() - 3 );
			camera.setTranslateX( camera.getTranslateX() + 0.5 );
		} ) );
		tl.setCycleCount( Animation.INDEFINITE );
		tl.play();

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
