package org.janelia.saalfeldlab.paintera.meshes;

import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithSingleMesh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class MeshInfo<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshManagerWithSingleMesh<T> meshManager;

	public MeshInfo(final MeshManagerWithSingleMesh<T> meshManager) {

		this.meshManager = meshManager;
	}

	public MeshSettings getMeshSettings() {

		return meshManager.getSettings();
	}

	public MeshManagerWithSingleMesh<T> meshManager() {

		return this.meshManager;
	}

	public T getKey() {

		return this.meshManager.getMeshKey();
	}
}
