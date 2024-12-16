package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.cache.SharedQueue
import io.github.oshai.kotlinlogging.KotlinLogging
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.channelAxis
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.isLabelMultiset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.spatialAxes
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.timeAxis
import org.janelia.saalfeldlab.util.n5.*
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup
import java.util.stream.Stream
import kotlin.streams.asSequence

interface MetadataState {

	val n5ContainerState: N5ContainerState

	val metadata: N5Metadata
	var datasetAttributes: DatasetAttributes
	var transform: AffineTransform3D
	var isLabel: Boolean
	var isLabelMultiset: Boolean
	var minIntensity: Double
	var maxIntensity: Double
	var resolution: DoubleArray
	var translation: DoubleArray
	var spatialAxes: Map<Axis, Int>
	var channelAxis: Pair<Axis, Int>?
	var timeAxis: Pair<Axis, Int>?
	var virtualCrop: Interval?
	var unit: String
	var reader: N5Reader

	val writer: N5Writer?
	var group: String
	val dataset: String
		get() = group

	fun updateTransform(newTransform: AffineTransform3D)
	fun updateTransform(resolution: DoubleArray, offset: DoubleArray)

	fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>>

	fun copy(): MetadataState

	companion object {
		fun isLabel(dataType: DataType): Boolean {
			return when (dataType) {
				DataType.UINT64 -> true
				DataType.UINT32 -> true
				DataType.INT16 -> true
				else -> false
			}
		}

		@JvmStatic
		fun <T : MetadataState> setBy(source: T, target: T) {
			target.transform.set(source.transform)
			target.isLabelMultiset = source.isLabelMultiset
			target.isLabel = source.isLabel
			target.datasetAttributes = source.datasetAttributes
			target.minIntensity = source.minIntensity
			target.maxIntensity = source.maxIntensity
			target.resolution = source.resolution.copyOf()
			target.translation = source.translation.copyOf()
			target.virtualCrop = source.virtualCrop?.let { FinalInterval(it.minAsLongArray(), it.maxAsLongArray()) }
			target.unit = source.unit
			target.group = source.group
		}
	}
}

open class SingleScaleMetadataState(
	final override var n5ContainerState: N5ContainerState,
	final override val metadata: N5SpatialDatasetMetadata
) : MetadataState {

	final override var transform: AffineTransform3D = metadata.spatialTransform3d()
	override var isLabelMultiset: Boolean = metadata.isLabelMultiset
	override var isLabel: Boolean = isLabel(metadata.attributes.dataType) || metadata.isLabelMultiset
	override var datasetAttributes: DatasetAttributes = metadata.attributes
	override var minIntensity = metadata.minIntensity()
	override var maxIntensity = metadata.maxIntensity()
	override var resolution = metadata.resolution
	override var translation = metadata.offset
	override var spatialAxes = metadata.spatialAxes
	override var channelAxis: Pair<Axis, Int>? = metadata.channelAxis
	override var timeAxis: Pair<Axis, Int>? = metadata.timeAxis
	override var virtualCrop: Interval? = null
	override var unit: String = metadata.unit()
	override var reader = n5ContainerState.reader
	override val writer: N5Writer?
		get() = n5ContainerState.writer

	override var group = metadata.path!!

	override fun copy(): SingleScaleMetadataState {
		return SingleScaleMetadataState(n5ContainerState, metadata).also {
			MetadataState.setBy(this, it)
		}
	}

	override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
		val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
		updateTransform(newTransform)
	}

	override fun updateTransform(newTransform: AffineTransform3D) {

		val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
		transform.preConcatenate(deltaTransform)
		this@SingleScaleMetadataState.resolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
		this@SingleScaleMetadataState.translation = transform.translation
	}

	override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
		return arrayOf(
			if (isLabelMultiset) {
				N5Data.openLabelMultiset(this, queue, priority)
			} else {
				N5Data.openRaw(this, queue, priority)
			}
		) as Array<ImagesWithTransform<D, T>>
	}
}


