package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.util.fx.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.util.Duration;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.SimilarityTransformInterpolator;

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

	private final Transform cameraTransform = new Translate(0, 0, -1);

	private final ObjectProperty<ViewFrustum> viewFrustumProperty = new SimpleObjectProperty<>();

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty = new SimpleObjectProperty<>();

	private final BooleanProperty isMeshesEnabled = new SimpleBooleanProperty();

	private final BooleanProperty showBlockBoundaries = new SimpleBooleanProperty();

	private final IntegerProperty rendererBlockSize = new SimpleIntegerProperty();

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
		this.cameraGroup.getTransforms().add(cameraTransform);

		this.handler = new Scene3DHandler(this);

		this.root.visibleProperty().bind(this.isMeshesEnabled);

		final AffineTransform3D cameraAffineTransform = Transforms.fromTransformFX(cameraTransform);
		final Affine sceneTransform = new Affine();
		this.handler.addListener(obs -> {
				handler.getAffine(sceneTransform);
				final AffineTransform3D sceneToWorldTransform = Transforms.fromTransformFX(sceneTransform).inverse();
				eyeToWorldTransformProperty.set(sceneToWorldTransform.concatenate(cameraAffineTransform));
			});

		final InvalidationListener sizeChangedListener = obs -> viewFrustumProperty.set(
				new ViewFrustum(camera, new double[] {getWidth(), getHeight()})
			);
		widthProperty().addListener(sizeChangedListener);
		heightProperty().addListener(sizeChangedListener);

		// set initial value
		sizeChangedListener.invalidated(null);
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

	public ObjectProperty<ViewFrustum> viewFrustumProperty()
	{
		return this.viewFrustumProperty;
	}

	public ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty()
	{
		return this.eyeToWorldTransformProperty;
	}

	public BooleanProperty isMeshesEnabledProperty()
	{
		return this.isMeshesEnabled;
	}

	public BooleanProperty showBlockBoundariesProperty()
	{
		return this.showBlockBoundaries;
	}

	public IntegerProperty rendererBlockSizeProperty()
	{
		return this.rendererBlockSize;
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
		final SimilarityTransformInterpolator interpolator = new SimilarityTransformInterpolator(
				Transforms.fromTransformFX(currentState),
				Transforms.fromTransformFX(affine)
			);
		progressProperty.addListener((obs, oldv, newv) -> setAffine(Transforms.toTransformFX(interpolator.interpolateAt(newv.doubleValue()))));
		final KeyValue kv = new KeyValue(progressProperty, 1.0, Interpolator.EASE_BOTH);
		timeline.getKeyFrames().add(new KeyFrame(duration, kv));
		timeline.play();
	}

	public void getAffine(final Affine target) {
		handler.getAffine(target);
	}

	public void setAffine(final Affine affine) {
		handler.setAffine(affine);
	}
}
