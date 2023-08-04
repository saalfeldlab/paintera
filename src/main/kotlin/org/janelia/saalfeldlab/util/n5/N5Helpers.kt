package org.janelia.saalfeldlab.util.n5

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javafx.beans.property.BooleanProperty
import javafx.beans.value.ChangeListener
import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.ScaleAndTranslation
import net.imglib2.realtransform.Translation3D
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter
import org.janelia.saalfeldlab.labels.blocks.n5.LabelBlockLookupFromN5Relative
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.universe.N5DatasetDiscoverer
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.*
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal.NoInitialLutAvailable
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException
import org.janelia.saalfeldlab.paintera.exception.PainteraException
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.id.N5IdService
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.metadataIsValid
import org.janelia.saalfeldlab.paintera.state.metadata.MultiScaleMetadataState
import org.janelia.saalfeldlab.paintera.state.raw.n5.SerializationKeys
import org.janelia.saalfeldlab.paintera.util.n5.metadata.LabelBlockLookupGroup
import org.janelia.saalfeldlab.util.NamedThreadFactory
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraDataMultiScaleMetadata.PainteraDataMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup.PainteraLabelMultiScaleParser
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraRawMultiScaleGroup.PainteraRawMultiScaleParser
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.nio.file.Paths
import java.util.*
import java.util.List
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.Map
import kotlin.collections.all
import kotlin.collections.contentToString
import kotlin.collections.filter
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.sortBy
import kotlin.collections.toDoubleArray
import kotlin.collections.toTypedArray

object N5Helpers {
	const val MULTI_SCALE_KEY = "multiScale"
	const val IS_LABEL_MULTISET_KEY = "isLabelMultiset"
	const val MAX_ID_KEY = "maxId"
	const val RESOLUTION_KEY = "resolution"
	const val OFFSET_KEY = "offset"
	const val DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors"
	const val LABEL_MULTISETTYPE_KEY = "isLabelMultiset"
	const val MAX_NUM_ENTRIES_KEY = "maxNumEntries"
	const val PAINTERA_DATA_KEY = "painteraData"
	const val PAINTERA_DATA_DATASET = "data"
	const val PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET = "fragment-segment-assignment"
	const val LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping"
	private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

	private val GROUP_PARSERS = List.of<N5MetadataParser<*>>(
		PainteraRawMultiScaleParser(),
		PainteraLabelMultiScaleParser(),
		PainteraDataMultiScaleParser(),
		N5CosemMultiScaleMetadata.CosemMultiScaleParser(),
		N5MultiScaleMetadata.MultiScaleParser()
	)
	private val METADATA_PARSERS = List.of<N5MetadataParser<*>>(
		N5CosemMetadataParser(),
		N5SingleScaleMetadataParser(),
		N5GenericSingleScaleMetadataParser()
	)
	private val N5_METADATA_CACHE = HashMap<String, Optional<N5TreeNode>>()

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
		LOG.debug("Is {}/{} Paintera dataset? {}", n5, group, isPainteraDataset)
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
				LOG.debug(
					"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in the future. Please add \"{}\":{} to attributes.json.",
					MULTI_SCALE_KEY,
					MULTI_SCALE_KEY,
					true
				)
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
		LOG.debug("Found these scale dirs: {}", scaleDirs.contentToString())
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
		LOG.debug("Sorted scale dirs: {}", scaleDirs.contentToString())
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
		LOG.debug("Getting data type for group/dataset {}", group)
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

	/**
	 * Find all datasets inside an n5 container
	 * A dataset is any one of:
	 * - N5 dataset
	 * - multi-sclae group
	 * - paintera dataset
	 *
	 * @param n5          container
	 * @param discoverActive discover datasets while while `keepLooking.get() == true`
	 * @return List of all contained datasets (paths wrt to the root of the container)
	 */
	@JvmStatic
	fun parseMetadata(n5: N5Reader?, discoverActive: BooleanProperty?): Optional<N5TreeNode> {
		val threadFactory = NamedThreadFactory("dataset-discovery-%d", true)
		val es = if (n5 is N5HDF5Reader) {
			Executors.newFixedThreadPool(1, threadFactory)
		} else {
			Executors.newCachedThreadPool(threadFactory)
		}
		val stopDiscovery = ChangeListener<Boolean> { _, _, continueLooking -> if (!continueLooking) es.shutdown() }
		discoverActive?.addListener(stopDiscovery)
		val parsedN5Tree = parseMetadata(n5, es)
		LOG.debug("Shutting down discovery ExecutorService.")
		/* we are done, remove our listener */
		discoverActive?.removeListener(stopDiscovery)
		es.shutdownNow()
		return parsedN5Tree
	}