open class MultiScaleMetadataState(
	override val n5ContainerState: N5ContainerState,
	final override val metadata: SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>
) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {

	val highestResMetadata: N5SpatialDatasetMetadata = metadata[0]
	final override var transform: AffineTransform3D = metadata.spatialTransform3d()
	final override var isLabelMultiset: Boolean = metadata[0].isLabelMultiset
	override var isLabel: Boolean = when {
		metadata is N5PainteraLabelMultiScaleGroup -> metadata.isLabel
		else -> isLabel(highestResMetadata.attributes.dataType) || isLabelMultiset
	}
	override var resolution: DoubleArray = transform.run { doubleArrayOf(get(0, 0), get(1, 1), get(2, 2)) }
	override var translation: DoubleArray = transform.translation
	override var group: String = metadata.path
	override val dataset: String = metadata.path
	val scaleTransforms: Array<AffineTransform3D> = metadata.spatialTransforms3d()

	override fun copy(): MultiScaleMetadataState {
		return MultiScaleMetadataState(n5ContainerState, metadata).also {
			MetadataState.setBy(this, it)
			scaleTransforms.forEachIndexed { idx, transform ->
				transform.set(it.scaleTransforms[idx])
			}
		}
	}

	override fun updateTransform(resolution: DoubleArray, offset: DoubleArray) {
		val newTransform = MetadataUtils.transformFromResolutionOffset(resolution, offset)
		updateTransform(newTransform)
	}

	override fun updateTransform(newTransform: AffineTransform3D) {
		val deltaTransform = newTransform.copy().concatenate(transform.inverse().copy())
		if (deltaTransform.isIdentity) return

		transform.concatenate(deltaTransform)
		this@MultiScaleMetadataState.resolution = doubleArrayOf(transform.get(0, 0), transform.get(1, 1), transform.get(2, 2))
		this@MultiScaleMetadataState.translation = transform.translation

		scaleTransforms.forEach { it.concatenate(deltaTransform) }

	}


	override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
		return if (isLabelMultiset) {
			N5Data.openLabelMultisetMultiscale(this, queue, priority)
		} else {
			N5Data.openRawMultiscale(this, queue, priority)
		} as Array<ImagesWithTransform<D, T>>
	}
}

class PainteraDataMultiscaleMetadataState(
	n5ContainerState: N5ContainerState,
	var painteraDataMultiscaleMetadata: N5PainteraDataMultiScaleGroup
) : MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata) {

	override var maxIntensity: Double = (painteraDataMultiscaleMetadata as? N5PainteraLabelMultiScaleGroup)?.maxId?.toDouble() ?: super.maxIntensity
	val dataMetadataState = MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata.dataGroupMetadata)

	override var virtualCrop: Interval? = null
		get() = field
		set(value) {
			dataMetadataState.virtualCrop = value
			field = value
		}

	override fun <D : NativeType<D>, T : Volatile<D>> getData(queue: SharedQueue, priority: Int): Array<ImagesWithTransform<D, T>> {
		return if (isLabelMultiset) {
			N5Data.openLabelMultisetMultiscale(dataMetadataState, queue, priority)
		} else {
			N5Data.openRawMultiscale(dataMetadataState, queue, priority)
		} as Array<ImagesWithTransform<D, T>>
	}

	override fun copy(): PainteraDataMultiscaleMetadataState {
		return PainteraDataMultiscaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata).also {
			MetadataState.setBy(this, it)
		}
	}
}

operator fun <T> SpatialMultiscaleMetadata<T>.get(index: Int): T where T : N5SpatialDatasetMetadata {
	return childrenMetadata[index]
}

class MetadataUtils {

	enum class SpatialAxes(val label: String) {
		X("x"),
		Y("y"),
		Z("z");

