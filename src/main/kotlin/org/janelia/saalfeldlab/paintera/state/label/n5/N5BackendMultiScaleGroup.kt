package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.cache.SharedQueue
import com.google.gson.*
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.LocalIdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.dialogs.DataSourceDialogs
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Helpers.serializeTo
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction
import java.util.function.Supplier

class N5BackendMultiScaleGroup<D, T> constructor(
	override val metadataState: MetadataState,
	val propagationExecutorService: ExecutorService,
) : N5BackendLabel<D, T>
		where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

	override val container: N5Reader = metadataState.reader
	override val dataset: String = metadataState.dataset

	override fun createSource(
		queue: SharedQueue,
		priority: Int,
		name: String
	): DataSource<D, T> {
		return makeSource(
			metadataState,
			queue,
			priority,
			name,
			propagationExecutorService
		)
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun persistError(dataset: String) = "Persisting assignments not supported for non Paintera dataset $dataset."

		private fun <D, T> makeSource(
			metadataState: MetadataState,
			queue: SharedQueue,
			priority: Int,
			name: String,
			propagationExecutorService: ExecutorService,
		): DataSource<D, T> where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
			val dataSource = N5DataSource<D, T>(metadataState, name, queue, priority)
			return metadataState.writer?.let {
				val canvasDirSupplier = Masks.canvasTmpDirDirectorySupplier(paintera.properties.painteraDirectoriesConfig.appCacheDir)
				Masks.maskedSource(dataSource, queue, canvasDirSupplier.get(), canvasDirSupplier, CommitCanvasN5(metadataState), propagationExecutorService)
			} ?: dataSource
		}
	}


	override val fragmentSegmentAssignment = FragmentSegmentAssignmentOnlyLocal(
		FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
		FragmentSegmentAssignmentOnlyLocal.doesNotPersist(persistError(dataset))
	)

	override fun createIdService(source: DataSource<D, T>): IdService {
		return metadataState.writer?.let {
			N5Helpers.idService(
				it,
				dataset,
				Supplier { DataSourceDialogs.getN5IdServiceFromData(it, dataset, source) })
		} ?: LocalIdService(metadataState.reader.getAttribute(dataset, "maxId", Long::class.java) ?: 1L)
	}

	override fun createLabelBlockLookup(source: DataSource<D, T>) = DataSourceDialogs.getLabelBlockLookupFromN5DataSource(container, dataset, source)

	private object SerializationKeys {
		const val CONTAINER = "container"
		const val DATASET = "dataset"
		const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendMultiScaleGroup<D, T>>
			where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

		override fun serialize(
			backend: N5BackendMultiScaleGroup<D, T>,
			typeOfSrc: Type,
			context: JsonSerializationContext,
		): JsonElement {
			return with(SerializationKeys) {
				JsonObject().apply {
					backend.container.serializeTo(this)
					addProperty(DATASET, backend.dataset)
					add(FRAGMENT_SEGMENT_ASSIGNMENT, context[FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)])
				}
			}
		}

		override fun getTargetClass() = N5BackendMultiScaleGroup::class.java as Class<N5BackendMultiScaleGroup<D, T>>
	}

	class Deserializer<D, T>(
		private val projectDirectory: Supplier<String>,
		private val propagationExecutorService: ExecutorService,
	) : JsonDeserializer<N5BackendMultiScaleGroup<D, T>>
			where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

		@Plugin(type = StatefulSerializer.DeserializerFactory::class)
		class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendMultiScaleGroup<D, T>, Deserializer<D, T>>
				where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>,
			): Deserializer<D, T> = Deserializer(
				projectDirectory,
				arguments.viewer.propagationQueue
			)

			override fun getTargetClass() = N5BackendMultiScaleGroup::class.java as Class<N5BackendMultiScaleGroup<D, T>>
		}

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext,
		): N5BackendMultiScaleGroup<D, T> {
			return with(SerializationKeys) {
				with(GsonExtensions) {
					val container = N5Helpers.deserializeFrom(json.asJsonObject)
					val dataset: String = json[DATASET]!!
					val n5ContainerState = N5ContainerState(container)
					val metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset)!!
					if (metadataState.datasetAttributes.numDimensions > 3)
						metadataState.n5ContainerState = n5ContainerState.readOnlyCopy()
					N5BackendMultiScaleGroup<D, T>(
						metadataState,
						propagationExecutorService
					).also { json.getProperty(FRAGMENT_SEGMENT_ASSIGNMENT)?.asAssignmentActions(context)?.feedInto(it.fragmentSegmentAssignment) }
				}
			}
		}

		companion object {
			private fun JsonElement.asAssignmentActions(context: JsonDeserializationContext) = context
				.deserialize<FragmentSegmentAssignmentActions?>(this, FragmentSegmentAssignmentActions::class.java)
		}
	}
}
