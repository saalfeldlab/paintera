package org.janelia.saalfeldlab.paintera.state.raw

import bdv.viewer.SourceAndConverter
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import javafx.scene.paint.Color
import net.imglib2.RandomAccessibleInterval
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import net.imglib2.view.composite.RealComposite
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.ChannelCompositeSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.get
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend
import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.util.Colors

private typealias CompositeConverter<T> = ARGBCompositeColorConverter<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>

/**
 * The channel axis of a raw source rendered as a composite of its active channels, with the color, range and opacity of
 * every dataset channel. Only a source with more than one channel has one; see [of]
 */
class ChannelComposition<D, T> private constructor(
    val metadataState: MetadataState,
    private val source: N5DataSource<*, *>,
    val axis: Int,
    plainConverter: ARGBColorConverter<T>
) where D : RealType<D>, T : AbstractVolatileRealType<D, T> {

    val numChannels: Int = metadataState.datasetAttributes.dimensions[axis].toInt()

    /* in composite order */
    private val _activeChannels = SimpleObjectProperty(listOf(0))
    val activeChannelsProperty: ObjectProperty<List<Int>>
        get() = _activeChannels
    var activeChannels: List<Int>
        get() = _activeChannels.value
        set(value) = _activeChannels.set(value.filter { it in 0 until numChannels }.distinct())

    /* every channel shares the range of channel 0 */
    val globalRangeProperty: BooleanProperty = SimpleBooleanProperty(false)
    var globalRange: Boolean
        get() = globalRangeProperty.get()
        set(value) = globalRangeProperty.set(value)

    /** Color, range and opacity per dataset channel; the converter the viewer renders with is bound to it over the active channels */
    var converter: CompositeConverter<T> = defaultConverter(numChannels, plainConverter.min, plainConverter.max)
        private set

    private lateinit var compositeSource: ChannelCompositeSource<D, T>
    private lateinit var renderConverter: CompositeConverter<T>
    private var addedTo: PainteraBaseView? = null

    init {
        rebuild(activeChannels)
        _activeChannels.subscribe { _, channels -> rebuild(channels) }
    }

    @Suppress("UNCHECKED_CAST")
    val sourceAndConverter: SourceAndConverter<T>
        get() = SourceAndConverter(compositeSource, renderConverter) as SourceAndConverter<T>

    val defaultRange: Pair<Double, Double>
        get() = metadataState.minIntensity to metadataState.maxIntensity

    fun isActive(channel: Int) = channel in activeChannels

    fun sourceTransform(level: Int): AffineTransform3D = source.getSourceTransformCopy(0, level)

    /** The view at [level] and sliced at [channel] */
    @Suppress("UNCHECKED_CAST")
    fun dataSourceView(channel: Int, level: Int): RandomAccessibleInterval<D> =
        source.getDataSource(0, level, channelMapping(channel)) as RandomAccessibleInterval<D>

    /** The volatile view at [level] and sliced at [channel] */
    @Suppress("UNCHECKED_CAST")
    fun sourceView(channel: Int, level: Int): RandomAccessibleInterval<T> =
        source.getSource(0, level, channelMapping(channel)) as RandomAccessibleInterval<T>

    private fun channelMapping(channel: Int) = metadataState.xyzView.spatialMapping().withSlicePosition(axis, channel.toLong())

    fun onAdd(paintera: PainteraBaseView) {
        addedTo = paintera
        converter.repaintOnChange(paintera)
        rebuild(activeChannels)
    }

    fun preferencePaneNode(): Node = ChannelCompositeNode(this)

    private fun rebuild(channels: List<Int>) {
        if (::renderConverter.isInitialized)
            renderConverter.unbindAll()
        if (channels.isEmpty()) {
            compositeSource = ChannelCompositeSource(source, axis, longArrayOf(0))
            renderConverter = ARGBCompositeColorConverter.InvertingImp0<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>(1).also { it.channelAlphaProperty(0).set(0.0) }
        } else {
            compositeSource = ChannelCompositeSource(source, axis, channels.map { it.toLong() }.toLongArray())
            renderConverter = converter.boundOver(channels)
        }
        addedTo?.let {
            it.sourceInfo().refreshVisibleSourcesAndConverters()
            it.orthogonalViews().requestRepaint()
        }
    }

    /* the render converter's channel `i` shows the settings of dataset channel `channels[i]` */
    private fun CompositeConverter<T>.boundOver(channels: List<Int>) =
        ARGBCompositeColorConverter.InvertingImp0<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>(channels.size).also { render ->
            render.setChannelIndices(channels.toIntArray())
            render.alphaProperty().bind(alphaProperty())
            channels.forEachIndexed { idx, channel ->
                render.colorProperty(idx).bind(colorProperty(channel))
                render.minProperty(idx).bind(minProperty(channel))
                render.maxProperty(idx).bind(maxProperty(channel))
                render.channelAlphaProperty(idx).bind(channelAlphaProperty(channel))
            }
        }

    private fun ARGBCompositeColorConverter<*, *, *>.unbindAll() {
        alphaProperty().unbind()
        for (channel in 0 until numChannels()) {
            colorProperty(channel).unbind()
            minProperty(channel).unbind()
            maxProperty(channel).unbind()
            channelAlphaProperty(channel).unbind()
        }
    }

    private fun ARGBCompositeColorConverter<*, *, *>.repaintOnChange(paintera: PainteraBaseView) {
        for (channel in 0 until numChannels()) {
            listOf(colorProperty(channel), minProperty(channel), maxProperty(channel), channelAlphaProperty(channel)).forEach { property ->
                property.subscribe { _, _ -> paintera.orthogonalViews().requestRepaint() }
            }
        }
        alphaProperty().subscribe { _, _ -> paintera.orthogonalViews().requestRepaint() }
    }

    /** Take over [saved]; a converter sized to a selection rather than the dataset is spread over the active channels */
    fun restoreConverter(saved: CompositeConverter<T>) {
        converter =
            if (saved.numChannels() == numChannels) saved else defaultConverter<D, T>(numChannels, saved.minProperty(0).get(), saved.maxProperty(0).get()).also { settings ->
                settings.alphaProperty().set(saved.alphaProperty().get())
                activeChannels.forEachIndexed { idx, channel ->
                    if (idx < saved.numChannels()) {
                        settings.colorProperty(channel).set(saved.colorProperty(idx).get())
                        settings.minProperty(channel).set(saved.minProperty(idx).get())
                        settings.maxProperty(channel).set(saved.maxProperty(idx).get())
                        settings.channelAlphaProperty(channel).set(saved.channelAlphaProperty(idx).get())
                    }
                }
            }
        addedTo?.let { converter.repaintOnChange(it) }
        rebuild(activeChannels)
    }

    fun toJson(context: JsonSerializationContext): JsonObject = JsonObject().apply {
        addProperty(AXIS_KEY, axis)
        add(CHANNELS_KEY, JsonArray().also { array -> activeChannels.forEach { array.add(it) } })
        addProperty(GLOBAL_RANGE_KEY, globalRange)
        add(CONVERTER_KEY, context.withClassInfo(converter))
    }

    fun fromJson(json: JsonObject, context: JsonDeserializationContext) {
        json.get<Int>(AXIS_KEY)?.takeIf { it != axis }?.let { LOG.warn { "saved channel axis $it differs from the dataset's channel axis $axis; using $axis" } }
        json.getAsJsonArray(CHANNELS_KEY)?.let { activeChannels = it.map { channel -> channel.asInt } }
        json.get<Boolean>(GLOBAL_RANGE_KEY) { globalRange = it }
        context.fromClassInfo<CompositeConverter<T>>(json, CONVERTER_KEY) { restoreConverter(it) }
    }

    companion object {
        private val LOG = KotlinLogging.logger { }

        const val AXIS_KEY = "axis"
        const val CHANNELS_KEY = "channels"
        const val GLOBAL_RANGE_KEY = "globalRange"
        const val CONVERTER_KEY = "converter"

        /** The composition of an N5 [backend]'s channel axis, or null when it has none or a single channel */
        fun <D, T> of(backend: SourceStateBackend<D, T>, source: DataSource<D, T>, plainConverter: ARGBColorConverter<T>): ChannelComposition<D, T>?
                where D : RealType<D>, T : AbstractVolatileRealType<D, T> {
            val metadataState = (backend as? SourceStateBackendN5<*, *>)?.metadataState ?: return null
            val n5Source = source as? N5DataSource<*, *> ?: return null
            val axis = channelAxis(metadataState) ?: return null
            return ChannelComposition(metadataState, n5Source, axis, plainConverter)
        }

        /** The channel axis of [metadataState] when it has more than one channel */
        @JvmStatic
        fun channelAxis(metadataState: MetadataState): Int? =
            metadataState.xyzView.nonSpatialAxes.firstOrNull { metadataState.axes[it].type == Axis.CHANNEL }
                ?.takeIf { metadataState.datasetAttributes.dimensions[it] > 1 }

        /** The settings for [numChannels] dataset channels, colored [ChannelColors.WhiteCMY] */
        fun <D, T> defaultConverter(
            numChannels: Int,
            min: Double,
            max: Double
        ): ARGBCompositeColorConverter<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>
                where D : RealType<D>, T : AbstractVolatileRealType<D, T> {
            val converter = ARGBCompositeColorConverter.InvertingImp0<T, RealComposite<T>, VolatileWithSet<RealComposite<T>>>(numChannels, min, max)
            ChannelColors.WhiteCMY.applyTo(converter, (0 until numChannels).toList())
            return converter
        }
    }
}

