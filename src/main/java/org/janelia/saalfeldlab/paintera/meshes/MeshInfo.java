package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;

public class MeshInfo<T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Long segmentId;

	private final MeshSettings meshSettings;

	private final FragmentSegmentAssignment assignment;

	private final MeshManager<Long, T> meshManager;

	private final ObservableIntegerValue numTasks;

	private final ObservableIntegerValue numCompletedTasks;

	private final BooleanProperty isManaged;

	public MeshInfo(
			final Long segmentId,
			final MeshSettings meshSettings,
			final BooleanProperty isManaged,
			final FragmentSegmentAssignment assignment,
			final MeshManager<Long, T> meshManager)
	{
		super();
		this.segmentId = segmentId;
		this.meshSettings = meshSettings;
		this.isManaged = isManaged;
		this.assignment = assignment;
		this.meshManager = meshManager;

		final MeshGenerator<T> meshGenerator = meshManager.unmodifiableMeshMap().get(segmentId);
		if (meshGenerator != null)
		{
			this.numTasks = meshGenerator.numTasksProperty();
			this.numCompletedTasks = meshGenerator.numCompletedTasksProperty();
		}
		else
		{
			this.numTasks = null;
			this.numCompletedTasks = null;
		}
	}

	public Long segmentId()
	{
		return this.segmentId;
	}

	public IntegerProperty levelOfDetailProperty()
	{
		return this.meshSettings.levelOfDetailProperty();
	}

	public IntegerProperty highestScaleLevelProperty()
	{
		return this.meshSettings.highestScaleLevelProperty();
	}

	public IntegerProperty simplificationIterationsProperty()
	{
		return this.meshSettings.simplificationIterationsProperty();
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return this.meshSettings.smoothingLambdaProperty();
	}

	public IntegerProperty smoothingIterationsProperty()
	{
		return this.meshSettings.smoothingIterationsProperty();
	}

	public DoubleProperty minLabelRatioProperty()
	{
		return this.meshSettings.minLabelRatioProperty();
	}

	public FragmentSegmentAssignment assignment()
	{
		return this.assignment;
	}

	public int numScaleLevels()
	{
		return meshSettings.numScaleLevels();
	}

	public DoubleProperty opacityProperty()
	{
		return this.meshSettings.opacityProperty();
	}

	public DoubleProperty inflateProperty()
	{
		return this.meshSettings.inflateProperty();
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.meshSettings.isVisibleProperty();
	}

	private class PropagateChanges<U> implements ChangeListener<U>
	{

		final BiConsumer<MeshGenerator<T>, U> apply;

		public PropagateChanges(final BiConsumer<MeshGenerator<T>, U> apply)
		{
			super();
			this.apply = apply;
		}

		@Override
		public void changed(final ObservableValue<? extends U> observable, final U oldValue, final U newValue)
		{
			final Map<Long, MeshGenerator<T>> meshes = meshManager.unmodifiableMeshMap();
			apply.accept(meshes.get(segmentId), newValue);
		}

	}

	@Override
	public int hashCode()
	{
		return segmentId.hashCode();
	}

	@Override
	public boolean equals(final Object o)
	{
		return o instanceof MeshInfo<?> && ((MeshInfo<?>) o).segmentId == segmentId;
	}

	public ObservableIntegerValue numTasksProperty()
	{
		return this.numTasks;
	}

	public ObservableIntegerValue numCompletedTasksProperty()
	{
		return this.numCompletedTasks;
	}

	public MeshManager<Long, T> meshManager()
	{
		return this.meshManager;
	}

	public ObjectProperty<DrawMode> drawModeProperty()
	{
		return this.meshSettings.drawModeProperty();
	}

	public ObjectProperty<CullFace> cullFaceProperty()
	{
		return this.meshSettings.cullFaceProperty();
	}

	public long[] containedFragments()
	{
		return meshManager.containedFragments(segmentId);
	}

	public BooleanProperty isManagedProperty()
	{
		return this.isManaged;
	}

}
