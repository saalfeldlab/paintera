package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.util.Subscription
import net.imglib2.realtransform.AffineTransform3D

/**
 * The scale [levels] and associated logic that keep them consistent with the base spatial values.
 * Level 0 is the base; adding/removing levels re-bases the pyramid and
 * recomputes each level's derived values.
 */
interface ScaleLevelsModel {

	val levels: ObservableList<ScaleLevel>
	val blockSize: SpatialValues<IntegerProperty>

	/** True when any level has a non-positive dimension; disables Create. */
	val invalidProperty: ReadOnlyBooleanProperty

	fun addLevel()

	fun removeLevel(level: ScaleLevel)

	fun setFromSource(source: Source<*>)

	fun downsamplingFactors(): Array<DoubleArray>

	fun maxNumEntries(): IntArray

	companion object {
		fun default(): ScaleLevelsModel = DefaultScaleLevelsModel(
			SpatialValues.longValues(1),
			SpatialValues.doubleValues(1.0),
			SpatialValues.doubleValues(0.0),
			SpatialValues.intValues(1)
		)
	}
}

internal class DefaultScaleLevelsModel(
	private val dimensions: SpatialValues<LongProperty>,
	private val resolution: SpatialValues<DoubleProperty>,
	private val offset: SpatialValues<DoubleProperty>,
	override val blockSize: SpatialValues<IntegerProperty>
) : ScaleLevelsModel {

	override val levels: ObservableList<ScaleLevel> = FXCollections.observableArrayList()

	private val invalidWrapper = SimpleBooleanProperty(false)
	override val invalidProperty: ReadOnlyBooleanProperty = invalidWrapper
	private var levelDimensionSubscription = Subscription.EMPTY

	init {
		levels.addListener(ListChangeListener {
			if (levels.isEmpty()) return@ListChangeListener
			while (it.next()) {
				if (it.wasRemoved()) {
					if (it.from == 0) {
						/* s0 was removed, re-base the dimensions based on the new s0 */
						reduceBaseScale(levels[0])
					} else if (it.from < levels.size) {
						adjustSubsequentScale(it.removed[0]!!, levels[it.from])
						provideAbsoluteValues(levels)
					}
				} else if (it.wasAdded()) {
					provideAbsoluteValues(levels)
				}
			}
		})
		/* keep [invalidProperty] in sync as levels are added/removed and as their computed dimensions change */
		levels.addListener(ListChangeListener { trackLevelDimensions() })
		val scale0 = newLevel(1)
		provideAbsoluteValues(listOf(scale0))
		levels += scale0
	}

	private fun trackLevelDimensions() {
		levelDimensionSubscription.unsubscribe()
		levelDimensionSubscription = levels
			.flatMap { listOf(it.dimensions.xProperty, it.dimensions.yProperty, it.dimensions.zProperty) }
			.fold(Subscription.EMPTY) { subscription, dimension ->
				subscription.and(dimension.subscribe { _ -> recomputeInvalid() })
			}
		recomputeInvalid()
	}

	private fun recomputeInvalid() {
		invalidWrapper.value = levels.any { level -> level.dimensions.asLongArray().any { it <= 0 } }
	}

	private fun newLevel(downsamplingFactor: Int) = ScaleLevel(downsamplingFactor, -1)

	override fun addLevel() {
		val newLevel = newLevel(2)
		levels.lastOrNull()?.let { previous ->
			newLevel.displayAbsoluteValues(previous.resolution, previous.absoluteDownsamplingFactors, previous.baseDimensions)
		}
		levels += newLevel
	}

	override fun removeLevel(level: ScaleLevel) {
		levels -= level
	}

	override fun setFromSource(source: Source<*>) {
		/* s0 has relative factors of 1, since s0 is the base */
		val levels = mutableListOf(newLevel(1))
		val firstTransform = AffineTransform3D()
		source.getSourceTransform(0, 0, firstTransform)
		var previousFactors = doubleArrayOf(firstTransform[0, 0], firstTransform[1, 1], firstTransform[2, 2])
		for (i in 1 until source.numMipmapLevels) {
			val transform = AffineTransform3D()
			source.getSourceTransform(0, i, transform)
			val downsamplingFactors = doubleArrayOf(transform[0, 0], transform[1, 1], transform[2, 2])
			val level = newLevel(2)
			val relativeXFactor = downsamplingFactors[0] / previousFactors[0]
			val relativeYFactor = downsamplingFactors[1] / previousFactors[1]
			val relativeZFactor = downsamplingFactors[2] / previousFactors[2]
			level.relativeDownsamplingFactors.setValues(relativeXFactor, relativeYFactor, relativeZFactor)
			previousFactors = downsamplingFactors
			levels.add(level)
		}
		provideAbsoluteValues(levels)
		levels.removeIf { level ->
			val isFirst = level == levels[0]
			val tooSmall = level.dimensions.asLongArray().zip(blockSize.asLongArray()).all { (dim, block) -> dim <= block }
			!isFirst && tooSmall
		}
		this.levels.clear()
		this.levels += levels
	}

	/** The scale levels that actually downsample; levels with relative factors all 1 are dropped. */
	private fun effectiveLevels(): List<ScaleLevel> = levels.filter { level ->
		level.relativeDownsamplingFactors.asDoubleArray().reduce { l, r -> l * r } != 1.0
	}

	override fun downsamplingFactors(): Array<DoubleArray> = effectiveLevels().map { it.downsamplingFactors() }.toTypedArray()

	override fun maxNumEntries(): IntArray = effectiveLevels().stream().mapToInt { it.maxNumEntries() }.toArray()

	private fun reduceBaseScale(newBaseScale: ScaleLevel) {
		val relativeFactors = newBaseScale.relativeDownsamplingFactors.asLongArray().toTypedArray()

		data class ReduceBaseData(val dimension: LongProperty, val resolution: DoubleProperty, val offset: DoubleProperty)

		val xData = ReduceBaseData(dimensions.xProperty, resolution.xProperty, offset.xProperty)
		val yData = ReduceBaseData(dimensions.yProperty, resolution.yProperty, offset.yProperty)
		val zData = ReduceBaseData(dimensions.zProperty, resolution.zProperty, offset.zProperty)

		listOf(xData, yData, zData).zip(relativeFactors).forEach { (baseData, factor) ->
			baseData.dimension.value /= factor
			val prevRes = baseData.resolution.value
			baseData.resolution.value *= factor
			baseData.offset.value += (baseData.resolution.value - prevRes) / 2.0
		}

		newBaseScale.relativeDownsamplingFactors.setValues(1, 1, 1)
	}

	private fun adjustSubsequentScale(removedScale: ScaleLevel, nextScale: ScaleLevel) {
		nextScale.relativeDownsamplingFactors.apply {
			removedScale.relativeDownsamplingFactors.let {
				xProperty.value *= it.xProperty.value
				yProperty.value *= it.yProperty.value
				zProperty.value *= it.zProperty.value
			}
		}
	}

	private fun provideAbsoluteValues(levels: List<ScaleLevel>) {
		val baseAbsoluteFactors = SpatialValues.intValues(1).apply {
			xProperty.bind(levels[0].relativeDownsamplingFactors.xProperty)
			yProperty.bind(levels[0].relativeDownsamplingFactors.yProperty)
			zProperty.bind(levels[0].relativeDownsamplingFactors.zProperty)
		}
		levels[0].displayAbsoluteValues(resolution, baseAbsoluteFactors, dimensions)

		var previousAbsoluteFactors = baseAbsoluteFactors
		if (levels.size > 1) {
			levels.subList(1, levels.size).forEach { level ->
				val absoluteFactors = SpatialValues.intValues(1).apply {
					xProperty.bind(previousAbsoluteFactors.xProperty.multiply(level.relativeDownsamplingFactors.xProperty))
					yProperty.bind(previousAbsoluteFactors.yProperty.multiply(level.relativeDownsamplingFactors.yProperty))
					zProperty.bind(previousAbsoluteFactors.zProperty.multiply(level.relativeDownsamplingFactors.zProperty))
				}
				level.displayAbsoluteValues(resolution, absoluteFactors, dimensions)
				previousAbsoluteFactors = absoluteFactors
			}
		}
	}
}
