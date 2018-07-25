package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

public class MeshInfos<T>
{
	private final ObservableList<MeshInfo<T>> infos = FXCollections.observableArrayList();

	private final ObservableList<MeshInfo<T>> readOnlyInfos = FXCollections.unmodifiableObservableList(infos);

	private final ManagedMeshSettings meshSettings;

	public MeshInfos(
			final SelectedSegments selectedSegments,
			final FragmentSegmentAssignment assignment,
			final MeshManager<Long, T> meshManager,
			final ManagedMeshSettings meshSettings,
			final int numScaleLevels)
	{
		super();

		this.meshSettings = meshSettings;

		selectedSegments.addListener(obs -> {
			final long[] segments = selectedSegments.getSelectedSegments();
			final List<MeshInfo<T>> infos = Arrays
					.stream(segments)
					.mapToObj(id -> new MeshInfo<>(
							id,
							meshSettings.getOrAddMesh(id),
							meshSettings.isManagedProperty(id),
							assignment,
							meshManager
					))
					.collect(Collectors.toList());

			this.infos.forEach(MeshInfo::hangUp);
			this.infos.setAll(infos);
		});
	}

	public ObservableList<MeshInfo<T>> readOnlyInfos()
	{
		return this.readOnlyInfos;
	}

	public ManagedMeshSettings meshSettings()
	{
		return meshSettings;
	}
}
