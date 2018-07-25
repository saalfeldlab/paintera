package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;

public interface HasMeshes<T>
{

	public MeshManager<Long, T> meshManager();

	public ManagedMeshSettings managedMeshSettings();

}
