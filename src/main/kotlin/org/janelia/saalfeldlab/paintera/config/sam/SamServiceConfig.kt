package org.janelia.saalfeldlab.paintera.config.sam

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.Separator
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.ai.ImageEncoderCache
import org.janelia.saalfeldlab.paintera.ai.sam.sam1.Sam1EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.ai.sam.sam2.Sam2EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.ai.sam.sam3.Sam3EncodingLoaderCache
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class SamServiceConfig(
    initSam1: Sam1Config? = Sam1Config(),
    initSam2: Sam2Config? = Sam2Config(),
    initSam3: Sam3Config? = Sam3Config(),
) {

    val sam1ConfigProperty = SimpleObjectProperty(initSam1 ?: Sam1Config())
    var sam1Config: Sam1Config by sam1ConfigProperty.nonnull()

    val sam2ConfigProperty = SimpleObjectProperty(initSam2 ?: Sam2Config())
    var sam2Config: Sam2Config by sam2ConfigProperty.nonnull()

    val sam3ConfigProperty = SimpleObjectProperty(initSam3 ?: Sam3Config())
    var sam3Config: Sam3Config by sam3ConfigProperty.nonnull()

    val currentSamConfigProperty = SimpleObjectProperty<SamModelConfig<*>>(sam2Config)
    var currentSamConfig: SamModelConfig<*> by currentSamConfigProperty.nonnull()

    init {
        currentSamConfigProperty.subscribe { model ->
            Paintera.ifPaintable {
                ImageEncoderCache.close()
                LOG.trace { "Closing ${ImageEncoderCache::class.simpleName} Image Encoder Cache" }
                ImageEncoderCache = when (model) {
                    is Sam1Config -> Sam1EncodingLoaderCache()
                    is Sam2Config -> Sam2EncodingLoaderCache()
                    is Sam3Config -> Sam3EncodingLoaderCache()
                }
                LOG.info { "Switched to ${ImageEncoderCache::class.simpleName} for Sam Service" }
            }
        }
    }
}

private val LOG = KotlinLogging.logger {  }

class SamServiceConfigNode(config: SamServiceConfig) : TitledPane() {

    init {
        isExpanded = false
        text = "SAM Service"
        val borderPane = BorderPane().apply {
            top = createHeaderNode(config)
        }
        content = borderPane
        config.currentSamConfigProperty.subscribe { modelConfig ->
            borderPane.center = modelConfig?.createConfigNode()
        }
    }

    private fun createHeaderNode(config: SamServiceConfig): GridPane = GridPane().apply {
        var row = 0
        addHeaderLabel(row++)
        addModelSelectionNode(row++, config)
        addHeaderSeparator(row)
    }

    private fun GridPane.addHeaderSeparator(row: Int){
        val separator = Separator(Orientation.HORIZONTAL)
        add(separator, 0, row, GridPane.REMAINING, 1)
        GridPane.setMargin(separator, Insets(5.0, 0.0, 5.0, 0.0))
        GridPane.setHgrow(separator, Priority.ALWAYS)
    }

    private fun GridPane.addHeaderLabel(row: Int) {
        val headerLabel = Label("Select SAM Version:")
        add(headerLabel, 0, row, GridPane.REMAINING, 1)
        GridPane.setMargin(headerLabel, Insets(0.0, 0.0, 5.0, 0.0))
        GridPane.setHalignment(headerLabel, HPos.LEFT)
        GridPane.setHgrow(headerLabel, Priority.ALWAYS)
    }

    private fun GridPane.addModelSelectionNode(row: Int, config: SamServiceConfig) {
        val modelToggleGroup = ToggleGroup()
        val options: Map<String, () -> SamModelConfig<*>> = mapOf(
            "SAM 1" to config::sam1Config,
            "SAM 2" to config::sam2Config,
            "SAM 3" to config::sam3Config
        )
        columnConstraints.setAll(
            *Array(options.size) {
                ColumnConstraints().apply {
                    halignment = HPos.CENTER
                    hgrow = Priority.ALWAYS
                }
            }
        )
        options.entries.forEachIndexed { col, (name, getConfig) ->
            val button = RadioButton(name).apply {
                toggleGroup = modelToggleGroup
                setOnAction {
                    config.currentSamConfig = getConfig()
                }
                if (getConfig() == config.currentSamConfig) {
                    isSelected = true
                }
            }
            add(button, col, row)
        }
    }

    companion object {
        private fun SamModelConfig<*>.createConfigNode(): Node {
            return when (this) {
                is Sam1Config -> Sam1ConfigNode(this)
                is Sam2Config -> Sam2ConfigNode(this)
                is Sam3Config -> Sam3ConfigNode(this)
            }
        }
    }
}

@Plugin(type = PainteraSerialization.PainteraAdapter::class)
class SamServiceAdapter : PainteraSerialization.PainteraAdapter<SamServiceConfig> {

    companion object {
        private const val SAM1 = "SAM1"
        private const val SAM2 = "SAM2"
        private const val SAM3 = "SAM3"
    }

    override fun getTargetClass() = SamServiceConfig::class.java

    override fun serialize(src: SamServiceConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonObject().also {
            it["selectedModel"] = when (src.currentSamConfig) {
                is Sam1Config -> SAM1
                is Sam2Config -> SAM2
                is Sam3Config -> SAM3
            }
            context.serialize(src.sam1Config).takeUnless { json -> json.isJsonNull }?.let { json ->
                it["SAM1"] = json
            }
            context.serialize(src.sam2Config).takeUnless { json -> json.isJsonNull }?.let { json ->
                it["SAM2"] = json
            }
            context.serialize(src.sam3Config).takeUnless { json -> json.isJsonNull }?.let { json ->
                it["SAM3"] = json
            }
        }
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): SamServiceConfig {

        json ?: return SamServiceConfig()

        val sam1: Sam1Config? = context[json, SAM1]
        val sam2: Sam2Config? = context[json,SAM2]
        val sam3: Sam3Config? = context[json,SAM3]

        return SamServiceConfig(sam1, sam2, sam3)
    }

}

