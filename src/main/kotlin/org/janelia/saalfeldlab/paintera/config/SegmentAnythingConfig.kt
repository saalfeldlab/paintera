package org.janelia.saalfeldlab.paintera.config

import com.google.gson.*
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValueBase
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_COMPRESS_ENCODING
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_IMAGE_ENCODING
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_MODEL_LOCATION
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_RESPONSE_TIMEOUT
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.DEFAULT_SERVICE_URL
import org.janelia.saalfeldlab.paintera.config.SegmentAnythingConfig.Companion.ImageEncoding
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.UUID

class SegmentAnythingConfig : ObservableValueBase<SegmentAnythingConfig>() {

	private val serviceUrlProperty = SimpleStringProperty(System.getenv(SAM_SERVICE_HOST_ENV) ?: DEFAULT_SERVICE_URL).apply {
		addListener { _, _, new -> if (new.isBlank()) serviceUrl = DEFAULT_SERVICE_URL }
	}
	var serviceUrl: String by serviceUrlProperty.nonnull()

	private val modelLocationProperty = SimpleStringProperty(DEFAULT_MODEL_LOCATION).apply {
		addListener { _, _, new -> if (new.isBlank()) modelLocation = DEFAULT_MODEL_LOCATION }
	}
	var modelLocation: String by modelLocationProperty.nonnull()

	private val responseTimeoutProperty = SimpleIntegerProperty(DEFAULT_RESPONSE_TIMEOUT).apply {
		addListener { _, _, new -> if (new == null || new.toInt() < -1) responseTimeout = DEFAULT_RESPONSE_TIMEOUT }
	}
	var responseTimeout: Int by responseTimeoutProperty.nonnull()

	private val compressEncodingProperty = SimpleBooleanProperty(DEFAULT_COMPRESS_ENCODING)
	var compressEncoding: Boolean by compressEncodingProperty.nonnull()

	internal val imageEncodingProperty = SimpleObjectProperty<ImageEncoding>(ImageEncoding.JPG)
	var imageEncoding: ImageEncoding by imageEncodingProperty.nonnull()

	internal val allDefault
		get() = serviceUrl == DEFAULT_SERVICE_URL && modelLocation == DEFAULT_MODEL_LOCATION && responseTimeout == DEFAULT_RESPONSE_TIMEOUT

	@Transient
	private val observableInvalidationBinding = serviceUrlProperty.createObservableBinding(modelLocationProperty, responseTimeoutProperty, compressEncodingProperty) {
		UUID.randomUUID()
	}.apply {
		subscribe { _ ->
			this.get() // validate the UUID binding change
			fireValueChangedEvent()
		}
	} //trigger the SegmentAnythingConfig listeners

	override fun getValue() = this

	companion object {
		private const val SAM_SERVICE_HOST_ENV = "SAM_SERVICE_HOST"
		internal const val EMBEDDING_REQUEST_ENDPOINT = "embedded_model"
		internal const val CANCEL_PENDING_ENDPOINT = "cancel_pending"
		internal const val SESSION_ID_REQUEST_ENDPOINT = "new_session_id"
		internal const val COMPRESS_ENCODING_PARAMETER = "encoding=compress"
		internal const val DEFAULT_SERVICE_URL = "https://samservice.janelia.org/"
		internal const val DEFAULT_MODEL_LOCATION = "sam/sam_vit_h_4b8939.onnx"
		internal const val DEFAULT_RESPONSE_TIMEOUT = 10 * 1000

		internal const val DEFAULT_COMPRESS_ENCODING = true
		internal val DEFAULT_IMAGE_ENCODING = ImageEncoding.JPG

		enum class ImageEncoding {
			JPG,
			PNG
		}
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
		addResponseTimeoutConfigRow(2)
		addCompressEncodingConfigRow(3)
		addImageEncodingConfigRow(4)

		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
		columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
	}

