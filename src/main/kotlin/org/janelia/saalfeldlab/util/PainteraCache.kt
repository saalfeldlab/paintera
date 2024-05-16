package org.janelia.saalfeldlab.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.max

object PainteraCache {
	private val LOG = KotlinLogging.logger { }

	private fun getCacheFile(clazz: Class<*>, filename: String): Path {
		return Paths.get(PainteraDirectoriesConfig.DEFAULT_CACHE_DIR, clazz.name, filename)
	}

	@JvmStatic
	fun readLines(clazz: Class<*>, filename: String): List<String> {
		val cacheFile = getCacheFile(clazz, filename)
		try {
			LOG.debug { "Reading lines from $cacheFile" }
			return Files.readAllLines(cacheFile)
		} catch (e: IOException) {
			LOG.debug(e) { "Caught exception when trying to read lines from file at $cacheFile" }
			return emptyList()
		}
	}

	@JvmStatic
	fun appendLine(clazz: Class<*>, filename: String, toAppend: String, maxNumLines: Int) {
		val lines: MutableList<String> = ArrayList(readLines(clazz, filename))
		lines.remove(toAppend)
		lines.add(toAppend)
		val cacheFile = getCacheFile(clazz, filename)
		try {
			LOG.debug { "Writing lines to $cacheFile: $lines" }
			cacheFile.parent.toFile().mkdirs()
			Files.write(cacheFile, lines.subList(max((lines.size - maxNumLines).toDouble(), 0.0).toInt(), lines.size))
		} catch (e: IOException) {
			LOG.error(e) { "Caught exception when trying to write lines to file at $cacheFile: $lines" }
		}
	}
}
