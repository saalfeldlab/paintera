package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import java.util.*

class ScreenScalesConfig @JvmOverloads constructor(vararg initialScales: Double = DEFAULT_SCREEN_SCALES.clone()) {

    private val screenScales = SimpleObjectProperty<ScreenScales>()

    class ScreenScales(vararg scales: Double) {

        private val scales: DoubleArray

        val scalesCopy: DoubleArray
            get() = this.scales.clone()

        constructor(scales: ScreenScales) : this(*scales.scales.clone()) {}

        init {
            this.scales = scales
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(scales)
        }

        override fun equals(other: Any?): Boolean {
            return other is ScreenScales && Arrays.equals(other.scales, this.scales)
        }

        override fun toString(): String {
            return String.format("{ScreenScales: %s}", Arrays.toString(scales))
        }
    }

    init {
        this.screenScales.set(ScreenScales(*initialScales.clone()))
    }

    fun screenScalesProperty(): ObjectProperty<ScreenScales> {
        return this.screenScales
    }

    fun set(that: ScreenScalesConfig) {
        this.screenScales.set(that.screenScales.get())
    }

    override fun toString(): String {
        return String.format(
                "{ScreenScalesConfig: %s}",
                if (this.screenScales.get() == null) null else this.screenScales.get().toString()
        )
    }

    companion object {

        private val DEFAULT_SCREEN_SCALES = doubleArrayOf(1.0, 0.5, 0.25, 0.125, 0.0625)

        @JvmStatic
        fun defaultScreenScalesCopy(): DoubleArray {
            return DEFAULT_SCREEN_SCALES.clone()
        }
    }

}
