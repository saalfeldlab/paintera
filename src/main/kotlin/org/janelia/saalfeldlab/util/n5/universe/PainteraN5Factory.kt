package org.janelia.saalfeldlab.util.n5.universe

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.KeyValueAccess
import org.janelia.saalfeldlab.n5.N5Exception
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.universe.N5FactoryWithCache
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import java.net.URI

class PainteraN5Factory : N5FactoryWithCache() {

	init {
		options.apply {
			cacheAttributes(true)
			zarr2Builder.apply { dimensionSeparator("/") }
			zarr3Builder.apply { dimensionSeparator("/") }
		}
		preferredStorageFormat(StorageFormat.N5)
	}

    internal class ThreadLocalConditional(val initial : Boolean) : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean {
            return initial
        }

        fun <T> allowOrNull(allow: () -> T): T? =
            if (get()) allow() else null

        fun <T> use(allow: Boolean, invoke: () -> T): T {
            set(allow)
            try {
                return invoke()
            } finally {
                remove()
            }
        }
    }


    /* whether to allow a writer to be returned as a reader. */
    private val allowWriterAsReader = ThreadLocalConditional(true)

    /* whether to allow creating a new n5 container if one doesn't already exist.  */
    private val allowCreateContainer = ThreadLocalConditional(false)

    @Synchronized
	@Throws(N5ContainerDoesntExist::class)
    override fun openReader(format: StorageFormat?, access: KeyValueAccess, location: URI): N5Reader {
        val cachedReader by lazy(LazyThreadSafetyMode.NONE) { getReaderFromCache(format, location) }
        val cachedWriterAsReader by lazy(LazyThreadSafetyMode.NONE) {
            allowWriterAsReader.allowOrNull { getWriterFromCache(format, location) as? N5Reader }
	}
        val openReader by lazy(LazyThreadSafetyMode.NONE) {
			try {
                super.openReader(format, access, location)
			} catch (e: N5Exception.N5IOException) {
				if (e.message?.startsWith("No container exists at") == true)
                    throw N5ContainerDoesntExist(location.toString(), e)
				throw e
			}
		}
        return cachedReader /* reader was cached */
            ?: cachedWriterAsReader /* writer was cached, but can be returned as N5Reader */
            ?: openReader /* open and cache if valid N5 Container*/
    }

    @JvmSynthetic
    internal fun openReader(uri: String, allowWriter: Boolean): N5Reader = allowWriterAsReader.use(allowWriter) { super.openReader(uri) }

    @Synchronized
	@Throws(N5ContainerDoesntExist::class)
    override fun openWriter(format: StorageFormat?, access: KeyValueAccess, location: URI): N5Writer {
        val cachedWriter by lazy(LazyThreadSafetyMode.NONE) {
            getWriterFromCache(format, location)
        }
        val createNewWriter by lazy(LazyThreadSafetyMode.NONE) {
            allowCreateContainer.allowOrNull<N5Writer> { super.openWriter(format, access, location) }
        }
        val openExistingWriter by lazy(LazyThreadSafetyMode.NONE) {
            openAndCacheExistingN5Writer(format, access, location)
        }
        return cachedWriter /* Writer already cached, return it */
            ?: createNewWriter /* not cached, but we are allowed to create it if necessary, so do it*/
            ?: openExistingWriter /* not cached, and can't create new, but if an N5 Container exists, we can still get the writer for it.*/
	}

    @Synchronized
    fun newWriter(uri: String): N5Writer {
        runCatching { openWriter(uri) }.getOrNull()?.let { return it }
        val (format, asUri) = StorageFormat.parseUri(uri).run { a to b }
        return allowCreateContainer.use(true) {
            val kva = getKeyValueAccess(asUri, false)!!
            openWriter(format, kva, asUri)
        }
			}

    @Synchronized
	fun newWriter(format: StorageFormat?, uri: String): N5Writer {
		val asUri = StorageFormat.parseUri(uri).b
        runCatching { openWriter(format, asUri) }.getOrNull()?.let { return it }
        return allowCreateContainer.use(true) {
            val kva = getKeyValueAccess(asUri, false)!!
            openWriter(format, kva, asUri)
		}
    }

    fun openWriterOrNull(uri: String): N5Writer? =
        runCatching { openWriter(uri) }
			.onFailure { LOG.debug(it) { "Unable to open $uri as N5Writer" } }
			.getOrNull()

	fun openReaderOrNull(uri: String, allowWriter: Boolean = true): N5Reader? =
		runCatching { openReader(uri, allowWriter) }
			.onFailure { LOG.debug(it) { "Unable to open $uri as N5Reader" } }
			.getOrNull()

	fun openWriterOrReaderOrNull(uri: String) =
		runCatching { openWriterOrNull(uri) ?: openReaderOrNull(uri) }
			.onFailure { LOG.trace(it) { "Cannot get N5Reader at $uri" } }
			.getOrNull()

	fun openWriterElseOpenReader(uri: String): N5Reader =
		runCatching { openWriterOrNull(uri) ?: openReader(uri) }
			.onFailure {
				if (it.message?.startsWith("No container exists at ") == true)
					throw N5ContainerDoesntExist(uri, it)
				else throw it
			}.getOrThrow()

	@Throws(N5ContainerDoesntExist::class)
    private fun openAndCacheExistingN5Writer(format: StorageFormat?, access: KeyValueAccess, location: URI): N5Writer {
        /* We only want a writer if the container exists. Check by attempting to get a reader.
		*  If we don't error, open the writer */
        runCatching { openReader(format, access, location) }
			.onSuccess { (it as? N5HDF5Reader)?.close() }
			.getOrThrow()

        return super.openWriter(format, access, location)
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}

class N5DatasetDoesntExist : N5Exception {

	companion object {
		private fun displayDataset(dataset: String) = N5URI.normalizeGroupPath(dataset).ifEmpty { "/" }
	}

	constructor(uri: String, dataset: String) : super("Dataset \"${displayDataset(dataset)}\" not found in container $uri")
	constructor(uri: String, dataset: String, cause: Throwable) : super("Dataset \"${displayDataset(dataset)}\" not found in container $uri", cause)
}

class N5ContainerDoesntExist : N5Exception {

	constructor(location: String) : super("Cannot Open $location")
	constructor(location: String, cause: Throwable) : super("Cannot Open $location", cause)
}