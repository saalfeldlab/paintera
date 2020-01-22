package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.meshes.managed.PainteraMeshManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.BiConsumer;

public class MeshInfo
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Long segmentId;

	private final MeshSettings meshSettings;

	private final FragmentSegmentAssignment assignment;

	private final PainteraMeshManager<Long> meshManager;

	private final ObservableMeshProgress meshProgress;

	private final BooleanProperty isManaged;

	public MeshInfo(
			final Long segmentId,
			final MeshSettings meshSettings,
			final BooleanProperty isManaged,
			final FragmentSegmentAssignment assignment,
			final PainteraMeshManager<Long> meshManager)
	{
		super();
		this.segmentId = segmentId;
		this.meshSettings = meshSettings;
		this.isManaged = isManaged;
		this.assignment = assignment;
		this.meshManager = meshManager;

		// TODO
//		final MeshGenerator<T> meshGenerator = meshManager.unmodifiableMeshMap().get(segmentId);
//		this.meshProgress = meshGenerator != null ? meshGenerator.meshProgress() : null;
		this.meshProgress = null;
	}

	public Long segmentId()
	{
		return this.segmentId;
	}

	public IntegerProperty levelOfDetailProperty()
	{
		return this.meshSettings.levelOfDetailProperty();
	}

	public IntegerProperty coarsestScaleLevelProperty()
	{
		return this.meshSettings.coarsestScaleLevelProperty();
	}

	public IntegerProperty finestScaleLevelProperty()
	{
		return this.meshSettings.finestScaleLevelProperty();
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
		return meshSettings.getNumScaleLevels();
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

		final BiConsumer<MeshGenerator<Long>, U> apply;

		public PropagateChanges(final BiConsumer<MeshGenerator<Long>, U> apply)
		{
			super();
			this.apply = apply;
		}

		@Override
		public void changed(final ObservableValue<? extends U> observable, final U oldValue, final U newValue)
		{
			// TODO
//			final Map<Long, MeshGenerator<T>> meshes = meshManager.unmodifiableMeshMap();
//			apply.accept(meshes.get(segmentId), newValue);
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
		return o instanceof MeshInfo && Objects.equals(((MeshInfo) o).segmentId, segmentId);
	}

	public ObservableMeshProgress meshProgress()
	{
		return this.meshProgress;
	}

	public PainteraMeshManager<Long> meshManager()
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

	// TODO
//	public long[] containedFragments()
//	{
//		return meshManager.containedFragments(segmentId);
//	}

	public BooleanProperty isManagedProperty()
	{
		return this.isManaged;
	}

}
