package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.collections.FXCollections
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils.Companion.isRootLoggerName
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class LoggingConfig {

    private val _rootLoggerLevel = SimpleObjectProperty<Level>(LogUtils.rootLoggerLevel ?: defaultLogLevel)
        .also { it.addListener { _, _, level -> LogUtils.rootLoggerLevel = level } }
    var rootLoggerLevel: Level
        get() = _rootLoggerLevel.value
        set(level) = _rootLoggerLevel.set(level)

    fun rootLoggerLevelProperty(): ObjectProperty<Level> = _rootLoggerLevel

    private val loggerLevels = FXCollections.observableHashMap<String, ObjectProperty<Level>>()

    val unmodifiableLoggerLevels
        get() = FXCollections.unmodifiableObservableMap(loggerLevels)

    private val _isLoggingEnabled = SimpleBooleanProperty(defaultIsLoggingEnabled)
        .also { it.addListener { _, _, new -> LogUtils.setLoggingEnabled(new) } }
        .also { LogUtils.setLoggingEnabled(it.value) }
    var isLoggingEnabled: Boolean
        get() = _isLoggingEnabled.value
        set(enabled) = _isLoggingEnabled.set(enabled)
    val loggingEnabledProperty: BooleanProperty = _isLoggingEnabled

    private val _isLoggingToConsoleEnabled = SimpleBooleanProperty(defaultIsLoggingToConsoleEnabled)
        .also { it.addListener { _, _, new -> LogUtils.setLoggingToConsoleEnabled(new) } }
        .also { LogUtils.setLoggingToConsoleEnabled(it.value) }
    var isLoggingToConsoleEnabled: Boolean
        get() = _isLoggingToConsoleEnabled.value
        set(enabled) = _isLoggingToConsoleEnabled.set(enabled)
    val loggingToConsoleEnabledProperty: BooleanProperty = _isLoggingToConsoleEnabled

    private val _isLoggingToFileEnabled = SimpleBooleanProperty(defaultIsLoggingToFileEnabled)
        .also { it.addListener { _, _, new -> LogUtils.setLoggingToFileEnabled(new) } }
        .also { LogUtils.setLoggingToFileEnabled(it.value) }
    var isLoggingToFileEnabled: Boolean
        get() = _isLoggingToFileEnabled.value
        set(enabled) = _isLoggingToFileEnabled.set(enabled)
    val loggingToFileEnabledProperty: BooleanProperty = _isLoggingToFileEnabled

    fun setLogLevelFor(name: String, level: String) = LogUtils.Logback.Levels[level]?.let { setLogLevelFor(name, it) }

    fun unsetLogLevelFor(name: String) = setLogLevelFor(name, null)

    fun setLogLevelFor(name: String, level: Level?) {
        if (name.isRootLoggerName()) {
            if (level === null) {
                // cannot unset root logger level
            } else
                rootLoggerLevel = level
        } else {
            if (level === null) {
                loggerLevels.remove(name)
                LogUtils.Logback.Loggers[name]?.level = null
            } else
                loggerLevels
                    .computeIfAbsent(name) { SimpleObjectProperty<Level>().also { it.addListener { _, _, l -> LogUtils.setLogLevelFor(name, l) } } }
                    .set(level)
        }
    }

    companion object {

        @JvmStatic
        val defaultLogLevel = Level.INFO

        @JvmStatic
        val defaultIsLoggingEnabled = true

        @JvmStatic
        val defaultIsLoggingToConsoleEnabled = true

        @JvmStatic
        val defaultIsLoggingToFileEnabled = true


        fun String.toLogbackLevel(defaultLevel: Level = defaultLogLevel) = Level.toLevel(this, defaultLevel)
    }

    @Plugin(type = PainteraSerialization.PainteraAdapter::class)
    class Serializer : PainteraSerialization.PainteraAdapter<LoggingConfig> {

        private object Keys {
            const val ROOT_LOGGER = "rootLogger"
            const val LEVEL = "level"
            const val LOGGER_LEVELS = "loggerLevels"
            const val IS_ENABLED = "isLoggingEnabled"
            const val IS_LOGGING_TO_CONSOLE_ENABLED = "isLoggingToConsoleEnabled"
            const val IS_LOGGING_TO_FILE_ENABLED = "isLoggingToFileEnabled"
        }

        override fun serialize(
            config: LoggingConfig,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement? {
            val map = JsonObject()
            JsonObject().let { rootLoggerMap ->
                config.rootLoggerLevel.takeUnless { it == defaultLogLevel }?.let { rootLoggerMap.addProperty(Keys.LEVEL, it.levelStr) }
                rootLoggerMap.takeUnless { it.size() == 0 }?.let { map.add(Keys.ROOT_LOGGER, it) }
                config.isLoggingEnabled.takeUnless { it == defaultIsLoggingEnabled }?.let { map.addProperty(Keys.IS_ENABLED, it) }
                config.isLoggingToConsoleEnabled.takeUnless { it == defaultIsLoggingToConsoleEnabled }
                    ?.let { map.addProperty(Keys.IS_LOGGING_TO_CONSOLE_ENABLED, it) }
                config.isLoggingToFileEnabled.takeUnless { it == defaultIsLoggingToFileEnabled }?.let { map.addProperty(Keys.IS_LOGGING_TO_FILE_ENABLED, it) }
            }
            JsonObject().let { loggerLevels ->
                config.unmodifiableLoggerLevels.forEach { (name, level) ->
                    level.value?.let { loggerLevels.addProperty(name, it.levelStr) }
                }
                loggerLevels.takeUnless { it.size() == 0 }?.let { map.add(Keys.LOGGER_LEVELS, it) }
            }
            return map.takeUnless { it.size() == 0 }
        }

        override fun getTargetClass(): Class<LoggingConfig> = LoggingConfig::class.java

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): LoggingConfig {
            val config = LoggingConfig()
            with(GsonExtensions) {
                json.getJsonObject(Keys.ROOT_LOGGER)?.getStringProperty(Keys.LEVEL)?.let { config.rootLoggerLevel = it.toLogbackLevel() }
                json.getJsonObject(Keys.LOGGER_LEVELS)?.entrySet()?.forEach { (name, level) -> config.setLogLevelFor(name, level.asString) }
                json.getBooleanProperty(Keys.IS_ENABLED)?.let { config.isLoggingEnabled = it }
                json.getBooleanProperty(Keys.IS_LOGGING_TO_CONSOLE_ENABLED)?.let { config.isLoggingToConsoleEnabled = it }
                json.getBooleanProperty(Keys.IS_LOGGING_TO_FILE_ENABLED)?.let { config.isLoggingToFileEnabled = it }
            }
            return config
        }

    }

}
