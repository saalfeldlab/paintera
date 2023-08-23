package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlyMapWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class SegmentMeshInfos {

	private final ReadOnlyListWrapper<SegmentMeshInfo> readOnlyInfos = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
	private final ManagedMeshSettings meshSettings;

	private final int numScaleLevels;

	public SegmentMeshInfos(
			final SelectedSegments selectedSegments,
			final MeshManagerWithAssignmentForSegments meshManager,
			final ManagedMeshSettings meshSettings,
			final int numScaleLevels) {

		super();

		this.meshSettings = meshSettings;
		this.numScaleLevels = numScaleLevels;

		final InvalidationListener updateMeshInfosHandler = obs -> {
			final long[] segments = selectedSegments.getSelectedSegmentsCopyAsArray();
			this.infos().clear();
			final var addInfos = new ArrayList<SegmentMeshInfo>();
			Arrays.stream(segments).forEach(id -> {
				final MeshSettings settings = meshSettings.getOrAddMesh(id, true);
				addInfos.add(new SegmentMeshInfo(
						id,
						settings,
						meshSettings.isManagedProperty(id),
						selectedSegments.getAssignment(),
						meshManager));
			});
			this.infos().addAll(addInfos);
		};

		meshManager.getMeshUpdateObservable().addListener(updateMeshInfosHandler);
		meshSettings.isMeshListEnabledProperty().addListener(updateMeshInfosHandler);
	}

	public Color getColor(long id) {
		return readOnlyInfos.stream()
				.filter(it -> it.segmentId() == id)
				.map(it -> it.meshManager().getStateFor(it.segmentId()).getColor())
				.findFirst()
				.orElse(Color.WHITE);
	}

	private ObservableList<SegmentMeshInfo> infos() {

		return readOnlyInfos.get();
	}

	public ReadOnlyListProperty<SegmentMeshInfo> readOnlyInfos() {

		return this.readOnlyInfos.getReadOnlyProperty();
	}

	public ManagedMeshSettings meshSettings() {

		return meshSettings;
	}

	public int getNumScaleLevels() {

		return this.numScaleLevels;
	}
}
