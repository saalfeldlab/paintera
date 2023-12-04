package org.janelia.saalfeldlab.paintera.config

import com.google.gson.*
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_MODEL_LOCATION
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_SERVICE_URL
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class SegmentAnythingConfig {

	private val serviceUrlProperty = SimpleStringProperty(System.getenv(SAM_SERVICE_HOST_ENV) ?: DEFAULT_SERVICE_URL).apply {
		addListener { _, _, new -> if (new.isBlank()) serviceUrl = DEFAULT_SERVICE_URL }
	}
	var serviceUrl: String by serviceUrlProperty.nonnull()

	private val modelLocationProperty = SimpleStringProperty(DEFAULT_MODEL_LOCATION).apply {
		addListener { _, _, new -> if (new.isBlank()) modelLocation = DEFAULT_MODEL_LOCATION }
	}
	var modelLocation: String by modelLocationProperty.nonnull()

	internal val allDefault
		get() = serviceUrl == DEFAULT_SERVICE_URL && modelLocation == DEFAULT_MODEL_LOCATION

	companion object {
		private const val SAM_SERVICE_HOST_ENV = "SAM_SERVICE_HOST"
		internal const val DEFAULT_SERVICE_URL =  "https://samservice.janelia.org/embedded_model?encoding=compress"
		internal const val DEFAULT_MODEL_LOCATION = "sam/sam_vit_h_4b8939.onnx"
	}
}

class SegmentAnythingConfigNode(val config: SegmentAnythingConfig) : TitledPane() {

	init {
		isExpanded = false
		text = "SAM Service"
		content = createNode()
	}

	private fun createNode() = GridPane().apply {
		addServiceUrlConfigRow(0)
		addModelLocationConfigRow(1)

		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
	}

	private fun GridPane.addServiceUrlConfigRow(row : Int) {
		Label("Service URL").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val serviceTextField = TextField(config.serviceUrl).also {
			VBox.setVgrow(it, Priority.NEVER)
			it.maxWidth = Double.MAX_VALUE
			it.prefWidth - Double.MAX_VALUE
			it.textProperty().addListener { _, _, new ->
				if (new.isBlank()) {
					it.text = DEFAULT_SERVICE_URL
					Platform.runLater {  it.positionCaret(0) }
				} else {
					config.serviceUrl = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.UNDO]
			it.onAction = EventHandler { serviceTextField.text = DEFAULT_SERVICE_URL }
			add(it, 2, row)
		}
	}

	private fun GridPane.addModelLocationConfigRow(row : Int) {
		Label("Model Location").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val modelTextField = TextField(config.modelLocation).also {
			VBox.setVgrow(it, Priority.NEVER)
			it.maxWidth = Double.MAX_VALUE
			it.prefWidth - Double.MAX_VALUE
			it.textProperty().addListener { _, _, new ->
				if (new.isBlank()) {
					it.text = DEFAULT_MODEL_LOCATION
					Platform.runLater {  it.positionCaret(0) }
				} else {
					config.modelLocation = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.graphic = FontAwesome[FontAwesomeIcon.UNDO]
			it.onAction = EventHandler { modelTextField.text = DEFAULT_MODEL_LOCATION }
			add(it, 2, row)
		}
	}
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class SamServiceConfigSerializer : PainteraSerialization.PainteraAdapter<SegmentAnythingConfig> {

	override fun getTargetClass() = SegmentAnythingConfig::class.java

	override fun serialize(src: SegmentAnythingConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
		return if (src.allDefault) JsonNull.INSTANCE
		else JsonObject().also {
			if (src.serviceUrl != DEFAULT_SERVICE_URL)
				it[src::serviceUrl.name] = src.serviceUrl
			if (src.modelLocation != DEFAULT_MODEL_LOCATION)
				it[src::modelLocation.name] = src.modelLocation
		}
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SegmentAnythingConfig {
		return SegmentAnythingConfig().apply {
			json?.let {
				it[::serviceUrl.name, { model: String -> serviceUrl = model }]
				it[::modelLocation.name, { model: String -> modelLocation = model }]
			}
		}
	}
}