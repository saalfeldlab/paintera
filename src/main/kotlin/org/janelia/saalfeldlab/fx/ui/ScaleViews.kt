package org.janelia.saalfeldlab.fx.ui

import javafx.css.*
import javafx.scene.shape.Circle

class CircleScaleView(var child : Circle) : ScaleView(), Styleable {

	private val radiusPercentProperty: SimpleStyleableObjectProperty<Number> = SimpleStyleableObjectProperty(RADIUS_PERCENT, this, "radiusPercent", 1.0)
	private val radiusPercent: Double
		get() = radiusPercentProperty.value.toDouble()

	init {
		styleClass += "circle-scale-pane"
		children.setAll(child)

		requestLayout()

		radiusPercentProperty.addListener { _, _, percent ->
			child.radius = percent.toDouble() * prefWidth / 2.0
			requestLayout()
		}
		paneWidthProperty.addListener { _, _, new -> new?.let { width ->
			prefWidth = width.toDouble()
			child.radius = radiusPercent * prefWidth / 2.0
			requestLayout()
		} }
		paneHeightProperty.addListener { _, _, new -> new?.let { height ->
			prefHeight = height.toDouble()
			child.radius = radiusPercent * prefHeight / 2.0
			requestLayout()
		} }
	}

	override fun layoutChildren() {
		super.layoutChildren()
		val newX = (layoutBounds.width - child.layoutBounds.width) / 2.0
		val newY = (layoutBounds.height - child.layoutBounds.height) / 2.0
		child.relocate(newX, newY)
	}



	override fun getCssMetaData(): MutableList<CssMetaData<out Styleable, *>> {
		return getClassCssMetaData()
	}

	companion object {

		private val RADIUS_PERCENT = object : CssMetaData<CircleScaleView, Number>("-radius-percent", StyleConverter.getSizeConverter()) {
			override fun isSettable(styleable: CircleScaleView) = !styleable.radiusPercentProperty.isBound

			override fun getStyleableProperty(styleable: CircleScaleView): StyleableProperty<Number> {
				return styleable.radiusPercentProperty
			}

			override fun getInitialValue(styleable: CircleScaleView): Double {
				return styleable.radiusPercent
			}
		}

		private val STYLEABLES: MutableList<CssMetaData<out Styleable, *>> = mutableListOf<CssMetaData<out Styleable, *>>().also {
			it += ScaleView.getClassCssMetaData()
			it += RADIUS_PERCENT
		}

		fun getClassCssMetaData(): MutableList<CssMetaData<out Styleable, *>> = STYLEABLES
	}
}