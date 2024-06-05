package org.janelia.saalfeldlab.paintera.state.metadata

import bdv.cache.SharedQueue
import net.imglib2.Volatile
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.NativeType
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.NgffSingleScaleAxesMetadata
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState.Companion.isLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.isLabelMultiset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.offset
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.resolution
import org.janelia.saalfeldlab.util.n5.ImagesWithTransform
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup
import java.util.Optional

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


open class MultiScaleMetadataState (
	override val n5ContainerState: N5ContainerState,
	final override val metadata: SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>
) : MetadataState by SingleScaleMetadataState(n5ContainerState, metadata[0]) {

	private val highestResMetadata: N5SpatialDatasetMetadata = metadata[0]
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

class PainteraDataMultiscaleMetadataState (
	n5ContainerState: N5ContainerState,
	var painteraDataMultiscaleMetadata: N5PainteraDataMultiScaleGroup
) : MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata) {

	override var maxIntensity: Double = (painteraDataMultiscaleMetadata as? N5PainteraLabelMultiScaleGroup)?.maxId?.toDouble() ?: super.maxIntensity
	val dataMetadataState = MultiScaleMetadataState(n5ContainerState, painteraDataMultiscaleMetadata.dataGroupMetadata)

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

	companion object {
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
		fun metadataIsValid(metadata: N5Metadata?): Boolean {
			/* Valid if we are SpatialMultiscaleMetadata whose children are single scale, or we are SingleScale ourselves. */
			return (metadata as? SpatialMultiscaleMetadata<*>)?.let {
				it.childrenMetadata[0] is N5SpatialDatasetMetadata
			} ?: run {
				metadata is N5SpatialDatasetMetadata
			}
		}

		@JvmStatic
		fun createMetadataState(n5ContainerState: N5ContainerState, metadata: N5Metadata?): Optional<MetadataState> {
			@Suppress("UNCHECKED_CAST")
			return Optional.ofNullable(
				(metadata as? N5PainteraDataMultiScaleGroup)?.let { PainteraDataMultiscaleMetadataState(n5ContainerState, it) }
					?: (metadata as? SpatialMultiscaleMetadata<N5SpatialDatasetMetadata>)?.let { MultiScaleMetadataState(n5ContainerState, it) }
					?: (metadata as? N5SpatialDatasetMetadata)?.let { SingleScaleMetadataState(n5ContainerState, it) }
			)
		}

		@JvmStatic
		fun createMetadataState(n5containerAndDataset: String): Optional<MetadataState> {

			val reader = with(Paintera.n5Factory) {
				openWriterOrNull(n5containerAndDataset) ?: openReaderOrNull(n5containerAndDataset) ?: return Optional.empty()
			}

			val n5ContainerState = N5ContainerState(reader)
			return N5Helpers.parseMetadata(reader).map { treeNode ->
				if (treeNode.isDataset && metadataIsValid(treeNode.metadata) && treeNode.metadata is N5Metadata) {
					createMetadataState(n5ContainerState, treeNode.metadata).get()
				} else {
					null
				}
			}
		}

		@JvmStatic
		fun createMetadataState(n5container: String, dataset: String?): Optional<MetadataState> {
			val reader = with(Paintera.n5Factory) {
				openWriterOrNull(n5container) ?: openReaderOrNull(n5container) ?: return Optional.empty()
			}

			val n5ContainerState = N5ContainerState(reader)
			val metadataRoot = N5Helpers.parseMetadata(reader)
			if (metadataRoot.isEmpty) return Optional.empty()
			val metadataState = N5TreeNode.flattenN5Tree(metadataRoot.get())
				.filter { node: N5TreeNode -> (node.path == dataset || node.nodeName == dataset) && metadataIsValid(node.metadata) }
				.findFirst()
				.map { obj: N5TreeNode -> obj.metadata }
				.map { md: N5Metadata -> createMetadataState(n5ContainerState, md) }
				.get()

			return metadataState
		}

		@JvmStatic
		fun createMetadataState(n5ContainerState: N5ContainerState, dataset: String?): Optional<MetadataState> {
			return N5Helpers.parseMetadata(n5ContainerState.reader)
				.flatMap { tree: N5TreeNode? ->
					N5TreeNode.flattenN5Tree(tree).filter { node: N5TreeNode -> node.path == dataset || node.path == "/$dataset" }.findFirst()
				}
				.filter { node: N5TreeNode -> metadataIsValid(node.metadata) }
				.map { obj: N5TreeNode -> obj.metadata }
				.flatMap { md: N5Metadata? -> createMetadataState(n5ContainerState, md) }
		}

		@JvmStatic
		fun createMetadataState(reader: N5Reader, dataset: String): MetadataState {
			val n5ContainerState = N5ContainerState(reader)
			val createMetadataState = createMetadataState(n5ContainerState, dataset)
			return createMetadataState.nullable!!
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
