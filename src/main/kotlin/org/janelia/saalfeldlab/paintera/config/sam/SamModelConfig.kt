package org.janelia.saalfeldlab.paintera.config.sam

import com.google.gson.*
import javafx.application.Platform
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValueBase
import javafx.geometry.HPos
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.controlsfx.control.ToggleSwitch
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import java.lang.reflect.Type
import kotlin.reflect.KMutableProperty0


sealed class SamModelConfig<T>(
    val defaultServiceUrl: String,
    val defaultDecoderLocation: String,
    val defaultResponseTimeout: Int
) : ObservableValueBase<T>() {

    protected val serviceUrlProperty = SimpleStringProperty(defaultServiceUrl)
    var serviceUrl: String by serviceUrlProperty.nonnull()

    protected val decoderLocationProperty = SimpleStringProperty(defaultDecoderLocation)
    var decoderLocation: String by decoderLocationProperty.nonnull()

    protected val responseTimeoutProperty = SimpleIntegerProperty(defaultResponseTimeout)
    var responseTimeout: Int by responseTimeoutProperty.nonnull()


    init {
        serviceUrlProperty.subscribe { _, new ->
            if (new.isBlank())
                serviceUrl = defaultServiceUrl
            fireValueChangedEvent()
        }
        decoderLocationProperty.subscribe { _, new ->
            if (new.isBlank())
                decoderLocation = defaultDecoderLocation
            fireValueChangedEvent()
        }
        responseTimeoutProperty.subscribe { _, new ->
            if (new == null || new.toInt() < -1)
                responseTimeout = defaultResponseTimeout
            fireValueChangedEvent()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SamModelConfig<T>) return false
        return SamModelData(this) == SamModelData(other)
    }


    open fun isDefault(): Boolean {
        return serviceUrl == defaultServiceUrl && decoderLocation == defaultDecoderLocation && responseTimeout == defaultResponseTimeout
    }

    override fun hashCode() = SamModelData(this).hashCode()

    companion object {
        internal const val DEFAULT_RESPONSE_TIMEOUT = 10 * 1000

        internal const val DEFAULT_TRITON_SERVICE = "https://sam-inference.janelia.org"

        /**
         * internal data class for equality and hashcode over a subset of the config fields
         */
        internal data class SamModelData(
            val serviceUrl: String,
            val modelLocation: String,
            val responseTimeout: Int,
        ) {
            constructor(config: SamModelConfig<*>) : this(
                config.serviceUrl,
                config.decoderLocation,
                config.responseTimeout
            )
        }
    }
}

open class SamModelConfigNode(private val config: SamModelConfig<*>) : GridPane() {

