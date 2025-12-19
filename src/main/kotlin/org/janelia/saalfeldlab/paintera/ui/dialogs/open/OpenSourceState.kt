package org.janelia.saalfeldlab.paintera.ui.dialogs.open

import bdv.cache.SharedQueue
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.collections.FXCollections.observableMap
import javafx.collections.FXCollections.synchronizedObservableMap
import javafx.scene.Group
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.awaitPulse
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.cache.ParsedN5LoaderCache
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSourceState {

	private val containerStatePropertyWrapper = ReadOnlyObjectWrapper<N5ContainerState?>(null)
	private var writableContainerState by containerStatePropertyWrapper.nullable()

	val containerStateProperty = containerStatePropertyWrapper.readOnlyProperty!!
	val containerState by containerStateProperty.nullableVal()

	val activeNodeProperty = SimpleObjectProperty<N5TreeNode?>()
	var activeNode by activeNodeProperty.nullable()
	val activeMetadataProperty = activeNodeProperty.map {it?.metadata as? SpatialMetadata }!!

	val resolutionProperty = SimpleObjectProperty<DoubleArray?>()
	var resolution by resolutionProperty.nullable()

	val translationProperty = SimpleObjectProperty<DoubleArray?>()
	var translation by translationProperty.nullable()

	val minIntensityProperty = SimpleDoubleProperty(0.0)
	var minIntensity by minIntensityProperty.nonnull()

	val maxIntensityProperty = SimpleDoubleProperty(255.0)
	var maxIntensity by maxIntensityProperty.nonnull()

	val metadataStateBinding = activeMetadataProperty.createObservableBinding(containerStateProperty) {
		val metadata = activeMetadataProperty.value ?: return@createObservableBinding null
		val container = containerStateProperty.value ?: return@createObservableBinding null

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
	val dimensionsBinding = metadataStateBinding.createNullableValueBinding { it?.datasetAttributes?.dimensions }

	val datasetPath get() = activeNodeProperty.get()?.path
	val sourceNameProperty = SimpleStringProperty().also { prop ->
		activeNodeProperty.subscribe { it ->
			prop.value = arrayOf(datasetPath, containerState?.uri?.path).firstNotNullOfOrNull { it?.split("/")?.lastOrNull { it.isNotEmpty() } }
		}
	}
	var sourceName: String by sourceNameProperty.nonnull()

	val validDatasets = SimpleMapProperty<String, N5TreeNode>(synchronizedObservableMap(observableMap(ConcurrentHashMap())))
	val statusProperty = SimpleStringProperty()

	private var parseJob: Deferred<Map<String, N5TreeNode>?>? = null

	fun parseContainer(state: N5ContainerState?, cache: ParsedN5LoaderCache): Deferred<Map<String, N5TreeNode>?>? {
		writableContainerState = state
		InvokeOnJavaFXApplicationThread { activeNode = null }
		state ?: let {
			statusProperty.unbind()
			statusProperty.value = "Container not found"
			validDatasets.clear()
			parseJob = null
			return null
		}
		val ( observableMap, statusCallback, job) = cache.observableRequest(state.reader)
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
				/* unbind regardless */
				validDatasets.unbindContent(observableMap)
				statusUpdateJob.cancel()
				statusProperty.value = ""
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


	companion object {
		private val LOG = KotlinLogging.logger { }

		private data class MutableInfoState(
			val resolution: DoubleArray = DoubleArray(3) { 1.0 },
			val translation: DoubleArray = DoubleArray(3),
			val min: Double = Double.NaN,
			val max: Double = Double.NaN
		)

		@JvmStatic
		fun <T, V> getRaw(
			openSourceState: OpenSourceState,
			sharedQueue: SharedQueue,
			priority: Int
		): SourceState<T, V>
				where
				T : RealType<T>, T : NativeType<T>,
				V : AbstractVolatileRealType<T, V>, V : NativeType<V> {

			val metadataState = openSourceState.metadataState!!.copy()
			openSourceState.resolution?.let { resolution ->
				openSourceState.translation?.let { translation ->
                    if (
                        !metadataState.resolution.contentEquals(resolution) ||
                        !metadataState.translation.contentEquals(translation)
                    )
                        metadataState.updateTransform(resolution, translation)
				}
			}

			val backend = N5BackendRaw<T, V>(metadataState)
			return ConnectomicsRawState(backend, sharedQueue, priority, openSourceState.sourceName).apply {
				converter().min = openSourceState.minIntensity
				converter().max = openSourceState.maxIntensity
			}
		}


		@JvmStatic
		fun <T, V> getChannels(
			openSourceState: OpenSourceState,
			channelSelection: IntArray,
			sharedQueue: SharedQueue,
			priority: Int
		): List<SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>>
				where
				T : RealType<T>, T : NativeType<T>,
				V : AbstractVolatileRealType<T, V>, V : NativeType<V> {

			val metadataState = openSourceState.metadataState!!.copy().also {
				/* We are explicitly not opening a label source*/
				it.isLabel = false
				openSourceState.resolution?.let { resolution ->
					openSourceState.translation?.let { translation ->
						if (!it.resolution.contentEquals(resolution) || !it.translation.contentEquals(translation))
							it.updateTransform(resolution, translation)
					}
				}
			}

			var channelIdx = metadataState.channelAxis!!.second
			var backend = N5BackendChannel<T, V>(metadataState, channelSelection, channelIdx)
			val state = ConnectomicsChannelState(backend, sharedQueue, priority, openSourceState.sourceName).apply {
				converter().setMins { openSourceState.minIntensity }
				converter().setMaxs { openSourceState.maxIntensity }
			}
			return listOf(state)
		}


		@JvmStatic
		fun <T, V> getLabels(
			openSourceState: OpenSourceState,
			sharedQueue: SharedQueue,
			priority: Int,
			meshesGroup: Group,
			viewFrustumProperty: ObjectProperty<ViewFrustum>,
			eyeToWorldTransformProperty: ObjectProperty<AffineTransform3D>,
			workers: HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority>,
			propagationQueue: ExecutorService,
		): SourceState<T, V>
				where
				T : IntegerType<T>, T : NativeType<T>,
				V : Volatile<T>, V : NativeType<V> {

			val metadataState = openSourceState.metadataState!!.copy()
			if (metadataState.datasetAttributes.numDimensions > 3) {
				metadataState.n5ContainerState = metadataState.n5ContainerState.readOnlyCopy()
				/* We are explicitly opening a label source */
				metadataState.isLabel = true
			}
			openSourceState.resolution?.let { resolution ->
				openSourceState.translation?.let { translation ->
                    if (
                        !metadataState.resolution.contentEquals(resolution) ||
                        !metadataState.translation.contentEquals(translation)
                    )
                        metadataState.updateTransform(resolution, translation)
				}
			}

			val backend = N5BackendLabel.createFrom<T, V>(metadataState, propagationQueue)
			return ConnectomicsLabelState(
				backend,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				workers,
				sharedQueue,
				priority,
				openSourceState.sourceName,
				null
			)
		}

	}
}

fun main() {
	val opener = OpenSourceState()

	val containerLoaderCache = ParsedN5LoaderCache()
	opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!), containerLoaderCache)
	opener.parseContainer(null, containerLoaderCache)
	opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!), containerLoaderCache)
	opener.parseContainer(null, containerLoaderCache)
	var job = opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!), containerLoaderCache)
	opener.validDatasets.subscribe { map ->
		map.keys.forEach { key -> println(key) }
		println("\n")
	}
	runBlocking {
		job?.await()
	}

	job = opener.parseContainer(N5ContainerState(Paintera.n5Factory.openReaderOrNull("s3://janelia-cosem-datasets/jrc_mus-kidney/jrc_mus-kidney.zarr")!!), containerLoaderCache)
	runBlocking {
		job?.await()
	}
}