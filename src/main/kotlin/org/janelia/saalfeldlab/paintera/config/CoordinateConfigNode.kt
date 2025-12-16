package org.janelia.saalfeldlab.paintera.config

import bdv.viewer.TransformListener
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.navigation.OrthogonalCrossSectionsIntersect
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

class CoordinateConfigNode() {

	constructor(manager: GlobalTransformManager) : this() {
		listen(manager)
	}

	private val x = TextField().apply { isEditable = false }

	private val y = TextField().apply { isEditable = false }

	private val z = TextField().apply { isEditable = false }

	private val label = Label("Center")

	private val setCenterButton = Button("Set")

	private val contents = VBox()

	private val listener = TransformListener<AffineTransform3D> { this.update(it) }

	private val transform = AffineTransform3D()

	private var submitTransform = Consumer<AffineTransform3D> {}

	init {

		contents.apply {
			isFillWidth = true
			children += Label("World Coordinates:")
			children += HBox().apply {
				children += label.hGrow(Priority.NEVER) {
					minWidth = Region.USE_PREF_SIZE
				}
				alignment = Pos.BASELINE_CENTER
				children += x.hGrow()
				children += y.hGrow()
				children += z.hGrow()
				children += setCenterButton.hGrow(Priority.NEVER) {
					minWidth = Region.USE_PREF_SIZE
				}
				spacing = 5.0
				maxWidth = Double.MAX_VALUE
				prefWidth = Region.USE_COMPUTED_SIZE
			}
		}

		setCenterButton.setOnAction { event ->

			val gp = GridPane()
			val d = Dialog<DoubleArray>()
			val x = TextField()
			val y = TextField()
			val z = TextField()

			x.promptText = "x"
			y.promptText = "y"
			z.promptText = "z"

			val lx = Label("x")
			val ly = Label("y")
			val lz = Label("z")

			gp.add(lx, 0, 0)
			gp.add(ly, 0, 1)
			gp.add(lz, 0, 2)

			gp.add(x, 1, 0)
			gp.add(y, 1, 1)
			gp.add(z, 1, 2)

			d.dialogPane.content = gp
			d.dialogPane.buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
			d.setResultConverter { bt ->
				bt.takeIf { it == ButtonType.OK }?.let {
					try {
						val coordinate = DoubleArray(3)
						coordinate[0] = java.lang.Double.parseDouble(x.text)
						coordinate[1] = java.lang.Double.parseDouble(y.text)
						coordinate[2] = java.lang.Double.parseDouble(z.text)
						coordinate
					} catch (e: Exception) {
						null
					}
				}
			}

			d.showAndWait().getOrNull()?.let { coordinate ->
				val transformCopy = this.transform.copy()
				OrthogonalCrossSectionsIntersect.centerAt(transformCopy, coordinate[0], coordinate[1], coordinate[2])
				submitTransform.accept(transformCopy)
			}

			event.consume()
		}

	}

	fun listen(manager: GlobalTransformManager) {
		manager.addListener(listener)
		val tf = AffineTransform3D()
		manager.getTransform(tf)
		listener.transformChanged(tf)
		submitTransform = Consumer { manager.setTransform(it) }
	}

	fun hangup(manager: GlobalTransformManager) {
		manager.removeListener(listener)
		submitTransform = Consumer {}
	}

	fun getContents(): Node {
		return contents
	}

	private fun update(tf: AffineTransform3D) {
		this.transform.set(tf)
		// TODO update transform ui
		updateCenter(tf)
	}

	private fun updateCenter(tf: AffineTransform3D) {
		LOG.debug("Updating center with transform {}", tf)
		val center = DoubleArray(3)
		OrthogonalCrossSectionsIntersect.getCenter(tf, center)
		LOG.debug("New center = {}", center)
		InvokeOnJavaFXApplicationThread {
			x.text = BigDecimal(center[0]).setScale(3, RoundingMode.HALF_EVEN).toDouble().toString()
			y.text = BigDecimal(center[1]).setScale(3, RoundingMode.HALF_EVEN).toDouble().toString()
			z.text = BigDecimal(center[2]).setScale(3, RoundingMode.HALF_EVEN).toDouble().toString()
		}
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

}
