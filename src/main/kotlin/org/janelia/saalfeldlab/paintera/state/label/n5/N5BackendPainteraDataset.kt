package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.*
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
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
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.nio.file.Paths
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.IntFunction
import java.util.function.Supplier

class N5BackendPainteraDataset<D, T> constructor(
	override val container: N5Writer,
	override val dataset: String,
	private val projectDirectory: Supplier<String>,
	private val propagationExecutorService: ExecutorService,
    private val backupLookupAttributesIfMakingRelative: Boolean) : N5Backend<D, T>
		where D: NativeType<D>, D: IntegerType<D>, T: net.imglib2.Volatile<D>, T: NativeType<T> {

	override fun createSource(
		queue: SharedQueue,
		priority: Int,
		name: String,
		resolution: DoubleArray,
		offset: DoubleArray): DataSource<D, T> {
		return makeSource<D, T>(
			container,
			dataset,
			N5Helpers.fromResolutionAndOffset(resolution, offset),
			queue,
			priority,
			name,
			projectDirectory,
			propagationExecutorService)
	}

	override val fragmentSegmentAssignment = N5Helpers.assignments(container, dataset)!!
	override val providesLookup = true

	override fun createIdService(source: DataSource<D, T>) = N5Helpers.idService(container, dataset)!!

	override fun createLabelBlockLookup(source: DataSource<D, T>): LabelBlockLookup {
		container.asN5FSWriter()?.makeN5LabelBlockLookupRelative(dataset, backupLookupAttributesIfMakingRelative)
		return N5Helpers.getLabelBlockLookup(container, dataset)
			.also { if (it is IsRelativeToContainer && container is N5FSReader) it.setRelativeTo(container, dataset) }
	}

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

		private object MakeLabelBlockLookupRelativeConstants {
			const val LABEL_BLOCK_LOOKUP = "labelBlockLookup"
			const val N5_FILESYSTEM = "n5-filesystem"
			const val N5_FILESYSTEM_RELATIVE = "n5-filesystem-relative"
			const val TYPE = "type"
			const val SLASH = "/"
			const val SCALE_DATASET_PATTERN = "scaleDatasetPattern"
			const val ROOT = "root"
			const val LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping"
		}

		private fun N5Writer.asN5FSWriter(): N5FSWriter? = this as? N5FSWriter

		private fun N5FSWriter.makeN5LabelBlockLookupRelative(
            painteraDataset: String,
            backupAttributes: Boolean,
            date: Date = Date(),
            dateFormat: DateFormat = SimpleDateFormat("'.bkp.'yyyy-mm-dd_HH-mm-ss"),
            overwrite: Boolean = false) {
			val constants = MakeLabelBlockLookupRelativeConstants
			val painteraDatasetNoLeadingSlash = painteraDataset.trimSlashStart()
			val labelBlockLookupJson = this.getAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, JsonObject::class.java)
			labelBlockLookupJson
				?.takeIf { with (GsonExtensions) { constants.N5_FILESYSTEM == it.getStringProperty(constants.TYPE) } }
				?.takeIf { with (GsonExtensions) { basePath == it.getStringProperty(constants.ROOT) } }
				?.takeIf { with (GsonExtensions) { it.getStringProperty(constants.SCALE_DATASET_PATTERN)?.trimSlashStart()?.startsWith(painteraDatasetNoLeadingSlash) ?: false } }
				?.let { json ->
					LOG.warn("Converting deprecated label block lookup format {} into {}.", constants.N5_FILESYSTEM, constants.N5_FILESYSTEM_RELATIVE)
                    if (backupAttributes) {
                        val painteraDatasetAttributes = Paths.get(basePath, *painteraDataset.split("/").toTypedArray()).resolve("attributes.json").toAbsolutePath().toFile()
                        val lookupAttributes = Paths.get(basePath, *painteraDataset.split("/").toMutableList().also { it += constants.LABEL_TO_BLOCK_MAPPING }.toTypedArray()).resolve("attributes.json").toAbsolutePath().toFile()
                        val suffix = dateFormat.format(date)
                        painteraDatasetAttributes.copyTo(File("${painteraDatasetAttributes.absolutePath}$suffix"), overwrite)
                        lookupAttributes.copyTo(File("${lookupAttributes.absolutePath}$suffix"), overwrite)
                    }
					val oldPattern = with (GsonExtensions) { json.getStringProperty(constants.SCALE_DATASET_PATTERN)!!.trimSlashStart() }
					val newPattern = oldPattern.replaceFirst(painteraDatasetNoLeadingSlash, "")
					val newLookupJson = JsonObject()
						.also { it.addProperty(constants.TYPE, constants.N5_FILESYSTEM_RELATIVE) }
						.also { it.addProperty(constants.SCALE_DATASET_PATTERN, newPattern) }
					setAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, newLookupJson)
					setAttribute("$painteraDataset/${constants.LABEL_TO_BLOCK_MAPPING}", constants.LABEL_BLOCK_LOOKUP, newLookupJson)
				}
		}

		private fun String.trimSlashStart() = trimStart(*MakeLabelBlockLookupRelativeConstants.SLASH.toCharArray())

	}

	private object SerializationKeys {
		const val CONTAINER = "container"
		const val DATASET = "dataset"
		const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
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
				map.add(FRAGMENT_SEGMENT_ASSIGNMENT, context.serialize(FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)))
			}
			return map
		}

		override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
	}

	class Deserializer<D, T>(
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
						projectDirectory,
						propagationExecutorService,
                        true)
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
