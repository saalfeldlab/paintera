package org.janelia.saalfeldlab.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.math.max

object PainteraCache {

	const val RECENT_CACHE = "recent"

	private val LOG = KotlinLogging.logger { }

	private fun getCacheFile(cache: String?, filename: String): Path {
		return Paths.get(PainteraDirectoriesConfig.DEFAULT_CACHE_DIR, cache ?: "", filename)
	}

	@JvmStatic
	fun readLines(cache: String?, filename: String): List<String> {
		val cacheFile = getCacheFile(cache, filename).let {
			//TODO Caleb: remove eventually after some time/releases have passed
			mapFromDeprecatedCacheFiles(it)
		}
		try {
			LOG.debug { "Reading lines from $cacheFile" }
			return Files.readAllLines(cacheFile)
		} catch (e: IOException) {
			LOG.warn(e) { "Caught exception when trying to read lines from file at $cacheFile" }
			return emptyList()
		}
	}

	@JvmStatic
	fun appendLine(cache: String?, filename: String, toAppend: String, maxNumLines: Int) {
		val lines: MutableList<String> = ArrayList(readLines(cache, filename))
		lines.remove(toAppend)
		lines.add(toAppend)
		val cacheFile = getCacheFile(cache, filename)
		try {
			LOG.debug { "Writing lines to $cacheFile: $lines" }
			cacheFile.parent.toFile().mkdirs()
			Files.write(cacheFile, lines.subList(max((lines.size - maxNumLines).toDouble(), 0.0).toInt(), lines.size))
		} catch (e: IOException) {
			LOG.error(e) { "Caught exception when trying to write lines to file at $cacheFile: $lines" }
		}
	}

	private fun mapFromDeprecatedCacheFiles(cacheFile: Path): Path {
		/* If we already have the expected cache file, assume this was previously done */
		if (cacheFile.exists()) return cacheFile

		/* If the deprecated one exists, return it, else return the queried one. */
		return DEPRECATED_MAPPINGS[cacheFile]?.takeIf { it.exists() } ?: cacheFile

	}

	private val DEPRECATED_MAPPINGS = mapOf(
		getCacheFile(RECENT_CACHE, "projects") to getCacheFile("org.janelia.saalfeldlab.paintera.Paintera", "recent_projects"),
		getCacheFile(RECENT_CACHE, "containers") to getCacheFile("org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog.menu.n5.N5FactoryOpener", "recent"),
	)
}
