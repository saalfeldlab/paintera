package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Translate;
import net.imglib2.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Viewer3DFX extends Pane
{

	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Group root;

	private final Group meshesGroup;

	private final SubScene scene;

	private final PerspectiveCamera camera;

	private final Group cameraGroup;

	private final AmbientLight lightAmbient = new AmbientLight(new Color(0.1, 0.1, 0.1, 1));

	private final PointLight lightSpot = new PointLight(new Color(1.0, 0.95, 0.85, 1));

	private final PointLight lightFill = new PointLight(new Color(0.35, 0.35, 0.65, 1));

	private final Scene3DHandler handler;

	private final Group3DCoordinateTracker coordinateTracker;

	private final BooleanProperty isMeshesEnabled = new SimpleBooleanProperty();

	public Viewer3DFX(final double width, final double height)
	{
		super();
		this.root = new Group();
		this.meshesGroup = new Group();
		this.coordinateTracker = new Group3DCoordinateTracker(meshesGroup);
		this.setWidth(width);
		this.setHeight(height);
		this.scene = new SubScene(root, width, height, true, SceneAntialiasing.BALANCED);
		this.scene.setFill(Color.BLACK);

		this.camera = new PerspectiveCamera(true);
		this.camera.setNearClip(0.01);
		this.camera.setFarClip(10.0);
		this.camera.setTranslateY(0);
		this.camera.setTranslateX(0);
		this.camera.setTranslateZ(0);
		this.camera.setFieldOfView(90);
		this.scene.setCamera(this.camera);
		this.cameraGroup = new Group();

		this.getChildren().add(this.scene);
		this.root.getChildren().addAll(cameraGroup, meshesGroup);
		this.scene.widthProperty().bind(widthProperty());
		this.scene.heightProperty().bind(heightProperty());
		lightSpot.setTranslateX(-10);
		lightSpot.setTranslateY(-10);
		lightSpot.setTranslateZ(-10);
		lightFill.setTranslateX(10);

		this.cameraGroup.getChildren().addAll(camera, lightAmbient, lightSpot, lightFill);
		this.cameraGroup.getTransforms().add(new Translate(0, 0, -1));

		handler = new Scene3DHandler(this);

		this.root.visibleProperty().bind(isMeshesEnabled);

	}

	public void setInitialTransformToInterval(final Interval interval)
	{
		handler.setInitialTransformToInterval(interval);
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

	public Group3DCoordinateTracker coordinateTracker()
	{
		return this.coordinateTracker;
	}

	public BooleanProperty isMeshesEnabledProperty()
	{
		return this.isMeshesEnabled;
	}
}
