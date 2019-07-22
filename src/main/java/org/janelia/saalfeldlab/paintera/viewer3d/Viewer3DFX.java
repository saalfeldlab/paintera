package org.janelia.saalfeldlab.paintera.viewer3d;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import javafx.util.Duration;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.SimilarityTransformInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

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

	private final Group arbitraryGroup;

	public Viewer3DFX(final double width, final double height)
	{
		super();
		this.root = new Group();
		this.meshesGroup = new Group();
		this.arbitraryGroup = new Group();
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
		this.root.getChildren().addAll(cameraGroup, meshesGroup, arbitraryGroup);
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

		this.arbitraryGroup.setVisible(true);

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

	public void getAffine(final Affine target) {
		handler.getAffine(target);
	}

	public void setAffine(final Affine affine, final Duration duration) {
		if (duration.toMillis() == 0.0) {
			setAffine(affine);
			return;
		}
		final Timeline timeline = new Timeline(60.0);
		timeline.setCycleCount(1);
		timeline.setAutoReverse(false);
		final Affine currentState = new Affine();
		getAffine(currentState);
		final DoubleProperty progressProperty = new SimpleDoubleProperty(0.0);
		final SimilarityTransformInterpolator interpolator = new SimilarityTransformInterpolator(fromAffine(currentState), fromAffine(affine));
		progressProperty.addListener((obs, oldv, newv) -> setAffine(fromAffineTransform3D(interpolator.interpolateAt(newv.doubleValue()))));
		final KeyValue kv = new KeyValue(progressProperty, 1.0, Interpolator.EASE_BOTH);
		timeline.getKeyFrames().add(new KeyFrame(duration, kv));
		timeline.play();
	}

	public void setAffine(final Affine affine) {
		handler.setAffine(affine);
	}

	private static Affine fromAffineTransform3D(final AffineTransform3D affineTransform3D) {
		return new Affine(
				affineTransform3D.get(0, 0), affineTransform3D.get(0, 1), affineTransform3D.get(0, 2), affineTransform3D.get(0, 3),
				affineTransform3D.get(1, 0), affineTransform3D.get(1, 1), affineTransform3D.get(1, 2), affineTransform3D.get(1, 3),
				affineTransform3D.get(2, 0), affineTransform3D.get(2, 1), affineTransform3D.get(2, 2), affineTransform3D.get(2, 3));
	};

	private static AffineTransform3D fromAffine(final Affine affine) {
		final AffineTransform3D affineTransform3D = new AffineTransform3D();
		affineTransform3D.set(
				affine.getMxx(), affine.getMxy(), affine.getMxz(), affine.getTx(),
				affine.getMyx(), affine.getMyy(), affine.getMyz(), affine.getTy(),
				affine.getMzx(), affine.getMzy(), affine.getMzz(), affine.getTz());
		return affineTransform3D;
	}

	public Group arbitraryGroup() {
		return this.arbitraryGroup;
	}
}
