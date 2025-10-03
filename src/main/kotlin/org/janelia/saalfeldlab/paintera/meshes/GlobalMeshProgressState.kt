package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.*
import javafx.collections.ObservableList
import javafx.util.Subscription
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.fx.extensions.invoke
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread

class GlobalMeshProgressState(
	val meshInfosExpression: ObjectExpression<ObservableList<SegmentMeshInfo>>,
	val disableUpdates: BooleanExpression,
) : MeshProgressState() {

	var progressBindingSubscription: Subscription = Subscription.EMPTY

	init {
		meshInfosExpression.subscribe { it ->
			it
		}

		meshInfosExpression
			.`when`(disableUpdates.not())
			.subscribe { meshInfos ->
				updateCounts()
			}
	}

	private fun chunkSize(size: Int): Int {
		val chunks = Runtime.getRuntime().availableProcessors() / 2
		return (size / chunks).coerceAtLeast(500)
	}

	private fun sumOfInts(properties: List<() -> Int>): Int {
		val size = chunkSize(properties.size)
		return if (properties.size <= size)
			properties.sumOf { it() }
		else runBlocking {
			CoroutineScope(Dispatchers.Default + SupervisorJob()).async {
				properties.chunked(size).map { chunk ->
					async { chunk.sumOf { it() } }
				}
			}.await().awaitAll().sum()
		}
	}

	private fun sumOfDoubles(properties: List<DoubleExpression>): Double {
		val size = chunkSize(properties.size)
		return if (properties.size <= size)
			properties.sumOf { it() }
		else runBlocking {
			CoroutineScope(Dispatchers.Default + SupervisorJob()).async {
				properties.chunked(size).map { chunk ->
					async { chunk.sumOf { it() } }
				}
			}.await().awaitAll().sum()
		}
	}


	private fun getProgressAverageBinding(meshProgressStates: List<MeshProgressState>): DoubleBinding {
		val numMeshes = meshProgressStates.size
		val progressBindings = meshProgressStates.map { it.progressBinding }
		return Bindings.createDoubleBinding(
			{ sumOfDoubles(progressBindings) / numMeshes },
			*progressBindings.toTypedArray()
		)
	}

	private fun getTotalSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val bindings = meshProgressStates.map { it.progressBinding }
		val countProperties = meshProgressStates.map { it: MeshProgressState -> { it.progressData.totalTasks } }
		return Bindings.createIntegerBinding(
			{ sumOfInts(countProperties) },
			*bindings.toTypedArray()
		)
	}

	private fun getCompletedSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val bindings = meshProgressStates.map { it.progressBinding }
		val countProperties = meshProgressStates.map { it: MeshProgressState -> { it.progressData.completeTasks } }
		return Bindings.createIntegerBinding(
			{ sumOfInts(countProperties) },
			*bindings.toTypedArray()
		)
	}

	private fun resetProgress() = set(0, 0)

	private fun updateCounts() {
		progressBindingSubscription.unsubscribe()
		progressBindingSubscription = Subscription.EMPTY
		resetProgress()

		val progressStates = meshInfosExpression.get().map { it.progressState }

		when {
			progressStates.isEmpty() -> Unit
			progressStates.any { it == null } -> InvokeOnJavaFXApplicationThread {
				delay(100)
				updateCounts()
			}

			else -> {
				val globalProgressBinding = getProgressAverageBinding(progressStates as List<MeshProgressState>)
				progressProperty.bind(globalProgressBinding)
				progressBindingSubscription = Subscription { progressProperty.unbind() }
			}
		}


	}
}