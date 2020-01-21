package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MeshInfos<T>
{
	private final ObservableList<MeshInfo<T>> infos = FXCollections.observableArrayList();

	private final ObservableList<MeshInfo<T>> readOnlyInfos = FXCollections.unmodifiableObservableList(infos);

	private final ManagedMeshSettings meshSettings;

	private final int numScaleLevels;

	public MeshInfos(
			final SelectedSegments selectedSegments,
			final MeshManager<Long, T> meshManager,
			final ManagedMeshSettings meshSettings,
			final int numScaleLevels)
	{
		super();

		this.meshSettings = meshSettings;
		this.numScaleLevels = numScaleLevels;

		final InvalidationListener updateMeshInfosHandler = obs -> {
			final long[] segments = selectedSegments.getSelectedSegments();
			final List<MeshInfo<T>> infos = Arrays
					.stream(segments)
					.mapToObj(id -> new MeshInfo<>(
							id,
							meshSettings.getOrAddMesh(id),
							meshSettings.isManagedProperty(id),
							selectedSegments.getAssignment(),
							meshManager
					))
					.collect(Collectors.toList());
			this.infos.setAll(infos);
		};

		selectedSegments.addListener(updateMeshInfosHandler);
		meshSettings.isMeshListEnabledProperty().addListener(updateMeshInfosHandler);
	}

	public ObservableList<MeshInfo<T>> readOnlyInfos()
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
