package org.janelia.saalfeldlab.paintera.config

import com.google.gson.*
import dev.dirs.ProjectDirectories
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig.Companion.APPLICATION_DIRECTORIES
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig.Companion.TEMP_DIRECTORY
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.kordamp.ikonli.fontawesome.FontAwesome
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class PainteraDirectoriesConfig {

	internal val appCacheDirProperty = SimpleStringProperty(APPLICATION_DIRECTORIES.cacheDir).apply {
		addListener { _, _, new -> if (new.isBlank()) appCacheDir = APPLICATION_DIRECTORIES.cacheDir }
	}
	var appCacheDir: String by appCacheDirProperty.nonnull()

	internal val tmpDirProperty = SimpleStringProperty(TEMP_DIRECTORY).apply {
		addListener { _, _, new -> if (new.isBlank()) tmpDir = TEMP_DIRECTORY }
	}
	var tmpDir: String by tmpDirProperty.nonnull()

	internal val appConfigDirProperty = SimpleStringProperty(APPLICATION_DIRECTORIES.configDir).apply {
		subscribe { _, new -> if (new.isBlank()) appConfigDir = APPLICATION_DIRECTORIES.configDir }
	}
	var appConfigDir: String by appConfigDirProperty.nonnull()

	internal val allDefault
		get() = appCacheDir == APPLICATION_DIRECTORIES.cacheDir && tmpDir == TEMP_DIRECTORY

	companion object {
		@JvmField
		internal val APPLICATION_DIRECTORIES = ProjectDirectories.from("org", "janelia", "Paintera")

		@JvmField
		internal val DEFAULT_CACHE_DIR = APPLICATION_DIRECTORIES.cacheDir

		@JvmField
		internal val TEMP_DIRECTORY = System.getProperty("java.io.tmpdir")
	}
}

class PainteraDirectoriesConfigNode(val config: PainteraDirectoriesConfig) : TitledPane() {

	init {
		isExpanded = false
		text = "Application Directories"
		content = createNode()
	}

	private fun createNode() = GridPane().apply {
		addDirectoryConfigRow(
			0, "Cache Directory", config.appCacheDirProperty, APPLICATION_DIRECTORIES.cacheDir,
			"""
			Directory used for storing potentially large, temporary data files used during the application runtime for caching non-persisted data.
			
			By default, the cache directory is shared for all instances of the application, across all projects. 
			""".trimIndent()
		)
		addDirectoryConfigRow(
			1, "Temp Directory", config.tmpDirProperty, TEMP_DIRECTORY,
			"""
			Directory used for storing temporary non-data files used during the application runtime.
			
			By default, the temp directory is project-specific, and will change if a new project is loaded."""
			.trimIndent()
		)
		addDirectoryConfigRow(
			2, "Config Directory", config.appConfigDirProperty, APPLICATION_DIRECTORIES.configDir,
			"""
			Directory used to store application wide configuration.
			
			By default, the config directory is shared for all instances of the application, across all projects, per user. 
			""".trimIndent()
		)

		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
	}

	private fun GridPane.addDirectoryConfigRow(
		row: Int,
		label: String,
		directoryProperty: SimpleStringProperty,
		defaultValue: String,
		helpText: String
	) {
		Label(label).also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val textField = TextField(directoryProperty.get()).also {
			VBox.setVgrow(it, Priority.NEVER)
			it.maxWidth = Double.MAX_VALUE
			it.prefWidth - Double.MAX_VALUE
			it.textProperty().addListener { _, _, new ->
				if (new.isBlank()) {
					it.text = defaultValue
					Platform.runLater { it.positionCaret(0) }
				} else {
					directoryProperty.value = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.addStyleClass(Style.RESET_ICON)
			it.onAction = EventHandler { textField.text = defaultValue }
			add(it, 2, row)
		}
		Button().also {
			addStyleClass(Style.fontAwesome(FontAwesome.QUESTION))
			it.onAction = EventHandler {
				PainteraAlerts.information("Ok", true).also { alert ->
					alert.title = label
					alert.headerText = alert.title
					alert.dialogPane.content = TextArea().also { area ->
						area.isWrapText = true
						area.isEditable = false
						area.text = helpText
					}
				}.showAndWait()
			}
			add(it, 3, row)
		}
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class PainteraDirectoriesConfigSerializer : PainteraSerialization.PainteraAdapter<PainteraDirectoriesConfig> {

	override fun getTargetClass() = PainteraDirectoriesConfig::class.java

	override fun serialize(src: PainteraDirectoriesConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
		return if (src.allDefault) JsonNull.INSTANCE
		else JsonObject().also {
			if (src.appCacheDir != APPLICATION_DIRECTORIES.cacheDir)
				it[src::appCacheDir.name] = src.appCacheDir
			if (src.tmpDir != TEMP_DIRECTORY)
				it[src::tmpDir.name] = src.tmpDir
		}
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): PainteraDirectoriesConfig {
		return PainteraDirectoriesConfig().apply {
			json?.let {
				it[::appCacheDir.name, { model: String -> appCacheDir = model }]
				it[::tmpDir.name, { model: String -> tmpDir = model }]
			}
		}
	}
}