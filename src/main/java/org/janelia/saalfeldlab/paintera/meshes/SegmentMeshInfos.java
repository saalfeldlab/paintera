package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SegmentMeshInfos
{
	private final ObservableList<SegmentMeshInfo> infos = FXCollections.observableArrayList();

	private final ObservableList<SegmentMeshInfo> readOnlyInfos = FXCollections.unmodifiableObservableList(infos);

	private final ManagedMeshSettings meshSettings;

	private final int numScaleLevels;

	public SegmentMeshInfos(
			final SelectedSegments selectedSegments,
			final MeshManagerWithAssignmentForSegments meshManager,
			final ManagedMeshSettings meshSettings,
			final int numScaleLevels)
	{
		super();

		this.meshSettings = meshSettings;
		this.numScaleLevels = numScaleLevels;

		final InvalidationListener updateMeshInfosHandler = obs -> {
			final long[] segments = selectedSegments.getSelectedSegmentsCopyAsArray();
			final List<SegmentMeshInfo> infos = Arrays
					.stream(segments)
					.mapToObj(id -> {
						final MeshSettings settings = meshSettings.getOrAddMesh(id, true);
						return new SegmentMeshInfo(
								id,
								settings,
								meshSettings.isManagedProperty(id),
								selectedSegments.getAssignment(),
								meshManager);
					})
					.collect(Collectors.toList());
			this.infos.setAll(infos);
		};

		meshManager.getMeshUpdateObservable().addListener(updateMeshInfosHandler);
		meshSettings.isMeshListEnabledProperty().addListener(updateMeshInfosHandler);
	}

	public ObservableList<SegmentMeshInfo> readOnlyInfos()
	{
		return this.readOnlyInfos;
	}

	public ManagedMeshSettings meshSettings()
	{
		return meshSettings;
	}

	public int getNumScaleLevels() {
		return this.numScaleLevels;
	}
}
