package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshInfo<T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Long segmentId;

	private final MeshSettings meshSettings;

	private final FragmentSegmentAssignment assignment;

	private final MeshManager<Long, T> meshManager;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty completedTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty successfulTasks = new SimpleIntegerProperty(0);

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

		listen();

		updateTasksCountBindings();
		if (assignment instanceof FragmentSegmentAssignmentState)
		{
			((FragmentSegmentAssignmentState) assignment).addListener(obs -> updateTasksCountBindings());
		}

	}

	public void listen()
	{
	}

	public void hangUp()
	{
	}

	private void updateTasksCountBindings()
	{
		LOG.debug("Updating task count bindings.");
		final Map<Long, MeshGenerator<T>> meshes = new HashMap<>(meshManager.unmodifiableMeshMap());
		LOG.debug("Binding meshes to segmentId = {}", segmentId);
		Optional.ofNullable(meshes.get(segmentId)).map(MeshGenerator::submittedTasksProperty).ifPresent(this
				.submittedTasks::bind);
		Optional.ofNullable(meshes.get(segmentId)).map(MeshGenerator::completedTasksProperty).ifPresent(this
				.completedTasks::bind);
		Optional.ofNullable(meshes.get(segmentId)).map(MeshGenerator::successfulTasksProperty).ifPresent(this
				.successfulTasks::bind);
	}

	public Long segmentId()
	{
		return this.segmentId;
	}

	public IntegerProperty scaleLevelProperty()
	{
		return this.meshSettings.scaleLevelProperty();
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

	public ObservableIntegerValue submittedTasksProperty()
	{
		return this.submittedTasks;
	}

	public ObservableIntegerValue completedTasksProperty()
	{
		return this.completedTasks;
	}

	public ObservableIntegerValue successfulTasksProperty()
	{
		return this.successfulTasks;
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
