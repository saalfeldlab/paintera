package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.paintera.control.Navigation
import org.janelia.saalfeldlab.paintera.control.Navigation2
import org.janelia.saalfeldlab.paintera.control.navigation.ButtonRotationSpeedConfig
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class LoggingConfig {

    private val _rootLoggerLevel = SimpleObjectProperty<Level>(LogUtils.rootLoggerLevel ?: defaultLogLevel)
        .also { it.addListener { _, _, level -> LogUtils.rootLoggerLevel = level } }
    var rootLoggerLevel: Level
        get() = _rootLoggerLevel.value
        set(level) = _rootLoggerLevel.set(level)
    fun rootLoggerLevelProperty(): ObjectProperty<Level> = _rootLoggerLevel
    fun setRootLoggerLevel(level: String) {
        rootLoggerLevel = Level.toLevel(level) ?: defaultLogLevel
    }

    fun setTo(that: LoggingConfig) {
        this.rootLoggerLevel = that.rootLoggerLevel
    }

    fun bindBidirectionalTo(that: LoggingConfig) {
        this._rootLoggerLevel.bindBidirectional(that._rootLoggerLevel)
    }

    companion object {

        @JvmStatic
        val defaultLogLevel = Level.INFO

        @JvmStatic
        val logBackLevels = arrayOf(
            Level.ALL,
            Level.TRACE,
            Level.DEBUG,
            Level.INFO,
            Level.WARN,
            Level.ERROR,
            Level.OFF).sortedBy { it.levelInt }.asReversed()

        fun String.toLogbackLevel(defaultLevel: Level = defaultLogLevel) = Level.toLevel(this, defaultLevel)
    }

    @Plugin(type = PainteraSerialization.PainteraAdapter::class)
    class Serializer : PainteraSerialization.PainteraAdapter<LoggingConfig> {

        private object Keys {
            const val ROOT_LOGGER = "rootLogger"
            const val LEVEL = "level"
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
            }
            return config
        }

    }

}
