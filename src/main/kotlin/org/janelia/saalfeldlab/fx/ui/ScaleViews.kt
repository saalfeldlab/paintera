package org.janelia.saalfeldlab.fx.ui

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.css.*
import javafx.scene.shape.Circle

class GlyphScaleView(var child : FontAwesomeIconView) : ScaleView() {

	init {
		styleClass += "glyph-scale-pane"
		children.setAll(child)
		requestLayout()
		paneWidthProperty.addListener { _, _, new -> new?.let { width ->
			prefWidth = width.toDouble()
			child.glyphSize = prefWidth
			requestLayout()
		} }
		paneHeightProperty.addListener { _, _, new -> new?.let { height ->
			prefHeight = height.toDouble()
			child.glyphSize = prefHeight
			requestLayout()
		} }
	}

	override fun layoutChildren() {
		super.layoutChildren()
		child.relocate(0.0, 0.0)
	}
}

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
				return styleable.radiusPercentProperty as StyleableProperty<Number>
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