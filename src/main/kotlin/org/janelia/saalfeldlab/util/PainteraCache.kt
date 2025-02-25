package org.janelia.saalfeldlab.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig
import org.janelia.saalfeldlab.util.PainteraCache.Companion.DEPRECATED_MAPPINGS
import java.io.IOException
import java.nio.file.Files
import java.nio.file.OpenOption
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
	RECENT_CONTAINERS(RECENT_CACHE_DIR, "containers");

	val cachePath: Path = getCachePath(groupKey, cacheKey)

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
	fun appendLine(line : String, maxNumLines : Int) {
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

		@JvmStatic
		fun readLines(cache: PainteraCache) = cache.readLines()

		@JvmStatic
		fun appendLine(cache: PainteraCache, toAppend: String, maxNumLines: Int) = cache.appendLine(toAppend, maxNumLines)

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
