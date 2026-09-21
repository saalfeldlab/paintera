package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.Tooltip
import javafx.scene.text.Text
import javafx.util.Subscription
import kotlin.math.max
import org.controlsfx.control.ToggleSwitch
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.paintera.addStyleClass

enum class TranslationSpace(val label: String) {
	PIXEL("Pixel"),
	PHYSICAL("Physical")
}

/**
 * The coordinate space a translation is displayed in, and conversion to/from the backing physical space.
 */
class TranslationSpaceModel(initialSpace: TranslationSpace = TranslationSpace.PIXEL) {

	val translationSpaceProperty = SimpleObjectProperty(initialSpace)
	var translationSpace: TranslationSpace by translationSpaceProperty.nonnull()

	/**
	 * Display the translation in either physical or pixel space. [physicalTranslation] is the source
	 * of truth, and is either written to [displayed] directly, or divided by [resolution]
	 * to calculate the pixel space values.
	 */
	fun bindAxis(displayed: DoubleProperty, physicalTranslation: DoubleProperty, resolution: ObservableValue<Number>): Subscription {

		/* guard to avoid recursion */
		var converting = false

		fun convert(block: () -> Unit) {
			if (converting)
				return
			converting = true
			try {
				block()
			} finally {
				converting = false
			}
		}

		fun updateDisplay() = convert {
			val displayedTranslation = when (translationSpace) {
				TranslationSpace.PIXEL -> physicalTranslation.get() / resolution.value.toDouble()
				TranslationSpace.PHYSICAL -> physicalTranslation.get()
			}
			displayed.set(displayedTranslation)
		}

		fun updateTranslation() = convert {
			val translation = when (translationSpace) {
				TranslationSpace.PIXEL -> displayed.get() * resolution.value.toDouble()
				TranslationSpace.PHYSICAL -> displayed.get()
			}
			physicalTranslation.set(translation)
		}

		updateDisplay()
		return Subscription.combine(
			displayed.subscribe { _, _ -> updateTranslation() },
			physicalTranslation.subscribe { _, _ -> updateDisplay() },
			resolution.subscribe { _, _ -> updateDisplay() },
			translationSpaceProperty.subscribe { _, _ -> updateDisplay() }
		)
	}
}

/**
 * The [TranslationSpace] toggle.
 *
 * [subscription] must be unsubscribed before reusing [model].
 */
class TranslationSpaceToggle(model: TranslationSpaceModel) : ToggleSwitch() {

	val subscription: Subscription

	private val widestLabelWidth = ReadOnlyDoubleWrapper(0.0)

	/** The pref width of the widest toggle state label */
	val widestLabelWidthProperty: ReadOnlyDoubleProperty = widestLabelWidth.readOnlyProperty

	init {
		addStyleClass("coordinate-space-toggle")
		isSelected = model.translationSpace == TranslationSpace.PIXEL
		tooltip = Tooltip("Translation in physical (pixel * resolution) or in pixel space")
		subscription = Subscription.combine(
			selectedProperty().subscribe { _, isPixel ->
				model.translationSpace = if (isPixel) TranslationSpace.PIXEL else TranslationSpace.PHYSICAL
			},
			model.translationSpaceProperty.subscribe { space ->
				isSelected = space == TranslationSpace.PIXEL
				text = space.label
			}
		)
	}

	/* the labels can only be measured once css has been applied, which reaching layout guarantees */
	override fun layoutChildren() {
		if (widestLabelWidth.value <= 0.0)
			measureWidestLabel()
		super.layoutChildren()
	}

	/**
	 * The pref width for the current label, widened by however much the longest label needs..
	 */
	private fun measureWidestLabel() {
		val metrics = Text().apply { font = this@TranslationSpaceToggle.font }
		fun widthOf(label: String): Double {
			metrics.text = label
			return metrics.layoutBounds.width
		}

		val extra = TranslationSpace.entries.maxOf { widthOf(it.label) } - widthOf(text ?: "")
		widestLabelWidth.value = prefWidth(-1.0) + max(0.0, extra)
	}
}
