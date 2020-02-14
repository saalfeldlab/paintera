package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
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

    fun setLogLevelFor(name: String, level: String) = LogUtils.LogbackLevels[level]?.let { setLogLevelFor(name, it) }

    fun unsetLogLevelFor(name: String) = setLogLevelFor(name, null)

    fun setLogLevelFor(name: String, level: Level?) {
        if (name.isRootLoggerName()) {
            if (level === null) {
                // cannot unset root logger level
            }
            else
                rootLoggerLevel = level
        } else {
            if (level === null) {
                loggerLevels.remove(name)
                LogUtils.LogbackLoggers[name]?.level = null
            }
            else
                loggerLevels
                    .computeIfAbsent(name) { SimpleObjectProperty<Level>().also { it.addListener { _, _, l -> LogUtils.setLogLevelFor(name, l)  } } }
                    .set(level)
        }
    }

    companion object {

        @JvmStatic
        val defaultLogLevel = Level.INFO

        fun String.toLogbackLevel(defaultLevel: Level = defaultLogLevel) = Level.toLevel(this, defaultLevel)
    }

    @Plugin(type = PainteraSerialization.PainteraAdapter::class)
    class Serializer : PainteraSerialization.PainteraAdapter<LoggingConfig> {

        private object Keys {
            const val ROOT_LOGGER = "rootLogger"
            const val LEVEL = "level"
            const val LOGGER_LEVELS = "loggerLevels"
        }

        override fun serialize(
            config: LoggingConfig,
            typeOfSrc: Type,
            context: JsonSerializationContext): JsonElement? {
            val map = JsonObject()
            JsonObject().let { rootLoggerMap ->
                config.rootLoggerLevel.takeUnless { it == defaultLogLevel }?.let { rootLoggerMap.addProperty(Keys.LEVEL, it.levelStr) }
                rootLoggerMap.takeUnless { it.size() == 0 }?.let { map.add(Keys.ROOT_LOGGER, it) }
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
            context: JsonDeserializationContext): LoggingConfig {
            val config = LoggingConfig()
            with(GsonExtensions) {
                json.getJsonObject(Keys.ROOT_LOGGER)?.getStringProperty(Keys.LEVEL)?.let { config.rootLoggerLevel = it.toLogbackLevel() }
                json.getJsonObject(Keys.LOGGER_LEVELS)?.entrySet()?.forEach { (name, level) -> config.setLogLevelFor(name, level.asString) }
            }
            return config
        }

    }

}