    init {
        columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
        columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
        columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.NEVER })
        hgap = 5.0
    }

    protected fun GridPane.addBaseNodeRows(row: Int) {
        addOptionConfigRow(row, "Service URL ", config.defaultServiceUrl, config::serviceUrl)
        addOptionConfigRow(row + 1, "Model Location ", config.defaultDecoderLocation, config::decoderLocation)
        addOptionConfigRow(row + 2, "Timeout (ms) ", config.responseTimeout, config::responseTimeout)
    }


    companion object {

        internal inline fun <reified T> GridPane.addOptionConfigRow(
            row: Int,
            label: String,
            default: T,
            property: KMutableProperty0<T>
        ) {
            addRowLabel(label, row)
            when {
                T::class.java.isEnum ->
                    addPropertyConfigRow(row, property as KMutableProperty0<Enum<*>>, default as Enum<*>)
                T::class == String::class ->
                    addPropertyConfigRow(row, property as KMutableProperty0<String>, default as String)
                T::class == Int::class ->
                    addPropertyConfigRow(row, property as KMutableProperty0<Number>, default as Int)
                T::class == Boolean::class ->
                    addPropertyConfigRow(row, property as KMutableProperty0<Boolean>, default as Boolean)

                else -> throw IllegalArgumentException("Unsupported type for config field: ${T::class.simpleName}")
            }
        }

        internal fun GridPane.addRowLabel(label: String, row: Int) {
            Label(label).also {
                add(it, 0, row)
                it.alignment = Pos.BASELINE_LEFT
                it.minWidth = USE_PREF_SIZE
            }
        }

        internal fun GridPane.addPropertyConfigRow(
            row: Int,
            property: KMutableProperty0<Number>,
            default: Int? = null
        ) {

            val optionField = NumberField.intField(
                property.get().toInt(),
                { it >= -1 },
                ObjectField.SubmitOn.FOCUS_LOST, ObjectField.SubmitOn.ENTER_PRESSED
            )
            optionField.valueProperty().subscribe { _, value -> property.set(value.toInt()) }
            val textField = optionField.textField.apply {
                VBox.setVgrow(this, Priority.NEVER)
                maxWidth = Double.MAX_VALUE
                prefWidth - Double.MAX_VALUE
            }

            add(textField, 1, row)

            default?.let {
                val resetButton = newResetButton {
                    setOnAction {
                        textField.text = "$default"
                        optionField.valueProperty().set(default)
                    }
                }
                add(resetButton, 2, row)
            }
        }

        internal fun newResetButton(apply: Button.() -> Unit): Button {
            return Button().apply {
                addStyleClass(Style.RESET_ICON)
                apply()
            }
        }

        internal fun GridPane.addPropertyConfigRow(
            row: Int,
            property: KMutableProperty0<in Enum<*>>,
            default: Enum<*>?
        ) {
            val initVal = property.get() as Enum<*>
            val enumValues = initVal::class.java.enumConstants
            val comboBox = ComboBox<Enum<*>>().apply {
                items.addAll(enumValues)
                selectionModel.select(initVal)
                selectionModel.selectedItemProperty().subscribe { selection ->
                    property.set(selection)
                }
            }

            add(comboBox, 1, row)
            setHalignment(comboBox, HPos.RIGHT)

            default?.let {
                val resetButton = newResetButton {
                    setOnAction {
                        comboBox.selectionModel.select(default)
                    }
                }
                add(resetButton, 2, row)
            }
        }

        internal fun GridPane.addPropertyConfigRow(
            row: Int,
            property: KMutableProperty0<Boolean>,
            default: Boolean
        ) {

            val optionToggle = ToggleSwitch()
            optionToggle.isSelected = property.get()
            optionToggle.selectedProperty().subscribe { selected ->
                property.set(selected)
            }

            add(optionToggle, 1, row)
            setHalignment(optionToggle, HPos.RIGHT)

            default.let {
                val resetButton = newResetButton {
                    setOnAction {
                        optionToggle.isSelected = default
                    }
                }
                add(resetButton, 2, row)
            }

        }


        internal fun <T : String?> GridPane.addPropertyConfigRow(
            row: Int,
            property: KMutableProperty0<T>,
            default: T? = null
        ) {
            val optionField = TextField(property.get()).apply {
                VBox.setVgrow(this, Priority.NEVER)
                maxWidth = Double.MAX_VALUE
                prefWidth - Double.MAX_VALUE
                setOnAction {
                    default?.let {
                        if (text.isBlank()) {
                            text = default
                            Platform.runLater { positionCaret(0) }
                        }
                    }

                    property.set(text.trim() as T)
                }
                focusedProperty().subscribe { focused ->
                    if (!focused)
                        onAction.handle(null)
                }
            }

            add(optionField, 1, row)
            default?.let {
                val resetButton = newResetButton {
                    setOnAction {
                        optionField.text = default
                        optionField.onAction.handle(null)
                    }
                }
                add(resetButton, 2, row)
            }
        }
    }
}

abstract class SamModelConfigAdapter<T : SamModelConfig<*>> : PainteraSerialization.PainteraAdapter<T> {
    abstract fun newInstance(): T

    override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        if (src.isDefault())
            return JsonNull.INSTANCE

        return JsonObject().also {
            if (src.serviceUrl != src.defaultDecoderLocation)
                it[src::serviceUrl.name] = src.serviceUrl
            if (src.decoderLocation != src.defaultDecoderLocation)
                it[src::decoderLocation.name] = src.decoderLocation
            if (src.responseTimeout != src.defaultResponseTimeout)
                it[src::responseTimeout.name] = src.responseTimeout
        }

    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): T {
        return newInstance().apply {
            json?.let {
                it[::serviceUrl.name, { url: String -> serviceUrl = url }]
                it[::decoderLocation.name, { model: String -> decoderLocation = model }]
                it[::responseTimeout.name, { timeout: Int -> responseTimeout = timeout }]
            }
        }
    }

}