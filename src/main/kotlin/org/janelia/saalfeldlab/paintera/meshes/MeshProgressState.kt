package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.paintera.cache.SamEmbeddingLoaderCache.invalidate

abstract class MeshProgressState {

	/**
	 * Progress tracker for MeshProgressState. Progress should always be `complete/total`
	 * special cases:
	 *  - total initialized to -1 and results in a progress of `0.0` (no work done yet)
	 *  - total == 0 results in a progress of `1.0` (no work to do)
	 *
	 * @property totalTasks to complete for this progress to be finished. Initialized to `-1`.
	 * @property completeTasks completed
	 * @constructor Create empty Progress
	 */
	data class Progress(val totalTasks: Int, val completeTasks: Int)

	protected open val progressProperty = object : SimpleDoubleProperty(0.0) {
		override fun set(newValue: Double) {
			if (newValue >= 1.0)
				println("?")
			super.set(newValue)
		}
	}
	val progressBinding: ReadOnlyDoubleProperty = ReadOnlyDoubleProperty.readOnlyDoubleProperty(progressProperty)

	@Volatile
	var progressData = Progress(-1, 0)
		private set(value) {
			field = value
			val (total, complete) = value
			when {
				total < 0 -> progressProperty.set(0.0)
				total == 0 -> progressProperty.set(1.0)
				else -> progressProperty.set(complete.toDouble() / (total.toDouble()))
			}
		}

	fun reset() {
		progressData = Progress(-1, 0)
	}

	fun set(total : Int?, completed : Int?) {
		progressData = Progress(
			total ?: progressData.totalTasks,
			completed ?: progressData.completeTasks)
	}

	@JvmOverloads
	fun increment(total: Int = 0, completed: Int = 1) {
		val (curTotal, curCompleted) = progressData
		val newTotal = curTotal.coerceAtLeast(0) + total
		progressData = Progress(newTotal, curCompleted + completed)
	}

}