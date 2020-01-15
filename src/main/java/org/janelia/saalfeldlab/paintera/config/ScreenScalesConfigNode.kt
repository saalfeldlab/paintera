package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.util.StringConverter
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.Paintera2
import org.slf4j.LoggerFactory
import java.lang.Double
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.function.DoublePredicate
import java.util.function.IntPredicate

class ScreenScalesConfigNode() {

	constructor(config: ScreenScalesConfig): this() {
		bind(config)
	}

    private val screenScales = SimpleObjectProperty(ScreenScalesConfig.ScreenScales(1.0, 0.5))

    val contents: Node

    init {
        this.contents = createContents()
    }

    fun bind(config: ScreenScalesConfig) {
        this.screenScales.bindBidirectional(config.screenScalesProperty())
    }

    private fun createContents(): Node {
        val screenScalesField = ObjectField<ScreenScalesConfig.ScreenScales, ObjectProperty<ScreenScalesConfig.ScreenScales>>(
                screenScales,
                ScreenScalesStringConverter(),
                ObjectField.SubmitOn.ENTER_PRESSED
        )
        screenScalesField.textField().tooltip = Tooltip(
                "Comma separated list of at least one screen-scale(s), monotonically decreasing and in the half-closed interval (0, 1]"
        )
        val geometricSequenceButton = MenuItem("From Geometric Sequence")
        geometricSequenceButton.setOnAction { fromGeometricSequence().showAndWait().ifPresent { screenScales.set(it) } }
        val setButton = MenuButton("Set", null, geometricSequenceButton)

        return TitledPanes.createCollapsed("Screen Scales", HBox(screenScalesField.textField(), setButton))
    }

    private class ScreenScalesStringConverter : StringConverter<ScreenScalesConfig.ScreenScales>() {

        override fun toString(scales: ScreenScalesConfig.ScreenScales): String {
            val scalesArray = scales.scalesCopy
            val sb = StringBuilder().append(scalesArray[0])
            for (i in 1 until scalesArray.size) {
                sb.append(", ").append(scalesArray[i])
            }
            return sb.toString()
        }

        override fun fromString(string: String): ScreenScalesConfig.ScreenScales {

            try {
                val scales = Arrays
                        .stream(string.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                        .map { it.trim { it <= ' ' } }
						.mapToDouble { Double.parseDouble(it) }
						.toArray()

                if (scales.isEmpty())
                    throw ObjectField.InvalidUserInput("Need at least one screen scale.")

                if (Arrays.stream(scales).filter { s -> s <= 0.0 || s > 1.0 }.count() > 0)
                    throw ObjectField.InvalidUserInput("All scales must be in the half closed interval (0, 1]")

                for (i in 1 until scales.size) {
                    if (scales[i] >= scales[i - 1])
                        throw ObjectField.InvalidUserInput("Scales must be strictly monotonically decreasing!")
                }

                LOG.debug("Setting scales: {}", scales)
                return ScreenScalesConfig.ScreenScales(*scales)

            } catch (e: Exception) {
                throw ObjectField.InvalidUserInput("Invalid screen scale supplied.", e)
            }

        }
    }


    fun screenScalesProperty(): ObjectProperty<ScreenScalesConfig.ScreenScales> {
        return this.screenScales
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        private fun fromGeometricSequence(): Dialog<ScreenScalesConfig.ScreenScales> {
            val d = Dialog<ScreenScalesConfig.ScreenScales>()
            d.title = Paintera2.Constants.NAME
            d.headerText = "Set N screen scales from geometric sequence: a_n = a * f^n"

            val aField = NumberField.doubleField(1.0, DoublePredicate { it <= 1.0 && it > 0 }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
            val fField = NumberField.doubleField(0.5, DoublePredicate { it < 1.0 && it > 0 }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
            val NField = NumberField.intField(5, IntPredicate { it > 0 }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)

            val grid = GridPane()

            grid.add(Label("a"), 0, 0)
            grid.add(Label("f"), 0, 1)
            grid.add(Label("N"), 0, 2)

            grid.add(aField.textField(), 1, 0)
            grid.add(fField.textField(), 1, 1)
            grid.add(NField.textField(), 1, 2)


            d.dialogPane.buttonTypes.add(ButtonType.OK)
            d.dialogPane.buttonTypes.add(ButtonType.CANCEL)
            d.dialogPane.content = grid

            d.setResultConverter { bt ->
                if (ButtonType.OK == bt) {
                    val screenScales = DoubleArray(NField.valueProperty().get())
                    screenScales[0] = aField.valueProperty().get()
                    for (i in 1 until screenScales.size)
                        screenScales[i] = screenScales[i - 1] * fField.valueProperty().get()
					ScreenScalesConfig.ScreenScales(*screenScales)
                } else
                	null
            }

            return d
        }
    }
}
