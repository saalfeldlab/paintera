package org.janelia.saalfeldlab.paintera.control.actions

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml
import org.janelia.saalfeldlab.paintera.cache.ParsedN5LoaderCache
import org.janelia.saalfeldlab.paintera.control.actions.state.PainteraActionState
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerStateCache
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.N5OpenSourceHelper
import org.janelia.saalfeldlab.util.PainteraCache
import java.nio.file.Paths

private val LOG = KotlinLogging.logger {}

internal class OpenSourceActionState(delegate: OpenSourceModel = OpenSourceModel.default()) :
	PainteraActionState(),
	OpenSourceModel by delegate {

	private var containerSelection by containerSelectionProperty.nullable()

	private val parseN5LoaderCache = ParsedN5LoaderCache()

	private val defaultModel = delegate as? DefaultOpenSourceModel

	init {
		containerSelectionProperty.subscribe { _, new -> selectionChanged(new) }
		DEFAULT_SOURCE_DIRECTORY?.let {
			containerSelection = Paths.get(it).toRealPath().toString()
		}
	}

	fun addSource() {
		try {
			N5OpenSourceHelper.addSource(type, this, channelSelection, paintera.baseView)
		} catch (_ : CancellationException) {
			LOG.warn { "add source cancelled " }
		}
	}

	fun cancelParsing() {
		parseN5LoaderCache.cancelUnfinishedRequests()
	}

	override fun reparseSelection(selection: String) {
		n5Factory.remove(selection)
		containerState?.reader?.let {
			N5ContainerStateCache.cache -= selection
			parseN5LoaderCache.invalidate(it)
		}
		selectionChanged(selection)
	}

	private fun selectionChanged(newSelection: String?) {
		if (newSelection.isNullOrBlank()) return

		parseN5LoaderCache.cancelUnfinishedRequests()
		parseN5LoaderCache.loaderScope.launch {
			InvokeOnJavaFXApplicationThread {
				isBusyProperty.set(true)
				statusProperty.unbind()
				statusProperty.set("Opening container...")
			}
			val state = N5ContainerStateCache.cache.getOrPut(newSelection) {
				val n5 = n5Factory.openReaderOrNull(newSelection)
				ensureActive()
				n5?.let { N5ContainerState(it) }
			}
			val parseJob = parseContainer(state)
			if (parseJob?.await()?.isNotEmpty() == true)
				cacheAsRecent(newSelection)
		}.invokeOnCompletion { cause ->
			InvokeOnJavaFXApplicationThread { isBusyProperty.set(false) }
			when (cause) {
				null -> Unit
				is CancellationException -> LOG.trace(cause) {}
				else -> LOG.error(cause) { "Error opening container: $newSelection" }
			}
		}
	}

	private var parseJob: Deferred<Map<String, org.janelia.saalfeldlab.n5.universe.N5TreeNode>?>? = null

	private fun parseContainer(state: N5ContainerState?): Deferred<Map<String, org.janelia.saalfeldlab.n5.universe.N5TreeNode>?>? {
		defaultModel?.writableContainerState = state
		InvokeOnJavaFXApplicationThread { activeNode = null }
		state ?: let {
			InvokeOnJavaFXApplicationThread {
				statusProperty.unbind()
				statusProperty.value = "Container not found"
			}
			validDatasets.clear()
			parseJob = null
			return null
		}
		val (observableMap, statusCallback, job) = parseN5LoaderCache.observableRequest(state.reader)
		validDatasets.bindContent(observableMap)
		val statusUpdateJob = InvokeOnJavaFXApplicationThread {
			delay(250)
			while (job.isActive) {
				ensureActive()
				statusProperty.set(statusCallback())
				awaitPulse()
			}
		}
		parseJob = job.apply {
			invokeOnCompletion { cause ->
				validDatasets.unbindContent(observableMap)
				statusUpdateJob.cancel()
				InvokeOnJavaFXApplicationThread { statusProperty.value = "" }
				when (cause) {
					null -> Unit
					is CancellationException -> {
						LOG.trace(cause) {}
						return@invokeOnCompletion
					}
					else -> {
						validDatasets.clear()
						throw cause
					}
				}
				getCompleted()?.let {
					LOG.trace { "Found ${it.size} valid datasets at ${state.uri}" }
					validDatasets.putAll(it)
				}
			}
		}
		return parseJob
	}

	private fun cacheAsRecent(n5ContainerLocation: String) {
		PainteraCache.RECENT_CONTAINERS.appendLine(n5ContainerLocation, 50)
	}

	companion object {
		internal val DEFAULT_SOURCE_DIRECTORY = getPainteraConfig("data", "n5", "defaultDirectory") {
			getPainteraConfig<String?>("data", "defaultDirectory") { null }
		}

		@Suppress("UNCHECKED_CAST")
		private fun <T> getPainteraConfig(vararg segments: String, fallback: () -> T) =
			PainteraConfigYaml.getConfig(fallback, *segments) as T
	}
}
