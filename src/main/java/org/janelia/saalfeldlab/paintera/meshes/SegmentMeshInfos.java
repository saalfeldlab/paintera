package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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
	  final var curInfos = this.infos().stream().map(SegmentMeshInfo::segmentId).collect(Collectors.toSet());
	  this.infos().removeIf(info -> !selectedSegments.isSegmentSelected(info.segmentId()));
	  final var addInfos = new ArrayList<SegmentMeshInfo>();
	  Arrays.stream(segments).forEach(id -> {
		if (!curInfos.contains(id)) {
		  final MeshSettings settings = meshSettings.getOrAddMesh(id, true);
		  addInfos.add(new SegmentMeshInfo(
				  id,
				  settings,
				  meshSettings.isManagedProperty(id),
				  selectedSegments.getAssignment(),
				  meshManager));
		}
	  });
	  this.infos().addAll(addInfos);
	};

	meshManager.getMeshUpdateObservable().addListener(updateMeshInfosHandler);
	meshSettings.isMeshListEnabledProperty().addListener(updateMeshInfosHandler);
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
