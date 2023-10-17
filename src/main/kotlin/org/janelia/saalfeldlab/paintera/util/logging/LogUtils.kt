package org.janelia.saalfeldlab.paintera.util.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import dev.dirs.ProjectDirectories
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import ch.qos.logback.classic.Logger as LogbackLogger

object LogUtils {

	private const val PAINTERA_STARTUP_DATE_PROPERTY = "paintera.startup.date"
	private const val PAINTERA_PID_PROPERTY = "paintera.pid"
	private const val PAINTERA_LOG_DIR_PROPERTY = "paintera.log.dir"
	private const val ROOT_LOGGER_LEVEL_PROPERTY = "paintera.log.root.logger.level"
	private const val PAINTERA_LOG_FILENAME_BASE_PROPERTY = "paintera.log.filename.base"
	private const val PAINTERA_LOGGING_ENABLED_PROPERTY = "paintera.log.enabled"
	private const val PAINTERA_LOGGING_TO_CONSOLE_ENABLED_PROPERTY = "paintera.log.console.enabled"
	private const val PAINTERA_LOGGING_TO_FILE_ENABLED_PROPERTY = "paintera.log.file.enabled"

	private val logContextProperties: MutableMap<String, String?> = mutableMapOf()

	@JvmStatic
	val currentTime = Date()

	private val context
		get() = LoggerFactory.getILoggerFactory() as LoggerContext

	init {
		resetLoggingConfig()
	}

	@JvmStatic
	val painteraLogDir: String
		get() = (context).getProperty(PAINTERA_LOG_DIR_PROPERTY)

	@JvmStatic
	val painteraLogFilenameBase: String
		get() = (context).getProperty(PAINTERA_LOG_FILENAME_BASE_PROPERTY)

	@JvmStatic
	val painteraLogFilePath: String
		get() = Path.of(painteraLogDir, "$painteraLogFilenameBase.log").toAbsolutePath().toString()

	@JvmStatic
	val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
	private val rootLoggerLogback = rootLogger as? LogbackLogger

	@JvmStatic
	var rootLoggerLevel: Level?
		@Synchronized set(level) {
			logContextProperties[ROOT_LOGGER_LEVEL_PROPERTY] = level?.also { setLogLevelFor(rootLogger, level) }?.levelStr
		}
		@Synchronized get() = rootLoggerLogback?.level

	private fun setPropertyAndResetLoggingConfig(property: String, value: String?) {
		value?.let { logContextProperties[property] = value } ?: logContextProperties.remove(property)
		resetLoggingConfig()
	}

	private fun setBooleanProperty(identifier: String, enabled: Boolean) = setPropertyAndResetLoggingConfig(identifier, "$enabled")

	@JvmStatic
	fun setLoggingEnabled(enabled: Boolean) = setBooleanProperty(PAINTERA_LOGGING_ENABLED_PROPERTY, enabled)

	@JvmStatic
	fun setLoggingToConsoleEnabled(enabled: Boolean) = setBooleanProperty(PAINTERA_LOGGING_TO_CONSOLE_ENABLED_PROPERTY, enabled)

	@JvmStatic
	fun setLoggingToFileEnabled(enabled: Boolean) = setBooleanProperty(PAINTERA_LOGGING_TO_FILE_ENABLED_PROPERTY, enabled)

	@JvmStatic
	fun resetLoggingConfig(clearProperties: Boolean = false) {
		if (clearProperties)
			logContextProperties.clear()
		context.reset()
		setLoggingProperties(context)
		ContextInitializer(context).autoConfig()
		val levels = context.loggerList.associate { Pair(it.name, it.level) }
		levels.forEach { (name, level) -> context.getLogger(name).level = level }
	}

	@JvmStatic
	fun setLoggingProperties(context: LoggerContext) {
		context.apply {
			logContextProperties.computeIfAbsent(PAINTERA_LOG_DIR_PROPERTY) {
				Path.of(ProjectDirectories.from("org", "janelia", "Paintera").dataLocalDir, "logs").toAbsolutePath().toString()
			}

			logContextProperties.computeIfAbsent(PAINTERA_STARTUP_DATE_PROPERTY) { SimpleDateFormat("yyyy-MM-dd").format(currentTime) }
			logContextProperties.computeIfAbsent(PAINTERA_PID_PROPERTY) { "${ProcessHandle.current().pid()}" }
			logContextProperties.computeIfAbsent(PAINTERA_LOG_FILENAME_BASE_PROPERTY) { "paintera" }
			logContextProperties.forEach { (key, value) -> value?.let { context.putProperty(key, it) } }
		}
	}

	@JvmStatic
	fun setLogLevelFor(name: String, level: Level) = setLogLevelFor(LoggerFactory.getLogger(name), level)

	@JvmStatic
	fun setLogLevelFor(logger: Logger, level: Level) = if (logger is LogbackLogger) logger.level = level else Unit

	fun String.isRootLoggerName() = ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME == this

	class Logback {
		object Loggers {
			operator fun get(name: String) = LoggerFactory.getLogger(name) as? ch.qos.logback.classic.Logger
		}

		object Levels {

			@JvmStatic
			val levels = arrayOf(
				Level.ALL,
				Level.TRACE,
				Level.DEBUG,
				Level.INFO,
				Level.WARN,
				Level.ERROR,
				Level.OFF
			).sortedBy { it.levelInt }.asReversed()

			operator fun get(level: String): Level? = levels.firstOrNull { level.equals(it.levelStr, ignoreCase = true) }

			operator fun contains(level: String): Boolean = get(level) != null

			class CmdLineConverter : CommandLine.ITypeConverter<Level?> {
				override fun convert(value: String): Level? {
					val level = Levels[value]
					if (level === null)
						LOG.warn("Ignoring invalid log level `{}'. Valid levels are (case-insensitive): {}", value, levels)
					return level
				}

				companion object {
					val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
				}

			}
		}

	}

}
