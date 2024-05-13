package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import org.janelia.saalfeldlab.fx.extensions.addTriggeredListener
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils.isRootLoggerName
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class LoggingConfig {

	private val loggerLevels = FXCollections.observableHashMap<String, ObjectProperty<Level>>()
	val unmodifiableLoggerLevels = FXCollections.unmodifiableObservableMap(loggerLevels)!!

	val rootLoggerLevelProperty = SimpleObjectProperty(LogUtils.rootLoggerLevel ?: DEFAULT_LOG_LEVEL)
		.apply { addTriggeredListener { _, _, level -> LogUtils.rootLoggerLevel = level } }
	var rootLoggerLevel: Level by rootLoggerLevelProperty.nonnull()

	val isLoggingEnabledProperty = SimpleBooleanProperty(DEFAULT_IS_LOGGING_ENABLED).apply {
			addTriggeredListener { _, _, new ->
				LogUtils.setLoggingEnabled(new)
				loggerLevels.forEach { (logger, level) -> LogUtils.setLogLevelFor(logger, level.get()) }
			}
		}
	var isLoggingEnabled: Boolean by isLoggingEnabledProperty.nonnull()

	val isLoggingToConsoleEnabledProperty = SimpleBooleanProperty(DEFAULT_IS_LOGGING_TO_CONSOLE_ENABLED).apply {
			addTriggeredListener { _, _, new ->
				LogUtils.setLoggingToConsoleEnabled(new)
				loggerLevels.forEach { (logger, level) -> LogUtils.setLogLevelFor(logger, level.get()) }
			}
		}
	var isLoggingToConsoleEnabled: Boolean by isLoggingEnabledProperty.nonnull()

	val isLoggingToFileEnabledProperty = SimpleBooleanProperty(DEFAULT_IS_LOGGING_TO_FILE_ENABLED).apply {
			addTriggeredListener { _, _, new ->
				LogUtils.setLoggingToFileEnabled(new)
				loggerLevels.forEach { (logger, level) -> LogUtils.setLogLevelFor(logger, level.get()) }
			}
		}
	var isLoggingToFileEnabled: Boolean by isLoggingEnabledProperty.nonnull()

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
		val DEFAULT_LOG_LEVEL: Level = Level.INFO

		const val DEFAULT_IS_LOGGING_ENABLED = true

		const val DEFAULT_IS_LOGGING_TO_CONSOLE_ENABLED = true

		const val DEFAULT_IS_LOGGING_TO_FILE_ENABLED = true


		fun String.toLogbackLevel(defaultLevel: Level = DEFAULT_LOG_LEVEL) = Level.toLevel(this, defaultLevel)
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
		): JsonElement {
			val map = JsonObject()
			map[Keys.ROOT_LOGGER] = JsonObject().also { rootLoggerMap -> rootLoggerMap[Keys.LEVEL] = config.rootLoggerLevel.levelStr }
			map[Keys.IS_ENABLED] = config.isLoggingEnabled
			map[Keys.IS_LOGGING_TO_CONSOLE_ENABLED] = config.isLoggingToConsoleEnabled
			map[Keys.IS_LOGGING_TO_FILE_ENABLED] = config.isLoggingToFileEnabled
			val loggerLevels = JsonObject().also { levels ->
				config.unmodifiableLoggerLevels.forEach { (name, level) ->
					level.value?.let { levels[name] = it.levelStr }
				}
			}
			map[Keys.LOGGER_LEVELS] = loggerLevels
			return map
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
