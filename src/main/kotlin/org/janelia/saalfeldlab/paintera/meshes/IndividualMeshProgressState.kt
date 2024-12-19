package org.janelia.saalfeldlab.paintera.meshes

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread

class IndividualMeshProgressState : MeshProgressState() {

	fun set(numTasks: Int, numCompletedTasks: Int) = InvokeOnJavaFXApplicationThread {
		writableTotalNumTasksProperty.set(numTasks)
		writableCompletedNumTasksProperty.set(numCompletedTasks)
	}

	fun incrementNumCompletedTasks() = InvokeOnJavaFXApplicationThread {
		writableCompletedNumTasksProperty.set(writableCompletedNumTasksProperty.get() + 1)
	}
}