		companion object {
			val labels = SpatialAxes.entries.map { it.label }
			val default = SpatialAxes.entries.associate { Axis(Axis.SPACE, it.name, null) to it.ordinal }
		}
	}

	companion object {

		private val LOG = KotlinLogging.logger {}

		val N5SpatialDatasetMetadata.isLabelMultiset
			get() = when (this) {
				is N5SingleScaleMetadata -> isLabelMultiset
				else -> false
			}

		val N5SpatialDatasetMetadata.resolution: DoubleArray
			get() = when (this) {
				is N5SingleScaleMetadata -> pixelResolution!!
				is NgffSingleScaleAxesMetadata -> scale
				else -> DoubleArray(this.spatialTransform().numDimensions()) { 1.0 }
			}

		val N5SpatialDatasetMetadata.offset
			get() = when (this) {
				is N5SingleScaleMetadata -> offset!!
				is NgffSingleScaleAxesMetadata -> translation!!
				else -> DoubleArray(this.spatialTransform().numDimensions()) { 0.0 }
			}

		@JvmStatic
		fun MetadataState.getAxes(): Array<Axis>? {
			val axesMetadata = when (this) {
				is SingleScaleMetadataState -> metadata
				is MultiScaleMetadataState -> highestResMetadata
				else -> null
			}

			return when (axesMetadata) {
				is NgffSingleScaleAxesMetadata -> axesMetadata.axes
				else -> null
			}
		}

		val N5SpatialDatasetMetadata.spatialAxes: Map<Axis, Int>
			get() = when (this) {
				is NgffSingleScaleAxesMetadata -> {
					axes.mapIndexed { idx, axis -> axis to idx }
						.filter { (axis, _) -> axis.type == Axis.SPACE && axis.name in SpatialAxes.labels }
						.associate { (axis, idx) -> axis to idx }
				}

				else -> mapOf(
					Axis("spatial", "x", unit()) to 0,
					Axis("spatial", "y", unit()) to 1,
					Axis("spatial", "z", unit()) to 2
				)
			}

		val N5SpatialDatasetMetadata.channelAxis: Pair<Axis, Int>?
			get() = when (this) {
				is NgffSingleScaleAxesMetadata -> {
					axes.mapIndexed { idx, axis -> axis to idx }
						.firstOrNull() { (axis, _) -> axis.type == Axis.CHANNEL }
				}

				else -> null
			}

		val N5SpatialDatasetMetadata.timeAxis: Pair<Axis, Int>?
			get() = when (this) {
				is NgffSingleScaleAxesMetadata -> {
					axes.mapIndexed { idx, axis -> axis to idx }
						.firstOrNull() { (axis, _) -> axis.type == Axis.TIME }
				}

				else -> null
			}

		/**
		 * If the MetadataState has [N5PainteraLabelMultiScaleGroup], create a [FragmentSegmentAssignmentOnlyLocal]
		 * used for fragment-segment lookups and persisting.
		 *
		 * If the group does not exist, but is otherwise valid, this will create it.
		 * If the N5Container is read-only, the existing LUT will be used if found, but no
		 *  new persisting can occur.
		 */
		@JvmStatic
		val MetadataState.fragmentSegmentAssignmentState: FragmentSegmentAssignmentOnlyLocal
			get() {

				val (lut, persist) = (metadata as? N5PainteraLabelMultiScaleGroup)?.let { painteraLabels ->
					val lut = painteraLabels.fragmentSegmentAssignmentMetadata?.let { initialLutMetadata ->
						if (reader.exists(initialLutMetadata.path))
							N5FragmentSegmentAssignmentInitialLut(reader, initialLutMetadata.path)
						else null
					} ?: NO_INITIAL_LUT_AVAILABLE
					val persist = runCatching {
						N5FragmentSegmentAssignmentPersister(writer, "${painteraLabels.path}/${N5Helpers.PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET}")
					}.getOrElse {
						LOG.error(it) {}
						FragmentSegmentAssignmentOnlyLocal.doesNotPersist("Cannot Persist: $it")
					}
					lut to persist
				} ?: let {
					val reason = "Persisting assignments not supported for non Paintera group/dataset $group"
					NO_INITIAL_LUT_AVAILABLE to FragmentSegmentAssignmentOnlyLocal.doesNotPersist(reason)
				}

				return FragmentSegmentAssignmentOnlyLocal(lut, persist)

			}


		/**
		 * Checks if the given metadata is valid. The metadata is considered valid if it is either
		 * a SpatialMultiscaleMetadata with children of type N5SpatialDatasetMetadata, or it is
		 * a single scale N5SpatialDatasetMetadata.
		 *
		 * @param metadata The metadata to validate.
		 * @return True if the metadata is valid, false otherwise.
		 */
		@JvmStatic
		fun metadataIsValid(metadata: N5Metadata?): Boolean {
			return (metadata as? SpatialMultiscaleMetadata<*>)?.let {
				it.childrenMetadata[0] is N5SpatialDatasetMetadata
			} ?: run {
				metadata is N5SpatialDatasetMetadata
			}
		}

		@JvmStatic
		fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): MetadataState? {
			@Suppress("UNCHECKED_CAST")
			return when {
				metadata is N5PainteraDataMultiScaleGroup -> PainteraDataMultiscaleMetadataState(n5ContainerState, metadata)
				(metadata as? SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>) != null -> MultiScaleMetadataState(n5ContainerState, metadata)
				metadata is N5SpatialDatasetMetadata -> SingleScaleMetadataState(n5ContainerState, metadata)
				else -> null
			}
		}

