package org.janelia.saalfeldlab.paintera.state.label.n5

import bdv.cache.SharedQueue
import com.google.gson.*
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer
import org.janelia.saalfeldlab.n5.FileSystemKeyValueAccess
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.mask.Masks
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.label.FragmentSegmentAssignmentActions
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.fragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.N5Helpers.serializeTo
import org.janelia.saalfeldlab.util.n5.universe.N5DatasetDoesntExist
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.function.IntFunction
import java.util.function.Supplier
import kotlin.io.path.toPath

class N5BackendPainteraDataset<D, T>(
	override val metadataState: MetadataState,
	private val propagationExecutorService: ExecutorService,
	private val backupLookupAttributesIfMakingRelative: Boolean,
) : N5BackendLabel<D, T>
		where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

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

	override val fragmentSegmentAssignment = metadataState.fragmentSegmentAssignmentState
	override val providesLookup = true

	//FIXME Caleb: same as above with idService
	override fun createIdService(source: DataSource<D, T>): IdService {
		return (metadataState.writer ?: metadataState.reader).let {
			N5Helpers.idService(it, dataset)
		}
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun <D, T> makeSource(
			metadataState: MetadataState,
			queue: SharedQueue,
			priority: Int,
			name: String,
			propagationExecutorService: ExecutorService,
		): DataSource<D, T> where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
			val dataSource = N5DataSource<D, T>(metadataState, name, queue, priority)
			val containerWriter = metadataState.writer
			return containerWriter?.let {
				val canvasDirSupplier = Masks.canvasTmpDirDirectorySupplier(paintera.properties.painteraDirectoriesConfig.appCacheDir)
				Masks.maskedSource(dataSource, queue, canvasDirSupplier.get(), canvasDirSupplier, CommitCanvasN5(metadataState), propagationExecutorService)
			} ?: dataSource
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

		private fun N5KeyValueWriter.makeN5LabelBlockLookupRelative(
			painteraDataset: String,
			backupAttributes: Boolean,
			date: Date = Date(),
			dateFormat: DateFormat = SimpleDateFormat("'.bkp.'yyyy-mm-dd_HH-mm-ss"),
			overwrite: Boolean = false
		) {
			if (keyValueAccess !is FileSystemKeyValueAccess) return
			val constants = MakeLabelBlockLookupRelativeConstants
			val painteraDatasetNoLeadingSlash = painteraDataset.trimSlashStart()
			val labelBlockLookupJson = this.getAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, JsonObject::class.java)
			with(GsonExtensions) {
				labelBlockLookupJson
					?.takeIf { constants.N5_FILESYSTEM == it.getStringProperty(constants.TYPE) }
					?.takeIf { uri.path == it.getStringProperty(constants.ROOT) }
					?.takeIf { it.getStringProperty(constants.SCALE_DATASET_PATTERN)?.trimSlashStart()?.startsWith(painteraDatasetNoLeadingSlash) ?: false }
					?.let { json ->
						LOG.warn("Converting deprecated label block lookup format {} into {}.", constants.N5_FILESYSTEM, constants.N5_FILESYSTEM_RELATIVE)
						if (backupAttributes) {
							val painteraDatasetAttributes = uri
								.resolve(painteraDataset)
								.resolve("attributes.json")
								.toPath().toAbsolutePath().toFile()
							val lookupAttributes = uri
								.resolve(painteraDataset)
								.resolve(constants.LABEL_TO_BLOCK_MAPPING)
								.resolve("attributes.json")
								.toPath().toAbsolutePath().toFile()
							val suffix = dateFormat.format(date)

							painteraDatasetAttributes.copyTo(File("${painteraDatasetAttributes.absolutePath}$suffix"), overwrite)
							lookupAttributes.copyTo(File("${lookupAttributes.absolutePath}$suffix"), overwrite)
						}
						val oldPattern = json.getStringProperty(constants.SCALE_DATASET_PATTERN)!!.trimSlashStart()
						val newPattern = oldPattern.replaceFirst(painteraDatasetNoLeadingSlash, "")
						val newLookupJson = JsonObject().apply {
							addProperty(constants.TYPE, constants.N5_FILESYSTEM_RELATIVE)
							addProperty(constants.SCALE_DATASET_PATTERN, newPattern)
						}
						setAttribute(painteraDataset, constants.LABEL_BLOCK_LOOKUP, newLookupJson)
						setAttribute("$painteraDataset/${constants.LABEL_TO_BLOCK_MAPPING}", constants.LABEL_BLOCK_LOOKUP, newLookupJson)
					}
			}

		}

		private fun String.trimSlashStart() = trimStart(*MakeLabelBlockLookupRelativeConstants.SLASH.toCharArray())

	}

	override fun createLabelBlockLookup(source: DataSource<D, T>): LabelBlockLookup {
		return (metadataState.writer as? N5KeyValueWriter)?.let { writer ->
			(writer.keyValueAccess as? FileSystemKeyValueAccess)?.let {
				writer.makeN5LabelBlockLookupRelative(dataset, backupLookupAttributesIfMakingRelative)
				N5Helpers.getLabelBlockLookup(metadataState).also {
					if (it is IsRelativeToContainer) {
						it.setRelativeTo(writer, dataset)
					}
				}
			}
		} ?: LabelBlockLookupNoBlocks()
	}

	private object SerializationKeys {
		const val DATASET = "dataset"
		const val FRAGMENT_SEGMENT_ASSIGNMENT = "fragmentSegmentAssignment"
	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendPainteraDataset<D, T>>
			where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

		override fun serialize(
			backend: N5BackendPainteraDataset<D, T>,
			typeOfSrc: Type,
			context: JsonSerializationContext,
		): JsonElement {
			val map = JsonObject()
			with(SerializationKeys) {
				backend.container.serializeTo(map)
				map.addProperty(DATASET, backend.dataset)
				map.add(FRAGMENT_SEGMENT_ASSIGNMENT, context[FragmentSegmentAssignmentActions(backend.fragmentSegmentAssignment)])
			}
			return map
		}

		override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
	}

	class Deserializer<D, T>(
		private val projectDirectory: Supplier<String>,
		private val propagationExecutorService: ExecutorService
	) : JsonDeserializer<N5BackendPainteraDataset<D, T>>
			where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {

		@Plugin(type = StatefulSerializer.DeserializerFactory::class)
		class Factory<D, T> : StatefulSerializer.DeserializerFactory<N5BackendPainteraDataset<D, T>, Deserializer<D, T>>
				where D : NativeType<D>, D : IntegerType<D>, T : net.imglib2.Volatile<D>, T : NativeType<T> {
			override fun createDeserializer(
				arguments: StatefulSerializer.Arguments,
				projectDirectory: Supplier<String>,
				dependencyFromIndex: IntFunction<SourceState<*, *>>
			): Deserializer<D, T> = Deserializer(
				projectDirectory,
				arguments.viewer.propagationQueue
			)

			override fun getTargetClass() = N5BackendPainteraDataset::class.java as Class<N5BackendPainteraDataset<D, T>>
		}

		override fun deserialize(
			json: JsonElement,
			typeOfT: Type,
			context: JsonDeserializationContext
		): N5BackendPainteraDataset<D, T> {
			return with(SerializationKeys) {
				with(GsonExtensions) {
					var container = N5Helpers.deserializeFrom(json.asJsonObject)
					val dataset: String = json[DATASET]!!
					var n5ContainerState = N5ContainerState(container)
					var metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset)
					while (metadataState == null) {
						container = N5Helpers.promptForNewLocationOrRemove(
							container.uri.toString(), N5DatasetDoesntExist(container.uri.toString(), dataset),
							"Dataset not found",
							"""
								Expected dataset "${dataset.ifEmpty { "/" }}" not found at
									${container.uri} 
							""".trimIndent()
						)
						n5ContainerState = N5ContainerState(container)
						metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset)
					}
					N5BackendPainteraDataset<D, T>(
						metadataState,
						propagationExecutorService,
						true
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
