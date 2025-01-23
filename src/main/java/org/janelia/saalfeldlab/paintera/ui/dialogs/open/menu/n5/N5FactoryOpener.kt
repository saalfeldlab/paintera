package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState
import org.janelia.saalfeldlab.util.PainteraCache
import java.nio.file.Paths

class N5FactoryOpener(private val openSourceState: OpenSourceState) {


	private val selectionProperty: StringProperty = SimpleStringProperty()
	private var selection by selectionProperty.nullable()

	private val containerStateProperty: ObjectProperty<N5ContainerState?> = SimpleObjectProperty()
	private val isOpeningContainer: BooleanProperty = SimpleBooleanProperty(false)

	init {
		containerStateProperty.subscribe { containerState ->
			openSourceState.parseContainer(containerState)
		}
		selectionProperty.subscribe { _, new -> selectionChanged(new) }
		DEFAULT_DIRECTORY?.let {
			selection = Paths.get(it).toRealPath().toString()
		}
	}

	fun backendDialog() = OpenSourceDialog(openSourceState, selectionProperty, )

	private fun cacheAsRecent(n5ContainerLocation: String) {
		PainteraCache.RECENT_CONTAINERS.appendLine(n5ContainerLocation, 50)
	}

	private var createContainerStateJob: Job? = null

	private fun selectionChanged(newSelection: String?) {
		if (newSelection.isNullOrBlank()) {
			containerStateProperty.set(null)
			return
		}

		createContainerStateJob?.cancel("Cancelled by new selection")
		createContainerStateJob = CoroutineScope(Dispatchers.IO).launch {
			isOpeningContainer.set(true)

			val state = n5ContainerStateCache.getOrPut(newSelection) {
				val n5 = n5Factory.openReaderOrNull(newSelection)
				ensureActive()
				n5?.let { N5ContainerState(it) }
			}
			state?.let { cacheAsRecent(newSelection) }
			containerStateProperty.set(state)
		}.apply {
			invokeOnCompletion { cause ->
				when (cause) {
					null -> Unit
					is CancellationException -> LOG.trace(cause) {}
					else -> LOG.error(cause) { "Error opening container: $newSelection" }
				}
				isOpeningContainer.set(false)
			}
		}
	}

	companion object {
		private val DEFAULT_DIRECTORY = getPainteraConfig("data", "n5", "defaultDirectory") {
			getPainteraConfig<String?>("data", "defaultDirectory") { null }
		}

		internal val FAVORITES: List<String> = getPainteraConfig("data", "n5", "favorites") { listOf() }

		internal val H5_EXTENSIONS = arrayOf("*.h5", "*.hdf", "*.hdf5")

		private val LOG: KLogger = KotlinLogging.logger {}
		internal val n5ContainerStateCache = HashMap<String, N5ContainerState?>()

		private fun <T> getPainteraConfig(vararg segments: String, fallback: () -> T) = PainteraConfigYaml.getConfig(fallback, *segments) as T
	}
}
