package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.*
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import kotlin.math.max
import kotlin.math.min

class MeshSettings @JvmOverloads constructor(
	val numScaleLevels: Int,
	val defaults: Defaults = Defaults.IMMUTABLE_INSTANCE
) {

	// Currently, all source states use the default defaults, i.e. MeshSettings.Defaults.IMMUTABLE_INSTANCE
	interface Defaults {
		val simplificationIterations: Int
		val smoothingIterations: Int
		val smoothingLambda: Double
		val opacity: Double
		val drawMode: DrawMode
		val cullFace: CullFace
		val isVisible: Boolean
		val minLabelRatio: Double
		val overlap: Boolean
		val levelOfDetail: Int


		object Values {
			@JvmStatic
			val simplificationIterations = 0

			@JvmStatic
			val smoothingIterations = Smooth.DEFAULT_ITERATIONS

			@JvmStatic
			val smoothingLambda = Smooth.DEFAULT_LAMBDA

			@JvmStatic
			val opacity = 1.0

			@JvmStatic
			val drawMode = DrawMode.FILL

			@JvmStatic
			val cullFace = CullFace.FRONT

			@JvmStatic
			val isVisible = true

			@JvmStatic
			val overlap = true

			@JvmStatic
			val minLabelRatio = 0.0

			@JvmStatic
			val minLevelOfDetail = 1

			@JvmStatic
			val maxLevelOfDetail = 10

			@JvmStatic
			val levelOfDetail = 8
		}

		companion object {
			@JvmStatic
			val IMMUTABLE_INSTANCE = MutableDefaults().asImmutable

			@JvmStatic
			fun getDefaultCoarsestScaleLevel(numScaleLevels: Int) = numScaleLevels - 1

			@JvmStatic
			fun getDefaultFinestScaleLevel(numScaleLevels: Int) = 0
		}
	}

	class MutableDefaults() : Defaults {
		override var simplificationIterations: Int = Defaults.Values.simplificationIterations
		override var smoothingIterations: Int = Defaults.Values.smoothingIterations
		override var smoothingLambda: Double = Defaults.Values.smoothingLambda
		override var opacity: Double = Defaults.Values.opacity
		override var drawMode: DrawMode = Defaults.Values.drawMode
		override var cullFace: CullFace = Defaults.Values.cullFace
		override var isVisible: Boolean = Defaults.Values.isVisible
		override var minLabelRatio: Double = Defaults.Values.minLabelRatio
		override var overlap: Boolean = Defaults.Values.overlap
		override var levelOfDetail: Int = Defaults.Values.levelOfDetail

		val asImmutable: Defaults
			get() = ImmutableDefaults(this)
	}

	class ImmutableDefaults(private val delegate: Defaults) : Defaults by delegate

	// TODO should scaleLevel actually be part of the MeshSettings?
	val coarsestScaleLevelProperty = SimpleIntegerProperty(Defaults.getDefaultCoarsestScaleLevel(numScaleLevels))
	val finestScaleLevelProperty = SimpleIntegerProperty(Defaults.getDefaultFinestScaleLevel(numScaleLevels))
	val simplificationIterationsProperty = SimpleIntegerProperty(defaults.simplificationIterations)
	val smoothingLambdaProperty: DoubleProperty = SimpleDoubleProperty(defaults.smoothingLambda)
	val smoothingIterationsProperty: IntegerProperty = SimpleIntegerProperty(defaults.smoothingIterations)
	val opacityProperty: DoubleProperty = SimpleDoubleProperty(defaults.opacity)
	val drawModeProperty: ObjectProperty<DrawMode> = SimpleObjectProperty(defaults.drawMode)
	val cullFaceProperty: ObjectProperty<CullFace> = SimpleObjectProperty(defaults.cullFace)
	val isVisibleProperty: BooleanProperty = SimpleBooleanProperty(defaults.isVisible)
	val minLabelRatioProperty: DoubleProperty = SimpleDoubleProperty(defaults.minLabelRatio)
	val overlapProperty: BooleanProperty = SimpleBooleanProperty(defaults.overlap)
	val levelOfDetailProperty: IntegerProperty = SimpleIntegerProperty(defaults.levelOfDetail)

	var coarsestScaleLevel by coarsestScaleLevelProperty.nonnull()
	var finestScaleLevel by finestScaleLevelProperty.nonnull()
	var simplificationIterations by simplificationIterationsProperty.nonnull()
	var smoothingLambda by smoothingLambdaProperty.nonnull()
	var smoothingIterations by smoothingIterationsProperty.nonnull()
	var opacity by opacityProperty.nonnull()
	var drawMode by drawModeProperty.nonnull()
	var cullFace by cullFaceProperty.nonnull()
	var isVisible by isVisibleProperty.nonnull()
	var minLabelRatio by minLabelRatioProperty.nonnull()
	var overlap by overlapProperty.nonnull()
	var levelOfDetail by levelOfDetailProperty.nonnull()

	init {
		levelOfDetailProperty.addListener { _, _, new ->
			// TODO can we do this without the bound check?
			if (!levelOfDetailProperty.isBound)
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
		InvokeOnJavaFXApplicationThread {
			levelOfDetail = that.levelOfDetail
			coarsestScaleLevel = that.coarsestScaleLevel
			finestScaleLevel = that.finestScaleLevel
			simplificationIterations = that.simplificationIterations
			smoothingLambda = that.smoothingLambda
			smoothingIterations = that.smoothingIterations
			minLabelRatio = that.minLabelRatio
			overlap = that.overlap
			opacity = that.opacity
			drawMode = that.drawMode
			cullFace = that.cullFace
			isVisible = that.isVisible
		}
	}

	fun bindTo(that: MeshSettings) {
		InvokeOnJavaFXApplicationThread {
			levelOfDetailProperty.bind(that.levelOfDetailProperty)
			coarsestScaleLevelProperty.bind(that.coarsestScaleLevelProperty)
			finestScaleLevelProperty.bind(that.finestScaleLevelProperty)
			simplificationIterationsProperty.bind(that.simplificationIterationsProperty)
			smoothingLambdaProperty.bind(that.smoothingLambdaProperty)
			smoothingIterationsProperty.bind(that.smoothingIterationsProperty)
			minLabelRatioProperty.bind(that.minLabelRatioProperty)
			overlapProperty.bind(that.overlapProperty)
			opacityProperty.bind(that.opacityProperty)
			drawModeProperty.bind(that.drawModeProperty)
			cullFaceProperty.bind(that.cullFaceProperty)
			isVisibleProperty.bind(that.isVisibleProperty)
		}
	}

	fun unbind() {
		InvokeOnJavaFXApplicationThread {
			levelOfDetailProperty.unbind()
			coarsestScaleLevelProperty.unbind()
			finestScaleLevelProperty.unbind()
			simplificationIterationsProperty.unbind()
			smoothingLambdaProperty.unbind()
			smoothingIterationsProperty.unbind()
			minLabelRatioProperty.unbind()
			overlapProperty.unbind()
			opacityProperty.unbind()
			drawModeProperty.unbind()
			cullFaceProperty.unbind()
			isVisibleProperty.unbind()
		}
	}

	fun bindBidirectional(that: MeshSettings) {
		InvokeOnJavaFXApplicationThread {
			levelOfDetailProperty.bindBidirectional(that.levelOfDetailProperty)
			coarsestScaleLevelProperty.bindBidirectional(that.coarsestScaleLevelProperty)
			finestScaleLevelProperty.bindBidirectional(that.finestScaleLevelProperty)
			simplificationIterationsProperty.bindBidirectional(that.simplificationIterationsProperty)
			smoothingLambdaProperty.bindBidirectional(that.smoothingLambdaProperty)
			smoothingIterationsProperty.bindBidirectional(that.smoothingIterationsProperty)
			minLabelRatioProperty.bindBidirectional(that.minLabelRatioProperty)
			overlapProperty.bindBidirectional(that.overlapProperty)
			opacityProperty.bindBidirectional(that.opacityProperty)
			drawModeProperty.bindBidirectional(that.drawModeProperty)
			cullFaceProperty.bindBidirectional(that.cullFaceProperty)
			isVisibleProperty.bindBidirectional(that.isVisibleProperty)
		}
	}

	fun unbindBidirectional(that: MeshSettings) {
		InvokeOnJavaFXApplicationThread {
			levelOfDetailProperty.unbindBidirectional(that.levelOfDetailProperty)
			coarsestScaleLevelProperty.unbindBidirectional(that.coarsestScaleLevelProperty)
			finestScaleLevelProperty.unbindBidirectional(that.finestScaleLevelProperty)
			simplificationIterationsProperty.unbindBidirectional(that.simplificationIterationsProperty)
			smoothingLambdaProperty.unbindBidirectional(that.smoothingLambdaProperty)
			smoothingIterationsProperty.unbindBidirectional(that.smoothingIterationsProperty)
			minLabelRatioProperty.unbindBidirectional(that.minLabelRatioProperty)
			overlapProperty.unbindBidirectional(that.overlapProperty)
			opacityProperty.unbindBidirectional(that.opacityProperty)
			drawModeProperty.unbindBidirectional(that.drawModeProperty)
			cullFaceProperty.unbindBidirectional(that.cullFaceProperty)
			isVisibleProperty.unbindBidirectional(that.isVisibleProperty)
		}
	}
}
