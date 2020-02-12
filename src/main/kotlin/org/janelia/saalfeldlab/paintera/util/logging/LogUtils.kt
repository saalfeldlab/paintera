package org.janelia.saalfeldlab.paintera.util.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.classic.Logger as LogbackLogger

class LogUtils {

    companion object {

        val DEFAULT_LEVEL = Level.INFO.also { setRootLoggerLevel(it) }

        @JvmStatic
        fun defaultLevel() = DEFAULT_LEVEL

        @JvmStatic
        fun setRootLoggerLevel(level: Level) = setLogLevelFor(Logger.ROOT_LOGGER_NAME, level)

        @JvmStatic
        fun setLogLevelFor(logger: String, level: Level) = setLogLevelFor(LoggerFactory.getLogger(logger), level)

        @JvmStatic
        fun setLogLevelFor(logger: Logger, level: Level) = if (logger is LogbackLogger) logger.level = level else Unit
    }

}
