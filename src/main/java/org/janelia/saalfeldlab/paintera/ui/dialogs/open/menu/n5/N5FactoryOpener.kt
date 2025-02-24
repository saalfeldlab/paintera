package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml
import org.janelia.saalfeldlab.paintera.cache.ParsedN5LoaderCache
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState
import org.janelia.saalfeldlab.util.PainteraCache
import java.nio.file.Paths

@OptIn(InternalCoroutinesApi::class)
class N5FactoryOpener(private val openSourceState: OpenSourceState) {


	private val selectionProperty: StringProperty = SimpleStringProperty()
	private var selection by selectionProperty.nullable()

	private val isBusyProperty: BooleanProperty = SimpleBooleanProperty(false)

	init {
		selectionProperty.subscribe { _, new -> selectionChanged(new) }
		DEFAULT_DIRECTORY?.let {
			selection = Paths.get(it).toRealPath().toString()
		}
	}

	fun backendDialog() = OpenSourceDialog(openSourceState, selectionProperty).also {
		it.openSourceNode.resetAction = { reparseSelection(it) }
		it.isBusy.bind(isBusyProperty)
		it.onCloseRequest = EventHandler {
			if (isBusyProperty.get()) {
				parseN5LoaderCache.cancelUnfinishedRequests()
				isBusyProperty.set(false)
				it.consume()
			}
		}
		it.dialogPane.scene.addEventFilter(KEY_PRESSED) { event ->
			if (event.code == KeyCode.ESCAPE) {
				it.result = null
				it.close()
				event.consume()
			}
		}
	}

	private fun cacheAsRecent(n5ContainerLocation: String) {
		PainteraCache.RECENT_CONTAINERS.appendLine(n5ContainerLocation, 50)
	}

	private val parseN5LoaderCache = ParsedN5LoaderCache()

	/**
	 * If the selection was previously parsed, clear the caches, and reparse
	 *
	 * @param selection to invalidate and parse
	 */
	fun reparseSelection(selection : String) {
		n5Factory.clearKey(selection)
		openSourceState.containerState?.reader?.let {
			n5ContainerStateCache -= selection
			parseN5LoaderCache.invalidate(it)
		}
		selectionChanged(selection)
	}

	private fun selectionChanged(newSelection: String?) {
		if (newSelection.isNullOrBlank()) {
			return
		}

		parseN5LoaderCache.cancelUnfinishedRequests()
		parseN5LoaderCache.loaderScope.launch {
			isBusyProperty.set(true)
			openSourceState.statusProperty.unbind()
			openSourceState.statusProperty.set("Opening container...")
			val state = n5ContainerStateCache.getOrPut(newSelection) {
				val n5 = n5Factory.openReaderOrNull(newSelection)
				ensureActive()
				n5?.let { N5ContainerState(it) }
			}
			val parseJob = openSourceState.parseContainer(state, parseN5LoaderCache)
			if (parseJob?.await()?.isNotEmpty() == true)
				cacheAsRecent(newSelection)
		}.invokeOnCompletion { cause ->
			isBusyProperty.set(false)
			when (cause) {
				null -> Unit
				is CancellationException -> LOG.trace(cause) {}
				else -> LOG.error(cause) { "Error opening container: $newSelection" }
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

		@JvmStatic
		fun main(args: Array<String>) {
			InvokeOnJavaFXApplicationThread {
				N5FactoryOpener(OpenSourceState()).backendDialog().apply {
					PainteraAlerts.initAppDialog(this)
					headerText = "Open Source Dataset"
					showAndWait()
				}
			}
		}
	}
}
