package org.janelia.saalfeldlab.util.n5

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.IntegerProperty
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.imglib2.Interval
import net.imglib2.img.cell.CellGrid
import net.imglib2.iterator.IntervalIterator
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.ScaleAndTranslation
import net.imglib2.realtransform.Translation3D
import net.imglib2.util.Intervals
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.n5.IsRelativeToContainer
import org.janelia.saalfeldlab.labels.blocks.n5.LabelBlockLookupFromN5Relative
import org.janelia.saalfeldlab.n5.*
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.*
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadataParser
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.N5IdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.fragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.metadataIsValid
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.raw.n5.SerializationKeys
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.n5.metadata.LabelBlockLookupGroup
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleMetadata.PainteraDataMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup.PainteraLabelMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraRawMultiScaleGroup.PainteraRawMultiScaleParser
import org.janelia.saalfeldlab.util.n5.universe.N5ContainerDoesntExist
import java.io.IOException
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.LongSupplier
import java.util.function.Supplier

object N5Helpers {
	const val MULTI_SCALE_KEY = "multiScale"
	const val IS_LABEL_MULTISET_KEY = "isLabelMultiset"
	const val MAX_ID_KEY = "maxId"
	const val RESOLUTION_KEY = "resolution"
	const val OFFSET_KEY = "offset"
	const val UNIT_KEY = "unit"
	const val DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors"
	const val LABEL_MULTISETTYPE_KEY = "isLabelMultiset"
	const val MAX_NUM_ENTRIES_KEY = "maxNumEntries"
	const val PAINTERA_DATA_KEY = "painteraData"
	const val PAINTERA_DATA_DATASET = "data"
	const val PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET = "fragment-segment-assignment"
	const val LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping"
	private val LOG = KotlinLogging.logger { }

	val GROUP_PARSERS = listOf<N5MetadataParser<*>>(
		OmeNgffMetadataParser(),
		PainteraRawMultiScaleParser(),
		PainteraLabelMultiScaleParser(),
		PainteraDataMultiScaleParser(),
		N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
		N5MultiScaleMetadata.MultiScaleParser()
	)
	val METADATA_PARSERS = listOf<N5MetadataParser<*>>(
		N5CosemMetadataParser(),
		N5SingleScaleMetadataParser(),
		N5GenericSingleScaleMetadataParser()
	)
	private val N5_METADATA_CACHE = HashMap<String, N5TreeNode?>()

	/**
	 * Check if a group is a paintera data set:
	 *
	 * @param n5    [N5Reader] container
	 * @param group to be tested for paintera dataset
	 * @return `true` if `group` exists and has attribute `painteraData`.
	 * @throws IOException if any N5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun isPainteraDataset(n5: N5Reader, group: String?): Boolean {
		val isPainteraDataset = n5.exists(group) && n5.listAttributes(group)?.containsKey(PAINTERA_DATA_KEY) == true
		LOG.debug { "Is $n5/$group Paintera dataset? $isPainteraDataset" }
		return isPainteraDataset
	}

	/**
	 * Determine if a group is multiscale.
	 *
	 * @param n5    [N5Reader] container
	 * @param group to be tested for multiscale
	 * @return `true` if `group` exists and is not a dataset and has attribute "multiScale": true,
	 * or (legacy) all children are groups following the regex pattern `"$s[0-9]+^"`, `false` otherwise.
	 * @throws IOException if any N5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun isMultiScale(n5: N5Reader, group: String): Boolean {
		if (!n5.exists(group) || n5.datasetExists(group)) return false

		/* based on attribute */
		var isMultiScale = n5.getAttribute(group, MULTI_SCALE_KEY, Boolean::class.java) ?: false

