package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;

@Deprecated
public interface HasMeshes<T>
{

	MeshManager<Long, T> meshManager();

	ManagedMeshSettings managedMeshSettings();

	void refreshMeshes();

}