/**
 * The [leading] colors for the first channels. the rest spread around the hue circle by the golden angle from the last
 * hue, so neighboring channels stay far apart and a channel's color does not depend on how many there are
 */
sealed class ChannelColors(private val leading: List<Color>) {

    fun color(idx: Int): Color {
        leading.getOrNull(idx)?.let { return it }
        val first = leading.first()
        val saturation = first.saturation.takeIf { it > 0.0 } ?: 1.0
        val brightness = first.brightness.takeIf { it > 0.0 } ?: 1.0
        val firstHue = leading.lastOrNull { it.saturation > 0.0 }?.hue ?: 0.0
        val idxHue = goldenAngleSpreadHue(firstHue, idx)
        return Color.hsb(idxHue, saturation, brightness)
    }

    private fun goldenAngleSpreadHue(firstHue: Double, idx: Int) = (firstHue + GOLDEN_ANGLE * (idx - leading.size + 1)) % 360.0

    /** Color [channels] of [converter] in their order */
    fun applyTo(converter: ARGBCompositeColorConverter<*, *, *>, channels: List<Int>) {
        channels.forEachIndexed { idx, channel -> converter.colorProperty(channel).set(Colors.toARGBType(color(idx))) }
    }

    class Spread(start: Color) : ChannelColors(listOf(start))
    object CMY : ChannelColors(listOf(Color.CYAN, Color.MAGENTA, Color.YELLOW))
    object WhiteCMY : ChannelColors(listOf(Color.WHITE, Color.CYAN, Color.MAGENTA, Color.YELLOW))

    companion object {
        const val GOLDEN_ANGLE = 137.50776405003785
    }
}
