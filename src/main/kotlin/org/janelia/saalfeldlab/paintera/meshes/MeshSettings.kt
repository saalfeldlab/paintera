package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import kotlin.math.max
import kotlin.math.min

class MeshSettings @JvmOverloads constructor(
    val numScaleLevels: Int,
    val defaults: Defaults = Defaults.IMMUTABLE_INSTANCE) {

    interface Defaults {
        val simplificationIterations: Int
        val smoothingIterations: Int
        val smoothingLambda: Double
        val opacity: Double
        val drawMode: DrawMode
        val cullFace: CullFace
        val inflate: Double
        val isVisible: Boolean
        val minLabelRatio: Double
        val levelOfDetail: Int


        object Values {
            @JvmStatic val simplificationIterations = 0
            @JvmStatic val smoothingIterations = Smooth.DEFAULT_ITERATIONS
            @JvmStatic val smoothingLambda = Smooth.DEFAULT_LAMBDA
            @JvmStatic val opacity = 1.0
            @JvmStatic val drawMode = DrawMode.FILL
            @JvmStatic val cullFace = CullFace.FRONT
            @JvmStatic val inflate = 1.0
            @JvmStatic val isVisible = true
            @JvmStatic val minLabelRatio = 0.25
            @JvmStatic val minLevelOfDetail = 1
            @JvmStatic val maxLevelOfDetail = 10
            @JvmStatic val levelOfDetail = (minLevelOfDetail + maxLevelOfDetail) / 2
        }

        companion object {
            @JvmStatic
            val IMMUTABLE_INSTANCE = MutableDefaults().asImmutable

            @JvmStatic fun getDefaultCoarsestScaleLevel(numScaleLevels: Int) = numScaleLevels - 1
            @JvmStatic fun getDefaultFinestScaleLevel(numScaleLevels: Int) = numScaleLevels / 2
        }
    }

    class MutableDefaults(): Defaults {
        override var simplificationIterations: Int = Defaults.Values.simplificationIterations
        override var smoothingIterations: Int = Defaults.Values.smoothingIterations
        override var smoothingLambda: Double = Defaults.Values.smoothingLambda
        override var opacity: Double = Defaults.Values.opacity
        override var drawMode: DrawMode = Defaults.Values.drawMode
        override var cullFace: CullFace = Defaults.Values.cullFace
        override var inflate: Double = Defaults.Values.inflate
        override var isVisible: Boolean = Defaults.Values.isVisible
        override var minLabelRatio: Double = Defaults.Values.minLabelRatio
        override var levelOfDetail: Int = Defaults.Values.levelOfDetail

        val asImmutable: Defaults
            get() = ImmutableDefaults(this)
    }

    class ImmutableDefaults(private val delegate: Defaults): Defaults by delegate

    // TODO should scaleLevel actually be part of the MeshSettings?
    private val _coarsestScaleLevel = SimpleIntegerProperty(Defaults.getDefaultCoarsestScaleLevel(numScaleLevels))
    private val _finestScaleLevel = SimpleIntegerProperty(Defaults.getDefaultFinestScaleLevel(numScaleLevels))
    private val _simplificationIterations = SimpleIntegerProperty(defaults.simplificationIterations)
    private val _smoothingLambda: DoubleProperty = SimpleDoubleProperty(defaults.smoothingLambda)
    private val _smoothingIterations: IntegerProperty = SimpleIntegerProperty(defaults.smoothingIterations)
    private val _opacity: DoubleProperty = SimpleDoubleProperty(defaults.opacity)
    private val _drawMode: ObjectProperty<DrawMode> = SimpleObjectProperty(defaults.drawMode)
    private val _cullFace: ObjectProperty<CullFace> = SimpleObjectProperty(defaults.cullFace)
    private val _inflate: DoubleProperty = SimpleDoubleProperty(defaults.inflate)
    private val _isVisible: BooleanProperty = SimpleBooleanProperty(defaults.isVisible)
    private val _minLabelRatio: DoubleProperty = SimpleDoubleProperty(defaults.minLabelRatio)
    private val _levelOfDetail: IntegerProperty = SimpleIntegerProperty(defaults.levelOfDetail)

    fun coarsestScaleLevelProperty(): IntegerProperty = _coarsestScaleLevel
    fun finestScaleLevelProperty(): IntegerProperty = _finestScaleLevel
    fun simplificationIterationsProperty(): IntegerProperty = _simplificationIterations
    fun smoothingLambdaProperty(): DoubleProperty = _smoothingLambda
    fun smoothingIterationsProperty(): IntegerProperty = _smoothingIterations
    fun opacityProperty(): DoubleProperty = _opacity
    fun drawModeProperty(): ObjectProperty<DrawMode> = _drawMode
    fun cullFaceProperty(): ObjectProperty<CullFace> = _cullFace
    fun inflateProperty(): DoubleProperty = _inflate
    fun visibleProperty(): BooleanProperty= _isVisible
    fun minLabelRatioProperty(): DoubleProperty = _minLabelRatio
    fun levelOfDetailProperty(): IntegerProperty = _levelOfDetail

    var coarsetsScaleLevel: Int
        get() = _coarsestScaleLevel.value
        set(level) = _coarsestScaleLevel.set(level)
    var finestScaleLevel: Int
        get() = _finestScaleLevel.value
        set(level) = _finestScaleLevel.set(level)
    var simplificationIterations: Int
        get() = _simplificationIterations.value
        set(iterations) = _simplificationIterations.set(iterations)
    var smoothingLambda: Double
        get() = _smoothingLambda.value
        set(lambda) = _smoothingLambda.set(lambda)
    var smoothingIterations: Int
        get() = _smoothingIterations.value
        set(iterations) = _smoothingIterations.set(iterations)
    var opacity: Double
        get() = _opacity.value
        set(opacity) = _opacity.set(opacity)
    var drawMode: DrawMode
        get() = _drawMode.value
        set(mode) = _drawMode.set(mode)
    var cullFace: CullFace
        get() = _cullFace.value
        set(cullFace) = _cullFace.set(cullFace)
    var inflate: Double
        get() = _inflate.value
        set(inflate) = _inflate.set(inflate)
    var isVisible: Boolean
        get() = _isVisible.value
        set(isVisible) = _isVisible.set(isVisible)
    var minLabelRatio: Double
        get() = _minLabelRatio.value
        set(ratio) = _minLabelRatio.set(ratio)
    var levelOfDetail: Int
        get() = _levelOfDetail.value
        set(level) = _levelOfDetail.set(level)

    init {
        _levelOfDetail.addListener { _, _, new ->
            // TODO can we do this without the bound check?
            if (!levelOfDetailProperty().isBound)
                levelOfDetail = min(Defaults.Values.maxLevelOfDetail, max(Defaults.Values.minLevelOfDetail, new.toInt()))
        }
    }

    @JvmOverloads
    fun copy(defaults: Defaults = this.defaults): MeshSettings {
        val that = MeshSettings(numScaleLevels, defaults)
        that.setTo(this)
        return that
    }

    fun setTo(that: MeshSettings) {
        levelOfDetail = that.levelOfDetail
        coarsetsScaleLevel = that.coarsetsScaleLevel
        finestScaleLevel = that.finestScaleLevel
        simplificationIterations = that.simplificationIterations
        smoothingLambda = that.smoothingLambda
        smoothingIterations = that.smoothingIterations
        opacity = that.opacity
        drawMode = that.drawMode
        cullFace = that.cullFace
        inflate = that.inflate
        isVisible = that.isVisible
    }

    fun resetToDefaults() = setTo(defaults)

    private fun setTo(defaults: Defaults): MeshSettings {
        levelOfDetail = defaults.levelOfDetail
        coarsetsScaleLevel = numScaleLevels - 1
        finestScaleLevel = 0
        simplificationIterations = defaults.simplificationIterations
        smoothingLambda = defaults.smoothingLambda
        smoothingIterations = defaults.smoothingIterations
        minLabelRatio = defaults.minLabelRatio
        opacity = defaults.opacity
        drawMode = defaults.drawMode
        cullFace = defaults.cullFace
        inflate = defaults.inflate
        isVisible = defaults.isVisible
        return this
    }

    fun bindTo(that: MeshSettings) {
        _levelOfDetail.bind(that._levelOfDetail)
        _coarsestScaleLevel.bind(that._coarsestScaleLevel)
        _finestScaleLevel.bind(that._finestScaleLevel)
        _simplificationIterations.bind(that._simplificationIterations)
        _smoothingLambda.bind(that._smoothingLambda)
        _smoothingIterations.bind(that._smoothingIterations)
        _minLabelRatio.bind(that._minLabelRatio)
        _opacity.bind(that._opacity)
        _drawMode.bind(that._drawMode)
        _cullFace.bind(that._cullFace)
        _inflate.bind(that._inflate)
        _isVisible.bind(that._isVisible)
    }

    fun unbind() {
        _levelOfDetail.unbind()
        _coarsestScaleLevel.unbind()
        _finestScaleLevel.unbind()
        _simplificationIterations.unbind()
        _smoothingLambda.unbind()
        _smoothingIterations.unbind()
        _minLabelRatio.unbind()
        _opacity.unbind()
        _drawMode.unbind()
        _cullFace.unbind()
        _inflate.unbind()
        _isVisible.unbind()
    }

    fun bindBidirectionalTo(that: MeshSettings) {
        _levelOfDetail.bindBidirectional(that._levelOfDetail)
        _coarsestScaleLevel.bindBidirectional(that._coarsestScaleLevel)
        _finestScaleLevel.bindBidirectional(that._finestScaleLevel)
        _simplificationIterations.bindBidirectional(that._simplificationIterations)
        _smoothingLambda.bindBidirectional(that._smoothingLambda)
        _smoothingIterations.bindBidirectional(that._smoothingIterations)
        _minLabelRatio.bindBidirectional(that._minLabelRatio)
        _opacity.bindBidirectional(that._opacity)
        _drawMode.bindBidirectional(that._drawMode)
        _cullFace.bindBidirectional(that._cullFace)
        _inflate.bindBidirectional(that._inflate)
        _isVisible.bindBidirectional(that._isVisible)
    }

    fun unbindBidrectional(that: MeshSettings) {
        _levelOfDetail.unbindBidirectional(that._levelOfDetail)
        _coarsestScaleLevel.unbindBidirectional(that._coarsestScaleLevel)
        _finestScaleLevel.unbindBidirectional(that._finestScaleLevel)
        _simplificationIterations.unbindBidirectional(that._simplificationIterations)
        _smoothingLambda.unbindBidirectional(that._smoothingLambda)
        _smoothingIterations.unbindBidirectional(that._smoothingIterations)
        _minLabelRatio.unbindBidirectional(that._minLabelRatio)
        _opacity.unbindBidirectional(that._opacity)
        _drawMode.unbindBidirectional(that._drawMode)
        _cullFace.unbindBidirectional(that._cullFace)
        _inflate.unbindBidirectional(that._inflate)
        _isVisible.unbindBidirectional(that._isVisible)
    }

    fun hasOnlyDefaultValues(): Boolean {
        return coarsetsScaleLevel == numScaleLevels - 1
            && simplificationIterations == defaults.simplificationIterations
            && smoothingLambda == defaults.smoothingLambda
            && smoothingIterations == defaults.smoothingIterations
            && opacity == defaults.opacity
            && defaults.drawMode == drawMode
            && defaults.cullFace == cullFace
            && inflate == defaults.inflate
            && isVisible == defaults.isVisible
    }

}