		/*
		 * based on groupd content (the old way)
		 * TODO consider removing as multi-scale declaration by attribute becomes part of the N5 spec.
		 */if (!isMultiScale && !n5.datasetExists(group)) {
			val subGroups = n5.list(group)
			isMultiScale = subGroups.isNotEmpty() && areAllSubGroupsValid(n5, group, subGroups)

			if (isMultiScale) {
				LOG.debug { "Found multi-scale group without $MULTI_SCALE_KEY tag. Implicit multi-scale detection will be removed in the future. Please add \"$MULTI_SCALE_KEY\":true to attributes.json." }
			}
		}
		return isMultiScale
	}

	private fun areAllSubGroupsValid(n5: N5Reader, group: String, subGroups: Array<String>): Boolean {
		return subGroups.all { subGroup ->
			subGroup.matches("^s[0-9]+$".toRegex()) && n5.datasetExists("$group/$subGroup")
		}
	}

	/**
	 * List all scale datasets within `group`
	 *
	 * @param n5    [N5Reader] container
	 * @param group contains scale directories
	 * @return array of all contained scale datasets, relative to `group`,
	 * e.g. for a structure `"group/{s0,s1}"` this would return `{"s0", "s1"}`.
	 * @throws IOException if any N5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun listScaleDatasets(n5: N5Reader, group: String): Array<String> {
		val scaleDirs = n5.list(group)
			.filter { it.matches("^s\\d+$".toRegex()) }
			.filter { scaleDir -> n5.datasetExists("$group/$scaleDir") }
			.toTypedArray()
		LOG.debug { "Found these scale dirs: ${scaleDirs.contentToString()}" }
		return scaleDirs
	}

	/**
	 * List and sort all scale datasets within `group`
	 *
	 * @param n5    [N5Reader] container
	 * @param group contains scale directories
	 * @return sorted list of all contained scale datasets, relative to `group`,
	 * e.g. for a structure `"group/{s0,s1}"` this would return `{"s0", "s1"}`.
	 * @throws IOException if any N5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun listAndSortScaleDatasets(n5: N5Reader, group: String): Array<String> {
		val scaleDirs = listScaleDatasets(n5, group)
		sortScaleDatasets(scaleDirs)
		LOG.debug { "Sorted scale dirs: ${scaleDirs.contentToString()}" }
		return scaleDirs
	}

	/**
	 * @param n5    [N5Reader] container
	 * @param group multi-scale group, dataset, or paintera dataset
	 * @return [DatasetAttributes] found in appropriate `attributes.json`
	 * @throws IOException if any N5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getDatasetAttributes(n5: N5Reader, group: String): DatasetAttributes? {
		LOG.debug { "Getting data type for group/dataset $group" }
		if (isPainteraDataset(n5, group)) {
			return getDatasetAttributes(n5, "$group/$PAINTERA_DATA_DATASET")
		}
		return if (isMultiScale(n5, group)) {
			getDatasetAttributes(n5, java.lang.String.join("/", getFinestLevelJoinWithGroup(n5, group)))
		} else n5.getDatasetAttributes(group)
	}

	/**
	 * Sort scale datasets numerically by removing all non-number characters during comparison.
	 *
	 * @param scaleDatasets list of scale datasets
	 */
	private fun sortScaleDatasets(scaleDatasets: Array<String>) {
		scaleDatasets.sortBy { s -> s.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }
	}

	@JvmStatic
	@JvmOverloads
	fun parseMetadata(n5: N5Reader, ignoreCache: Boolean = false): Optional<N5TreeNode> {
		//TODO Caleb: use [OpenSourceState.ParserContainerCache] here
		val uri = n5.uri.toString()
		if (!ignoreCache && N5_METADATA_CACHE.containsKey(uri)) {
			return Optional.ofNullable(N5_METADATA_CACHE[uri])
		}
		val n5TreeNode = discoverAndParseRecursive(n5)
		N5_METADATA_CACHE[uri] = n5TreeNode
		return Optional.ofNullable(n5TreeNode)
	}

	/**
	 * Adjust [AffineTransform3D] by scaling and translating appropriately.
	 *
	 * @param transform                  to be adjusted wrt to downsampling factors
	 * @param downsamplingFactors        at target level
	 * @param initialDownsamplingFactors at source level
	 * @return adjusted [AffineTransform3D]
	 */
	@JvmStatic
	fun considerDownsampling(
		transform: AffineTransform3D,
		downsamplingFactors: DoubleArray,
		initialDownsamplingFactors: DoubleArray
	): AffineTransform3D {
		val shift = DoubleArray(downsamplingFactors.size)
		for (d in downsamplingFactors.indices) {
			transform[transform[d, d] * downsamplingFactors[d] / initialDownsamplingFactors[d], d] = d
			shift[d] = 0.5 / initialDownsamplingFactors[d] - 0.5 / downsamplingFactors[d]
		}
		return transform.concatenate(Translation3D(*shift))
	}

	@Deprecated(
		"use MetadataUtils fragmentSegmentAssignmentState extension",
		ReplaceWith("MetadataUtils.createMetadataState(writer, group)?.fragmentSegmentAssignmentState!!")
	)
	fun assignments(writer: N5Writer, group: String) =
		MetadataUtils.createMetadataState(writer, group)?.fragmentSegmentAssignmentState!!

	/**
	 * Get id-service for n5 `container` and `dataset`.
	 * Requires write access on the attributes of `dataset` and attribute `"maxId": <maxId>` in `dataset`.
	 *
	 * @param n5            container
	 * @param dataset       dataset
	 * @param maxIdFallback Use this if maxId attribute is not specified in `dataset`.
	 * @return [N5IdService]
	 * @throws IOException If no attribute `"maxId": <maxId>` in `dataset` or any n5 operation throws.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun idService(n5: N5Writer, dataset: String?, maxIdFallback: Long): IdService {
		return idService(n5, dataset, LongSupplier { maxIdFallback })
	}

	/**
	 * Get id-service for n5 `container` and `dataset`.
	 * Requires write access on the attributes of `dataset` and attribute `"maxId": <maxId>` in `dataset`.
	 *
	 * @param n5            container
	 * @param dataset       dataset
	 * @param maxIdFallback Use this if maxId attribute is not specified in `dataset`.
	 * @return [N5IdService]
	 * @throws IOException If no attribute `"maxId": <maxId>` in `dataset` or any n5 operation throws.
	 */
	@Throws(IOException::class)
	fun idService(n5: N5Writer, dataset: String?, maxIdFallback: LongSupplier): IdService {
		return try {
			idService(n5, dataset)
		} catch (e: MaxIDNotSpecified) {
			n5.setAttribute(dataset, "maxId", maxIdFallback.asLong)
			try {
				idService(n5, dataset)
			} catch (e2: MaxIDNotSpecified) {
				throw IOException(e2)
			}
		}
	}

	/**
	 * Get id-service for n5 `container` and `dataset`.
	 * Requires write access on the attributes of `dataset` and attribute `"maxId": <maxId>` in `dataset`.
	 *
	 * @param n5       container
	 * @param dataset  dataset
	 * @param fallback Use this if maxId attribute is not specified in `dataset`.
	 * @return [N5IdService]
	 * @throws IOException If no attribute `"maxId": <maxId>` in `dataset` or any n5 operation throws.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun idService(n5: N5Writer, dataset: String?, fallback: Supplier<IdService>): IdService {
		return try {
			idService(n5, dataset)
		} catch (e: MaxIDNotSpecified) {
			fallback.get()
		}
	}

	/**
	 * Get id-service for n5 `container` and `dataset`.
	 * Requires write access on the attributes of `dataset` and attribute `"maxId": <maxId>` in `dataset`.
	 *
	 * @param n5      container
	 * @param dataset dataset
	 * @return [N5IdService]
	 * @throws IOException If no attribute `"maxId": <maxId>` in `dataset` or any n5 operation throws.
	 */
	@JvmStatic
	@Throws(MaxIDNotSpecified::class, IOException::class)
	fun idService(n5: N5Reader, dataset: String?): IdService {
		LOG.debug { "Requesting id service for $n5:$dataset" }
		val maxId = n5.getAttribute(dataset, "maxId", Long::class.java)
		LOG.debug { "Found maxId=$maxId" }
		return when {
			maxId == null && n5 is N5Writer -> throw MaxIDNotSpecified("Required attribute `maxId` not specified for dataset `$dataset` in container `$n5`.")
			maxId == null -> N5IdService(n5, dataset, 1)
			else -> N5IdService(n5, dataset, maxId)
		}
	}

	/**
	 * @param n5    container
	 * @param group scale group
	 * @return `"s0"` if `"s0"` is the finest scale level
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getFinestLevel(
		n5: N5Reader,
		group: String
	): String {
		LOG.debug { "Getting finest level for dataset $group" }
		val scaleDirs = listAndSortScaleDatasets(n5, group)
		return scaleDirs[0]
	}

	/**
	 * @param n5    container
	 * @param group scale group
	 * @return `String.format("%s/%s", group, "s0")` if `"s0"` is the finest scale level
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getFinestLevelJoinWithGroup(
		n5: N5Reader,
		group: String
	): String {
		return getFinestLevelJoinWithGroup(n5, group) { g: String?, d: String? -> String.format("%s/%s", g, d) }
	}

	/**
	 * @param n5     container
	 * @param group  scale group
	 * @param joiner join group and finest scale level
	 * @return `joiner.apply(group, "s0")` if `"s0"` is the finest scale level
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getFinestLevelJoinWithGroup(
		n5: N5Reader,
		group: String,
		joiner: BiFunction<String?, String?, String>
	): String {
		return joiner.apply(group, getFinestLevel(n5, group))
	}

	/**
	 * @param n5    container
	 * @param group scale group
	 * @return `"sN"` if `"sN" is the coarsest scale level`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getCoarsestLevel(
		n5: N5Reader,
		group: String
	): String {
		val scaleDirs = listAndSortScaleDatasets(n5, group)
		return scaleDirs[scaleDirs.size - 1]
	}

	/**
	 * @param n5    container
	 * @param group scale group
	 * @return `String.format("%s/%s", group, "sN")` if `"sN"` is the coarsest scale level
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getCoarsestLevelJoinWithGroup(
		n5: N5Reader,
		group: String
	): String {
		return getCoarsestLevelJoinWithGroup(n5, group) { g: String?, d: String? -> String.format("%s/%s", g, d) }
	}

	/**
	 * @param n5     container
	 * @param group  scale group
	 * @param joiner join group and finest scale level
	 * @return `joiner.apply(group, "s0")` if `"s0"` is the coarsest scale level
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getCoarsestLevelJoinWithGroup(
		n5: N5Reader,
		group: String,
		joiner: BiFunction<String?, String?, String>
	): String {
		return joiner.apply(group, getCoarsestLevel(n5, group))
	}

	/**
	 * @param n5       container
	 * @param group    group
	 * @param key      key for array attribute
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at `key` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getDoubleArrayAttribute(
		n5: N5Reader,
		group: String,
		key: String?,
		vararg fallBack: Double
	): DoubleArray {
		return getDoubleArrayAttribute(n5, group, key, false, *fallBack)
	}

	/**
	 * @param n5       container
	 * @param group    group
	 * @param key      key for array attribute
	 * @param reverse  set to `true` to reverse order of array entries
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at `key` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getDoubleArrayAttribute(
		n5: N5Reader,
		group: String,
		key: String?,
		reverse: Boolean,
		vararg fallBack: Double
	): DoubleArray {
		if (reverse) {
			val toReverse = getDoubleArrayAttribute(n5, group, key, false, *fallBack)
			LOG.debug { "Will reverse $toReverse" }
			var i = 0
			var k = toReverse.size - 1
			while (i < toReverse.size / 2) {
				val tmp = toReverse[i]
				toReverse[i] = toReverse[k]
				toReverse[k] = tmp
				++i
				--k
			}
			LOG.debug { "Reversed $toReverse" }
			return toReverse
		}
		return if (isPainteraDataset(n5, group)) {
			getDoubleArrayAttribute(n5, "$group/$PAINTERA_DATA_DATASET", key, false, *fallBack)
		} else try {
			n5.getAttribute(group, key, DoubleArray::class.java) ?: fallBack
		} catch (e: ClassCastException) {
			LOG.debug(e) { "Caught exception when trying to read double[] attribute. Will try to read as long[] attribute instead." }
			n5.getAttribute(group, key, LongArray::class.java)?.map { it.toDouble() }?.toDoubleArray() ?: fallBack
		}
	}

	/**
	 * @param n5    container
	 * @param group group
	 * @return value of attribute at `"resolution"` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getResolution(n5: N5Reader, group: String): DoubleArray {
		return getResolution(n5, group, false)
	}

	/**
	 * @param n5      container
	 * @param group   group
	 * @param reverse set to `true` to reverse order of array entries
	 * @return value of attribute at `"resolution"` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getResolution(n5: N5Reader, group: String, reverse: Boolean): DoubleArray {
		return getDoubleArrayAttribute(n5, group, RESOLUTION_KEY, reverse, 1.0, 1.0, 1.0)
	}

	/**
	 * @param n5    container
	 * @param group group
	 * @return value of attribute at `"offset"` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getOffset(n5: N5Reader, group: String): DoubleArray {
		return getOffset(n5, group, false)
	}

	/**
	 * @param n5      container
	 * @param group   group
	 * @param reverse set to `true` to reverse order of array entries
	 * @return value of attribute at `"offset"` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getOffset(n5: N5Reader, group: String, reverse: Boolean): DoubleArray {
		return getDoubleArrayAttribute(n5, group, OFFSET_KEY, reverse, 0.0, 0.0, 0.0)
	}

	/**
	 * @param n5    container
	 * @param group group
	 * @return value of attribute at `"downsamplingFactors"` as `double[]`
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getDownsamplingFactors(n5: N5Reader, group: String): DoubleArray {
		return getDoubleArrayAttribute(n5, group, DOWNSAMPLING_FACTORS_KEY, 1.0, 1.0, 1.0)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun getBooleanAttribute(n5: N5Reader?, group: String?, attribute: String?, fallback: Boolean): Boolean {
		return getAttribute(n5!!, group, attribute, Boolean::class.java, fallback)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun getIntegerAttribute(n5: N5Reader?, group: String?, attribute: String?, fallback: Int): Int {
		return getAttribute(n5!!, group, attribute, Int::class.java, fallback)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun <T> getAttribute(n5: N5Reader, group: String?, attribute: String?, clazz: Class<T>?, fallback: T): T {
		return getAttribute(n5, group, attribute, clazz, Supplier { fallback })
	}

	@JvmStatic
	@Throws(IOException::class)
	fun <T> getAttribute(n5: N5Reader, group: String?, attribute: String?, clazz: Class<T>?, fallback: Supplier<T>): T {
		val `val` = n5.getAttribute(group, attribute, clazz)
		return `val` ?: fallback.get()
	}

	/**
	 * @param resolution voxel-size
	 * @param offset     in real-world coordinates
	 * @return [AffineTransform3D] with `resolution` on diagonal and `offset` on 4th column.
	 */
	@JvmStatic
	fun fromResolutionAndOffset(resolution: DoubleArray?, offset: DoubleArray?): AffineTransform3D {
		return AffineTransform3D().concatenate(ScaleAndTranslation(resolution, offset))
	}

	/**
	 * @param n5    container
	 * @param group group
	 * @return [AffineTransform3D] that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getTransform(n5: N5Reader, group: String): AffineTransform3D {
		return getTransform(n5, group, false)
	}

	/**
	 * @param n5                       container
	 * @param group                    group
	 * @param reverseSpatialAttributes reverse offset and resolution attributes if `true`
	 * @return [AffineTransform3D] that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun getTransform(n5: N5Reader, group: String, reverseSpatialAttributes: Boolean): AffineTransform3D {
		return fromResolutionAndOffset(getResolution(n5, group, reverseSpatialAttributes), getOffset(n5, group, reverseSpatialAttributes))
	}

	/**
	 * @param metadataState object containing the metadata information and context for the dataset we are interrogating.
	 * @return unsupported lookup if `is not a paintera dataset`, [LabelBlockLookup] otherwise.
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class, NotAPainteraDataset::class)
	fun getLabelBlockLookup(metadataState: MetadataState): LabelBlockLookup {
		val group = metadataState.group
		val reader = metadataState.reader
		LOG.debug { "Getting label block lookup for ${metadataState.metadata.path}" }
		return if (isPainteraDataset(reader, group)) {
			val labelBlockLookupJson: LabelBlockLookup? = reader[group, "labelBlockLookup"]
			LOG.debug { "Got label block lookup json: $labelBlockLookupJson" }
			val lookup = labelBlockLookupJson ?: let {
				val lblGroup = "label-to-block-mapping"
				val scaleDatasetPattern = N5URI.normalizeGroupPath("$lblGroup/s%d")
				val relativeLookup = LabelBlockLookupFromN5Relative(scaleDatasetPattern)
				val numScales = if (metadataState is MultiScaleMetadataState) metadataState.scaleTransforms.size else 1
				LabelBlockLookupGroup(group, lblGroup, numScales, relativeLookup).write(metadataState.writer!!)
				relativeLookup
			}
			LOG.debug { "Got lookup type: ${lookup.javaClass}" }
			(lookup as? IsRelativeToContainer)?.setRelativeTo(metadataState.writer!!, group)
			lookup
		} else throw NotAPainteraDataset(reader, group)
	}

	/**
	 * @param attributes attributes
	 * @return [CellGrid] that is equivalent to dimensions and block size of `attributes`
	 */
	@JvmStatic
	fun asCellGrid(attributes: DatasetAttributes): CellGrid {
		return CellGrid(attributes.dimensions, attributes.blockSize)
	}

	/**
	 * @param group             dataset, multi-scale group, or paintera dataset
	 * @param isPainteraDataset set to `true` if `group` is paintera data set
	 * @return multi-scale group or n5 dataset
	 */
	@JvmStatic
	fun volumetricDataGroup(group: String, isPainteraDataset: Boolean): String {
		return if (isPainteraDataset) group + "/" + PAINTERA_DATA_DATASET else group
	}

	@JvmStatic
	fun validPainteraGroupMap(metadataTree: N5TreeNode): Map<String, N5TreeNode> {
		val validChoices = HashMap<String, N5TreeNode>()
		/* filter the metadata for valid groups/datasets*/
		val potentialDatasets = ArrayList<N5TreeNode>()
		potentialDatasets.add(metadataTree)
		var idx = 0
		while (idx < potentialDatasets.size) {
			val potentialChoice = potentialDatasets[idx]
			val metadata = potentialChoice.metadata
			if (metadataIsValid(metadata)) {
				/* if we are valid, add and update out map. */
				val validChoicePath = potentialChoice.path
				validChoices[validChoicePath] = potentialChoice
			} else {
				if (!potentialChoice.childrenList().isEmpty()) {
					/* if we aren't valid, but have kids, lets check them later */
					potentialDatasets.addAll(potentialChoice.childrenList())
				}
			}
			idx++;
		}
		return validChoices
	}

	/**
	 * Helper exception class, only intented to be used in [.idService] if `maxId` is not specified.
	 */
	class MaxIDNotSpecified(message: String) : PainteraException(message)
	class NotAPainteraDataset(val container: N5Reader, val group: String) : PainteraException(String.format("Group %s in container %s is not a Paintera dataset.", group, container))

	private const val URI = "uri"
	internal fun N5Reader.serializeTo(json: JsonObject) {
		if (!uri.equals(paintera.projectDirectory.actualDirectory.toURI())) {
			json[URI] = uri.toString()
		}
	}

	internal fun N5Reader.name() = uri.path.split("/").lastOrNull { it.isNotBlank() } ?: uri.path


	internal fun deserializeFrom(json: JsonObject): N5Reader {
		/* `fromClassInfo is the old style. Support both when deserializing, at least for now. Should be replaced on next save */
		val fromClassInfo = json[SerializationKeys.CONTAINER]
			?.asJsonObject?.get("data")
			?.asJsonObject?.let { it.get("basePath") ?: it.get("file") }
			?.asString
		val uri = fromClassInfo
			?: json[URI]?.asString
			?: paintera.projectDirectory.actualDirectory.absolutePath
		return getN5ContainerWithRetryPrompt(uri)
	}

	internal fun getN5ContainerWithRetryPrompt(uri: String): N5Reader {
		return try {
			n5Factory.openWriterElseOpenReader(uri)
		} catch (e: N5ContainerDoesntExist) {
			promptForNewLocationOrRemove(
				uri, e, "Container Not Found",
				"""
					N5 container does not exist at
						$uri
						
					If the container has moved, specify it's new location.
					If the container no longer exists, you can attempt to remove this source.
				""".trimIndent()
			)
		}
	}

	internal fun promptForNewLocationOrRemove(uri: String, cause: Throwable, header: String? = null, contentText: String? = null): N5Reader {
		var exception: () -> Throwable = { cause }
		val n5Container = runBlocking {
			InvokeOnJavaFXApplicationThread {
				PainteraAlerts.confirmation("Accept", "Quit").let { alert ->
					alert.headerText = header ?: "Error Opening N5 Container"
					alert.buttonTypes.add(ButtonType.FINISH)
					(alert.dialogPane.lookupButton(ButtonType.FINISH) as Button).apply {
						text = "Remove Source"
						onAction = EventHandler {
							it.consume()
							alert.close()
							exception = { RemoveSourceException(uri) }
						}
					}

					val newLocationField = TextField()
					var n5: N5Reader? = null
					(alert.dialogPane.lookupButton(ButtonType.OK) as Button).apply {
						disableProperty().bind(newLocationField.textProperty().isEmpty)
						onAction = EventHandler {
							n5 = getN5ContainerWithRetryPrompt(newLocationField.textProperty().get())
							it.consume()
							alert.close()
						}
					}


					alert.dialogPane.content = VBox().apply {
						children += HBox().apply {
							children += TextArea(contentText ?: "Error accessing container at $uri").also { it.editableProperty().set(false) }
						}
						children += HBox().apply {
							children += Label("New Location ").also { HBox.setHgrow(it, Priority.NEVER) }
							children += newLocationField
							newLocationField.maxWidth = Double.MAX_VALUE
							HBox.setHgrow(newLocationField, Priority.ALWAYS)
							children += Button("Browse").also {
								HBox.setHgrow(it, Priority.NEVER)
								it.onAction = EventHandler {
									DirectoryChooser().showDialog(alert.owner)?.let { newLocationField.textProperty().set(it.canonicalPath) }
								}
							}
						}
					}
					alert.showAndWait()
					n5
				}
			}.await()
		}
		return n5Container ?: throw exception()
	}

	/**
	 * Iterates through each block in the specified dataset that exists and performs an action using the provided lambda function.
	 * `withBlock` is called asynchronously for each block that exists, and the overall call will return only
	 * when all blocks are processed.
	 *
	 * @param n5 The N5 reader used to check for the existence of blocks.
	 * @param dataset The dataset path to check within the N5 container.
	 * @param processedCount An optional IntegerProperty to track the number of processed blocks.
	 * @param withBlock A lambda function to execute for each existing block's interval.
	 */
	suspend fun forEachBlockExists(
		n5: GsonKeyValueN5Reader,
		dataset: String,
		processedCount: IntegerProperty? = null,
		withBlock: (Interval) -> Unit
	) {

		val normalizedDatasetName = N5URI.normalizeGroupPath(dataset)
		val blockGrid = n5.getDatasetAttributes(normalizedDatasetName).run {
			CellGrid(dimensions, blockSize)
		}

		val gridIterable = IntervalIterator(blockGrid.gridDimensions)
		val cellDims = LongArray(gridIterable.numDimensions()) { blockGrid.cellDimensions[it].toLong() }

		coroutineScope {
			while (gridIterable.hasNext()) {
				gridIterable.fwd()
				val curBlock = gridIterable.positionAsLongArray()
				launch {
					val cellMin = LongArray(gridIterable.numDimensions())
					if (n5.keyValueAccess.exists(n5.absoluteDataBlockPath(normalizedDatasetName, *curBlock))) {

						for (i in cellMin.indices)
							cellMin[i] = blockGrid.getCellMin(i, curBlock[i])
						val cellInterval = Intervals.createMinSize(*cellMin, *cellDims)
						launch {
							withBlock(cellInterval)
							processedCount?.apply { InvokeOnJavaFXApplicationThread { set(value + 1) } }
						}
					} else
						processedCount?.apply { InvokeOnJavaFXApplicationThread { set(value + 1) } }
				}
			}
		}
	}


	/**
	 * Iterates through each block in the specified dataset and performs an action using the provided lambda function.
	 * `withBlock` is called asynchronously for each block, and the overall call will return only
	 * when all blocks are processed.
	 *
	 * @param blockGrid to iterate over
	 * @param processedCount An optional IntegerProperty to track the number of processed blocks.
	 * @param withBlock A lambda function to execute for each existing block's interval.
	 */
	suspend fun forEachBlock(
		blockGrid: CellGrid,
		processedCount: IntegerProperty? = null,
		withBlock: (Interval) -> Unit
	) {

		val gridIterable = IntervalIterator(blockGrid.gridDimensions)
		val curBlock = LongArray(gridIterable.numDimensions())
		val cellDims = LongArray(gridIterable.numDimensions()) { blockGrid.cellDimensions[it].toLong() }
		val cellMin = LongArray(gridIterable.numDimensions())

		coroutineScope {
			while (gridIterable.hasNext()) {
				gridIterable.fwd()
				gridIterable.localize(curBlock)
				for (i in cellMin.indices)
					cellMin[i] = blockGrid.getCellMin(i, curBlock[i])
				val cellInterval = Intervals.createMinSize(*cellMin, *cellDims)
				launch {
					withBlock(cellInterval)
					processedCount?.apply { InvokeOnJavaFXApplicationThread { set(value + 1) } }
				}
			}
		}
	}

	class RemoveSourceException : PainteraException {

		constructor(location: String) : super("Source expected at:\n$location\nshould be removed")
		constructor(location: String, cause: Throwable) : super("Source expected at:\n$location\nshould be removed", cause)

	}
}