	@JvmStatic
	@JvmOverloads
	fun parseMetadata(n5: N5Reader, ignoreCache: Boolean = false): Optional<N5TreeNode> {
		val uri: String = n5.uri.toString()
		if (!ignoreCache && N5_METADATA_CACHE.containsKey(uri)) {
			return N5_METADATA_CACHE[uri]!!
		}
		val n5TreeNode = parseMetadata(n5, null as BooleanProperty?)
		N5_METADATA_CACHE[uri] = n5TreeNode
		return n5TreeNode
	}

	/**
	 * Find all datasets inside an n5 container
	 * A dataset is any one of:
	 * - N5 dataset
	 * - multi-sclae group
	 * - paintera dataset
	 *
	 * @param n5 container
	 * @param es ExecutorService for parallelization of discovery
	 * @return List of all contained datasets (paths wrt to the root of the container)
	 */
	@JvmStatic
	fun parseMetadata(
		n5: N5Reader?,
		es: ExecutorService?): Optional<N5TreeNode> {
		val discoverer = N5DatasetDiscoverer(n5, es, METADATA_PARSERS, GROUP_PARSERS)
		return try {
			val rootNode = discoverer.discoverAndParseRecursive("/")
			Optional.of(rootNode)
		} catch (e: IOException) {
			//FIXME give more info in error, remove stacktrace.
			LOG.error("Unable to discover datasets")
			e.printStackTrace()
			Optional.empty()
		}
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
		initialDownsamplingFactors: DoubleArray): AffineTransform3D {
		val shift = DoubleArray(downsamplingFactors.size)
		for (d in downsamplingFactors.indices) {
			transform[transform[d, d] * downsamplingFactors[d] / initialDownsamplingFactors[d], d] = d
			shift[d] = 0.5 / initialDownsamplingFactors[d] - 0.5 / downsamplingFactors[d]
		}
		return transform.concatenate(Translation3D(*shift))
	}

	/**
	 * Get appropriate [FragmentSegmentAssignmentState] for `group` in n5 container `writer`
	 *
	 * @param writer container
	 * @param group  group
	 * @return [FragmentSegmentAssignmentState]
	 * @throws IOException if any n5 operation throws [IOException]
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun assignments(writer: N5Writer, group: String): FragmentSegmentAssignmentOnlyLocal {
		if (!isPainteraDataset(writer, group)) {
			val persistError = "Persisting assignments not supported for non Paintera group/dataset $group"
			return FragmentSegmentAssignmentOnlyLocal(
				FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
				FragmentSegmentAssignmentOnlyLocal.doesNotPersist(persistError))
		}
		val dataset = "$group/$PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASET "
		val initialLut = if (writer.exists(dataset)) {
			N5FragmentSegmentAssignmentInitialLut(writer, dataset)
		} else NoInitialLutAvailable()
		return try {
			FragmentSegmentAssignmentOnlyLocal(
				initialLut ,
				N5FragmentSegmentAssignmentPersister(writer, dataset))
		} catch (e: ReflectionException) {
			LOG.debug("Unable to create initial lut supplier", e)
			FragmentSegmentAssignmentOnlyLocal(
				FragmentSegmentAssignmentOnlyLocal.NO_INITIAL_LUT_AVAILABLE,
				N5FragmentSegmentAssignmentPersister(writer, dataset))
		}
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
	fun idService(n5: N5Writer, dataset: String?): IdService {
		LOG.debug("Requesting id service for {}:{}", n5, dataset)
		val maxId = n5.getAttribute(dataset, "maxId", Long::class.java)
		LOG.debug("Found maxId={}", maxId)
		if (maxId == null) throw MaxIDNotSpecified(String.format(
			"Required attribute `maxId' not specified for dataset `%s' in container `%s'.",
			dataset,
			n5))
		return N5IdService(n5, dataset, maxId)
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
		group: String): String {
		LOG.debug("Getting finest level for dataset {}", group)
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
		group: String): String {
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
		joiner: BiFunction<String?, String?, String>): String {
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
		group: String): String {
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
		group: String): String {
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
		joiner: BiFunction<String?, String?, String>): String {
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
		vararg fallBack: Double): DoubleArray {
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
		vararg fallBack: Double): DoubleArray {
		if (reverse) {
			val toReverse = getDoubleArrayAttribute(n5, group, key, false, *fallBack)
			LOG.debug("Will reverse {}", toReverse)
			var i = 0
			var k = toReverse.size - 1
			while (i < toReverse.size / 2) {
				val tmp = toReverse[i]
				toReverse[i] = toReverse[k]
				toReverse[k] = tmp
				++i
				--k
			}
			LOG.debug("Reversed {}", toReverse)
			return toReverse
		}
		return if (isPainteraDataset(n5, group)) {
			getDoubleArrayAttribute(n5, "$group/$PAINTERA_DATA_DATASET", key, false, *fallBack)
		} else try {
			n5.getAttribute(group, key, DoubleArray::class.java) ?: fallBack
		} catch (e: ClassCastException) {
			LOG.debug("Caught exception when trying to read double[] attribute. Will try to read as long[] attribute instead.", e)
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
		LOG.debug("Getting label block lookup for {}", metadataState.metadata.getPath())
		return if (isPainteraDataset(reader, group)) {
			val gsonBuilder = GsonBuilder().registerTypeHierarchyAdapter(LabelBlockLookup::class.java, LabelBlockLookupAdapter.getJsonAdapter())
			val gson = gsonBuilder.create()
			val labelBlockLookupJson = reader.getAttribute(group, "labelBlockLookup", JsonElement::class.java)
			LOG.debug("Got label block lookup json: {}", labelBlockLookupJson)
			val lookup = labelBlockLookupJson
				?.takeIf { it.isJsonObject }
				?.let { gson.fromJson(it, LabelBlockLookup::class.java) as LabelBlockLookup }
				?: let {
					val labelToBlockDataset = Paths.get(group, "label-to-block-mapping").toString()
					val relativeLookup = LabelBlockLookupFromN5Relative("label-to-block-mapping/s%d")
					val numScales = if (metadataState is MultiScaleMetadataState) metadataState.scaleTransforms.size else 1
					val labelBlockLookupMetadata = LabelBlockLookupGroup(labelToBlockDataset, numScales)
					labelBlockLookupMetadata.write(metadataState.writer!!)
					relativeLookup
				}  as LabelBlockLookup
			LOG.debug("Got lookup type: {}", lookup.javaClass)
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

	/**
	 * If an n5 container exists at [uri], return it as [N5Writer] if possible, and [N5Reader] if not.
	 *
	 * @param uri the location of the n5 container
	 * @return [N5Writer] or [N5Reader] if container exists
	 */
	@JvmStatic
	fun getReaderOrWriterIfN5ContainerExists(uri: String): N5Reader? {
		val cachedContainer = getReaderOrWriterIfCached(uri)
		return cachedContainer ?: openReaderOrWriterIfContainerExists(uri)
	}

