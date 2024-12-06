package org.janelia.saalfeldlab.paintera.config

import bdv.viewer.TransformListener
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.paintera.control.navigation.OrthogonalCrossSectionsIntersect
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager
import org.ojalgo.series.BasicSeries.coordinate
import org.reactfx.value.Val.orElse
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Consumer

class CoordinateConfigNode() {

	constructor(manager: GlobalTransformManager) : this() {
		listen(manager)
	}

	private val x = Label()

	private val y = Label()

	private val z = Label()

	private val label = Label("Center")

	private val setCenterButton = Button("Set")

	private val contents = GridPane()

	private val listener = TransformListener<AffineTransform3D> { this.update(it) }

	private val transform = AffineTransform3D()

	private var submitTransform = Consumer<AffineTransform3D> {}

	init {

		contents.add(label, 0, 0)
		contents.add(x, 1, 0)
		contents.add(y, 2, 0)
		contents.add(z, 3, 0)
		contents.add(setCenterButton, 4, 0)
		contents.hgap = 5.0

		GridPane.setHgrow(label, Priority.ALWAYS)

		x.maxWidth = 50.0
		y.maxWidth = 50.0
		z.maxWidth = 50.0
		setCenterButton.maxWidth = 50.0

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

			d.showAndWait().nullable?.let { coordinate ->
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
		// TODO do something better than rounding here
		x.text = java.lang.Long.toString(Math.round(center[0]))
		y.text = java.lang.Long.toString(Math.round(center[1]))
		z.text = java.lang.Long.toString(Math.round(center[2]))
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
	}

}
