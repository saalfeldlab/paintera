package org.janelia.saalfeldlab.util.n5.universe

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.KeyValueAccess
import org.janelia.saalfeldlab.n5.N5Exception
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.universe.N5Factory
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import java.net.URI

class N5FactoryWithCache : N5Factory() {

	companion object {
		private val LOG = KotlinLogging.logger {  }

		private const val ZGROUP = ".zgroup"
		private const val ZARRAY = ".zarray"
		private const val ZATTRS = ".zattrs"
		private const val N5_ATTRIBUTES = "attributes.json"

		/** Check for existing of N5 and zarr specific files to indicate the format
		 **/
		internal fun KeyValueAccess.guessStorageFromFormatSpecificFiles(root : URI) : StorageFormat? {
			val uri = root.takeIf { it.isAbsolute} ?: URI("file://$root")
			return when {
				exists(compose(uri, N5_ATTRIBUTES)) -> StorageFormat.N5
				listOf(ZGROUP, ZARRAY, ZATTRS).any { exists(compose(uri, it)) } -> StorageFormat.ZARR
				else -> null
			}
		}
	}

	private val writerCache = HashMap<String, N5Writer>()
	private val readerCache = HashMap<String, N5Reader>()

	private fun parseUriWithN5Default(uri: String) : Pair<StorageFormat, URI> {
		return StorageFormat.parseUri(uri).run {
			val format = when {
				a != null -> a
				else -> this@N5FactoryWithCache.getKeyValueAccess(b)?.guessStorageFromFormatSpecificFiles(b)
			} ?: StorageFormat.N5
			format to b
		}
	}

	private fun openWriterDefaultN5(uri: String) : N5Writer {
		val (format, asUri) = parseUriWithN5Default(uri)
		return super.openWriter(format, asUri)
	}

	private fun openReaderDefaultN5(uri: String) : N5Reader {
		val (format, asUri) = parseUriWithN5Default(uri)
		return super.openReader(format, asUri)
	}

	override fun openReader(uri: String): N5Reader {
		return openReader(uri, allowWriter = true)
	}

	fun openReader(uri: String, allowWriter : Boolean): N5Reader {
		/* Get the cached reader if present, and not also a writer (or allowed)*/
		var reader: N5Reader? = getFromReaderCache(uri)?.takeIf { allowWriter || it !is N5Writer }
		/* if allowWriter, grab the cached writer if present */
		reader = reader ?: if (allowWriter) getFromWriterCache(uri) else null
		/* try to create a new reader */
		reader = reader ?: openReaderDefaultN5(uri)

		return reader.also {
			if (containerIsReadable(it)) {
				readerCache[uri] = it
				it
			} else {
				throw N5ContainerDoesntExist(uri)
			}
		}
	}

	override fun openWriter(uri: String): N5Writer {
		return getFromWriterCache(uri) ?: openAndCacheExistingN5Writer(uri)
	}
	fun newWriter(uri: String): N5Writer {
		return getFromWriterCache(uri) ?: createAndCacheN5Writer(uri)
	}

	fun openWriterOrNull(uri : String) : N5Writer? = try {
		openWriter(uri)
	} catch (e : Exception) {
		LOG.debug(e) {"Unable to open $uri as N5Writer"}
		null
	}

	fun openReadOnlyN5(uri : String) : N5Reader {
		return openReader(uri, allowWriter = false)
	}

	fun openReaderOrNull(uri : String) : N5Reader? = try {
		openReader(uri)
	} catch (e : Exception) {
		LOG.debug(e) { "Unable to open $uri as N5Reader"}
		null
	}

	fun openWriterOrReaderOrNull(uri: String) = try {
		openWriterOrNull(uri) ?: openReaderOrNull(uri)
	} catch (e : N5Exception) {
		LOG.trace(e) {"Cannot get N5Reader at $uri"}
		null
	}

	fun openWriterElseOpenReader(uri : String) = try {
		openWriterOrNull(uri) ?: openReader(uri)
	} catch (e : N5Exception) {
		if (e.message?.startsWith("No container exists at ") == true)
			throw N5ContainerDoesntExist(uri, e)
		else throw e
	}