	/**
	 * If an n5 container exists at [uri], return it as [N5Writer] if possible.
	 *
	 * @param uri the location of the n5 container
	 * @return [N5Writer] if container exists and is openable as a writer.
	 */
	@JvmStatic
	fun getWriterIfN5ContainerExists(uri: String): N5Writer? {
		return getReaderOrWriterIfN5ContainerExists(uri) as? N5Writer
	}

	/**
	 * Retrieves a reader or writer for the given container if it exists.
	 *
	 * If the reader is successfully opened, the container must exist, so we try to open a writer.
	 * This is to ensure that an N5 container isn't created by opening as a writer if one doesn't exist.
	 * If opening a writer is not possible it falls back to again as a reader.
	 *
	 * @param container the path to the container
	 * @return a reader or writer as N5Reader, or null if the container does not exist
	 */
	private fun openReaderOrWriterIfContainerExists(container: String) = try {
		n5Factory.openReader(container)?.let {
			var reader: N5Reader? = it
			if (it is N5HDF5Reader) {
				it.close()
				reader = null
			}
			try {
				n5Factory.openWriter(container)
			} catch (_: Exception) {
				reader ?: n5Factory.openReader(container)
			}
		}
	} catch (e: Exception) {
		null
	}

	/**
	 * If there is a cached N5Reader for [container] than try to open a writer (also may be cached),
	 *  or fallback to the cached reader
	 *
	 * @param container The path to the N5 container.
	 * @return The N5Reader instance.
	 */
	private fun getReaderOrWriterIfCached(container: String): N5Reader? {
		val reader: N5Reader? = n5Factory.getFromCache(container)?.let {
			try {
				n5Factory.openWriter(container)
			} catch (_: Exception) {
				it
			}
		}
		return reader
	}

	private const val URI = "uri"
	internal fun N5Reader.serializeTo(json: JsonObject) {
		if (!uri.equals(paintera.projectDirectory.actualDirectory.toURI())) {
			json[URI] = uri.toString()
		}
	}

	internal fun deserializeFrom(json: JsonObject): N5Reader {
		/* `fromClassInfo is the old style. Support both when deserializing, at least for now. Should be replaced on next save */
		val fromClassInfo = json[SerializationKeys.CONTAINER]
			?.asJsonObject?.get("data")
			?.asJsonObject?.get("basePath")
			?.asString
		val uri = fromClassInfo ?: json[URI]?.asString ?: paintera.projectDirectory.actualDirectory.toURI().toString()
		return N5Helpers.getReaderOrWriterIfN5ContainerExists(uri)!!
	}
}
