package org.janelia.saalfeldlab.util.n5.universe

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.N5Exception
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5URI
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.universe.N5FactoryWithCache
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import java.net.URI
import java.nio.file.Paths
import kotlin.Throws

class PainteraN5Factory : N5FactoryWithCache() {

	init {
		preferredStorageFormat(StorageFormat.N5)
	}

	@Throws(N5ContainerDoesntExist::class)
	override fun openReader(format: StorageFormat?, uri: URI): N5Reader {
		val normalUri = normalizeUri(uri)
		return getReaderFromCache(format, normalUri)
			?: getWriterFromCache(format, normalUri)
			?: super.openReader(format, uri)
			?: throw N5ContainerDoesntExist(uri.toString())
	}

	@Throws(N5ContainerDoesntExist::class)
	override fun openWriter(format: StorageFormat?, uri: URI): N5Writer {
		return getWriterFromCache(format, normalizeUri(uri)) ?: openAndCacheExistingN5Writer(format, uri)
	}

	fun newWriter(uri: String): N5Writer =
		runCatching { openWriter(uri) }.getOrNull()
			?: StorageFormat.parseUri(uri).let {
				super.openWriter(it.a ,it.b) /* must be the last `openWriter` that doesn't call an override version */
			}

	fun openWriterOrNull(uri: String): N5Writer? =
		runCatching { openWriter(uri) }
			.onFailure { LOG.debug(it) { "Unable to open $uri as N5Writer" } }
			.getOrNull()

	fun openReaderOrNull(uri: String): N5Reader? =
		runCatching { openReader(uri) }
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
	private fun openAndCacheExistingN5Writer(format: StorageFormat?, uri: URI): N5Writer {
		/* We only want a writer if the container exists. Check by attempting to get a writer.
		*  If we don't error, open the writer */
		runCatching { openReader(format, uri) }
			.onSuccess { (it as? N5HDF5Reader)?.close() }
			.getOrThrow()

		return super.openWriter(format, uri)
	}

	companion object {
		private val LOG = KotlinLogging.logger { }


		//FIXME Caleb: Copied and converted from [N5FactoryWithCaceh#normalizeUri]. Should be public (or protected)!
		private fun normalizeUri(uri: URI): URI {
			if (uri.isAbsolute && uri.scheme != "file") {
				return uri.normalize()
			}

			val uriFromPath: URI = if (uri.isAbsolute) {
				Paths.get(uri).normalize().toUri()
			} else {
				Paths.get(uri.path).normalize().toUri()
			}

			// By default, Path.toUri() adds a trailing `/` if it's a directory.
			// We remove this trailing slash to avoid unintended cache issues.
			val uriWithoutTrailingSlash = uriFromPath.toString().removeSuffix("/")
			return URI.create(uriWithoutTrailingSlash).normalize()
		}
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