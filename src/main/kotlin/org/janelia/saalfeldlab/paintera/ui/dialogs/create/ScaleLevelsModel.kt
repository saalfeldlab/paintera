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
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField

/**
 * The scale [levels] and associated logic that keep them consistent with the base spatial fields.
 * Level 0 is the base; adding/removing levels re-bases the pyramid and
 * recomputes each level's derived values.
 */
interface ScaleLevelsModel {

	val levels: ObservableList<ScaleLevel>
	val blockSize: SpatialField<IntegerProperty>

	/** True when any level has a non-positive dimension; disables Create. */
	val invalidProperty: ReadOnlyBooleanProperty

	fun addLevel()

	fun removeLevel(level: ScaleLevel)

	fun setFromSource(source: Source<*>)

	fun downsamplingFactors(): Array<DoubleArray>

	fun maxNumEntries(): IntArray

	companion object {
		fun default(): ScaleLevelsModel = DefaultScaleLevelsModel(
			SpatialField.longField(1, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()),
			SpatialField.doubleField(1.0, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()),
			SpatialField.doubleField(0.0, { true }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()),
			SpatialField.intField(1, { it > 0 }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
		)
	}
}

internal class DefaultScaleLevelsModel(
	private val dimensions: SpatialField<LongProperty>,
	private val resolution: SpatialField<DoubleProperty>,
	private val offset: SpatialField<DoubleProperty>,
	override val blockSize: SpatialField<IntegerProperty>
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
			.flatMap { listOf(it.dimensions.x, it.dimensions.y, it.dimensions.z) }
			.fold(Subscription.EMPTY) { subscription, dimension ->
				subscription.and(dimension.valueProperty().subscribe { _ -> recomputeInvalid() })
			}
		recomputeInvalid()
	}

	private fun recomputeInvalid() {
		invalidWrapper.value = levels.any { level -> level.dimensions.asLongArray().any { it <= 0 } }
	}

	private fun newLevel(downsamplingFactor: Int) =
		ScaleLevel(downsamplingFactor, -1, SCALE_FIELD_WIDTH, *SubmitOn.entries.toTypedArray())

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

		val xData = ReduceBaseData(dimensions.x.valueProperty(), resolution.x.valueProperty(), offset.x.valueProperty())
		val yData = ReduceBaseData(dimensions.y.valueProperty(), resolution.y.valueProperty(), offset.y.valueProperty())
		val zData = ReduceBaseData(dimensions.z.valueProperty(), resolution.z.valueProperty(), offset.z.valueProperty())

		listOf(xData, yData, zData).zip(relativeFactors).forEach { (baseData, factor) ->
			baseData.dimension.value /= factor
			val prevRes = baseData.resolution.value
			baseData.resolution.value *= factor
			baseData.offset.value += (baseData.resolution.value - prevRes) / 2.0
		}

		newBaseScale.relativeDownsamplingFactors.apply {
			x.valueProperty().value = 1
			y.valueProperty().value = 1
			z.valueProperty().value = 1
		}
	}

	private fun adjustSubsequentScale(removedScale: ScaleLevel, nextScale: ScaleLevel) {
		nextScale.relativeDownsamplingFactors.apply {
			removedScale.relativeDownsamplingFactors.let {
				x.valueProperty().value *= it.x.valueProperty().value
				y.valueProperty().value *= it.y.valueProperty().value
				z.valueProperty().value *= it.z.valueProperty().value
			}
		}
	}

	private fun provideAbsoluteValues(levels: List<ScaleLevel>) {
		val baseAbsoluteFactors = SpatialField.intField(1, { true }, MAIN_FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).also { it.editable = false }.also {
			it.x.valueProperty().bind(levels[0].relativeDownsamplingFactors.x.valueProperty())
			it.y.valueProperty().bind(levels[0].relativeDownsamplingFactors.y.valueProperty())
			it.z.valueProperty().bind(levels[0].relativeDownsamplingFactors.z.valueProperty())
		}
		levels[0].displayAbsoluteValues(resolution, baseAbsoluteFactors, dimensions)

		var previousAbsoluteFactors = baseAbsoluteFactors
		if (levels.size > 1) {
			levels.subList(1, levels.size).forEach { level ->
				val absoluteFactors = SpatialField.intField(1, { true })
				absoluteFactors.x.valueProperty().bind(previousAbsoluteFactors.x.valueProperty().multiply(level.relativeDownsamplingFactors.x.valueProperty()))
				absoluteFactors.y.valueProperty().bind(previousAbsoluteFactors.y.valueProperty().multiply(level.relativeDownsamplingFactors.y.valueProperty()))
				absoluteFactors.z.valueProperty().bind(previousAbsoluteFactors.z.valueProperty().multiply(level.relativeDownsamplingFactors.z.valueProperty()))
				level.displayAbsoluteValues(resolution, absoluteFactors, dimensions)
				previousAbsoluteFactors = absoluteFactors
			}
		}
	}
}