	private fun containerIsReadable(reader: N5Reader) = try {
		reader.getAttribute("/", "/", String::class.java)
		true
	} catch (e : Exception ) {
		false
	}

	private fun containerIsWritable(writer: N5Writer) = try {
		val version = writer.getAttribute("/", N5Writer.VERSION_KEY, String::class.java)
		if (version == null)
			false
		else {
			writer.setAttribute("/", N5Writer.VERSION_KEY, version)
			true
		}
	} catch (e : Exception ) {
		false
	}

	/**
	 * If a cached reader is present, and is valid (i.e., can actually read), return the reader.
	 *
	 *
	 * This has the side effect that if the cached reader is not valid (e.g. it has been closed,
	 * or can otherwise no longer read) then it will be removed from the cache, and null will be returned.
	 * Note this can case a removal of a writer from the writer cache, in the case that the reader we are
	 * removing is also a writer that is present in the writer cache.
	 *
	 * @param uri to check the cached reader for
	 * @return the cached reader, if it exists
	 */
	private fun getFromReaderCache(uri: String): N5Reader? {
		synchronized(readerCache) {
			readerCache[uri]?.also { reader -> if (!containerIsReadable(reader)) clearKey(uri) }
			return readerCache[uri]
		}
	}

	/**
	 * If a cached writer is present, and is valid (i.e., can actually write), return the writer.
	 *
	 *
	 * This has the side effect that if the cached writer is not valid (e.g. it has been closed,
	 * or can otherwise no longer write) then it will be removed from the cache, and null will be returned.
	 * Since an N5Writer is a valid N5Reader, when discovering that the cached N5Writer is no longer valid, it
	 * will also be removed from the reader cache, if it's present.
	 *
	 * @param uri to check the cached writer for
	 * @return the cached writer, if it exists and is valid
	 */
	private fun getFromWriterCache(uri: String): N5Writer? {
		synchronized(writerCache) {
			writerCache[uri]?.also { writer -> if (!containerIsWritable(writer)) clearKey(uri) }
			return writerCache[uri]
		}
	}

	private fun openAndCacheExistingN5Writer(uri: String): N5Writer {
		val readerWasCached = getFromReaderCache(uri) != null

		val reader = openReader(uri)
		if (!containerIsReadable(reader))
			throw N5ContainerDoesntExist(uri)

		/* If we opened explicitly to check, then close now.
		 * If we are HDF5, we need to close before opening */
		if (!readerWasCached || reader is N5HDF5Reader)
			 reader.close()

		return createAndCacheN5Writer(uri)
	}

	private fun createAndCacheN5Writer(uri: String): N5Writer {
		val n5Writer = openWriterDefaultN5(uri)
		/* See if we have write permissions before we declare success */
		n5Writer.setAttribute("/", N5Reader.VERSION_KEY, n5Writer.version.toString())
		if (readerCache[uri] == null) {
			readerCache[uri] = n5Writer
		}
		writerCache[uri] = n5Writer
		return n5Writer
	}

	fun clearKey(uri: String) {
		val writer = writerCache.remove(uri)
		writer?.close()
		val reader = readerCache.remove(uri)
		reader?.close()
	}

	fun clearCache() {
		writerCache.clear()
		readerCache.clear()
	}
}

class N5DatasetDoesntExist : N5Exception {

	companion object {
		private fun displayDataset(dataset: String) = N5URI.normalizeGroupPath(dataset).ifEmpty { "/" }
	}

	constructor(uri : String, dataset: String) : super("Dataset \"${displayDataset(dataset)}\" not found in container $uri")
	constructor(uri: String, dataset : String, cause: Throwable) : super("Dataset \"${displayDataset(dataset)}\" not found in container $uri", cause)
}

class N5ContainerDoesntExist : N5Exception {

	constructor(location: String) : super("Cannot Open $location")
	constructor(location: String, cause: Throwable) : super("Cannot Open $location", cause)
}