package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.*
import javafx.collections.ObservableList
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import org.janelia.saalfeldlab.fx.extensions.nonnullVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread

class GlobalMeshProgressState(
	val meshInfosExpression: ObjectExpression<ObservableList<SegmentMeshInfo>>,
	val disableUpdates: BooleanExpression,
) : MeshProgressState() {

	private val meshInfos by meshInfosExpression.nonnullVal()

	init {
		if (!disableUpdates.get())
			updateCounts()
		meshInfosExpression.subscribe { it -> updateCounts() }
	}

	private fun chunkSize(size: Int): Int {
		val chunks = Runtime.getRuntime().availableProcessors() / 2
		return (size / chunks).coerceAtLeast(500)
	}

	private fun sumOfProperties(properties: List<IntegerExpression>): Int {
		val size = chunkSize(properties.size)
		return if (properties.size <= size)
			properties.sumOf { it.get() }

		else runBlocking {
			CoroutineScope(Dispatchers.Default + SupervisorJob()).async {
				properties.chunked(size).map { chunk ->
					async { chunk.sumOf { it.get() } }
				}
			}.await().awaitAll().sum()
		}
	}

	private fun getTotalSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val countProperties = meshProgressStates.map { it: MeshProgressState -> it.totalNumTasksProperty }
		return Bindings.createIntegerBinding(
			{ sumOfProperties(countProperties) },
			*countProperties.toTypedArray()
		)
	}

	private fun getCompletedSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val countProperties = meshProgressStates.map { it: MeshProgressState -> it.completedNumTasksProperty }
		return Bindings.createIntegerBinding(
			{ sumOfProperties(countProperties) },
			*countProperties.toTypedArray()
		)
	}

	var countSubscriptions: Subscription? = null

	private fun updateCounts() {
		countSubscriptions?.unsubscribe()
		countSubscriptions = null

		if (disableUpdates.get() || meshInfos.isEmpty()) return

		val meshProgressStates = meshInfos.mapNotNull { it: SegmentMeshInfo -> it.progressState }
		val totalSumBinding = getTotalSumBinding(meshProgressStates)
		val completedSumBinding = getCompletedSumBinding(meshProgressStates)

		val totalCountSub = totalSumBinding.subscribe { it -> writableTotalNumTasksProperty.set(it.toInt()) }
		val completedCountSub = completedSumBinding.subscribe { it -> writableCompletedNumTasksProperty.set(it.toInt()) }

		countSubscriptions = totalCountSub.and(completedCountSub)
		// progressState is null until the mesh generator creates it.
		//  However, this may initialize the UI prior to the MeshGenerator.
		//  Just try again later.
		if (meshInfos.size > meshProgressStates.size)
			InvokeOnJavaFXApplicationThread {
				awaitPulse()
				updateCounts()
			}
	}
}