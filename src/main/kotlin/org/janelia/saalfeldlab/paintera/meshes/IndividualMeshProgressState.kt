package org.janelia.saalfeldlab.paintera.meshes


class IndividualMeshProgressState : MeshProgressState() {

	fun set(numTasks: Int, numCompletedTasks: Int) {
		writableTotalNumTasksProperty.set(numTasks)
		writableCompletedNumTasksProperty.set(numCompletedTasks)
	}

	fun incrementNumCompletedTasks() {
		writableCompletedNumTasksProperty.set(writableCompletedNumTasksProperty.get() + 1)
	}
}