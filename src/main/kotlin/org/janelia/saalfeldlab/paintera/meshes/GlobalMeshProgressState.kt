package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.*
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.ObservableList
import javafx.util.Subscription
import jdk.jshell.JShell
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.janelia.saalfeldlab.fx.ChannelLoop
import org.janelia.saalfeldlab.fx.extensions.invoke
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.extensions.subscribe

class GlobalMeshProgressState(
	val meshInfosExpression: ObjectExpression<ObservableList<SegmentMeshInfo>>,
	disableUpdates: BooleanExpression,
) : MeshProgressState() {

	var progressBindingSubscription: Subscription? = null

	private val updateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	private val channelLoop: ChannelLoop = ChannelLoop(updateScope, Channel.CONFLATED) {
		 delay(100)
	}

	init {

		var listSubscription : Subscription? = null
		meshInfosExpression.subscribe { list ->
			listSubscription?.unsubscribe()
			listSubscription = list?.subscribe {
				if (!disableUpdates.value) {
					updateProgressBindings()
				}
			}
		}

		disableUpdates.subscribe { skipUpdate ->
			if (!skipUpdate)
				updateProgressBindings()
		}
	}

	private fun chunkSize(size: Int): Int {
		val chunks = Runtime.getRuntime().availableProcessors() / 2
		return (size / chunks).coerceAtLeast(500)
	}

	private fun updateProgress(properties: List<DoubleExpression>) {
		val totalSize = properties.size
		val chunkSize = chunkSize(totalSize)

		channelLoop.submit {

			val progress = if (totalSize <= chunkSize) {
				properties.sumOf { it() } / totalSize
			} else {
				val chunkAvgParts = properties.chunked(chunkSize).map { chunk ->
					async { chunk.sumOf { it() } / chunk.size }
				}
				chunkAvgParts.awaitAll().sum()
			}
			progressProperty.set(progress)
		}
	}
	private fun resetProgress() = reset()

	@Synchronized
	private fun updateProgressBindings() {
		progressBindingSubscription?.unsubscribe()
		progressBindingSubscription = null
		resetProgress()
		val progressSubscriptions = mutableListOf<Subscription>()
		val progressPerMesh : List<DoubleExpression> = meshInfosExpression.get().map { meshInfo ->
			val progressBinding = SimpleDoubleProperty(0.0)
			val meshStateSubscription = meshInfo.meshStateProperty.subscribe { state ->
				if (state == null) {
					progressBinding.unbind()
					progressBinding.set(0.0)
				} else {
					progressBinding.bind(state.progress.progressBinding)
				}
			}
			progressSubscriptions += meshStateSubscription
			progressBinding
		}

		if (progressPerMesh.isEmpty())
			return

		val globalProgressSubscription = progressPerMesh.subscribe {
			updateProgress(progressPerMesh)
		}
		val meshProgressSubscriptions = Subscription {
			progressSubscriptions.forEach { it.unsubscribe() }
		}

		progressBindingSubscription = globalProgressSubscription + meshProgressSubscriptions

	}
}