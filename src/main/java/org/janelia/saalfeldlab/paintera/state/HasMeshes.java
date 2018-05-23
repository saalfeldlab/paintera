package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;

public interface HasMeshes< T >
{

	public MeshManager< T > meshManager();

	public MeshInfos< T > meshInfos();

}
