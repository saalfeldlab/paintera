package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.BooleanProperty;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

public class SegmentMeshInfo {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Long segmentId;

	private final MeshSettings meshSettings;

	private final FragmentSegmentAssignment assignment;

	private final MeshManagerWithAssignmentForSegments meshManager;

	private final ObservableMeshProgress meshProgress;

	private final BooleanProperty isManaged;

	public SegmentMeshInfo(
			final Long segmentId,
			final MeshSettings meshSettings,
			final BooleanProperty isManaged,
			final FragmentSegmentAssignment assignment,
			final MeshManagerWithAssignmentForSegments meshManager) {

		this.segmentId = segmentId;
		this.meshSettings = meshSettings;
		this.isManaged = isManaged;
		this.assignment = assignment;
		this.meshManager = meshManager;

		final MeshGenerator.State meshGeneratorState = meshManager.getStateFor(segmentId);
		this.meshProgress = meshGeneratorState == null ? null : meshGeneratorState.getProgress();
	}

	public Long segmentId() {

		return this.segmentId;
	}

	public MeshSettings getMeshSettings() {

		return this.meshSettings;
	}

	@Override
	public int hashCode() {

		return segmentId.hashCode();
	}

	@Override
	public boolean equals(final Object o) {

		return o instanceof SegmentMeshInfo && Objects.equals(((SegmentMeshInfo)o).segmentId, segmentId);
	}

	public ObservableMeshProgress meshProgress() {

		return this.meshProgress;
	}

	public MeshManagerWithAssignmentForSegments meshManager() {

		return this.meshManager;
	}

	public long[] containedFragments() {

		final TLongHashSet fragments = meshManager.getContainedFragmentsFor(segmentId);
		return fragments == null ? new long[]{} : fragments.toArray();
	}

	public BooleanProperty isManagedProperty() {

		return this.isManaged;
	}

}
