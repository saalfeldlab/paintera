package org.janelia.saalfeldlab.paintera

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.janelia.saalfeldlab.util.coroutineBackedExecutorService
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
internal object PainteraDispatchers {

	private fun CoroutineDispatcher.fractionalParallelism(fraction: Double) : CoroutineDispatcher {
		val processors = Runtime.getRuntime().availableProcessors()
		val paralellism = when {
			processors == 1 || fraction <= 0 -> 1
			fraction >= 1.0 -> processors
			else -> {
				val fractionalRange = 1..<processors
				(processors * fraction)
					.roundToInt()
					.coerceIn(fractionalRange)
			}
		}

		if (paralellism == processors) return this
		return limitedParallelism(paralellism)

	}

	internal fun CoroutineContext.asExecutorService() : ExecutorService {
		return coroutineBackedExecutorService(this).first
	}

	val MeshManagerDispatcher = Dispatchers.Default.fractionalParallelism(.75)
}