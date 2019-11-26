package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;

public class MeshSettings
{

	public static int DEFAULT_MESH_SIMPLIFICATION_ITERATIONS = 0;

	public static int DEFAULT_MESH_SMOOTHING_ITERATIONS = Smooth.DEFAULT_ITERATIONS;

	public static double DEFAULT_MESH_SMOOTHING_LAMBDA = Smooth.DEFAULT_LAMBDA;

	public static double DEFAULT_MESH_OPACITY = 1.0;

	public static DrawMode DEFAULT_MESH_DRAWMODE = DrawMode.FILL;

	public static CullFace DEFAULT_MESH_CULLFACE = CullFace.FRONT;

	public static double DEFAULT_MESH_INFLATE = 1.0;

	public static boolean DEFAULT_MESH_IS_VISIBLE = true;

	private final int numScaleLevels;

	private final SimpleIntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final SimpleIntegerProperty simplificationIterations = new SimpleIntegerProperty(DEFAULT_MESH_SIMPLIFICATION_ITERATIONS);

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty(DEFAULT_MESH_SMOOTHING_LAMBDA);

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty(DEFAULT_MESH_SMOOTHING_ITERATIONS);

	private final DoubleProperty opacity = new SimpleDoubleProperty(DEFAULT_MESH_OPACITY);

	private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DEFAULT_MESH_DRAWMODE);

	private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(DEFAULT_MESH_CULLFACE);

	private final DoubleProperty inflate = new SimpleDoubleProperty(DEFAULT_MESH_INFLATE);

	private final BooleanProperty isVisible = new SimpleBooleanProperty(DEFAULT_MESH_IS_VISIBLE);

	public MeshSettings(final int numScaleLevels)
	{
		super();
		this.numScaleLevels = numScaleLevels;
		this.scaleLevel.set(numScaleLevels - 1);
	}

	public IntegerProperty scaleLevelProperty()
	{
		return this.scaleLevel;
	}

	public IntegerProperty simplificationIterationsProperty()
	{
		return this.simplificationIterations;
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return this.smoothingLambda;
	}

	public IntegerProperty smoothingIterationsProperty()
	{
		return this.smoothingIterations;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	public ObjectProperty<DrawMode> drawModeProperty()
	{
		return this.drawMode;
	}

	public ObjectProperty<CullFace> cullFaceProperty()
	{
		return this.cullFace;
	}

	public DoubleProperty inflateProperty()
	{
		return this.inflate;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public int numScaleLevels()
	{
		return this.numScaleLevels;
	}

	public MeshSettings copy()
	{
		final MeshSettings that = new MeshSettings(this.numScaleLevels);
		that.set(this);
		return that;
	}

	public void set(final MeshSettings that)
	{
		this.scaleLevel.set(that.scaleLevel.get());
		this.simplificationIterations.set(that.simplificationIterations.get());
		this.smoothingLambda.set(that.smoothingLambda.get());
		this.smoothingIterations.set(that.smoothingIterations.get());
		this.opacity.set(that.opacity.get());
		this.drawMode.set(that.drawMode.get());
		this.cullFace.set(that.cullFace.get());
		this.inflate.set(that.inflate.get());
		this.isVisible.set(that.isVisible.get());
	}

}
