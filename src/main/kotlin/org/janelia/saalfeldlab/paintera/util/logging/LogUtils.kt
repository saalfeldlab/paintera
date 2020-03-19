package org.janelia.saalfeldlab.paintera.util.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import picocli.CommandLine
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date
import ch.qos.logback.classic.Logger as LogbackLogger

class LogUtils {

    companion object {

        // set property "paintera.startup.date" to current time
        @JvmStatic
        val currentTime = Date()

        @JvmStatic
        val currentTimeString = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(currentTime).also { System.setProperty("paintera.startup.date", it) }

        @JvmStatic
        val processId = ManagementFactory.getRuntimeMXBean().getName()?.split("@")?.firstOrNull()?.also { System.setProperty("paintera.process.id", it) }

        // set the file name base into which to log if logging to file is enabled
        //
        // if a process id (PID) is available:
        //   paintera.${PID}.yyyy-MM-dd_HH-mm-ss
        // else:
        //   paintera.yyyy-MM-dd_HH-mm-ss
        //
        // logback.xml is configured to store logs in
        // $HOME/.paintera/logs/${BASENAME}.log
        @JvmStatic
        val painteraLogFilenameBase = if (processId == null) {
            currentTimeString
        } else {
            "$processId.$currentTimeString"
        }.also {
            System.setProperty("paintera.log.filename.base", "paintera.$it")
            resetLoggingConfig()
        }



        @JvmStatic
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)

        private val rootLoggerLogback = rootLogger as? LogbackLogger

        private const val painteraLogDirProperty = "paintera.log.dir"

        private const val rootLoggerLevelProperty = "paintera.log.root.logger.level"

        private const val painteraLoggingEnabledProperty = "paintera.log.enabled"
        private const val painteraLoggingToConsoleEnabledProperty = "paintera.log.console.enabled"
        private const val painteraLoggingToFileEnabledProperty = "paintera.log.file.enabled"

        @JvmStatic
        var rootLoggerLevel: Level?
            @Synchronized set(level) {
                if (level === null)
                    System.clearProperty(rootLoggerLevelProperty)
                else {
                    setLogLevelFor(rootLogger, level)
                    System.setProperty(rootLoggerLevelProperty, level.levelStr)
                }
            }
            @Synchronized get() = rootLoggerLogback?.level

        private fun setPropertyAndResetLoggingConfig(property: String, value: String?) {
            if (value === null) System.clearProperty(property) else System.setProperty(property, value)
            resetLoggingConfig()
        }

        private fun setBooleanProperty(identifier: String, enabled: Boolean) = setPropertyAndResetLoggingConfig(identifier, "$enabled")

        @JvmStatic
        fun setLoggingEnabled(enabled: Boolean) = setBooleanProperty(painteraLoggingEnabledProperty, enabled)

        @JvmStatic
        fun setLoggingToConsoleEnabled(enabled: Boolean) = setBooleanProperty(painteraLoggingToConsoleEnabledProperty, enabled)

        @JvmStatic
        fun setLoggingToFileEnabled(enabled: Boolean) = setBooleanProperty(painteraLoggingToFileEnabledProperty, enabled)

        @JvmStatic
        fun resetLoggingConfig() {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext
            val levels = lc.loggerList.associate { Pair(it.name, it.level) }
            val ci = ContextInitializer(lc)
            lc.reset()
            ci.autoConfig()
            levels.forEach { (name, level) -> lc.getLogger(name).level = level }
        }

        @JvmStatic
        fun setLogLevelFor(name: String, level: Level) = setLogLevelFor(LoggerFactory.getLogger(name), level)

        @JvmStatic
        fun setLogLevelFor(logger: Logger, level: Level) = if (logger is LogbackLogger) logger.level = level else Unit

        fun String.isRootLoggerName() = ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME == this
    }

    class Logback {
        class Loggers {
            companion object {
                operator fun get(name: String) = LoggerFactory.getLogger(name) as? ch.qos.logback.classic.Logger
            }
        }

        class Levels {
            companion object {

                @JvmStatic
                val levels = arrayOf(
                    Level.ALL,
                    Level.TRACE,
                    Level.DEBUG,
                    Level.INFO,
                    Level.WARN,
                    Level.ERROR,
                    Level.OFF).sortedBy { it.levelInt }.asReversed()

                operator fun get(level: String): Level? = levels.firstOrNull { level.equals(it.levelStr, ignoreCase = true) }

                operator fun contains(level: String): Boolean = get(level) != null

            }

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
