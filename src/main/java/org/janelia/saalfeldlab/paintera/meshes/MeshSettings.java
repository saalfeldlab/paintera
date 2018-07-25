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

	private final int numScaleLevels;

	private final SimpleIntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final SimpleIntegerProperty simplificationIterations = new SimpleIntegerProperty(0);

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty(Smooth.DEFAULT_LAMBDA);

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty(Smooth.DEFAULT_ITERATIONS);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DrawMode.FILL);

	private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(CullFace.FRONT);

	private final DoubleProperty inflate = new SimpleDoubleProperty(1.0);

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

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