		@JvmStatic
		fun createMetadataState(n5containerAndDataset: String): MetadataState? {

			val reader = with(Paintera.n5Factory) {
				openWriterOrNull(n5containerAndDataset) ?: openReaderOrNull(n5containerAndDataset) ?: return null
			}

			val n5ContainerState = N5ContainerState(reader)
			return discoverAndParseRecursive(reader, n5containerAndDataset).run {
				if (isDataset && metadataIsValid(metadata))
					createMetadataState(n5ContainerState, metadata)
				else null
			}
		}

		@JvmStatic
		fun createMetadataState(n5container: String, dataset: String = ""): MetadataState? {
			val n5 = Paintera.n5Factory.openWriterOrReaderOrNull(n5container) ?: return null
			return createMetadataState(n5, dataset)
		}

		@JvmStatic
		fun createMetadataState(reader: N5Reader, dataset: String): MetadataState? {
			return createMetadataState(N5ContainerState(reader), dataset)!!
		}

		@JvmStatic
		fun createMetadataState(n5ContainerState: N5ContainerState, dataset: String): MetadataState? {
			val metadataRoot = discoverAndParseRecursive(n5ContainerState.reader)

			return N5TreeNode.flattenN5Tree(metadataRoot)
				.asSequence()
				.filter { node: N5TreeNode ->
					println("\t\tdataset:$dataset path:${node.path} name:${node.nodeName} ${metadataIsValid(node.metadata)}")
					(node.path == dataset || node.nodeName == dataset) && metadataIsValid(node.metadata)
				}
				.map { obj: N5TreeNode -> obj.metadata }
				.map { md: N5Metadata -> createMetadataState(n5ContainerState, md) ?: let{
					println("null metadatastate: $md")
					null
				}}
				.firstOrNull()
		}

		fun transformFromResolutionOffset(resolution: DoubleArray, offset: DoubleArray): AffineTransform3D {
			val newTransform = AffineTransform3D()
			newTransform.set(
				resolution[0], 0.0, 0.0, offset[0],
				0.0, resolution[1], 0.0, offset[1],
				0.0, 0.0, resolution[2], offset[2]
			)
			return newTransform
		}
	}
}
