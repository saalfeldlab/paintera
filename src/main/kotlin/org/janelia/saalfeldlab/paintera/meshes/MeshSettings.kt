package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.*
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode

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

        object Values {
            @JvmStatic val SIMPLIFICATION_ITERATIONS = 0
            @JvmStatic val SMOOTHING_ITERATIONS = Smooth.DEFAULT_ITERATIONS
            @JvmStatic val SMOOTHING_LAMBDA = Smooth.DEFAULT_LAMBDA
            @JvmStatic val OPACITY = 1.0
            @JvmStatic val DRAWMODE = DrawMode.FILL
            @JvmStatic val CULLFACE = CullFace.FRONT
            @JvmStatic val INFLATE = 1.0
            @JvmStatic val IS_VISIBLE = true
        }

        companion object {
            @JvmStatic
            val IMMUTABLE_INSTANCE = MutableDefaults().asImmutable
        }
    }

    class MutableDefaults(): Defaults {
        override var simplificationIterations: Int = Defaults.Values.SIMPLIFICATION_ITERATIONS
        override var smoothingIterations: Int = Defaults.Values.SMOOTHING_ITERATIONS
        override var smoothingLambda: Double = Defaults.Values.SMOOTHING_LAMBDA
        override var opacity: Double = Defaults.Values.OPACITY
        override var drawMode: DrawMode = Defaults.Values.DRAWMODE
        override var cullFace: CullFace = Defaults.Values.CULLFACE
        override var inflate: Double = Defaults.Values.INFLATE
        override var isVisible: Boolean = Defaults.Values.IS_VISIBLE

        val asImmutable: Defaults
            get() = ImmutableDefaults(this)
    }

    class ImmutableDefaults(private val delegate: Defaults): Defaults by delegate

    // TODO should scaleLevel actually be part of the MeshSettings?
    private val _scaleLevel = SimpleIntegerProperty(numScaleLevels - 1)
    private val _simplificationIterations = SimpleIntegerProperty(defaults.simplificationIterations)
    private val _smoothingLambda: DoubleProperty = SimpleDoubleProperty(defaults.smoothingLambda)
    private val _smoothingIterations: IntegerProperty = SimpleIntegerProperty(defaults.smoothingIterations)
    private val _opacity: DoubleProperty = SimpleDoubleProperty(defaults.opacity)
    private val _drawMode: ObjectProperty<DrawMode> = SimpleObjectProperty(defaults.drawMode)
    private val _cullFace: ObjectProperty<CullFace> = SimpleObjectProperty(defaults.cullFace)
    private val _inflate: DoubleProperty = SimpleDoubleProperty(defaults.inflate)
    private val _isVisible: BooleanProperty = SimpleBooleanProperty(defaults.isVisible)

    fun scaleLevelProperty(): IntegerProperty = _scaleLevel
    fun simplificationIterationsProperty(): IntegerProperty = _simplificationIterations
    fun smoothingLambdaProperty(): DoubleProperty = _smoothingLambda
    fun smoothingIterationsProperty(): IntegerProperty = _smoothingIterations
    fun opacityProperty(): DoubleProperty = _opacity
    fun drawModeProperty(): ObjectProperty<DrawMode> = _drawMode
    fun cullFaceProperty(): ObjectProperty<CullFace> = _cullFace
    fun inflateProperty(): DoubleProperty = _inflate
    fun isVisibleProperty(): BooleanProperty= _isVisible

    var scaleLevel: Int
        get() = _scaleLevel.value
        set(level) = _scaleLevel.set(level)
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

    @JvmOverloads
    fun copy(defaults: Defaults = this.defaults): MeshSettings {
        val that = MeshSettings(numScaleLevels, defaults)
        that.setTo(this)
        return that
    }

    fun setTo(that: MeshSettings) {
        scaleLevel = that.scaleLevel
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
        scaleLevel = numScaleLevels - 1
        simplificationIterations = defaults.simplificationIterations
        smoothingLambda = defaults.smoothingLambda
        smoothingIterations = defaults.smoothingIterations
        opacity = defaults.opacity
        drawMode = defaults.drawMode
        cullFace = defaults.cullFace
        inflate = defaults.inflate
        isVisible = defaults.isVisible
        return this
    }

    fun hasOnlyDefaultValues(): Boolean {
        return scaleLevel == numScaleLevels - 1
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
