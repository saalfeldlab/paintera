package org.janelia.saalfeldlab.paintera.ui.dialogs.open

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import kotlinx.coroutines.*
import net.imglib2.cache.ref.SoftRefLoaderCache
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.cache.AsyncCacheWithLoader
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.util.n5.DatasetDiscovery
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSourceState {

	private val containerStatePropertyWrapper = ReadOnlyObjectWrapper<N5ContainerState?>(null)
	private var writableContainerState by containerStatePropertyWrapper.nullable()

	val containerStateProperty = containerStatePropertyWrapper.readOnlyProperty!!
	val containerState by containerStateProperty.nullableVal()

	val activeNodeProperty = SimpleObjectProperty<N5TreeNode?>()
	var activeNode by activeNodeProperty.nullable()
	val activeMetadataProperty = activeNodeProperty.createObservableBinding {
		it.get()?.let { node -> node.metadata as? SpatialMetadata }
	}

	val resolutionProperty = SimpleObjectProperty<DoubleArray?>()
	var resolution by resolutionProperty.nullable()

	val translationProperty = SimpleObjectProperty<DoubleArray?>()
	var translation by translationProperty.nullable()

	val minIntensityProperty = SimpleDoubleProperty(Double.NaN)
	var minIntensity by minIntensityProperty.nonnull()

	val maxIntensityProperty = SimpleDoubleProperty(Double.NaN)
	var maxIntensity by maxIntensityProperty.nonnull()

	val metadataStateBinding = activeMetadataProperty.createObservableBinding {
		val metadata = activeMetadataProperty.get() ?: return@createObservableBinding null
		val container = containerStateProperty.get() ?: return@createObservableBinding null

		MetadataUtils.createMetadataState(container, metadata)
	}.apply {
		subscribe { metadata ->
			val (resolution, translation, min, max) = metadata?.run {
				MutableInfoState(resolution, translation, minIntensity, maxIntensity)
			} ?: MutableInfoState()

			resolutionProperty.value = resolution
			translationProperty.value = translation
			minIntensity = min
			maxIntensity = max
		}
	}
	val metadataState by metadataStateBinding.nullableVal()

	val datasetAttributes get() = metadataState?.datasetAttributes
	val dimensionsBinding = metadataStateBinding.createObservableBinding { it.value?.datasetAttributes?.dimensions }

	val datasetPath get() = activeNodeProperty.get()?.path
	val sourceNameProperty = SimpleStringProperty().also { prop ->
		activeNodeProperty.subscribe { it ->
			prop.value = datasetPath?.split("/")?.last()
		}
	}

	val validDatasets = SimpleObjectProperty<Map<String, N5TreeNode>>(emptyMap())

	private var parseJob: Deferred<Map<String, N5TreeNode>?>? = null

	fun parseContainer(state: N5ContainerState?): Deferred<Map<String, N5TreeNode>?>? {
		writableContainerState = state
		InvokeOnJavaFXApplicationThread { activeNode = null }
		parseJob?.cancel("Cancelled by new request")
		parseJob = state?.let { container ->
			ContainerLoaderCache.request(container).apply {
				invokeOnCompletion { cause ->
					when (cause) {
						null -> Unit
						is CancellationException -> {
							validDatasets.set(emptyMap<String, N5TreeNode>())
							LOG.trace(cause) {}
							return@invokeOnCompletion
						}

						else -> {
							validDatasets.set(emptyMap<String, N5TreeNode>())
							throw cause
						}
					}
					getCompleted()?.let {
						LOG.trace { "Found ${it.size} valid datasets at ${container.uri}" }
						validDatasets.set(it)
					}
				}
			}
		} ?: let {
			validDatasets.set(emptyMap())
			null
		}
		return parseJob
	}


	companion object {
		private val LOG = KotlinLogging.logger { }

		private data class MutableInfoState(
			val resolution: DoubleArray = DoubleArray(3) { 1.0 },
			val translation: DoubleArray = DoubleArray(3),
			val min: Double = Double.NaN,
			val max: Double = Double.NaN
		)

		@JvmStatic
		fun N5ContainerState.name() = uri.path.split("/").filter { it.isNotBlank() }.last()

		private fun getValidDatasets(node: N5TreeNode): MutableMap<String, N5TreeNode> {
			val map = mutableMapOf<String, N5TreeNode>()
			getValidDatasets(node, map)
			return map
		}

		private fun getValidDatasets(node: N5TreeNode, datasets: MutableMap<String, N5TreeNode>) {
			if (MetadataUtils.metadataIsValid(node.metadata))
				datasets[node.path] = node
			else
				node.childrenList().forEach { getValidDatasets(it, datasets) }

		}

		val ContainerLoaderCache = AsyncCacheWithLoader<N5ContainerState, Map<String, N5TreeNode>?>(
			SoftRefLoaderCache(),
			{ state ->
				val rootNode = DatasetDiscovery.parseMetadata(state.reader, "/")
				coroutineContext.ensureActive()
				val datasets = getValidDatasets(rootNode).run {
					remove("/")?.let { node -> put(state.name(), node) }

					isEmpty() && return@run null
					this
				}
				coroutineContext.ensureActive()
				datasets
			}
		)

	}
}

fun main() {
	val opener = OpenSourceState()

	opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!))
	opener.parseContainer(null)
	opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!))
	opener.parseContainer(null)
	var job = opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!))
	opener.validDatasets.subscribe { map ->
		map.keys.forEach { key -> println(key) }
		println("\n")
	}
	runBlocking {
		job?.await()
	}

	job = opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!))
	runBlocking {
		job?.await()
	}
}