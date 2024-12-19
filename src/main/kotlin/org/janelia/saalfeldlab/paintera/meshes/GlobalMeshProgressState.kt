package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanExpression
import javafx.beans.binding.IntegerBinding
import javafx.beans.binding.ListExpression
import javafx.collections.ListChangeListener
import javafx.util.Subscription
import kotlinx.coroutines.javafx.awaitPulse
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread

class GlobalMeshProgressState(val meshInfos: ListExpression<SegmentMeshInfo>, val disableUpdates: BooleanExpression) : MeshProgressState() {

	init {
		if (!disableUpdates.get())
			updateCounts()
		meshInfos.addListener(ListChangeListener { updateCounts() })
	}

	private fun getTotalSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val countProperties = meshProgressStates.map { it : MeshProgressState -> it.totalNumTasksProperty }.toTypedArray()
		return Bindings.createIntegerBinding({
			countProperties.sumOf { it.get()  }
		}, *countProperties)
	}

	private fun getCompletedSumBinding(meshProgressStates: List<MeshProgressState>): IntegerBinding {
		val countProperties = meshProgressStates.map { it : MeshProgressState -> it.completedNumTasksProperty }.toTypedArray()
		return Bindings.createIntegerBinding({
			countProperties.sumOf { it.get() }
		}, *countProperties)
	}

	var countSubscriptions : Subscription? = null

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