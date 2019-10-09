package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.*
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.IntFunction
import java.util.function.Supplier

class N5BackendPainteraDataset<D, T> constructor(
	override val container: N5Writer,
	override val dataset: String,
	private val resolution: DoubleArray,
	private val offset: DoubleArray,
	queue: SharedQueue,
	priority: Int,
	name: String,
	projectDirectory: Supplier<String>,
	propagationExecutorService: ExecutorService) : N5Backend<D, T>
		where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {

	private val transform = N5Helpers.fromResolutionAndOffset(resolution, offset)
	override val source: DataSource<D, T> = makeSource(container, dataset, transform, queue, priority, name, projectDirectory, propagationExecutorService)
	override val lockedSegments: LockedSegmentsState = LockedSegmentsOnlyLocal(Consumer {})
	override val fragmentSegmentAssignment = N5Helpers.assignments(container, dataset)!!

	override val idService = N5Helpers
		.idService(container, dataset)!!
		.also { if (it is IsRelativeToContainer && container is N5FSReader) it.setRelativeTo(container.basePath, dataset) }

	override val labelBlockLookup = N5Helpers.getLabelBlockLookup(container, dataset)

	override fun setResolution(x: Double, y: Double, z: Double) {
		resolution[0] = x
		resolution[1] = y
		resolution[2] = z
		updateTransform()
	}

	override fun setOffset(x: Double, y: Double, z: Double) {
		offset[0] = x
		offset[1] = y
		offset[2] = z
		updateTransform()
	}

	override fun getResolution() = resolution.clone()

	override fun getOffset() = offset.clone()

	private fun updateTransform() = this.transform.set(N5Helpers.fromResolutionAndOffset(resolution, offset))

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun persistError(dataset: String) = "Persisting assignments not supported for non Paintera dataset $dataset."

		private fun <D, T> makeSource(
			container: N5Reader,
			dataset: String,
			transform: AffineTransform3D,
			queue: SharedQueue,
			priority: Int,
			name: String,
			projectDirectory: Supplier<String>,
			propagationExecutorService: ExecutorService): DataSource<D, T> where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {
			val dataSource = N5DataSource<D, T>(N5Meta.fromReader(container, dataset), transform, name, queue, priority)
			return if (container is N5Writer) {
				val tmpDir = Masks.canvasTmpDirDirectorySupplier(projectDirectory)
				Masks.mask(dataSource, queue, tmpDir.get(), tmpDir, CommitCanvasN5(container, dataset), propagationExecutorService)
			} else
				dataSource
		}
	}

	private object SerializationKeys {
		const val CONTAINER = "container"
		const val DATASET = "dataset"
		const val RESOLUTION = "resolution"
		const val OFFSET = "offset"
		const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
		const val LOCKED_SEGMENTS = "lockedSegments"
		const val NAME = "name"
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendPainteraDataset<D, T>>
			where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {

		override fun serialize(
			backend: N5BackendPainteraDataset<D, T>,
			typeOfSrc: Type,
			context: JsonSerializationContext): JsonElement {
			val map = JsonObject()
			with (SerializationKeys) {
				map.add(CONTAINER, SerializationHelpers.serializeWithClassInfo(backend.container, context))
				map.addProperty(DATASET, backend.dataset)
				map.add(RESOLUTION, context.serialize(backend.resolution))
				map.add(OFFSET, context.serialize(backend.offset))
				map.add(FRAGMENT_SEGMENT_ASSIGNMENT, context.serialize(FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)))
				map.add(LOCKED_SEGMENTS, context.serialize(backend.lockedSegments.lockedSegmentsCopy()))
				map.addProperty(NAME, backend.source.name)
			}
			return map
		}

		override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
	}

	class Deserializer<D, T>(
		private val queue: SharedQueue,
		private val priority: Int,
		private val projectDirectory: Supplier<String>,
		private val propagationExecutorService: ExecutorService) : JsonDeserializer<N5BackendPainteraDataset<D, T>>
			where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {

		@Plugin(type = StatefulSerializer.DeserializerFactory::class)
		class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendPainteraDataset<D, T>, Deserializer<D, T>>
				where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>): Deserializer<D, T> = Deserializer(
				arguments.viewer.queue,
				0,
				projectDirectory,
				arguments.viewer.propagationQueue)

			override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
		}

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext
		): N5BackendPainteraDataset<D, T> {
			return with (SerializationKeys) {
				with (GsonExtensions) {
					N5BackendPainteraDataset<D, T>(
						SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(CONTAINER)!!, context),
						json.getStringProperty(DATASET)!!,
						json.getProperty(RESOLUTION)?.let { context.deserialize<DoubleArray>(it, DoubleArray::class.java) } ?: DoubleArray(3) { 1.0 },
						json.getProperty(OFFSET)?.let { context.deserialize<DoubleArray>(it, DoubleArray::class.java) } ?: DoubleArray(3) { 0.0 },
						queue,
						priority,
						json.getStringProperty(NAME) ?: json.getStringProperty(DATASET)!!,
						projectDirectory,
						propagationExecutorService)
							.also { json.getProperty(FRAGMENT_SEGMENT_ASSIGNMENT)?.asAssignmentActions(context)?.feedInto(it.fragmentSegmentAssignment) }
				}
			}
		}

		companion object {
			private fun JsonElement.asAssignmentActions(context: JsonDeserializationContext) = context
					.deserialize<FragmentSegmentAssignmentActions?>(this, FragmentSegmentAssignmentActions::class.java)
		}
	}
}