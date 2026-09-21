package org.janelia.saalfeldlab.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.n5.universe.StorageFormat
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeLines
import kotlin.math.max

private const val RECENT_CACHE_DIR = "recent"

private fun getCachePath(groupKey: String?, cacheKey : String) = Paths.get(PainteraDirectoriesConfig.DEFAULT_CACHE_DIR, groupKey ?: "", cacheKey).normalize()

enum class PainteraCache(groupKey : String?, private val cacheKey : String) {
	RECENT_PROJECTS(RECENT_CACHE_DIR, "projects"),
	RECENT_CONTAINERS(RECENT_CACHE_DIR, "containers"),
	RECENT_EXPORT_LOCATIONS(RECENT_CACHE_DIR, "export_containers");

	val cachePath: Path = getCachePath(groupKey, cacheKey)

	fun newestEntry() = readLines().lastOrNull()

	fun readLines() : List<String> {
		val cacheFile = mapFromDeprecatedCacheFiles(cachePath)
			.takeIf { it.toFile().isFile }
			?: return emptyList()
		try {
			LOG.debug { "Reading lines from $cacheFile" }
			return Files.readAllLines(cacheFile)
		} catch (e: IOException) {
			LOG.warn(e) { "Caught exception when trying to read lines from file at $cacheFile" }
			return emptyList()
		}
	}
	/**
	 * Drop every entry whose canonical form matches [entry].
	 *
	 * Rows are shown in canonical form while the cache holds whatever was written.
     * e.g. an exact comparison would miss `/x/y.n5` against `file:/x/y.n5`.
	 */
	fun removeEntry(entry: String) {
		val lines = readLines().takeUnless { it.isEmpty() } ?: return

        val remaining = lines.filterNot { line ->
            entry.canonicalOrNull() == line.canonicalOrNull()
        }
        /* no change, short circuit */
		if (remaining.size == lines.size)
			return
		try {
			cachePath.createParentDirectories()
			cachePath.writeLines(remaining)
			LOG.debug { "Removed $entry from $cacheKey" }
		} catch (e: IOException) {
			LOG.error(e) { "Caught exception when trying to remove $entry from file at $cacheKey" }
		}
	}

	fun appendLine(line : String, maxNumLines : Int = 10) {
		val lines: MutableList<String> = readLines().toMutableList().apply {
			remove(line)
			add(line)
		}
		try {
			LOG.debug { "Writing lines to $cacheKey: $lines" }
			cachePath.createParentDirectories()
			cachePath.writeLines(lines.subList(max((lines.size - maxNumLines).toDouble(), 0.0).toInt(), lines.size))
		} catch (e: IOException) {
			LOG.error(e) { "Caught exception when trying to write lines to file at $cacheKey: $lines" }
		}
	}

	companion object {

		private val LOG = KotlinLogging.logger { }

		/** the canonical form of a cached line, or null if it cannot be parsed as a URI */
		private fun String.canonicalOrNull(): String? = runCatching {
			val uri = StorageFormat.parseUri(this).b.takeIf { it.isAbsolute } ?: URI("file", this, null)
			N5Helpers.canonicalString(uri)
		}.getOrNull()

		@JvmStatic
		fun readLines(cache: PainteraCache) = cache.readLines()

		@JvmStatic
		fun appendLine(cache: PainteraCache, toAppend: String, maxNumLines: Int) = cache.appendLine(toAppend, maxNumLines)

		private fun PainteraCache.asUris(): List<URI> = readLines()
			.reversed()
			.mapNotNull { recentString ->
				val stringAsUri =
					this.runCatching { StorageFormat.parseUri(recentString).b.takeIf { uri -> uri.isAbsolute } }.getOrNull()
				stringAsUri ?: this.runCatching { URI("file", recentString, null) }.getOrNull()
			}

		fun PainteraCache.distinctCanonicalURIs() = asUris()
			.distinctBy { recent -> N5Helpers.canonicalString(recent) }

		fun PainteraCache.distinctCanonicalStrings() = asUris()
			.map { N5Helpers.canonicalString(it) }
			.distinct()

		private val DEPRECATED_MAPPINGS = mapOf(
			RECENT_PROJECTS.cachePath to getCachePath("org.janelia.saalfeldlab.paintera.Paintera", "recent_projects"),
			RECENT_CONTAINERS.cachePath to getCachePath("org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.N5FactoryOpener", "recent"),
		)

		private fun mapFromDeprecatedCacheFiles(cacheFile: Path): Path {
			//TODO Caleb: remove eventually after some time/releases have passed

			/* If we already have the expected cache file, assume this was previously done */
			if (cacheFile.exists()) return cacheFile
			/* If the deprecated one exists, return it, else return the queried one. */
			return DEPRECATED_MAPPINGS[cacheFile]?.takeIf { it.exists() } ?: cacheFile
		}

	}

}
