package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty

abstract class MeshProgressState {

	data class Progress(val totalTasks: Int, val completeTasks: Int)

	protected open val progressProperty = SimpleDoubleProperty(0.0)
	val progressBinding: ReadOnlyDoubleProperty = ReadOnlyDoubleProperty.readOnlyDoubleProperty(progressProperty)

	@Volatile
	var progressData = Progress(0, 0)
		private set(value) {
			field = value
			progressProperty.set(field.completeTasks.toDouble() / (field.totalTasks.takeUnless { it == 0 } ?: 1))
		}

	fun set(total : Int?, completed : Int?) {
		progressData = Progress(
			total ?: progressData.totalTasks,
			completed ?: progressData.completeTasks)
	}

	@JvmOverloads
	fun increment(total: Int = 0, completed: Int = 1) {
		val (curTotal, curCompleted) = progressData
		progressData = Progress(curTotal + total, curCompleted + completed)
	}

}