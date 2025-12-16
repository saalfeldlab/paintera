package org.janelia.saalfeldlab.paintera.ui

import javafx.scene.Node
import javafx.scene.control.TextFormatter
import javafx.scene.layout.HBox.setHgrow
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox.setVgrow
import javafx.util.StringConverter
import java.math.RoundingMode


class PositiveLongTextFormatter(initialValue: Long? = null, customMapping: ((String?) -> String?)? = null) : TextFormatter<Long?>(
	object : StringConverter<Long?>() {
		override fun toString(`object`: Long?) = `object`?.takeIf { it >= 0 }?.let { "$it" }
		override fun fromString(string: String?) = string?.toLongOrNull()
	},
	initialValue,
	{
		it.apply {
			customMapping?.invoke(controlNewText)?.let { mapped ->
				setRange(0, controlText.length)
				text = mapped.takeIf { it == "" || it.runCatching { toLong() }.isSuccess } ?: ""
			} ?: let {
				if (controlNewText.runCatching { toLong() }.isFailure)
					text = ""
			}
		}
		it
	}
)

class PositiveDoubleTextFormatter(initialValue: Double? = null, format: (Double) -> String = ::defaultFormat) : TextFormatter<Double>(
	object : StringConverter<Double>() {
		override fun toString(`object`: Double?) = `object`?.takeIf { it >= 0 }?.let { format(it) }
		override fun fromString(string: String?) = string?.toDoubleOrNull()
	},
	initialValue,
	{ it.apply { controlNewText?.toDoubleWithLeadingDecimalOrNull() ?: let { text = "" } } }
) {
	companion object {

		private fun String.toDoubleWithLeadingDecimalOrNull() = (takeUnless { it.startsWith(".") } ?: "0$this").toDoubleOrNull()

		private fun defaultFormat(value: Double): String {
			return value.toBigDecimal()
				.setScale(3, RoundingMode.HALF_UP)
				.toString()
				.trimTrailingZeros()
		}

		private fun String.trimTrailingZeros(): String = replace("\\.?0+$".toRegex(), "")
	}
}

fun <T : Node> T.hGrow(priority: Priority = Priority.ALWAYS, apply: (T.() -> Unit)? = { }): T {
	//TODO Caleb: consider setting maxWidth to Double.POSITIVE_INFINITY; maybe as a default param
	setHgrow(this, priority)
	apply?.invoke(this)
	return this
}

fun <T : Node> T.vGrow(priority: Priority = Priority.ALWAYS, apply: (T.() -> Unit)? = { }): T {
	//TODO Caleb: consider setting maxHeight to Double.POSITIVE_INFINITY; maybe as a default param
	setVgrow(this, priority)
	apply?.invoke(this)
	return this
}

fun <T : Node> T.hvGrow(apply: (T.() -> Unit)? = { }): T {
	hGrow()
	vGrow()
	apply?.invoke(this)
	return this
}