	private fun GridPane.addServiceUrlConfigRow(row: Int) {
		Label("Service URL").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val serviceTextField = TextField(config.serviceUrl).apply {
			VBox.setVgrow(this, Priority.NEVER)
			maxWidth = Double.MAX_VALUE
			prefWidth - Double.MAX_VALUE
			onAction = EventHandler {
				if(text.isBlank())
					text = DEFAULT_SERVICE_URL

				config.serviceUrl = text.trim()
			}
			focusedProperty().subscribe { focused ->
				if (!focused)
					onAction.handle(null)
			}
			add(this, 1, row)
		}
		Button().also {
			it.addStyleClass(Style.RESET_ICON)
			it.onAction = EventHandler { serviceTextField.text = DEFAULT_SERVICE_URL }
			add(it, 2, row)
		}
	}

	private fun GridPane.addModelLocationConfigRow(row: Int) {
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
					Platform.runLater { it.positionCaret(0) }
				} else {
					config.modelLocation = new
				}
			}
			add(it, 1, row)
		}
		Button().also {
			it.addStyleClass(Style.RESET_ICON)
			it.onAction = EventHandler { modelTextField.text = DEFAULT_MODEL_LOCATION }
			add(it, 2, row)
		}
	}

	private fun GridPane.addResponseTimeoutConfigRow(row: Int) {
		Label("Response Timeout (ms) ").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = Label.USE_PREF_SIZE
		}
		val responseTimeoutField = NumberField.intField(
			config.responseTimeout,
			{ it >= -1 },
			ObjectField.SubmitOn.FOCUS_LOST,
			ObjectField.SubmitOn.ENTER_PRESSED
		).also { numberField ->
			numberField.valueProperty().addListener { _, _, timeout -> config.responseTimeout = timeout.toInt() }
			numberField.textField.also {
				VBox.setVgrow(it, Priority.NEVER)
				it.maxWidth = Double.MAX_VALUE
				it.prefWidth - Double.MAX_VALUE
				add(it, 1, row)
			}
		}
		Button().also {
			it.addStyleClass(Style.RESET_ICON)
			it.onAction = EventHandler { responseTimeoutField.textField.text = "$DEFAULT_RESPONSE_TIMEOUT" }
			add(it, 2, row)
		}
	}

	private fun GridPane.addCompressEncodingConfigRow(row: Int) {
		Label("Compress Encoding").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = USE_PREF_SIZE
		}
		CheckBox().also {
			it.selectedProperty().addListener { _, _, check ->
				config.compressEncoding = check
			}
			add(it, 1, row, GridPane.REMAINING, 1)
			GridPane.setHalignment(it, HPos.RIGHT)
		}
	}

	private fun GridPane.addImageEncodingConfigRow(row: Int) {
		Label("Image Encoding").also {
			add(it, 0, row)
			it.alignment = Pos.BASELINE_LEFT
			it.minWidth = USE_PREF_SIZE
		}
		ComboBox(FXCollections.observableList(ImageEncoding.entries)).also {
			it.value = config.imageEncoding
			it.valueProperty().bindBidirectional(config.imageEncodingProperty)
			add(it, 1, row, GridPane.REMAINING, 1)
			GridPane.setHalignment(it, HPos.RIGHT)
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
			if (src.responseTimeout != DEFAULT_RESPONSE_TIMEOUT)
				it[src::responseTimeout.name] = src.responseTimeout
			if (src.compressEncoding != DEFAULT_COMPRESS_ENCODING)
				it[src::compressEncoding.name] = src.compressEncoding
			if (src.imageEncoding != DEFAULT_IMAGE_ENCODING)
				it[src::imageEncoding.name] = src.imageEncoding.name.lowercase()
		}
	}

	override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SegmentAnythingConfig {
		return SegmentAnythingConfig().apply {
			json?.let {
				it[::serviceUrl.name, { url: String -> serviceUrl = url }]
				it[::modelLocation.name, { model: String -> modelLocation = model }]
				it[::responseTimeout.name, { timeout: Int -> responseTimeout = timeout }]
				it[::compressEncoding.name, { compress: Boolean -> compressEncoding = compress }]
				it[::imageEncoding.name, { encoding: String -> imageEncoding = ImageEncoding.valueOf(encoding.trim().uppercase()) }]
			}
		}
	}
}