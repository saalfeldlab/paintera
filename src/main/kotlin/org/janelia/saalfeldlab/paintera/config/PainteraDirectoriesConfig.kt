package org.janelia.saalfeldlab.paintera.config

import com.google.gson.*
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
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
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig.Companion.APPLICATION_DIRECTORIES
import org.janelia.saalfeldlab.paintera.config.PainteraDirectoriesConfig.Companion.TEMP_DIRECTORY
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class PainteraDirectoriesConfig {

	private val appCacheDirProperty = SimpleStringProperty(APPLICATION_DIRECTORIES.cacheDir).apply {
		addListener { _, _, new -> if (new.isBlank()) appCacheDir = APPLICATION_DIRECTORIES.cacheDir }
	}
	var appCacheDir: String by appCacheDirProperty.nonnull()

	private val tmpDirProperty = SimpleStringProperty(TEMP_DIRECTORY).apply {
		addListener { _, _, new -> if (new.isBlank()) tmpDir = TEMP_DIRECTORY }
	}
	var tmpDir: String by tmpDirProperty.nonnull()

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
		addCacheDirectoryConfigRow(0)
		addTempDirectoryConfigRow(1)

		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
	}

	private fun GridPane.addCacheDirectoryConfigRow(row: Int) {
		Label("Cache Directory").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val cacheTextField = TextField(config.appCacheDir).also {
			VBox.setVgrow(it, Priority.NEVER)
			it.maxWidth = Double.MAX_VALUE
			it.prefWidth - Double.MAX_VALUE
			it.textProperty().addListener { _, _, new ->
				if (new.isBlank()) {
					it.text = APPLICATION_DIRECTORIES.cacheDir
					Platform.runLater { it.positionCaret(0) }
				} else {
					config.appCacheDir = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.UNDO]
			it.onAction = EventHandler { cacheTextField.text = APPLICATION_DIRECTORIES.cacheDir }
			add(it, 2, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.QUESTION]
			it.onAction = EventHandler {
				PainteraAlerts.information("Ok", true).also { alert ->
					alert.title = "Cache Directory"
					alert.headerText = alert.title
					alert.dialogPane.content = TextArea().also { area ->
						area.isWrapText = true
						area.isEditable = false
						area.text = """
						Directory used for storing potentially large, temporary data files used during the application runtime for caching non-persisted data.
						
						
						By default, the cache directory is shared for all instances of the application, across all projects. 
					""".trimIndent()
					}
				}.showAndWait()
			}
			add(it, 3, row)
		}
	}

	private fun GridPane.addTempDirectoryConfigRow(row: Int) {
		Label("Temp Directory").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val tempDirTextField = TextField(config.tmpDir).also {
			VBox.setVgrow(it, Priority.NEVER)
			it.maxWidth = Double.MAX_VALUE
			it.prefWidth - Double.MAX_VALUE
			it.textProperty().addListener { _, _, new ->
				if (new.isBlank()) {
					it.text = TEMP_DIRECTORY
					Platform.runLater { it.positionCaret(0) }
				} else {
					config.tmpDir = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.UNDO]
			it.onAction = EventHandler { tempDirTextField.text = TEMP_DIRECTORY }
			add(it, 2, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.QUESTION]
			it.onAction = EventHandler {
				PainteraAlerts.information("Ok", true).also { alert ->
					alert.title = "Temp Directory"
					alert.headerText = alert.title
					alert.dialogPane.content = TextArea().also { area ->
						area.isEditable = false
						area.isWrapText = true
						area.text = """
						Directory used for storing temporary non-data files used during the application runtime.
						
						
						By default, the temp directory is project-specific, and will change if a new project is loaded. 
					""".trimIndent()
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