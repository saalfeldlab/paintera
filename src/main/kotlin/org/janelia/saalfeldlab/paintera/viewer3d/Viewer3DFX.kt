package org.janelia.saalfeldlab.paintera.viewer3d

import bdv.viewer.TransformListener
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.embed.swing.SwingFXUtils
import javafx.scene.*
import javafx.scene.control.ContextMenu
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.transform.Affine
import javafx.scene.transform.Transform
import javafx.scene.transform.Translate
import javafx.stage.FileChooser
import javafx.util.Duration
import kotlinx.coroutines.runBlocking
import net.imglib2.Interval
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems
import org.janelia.saalfeldlab.util.SimilarityTransformInterpolator
import org.janelia.saalfeldlab.util.fx.Transforms
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class Viewer3DFX(width: Double, height: Double) : Pane() {
	private val root = Group()
	val meshesGroup = Group()
	val sceneGroup = Group().apply { children += meshesGroup }
	private val backgroundFill: ObjectProperty<Color> = SimpleObjectProperty(Color.BLACK)
	val scene = SubScene(root, width, height, true, SceneAntialiasing.BALANCED).also {
		it.fillProperty().bind(backgroundFill)
		it.widthProperty().bind(widthProperty())
		it.heightProperty().bind(heightProperty())
	}
	private val camera = PerspectiveCamera(true).also {
		it.nearClip = 0.01
		it.farClip = 10.0
		it.translateY = 0.0
		it.translateX = 0.0
		it.translateZ = 0.0
		it.fieldOfView = 90.0
		scene.camera = it
	}
	private val lightAmbient = AmbientLight(Color(0.1, 0.1, 0.1, 1.0))
	private val lightSpot = PointLight(Color(1.0, 0.95, 0.85, 1.0)).apply {
		translateX = -10.0
		translateY = -10.0
		translateZ = -10.0
	}
	private val lightFill = PointLight(Color(0.35, 0.35, 0.65, 1.0)).apply { translateX = 10.0 }
	private val handler = Scene3DHandler(this)

	private val cameraTransform: Transform = Translate(0.0, 0.0, -1.0)
	private val cameraGroup = Group().also {
		it.children.addAll(camera, lightAmbient, lightSpot, lightFill)
		it.transforms += cameraTransform
	}

	val viewFrustumProperty: ObjectProperty<ViewFrustum> = SimpleObjectProperty()
	val eyeToWorldTransformProperty: ObjectProperty<AffineTransform3D> = SimpleObjectProperty()
	val meshesEnabled: BooleanProperty = SimpleBooleanProperty()
	val showBlockBoundaries: BooleanProperty = SimpleBooleanProperty()
	val rendererBlockSize: IntegerProperty = SimpleIntegerProperty()
	val numElementsPerFrame: IntegerProperty = SimpleIntegerProperty()
	val frameDelayMsec: LongProperty = SimpleLongProperty()
	val sceneUpdateDelayMsec: LongProperty = SimpleLongProperty()

	val contextMenu by lazy { setupContextMenu() }

	init {
		this.width = width
		this.height = height
		children += scene
		this.root.children.addAll(cameraGroup, sceneGroup)
		this.root.visibleProperty().bind(meshesEnabled)
		val cameraAffineTransform = Transforms.fromTransformFX(cameraTransform)
		handler.addAffineListener { sceneTransform: Affine? ->
			val sceneToWorldTransform = Transforms.fromTransformFX(sceneTransform).inverse()
			eyeToWorldTransformProperty.set(sceneToWorldTransform.concatenate(cameraAffineTransform))
		}
		val sizeChangedListener = InvalidationListener { obs: Observable? ->
			viewFrustumProperty.set(
				ViewFrustum(camera, doubleArrayOf(getWidth(), getHeight()))
			)
		}
		widthProperty().addListener(sizeChangedListener)
		heightProperty().addListener(sizeChangedListener)

		// set initial value
		sizeChangedListener.invalidated(null)
	}

	private fun setupContextMenu(): ContextMenu {
		val contextMenu = ContextMenu()
		contextMenu.items.addAll(
			PainteraMenuItems.RESET_3D_LOCATION.menu,
			PainteraMenuItems.CENTER_3D_LOCATION.menu,
			PainteraMenuItems.SAVE_3D_PNG.menu
		)
		contextMenu.isAutoHide = true
		return contextMenu
	}

	fun saveAsPng() {
		val image = scene.snapshot(SnapshotParameters(), null)
		val fileChooser = FileChooser()
		fileChooser.title = "Save 3d snapshot "
		val fileProperty = SimpleObjectProperty<File?>()

		runBlocking {
			InvokeOnJavaFXApplicationThread {
				val showSaveDialog = fileChooser.showSaveDialog(root.sceneProperty().get().window)
				fileProperty.set(showSaveDialog)
			}.apply {
				invokeOnCompletion { cause ->
					cause?.let { LOG.error(it) { } }
				}
			}.join()
		}

		fileProperty.get()?.let { file ->
			if (!file.name.endsWith(".png")) {
				val png = File(file.absolutePath + ".png")
				try {
					ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", png)
				} catch (e: IOException) {
					e.printStackTrace()
				}

			}
		}
	}

	fun setInitialTransformToInterval(interval: Interval?) {
		handler.setInitialTransformToInterval(interval)
	}


	fun setAffine(affine: Affine?, duration: Duration) {
		if (duration.toMillis() == 0.0) {
			setAffine(affine)
			return
		}
		val timeline = Timeline(60.0)
		timeline.cycleCount = 1
		timeline.isAutoReverse = false
		val currentState = Affine()
		getAffine(currentState)
		val progressProperty: DoubleProperty = SimpleDoubleProperty(0.0)
		val interpolator = SimilarityTransformInterpolator(
			Transforms.fromTransformFX(currentState),
			Transforms.fromTransformFX(affine)
		)
		progressProperty.addListener { _: ObservableValue<out Number>?, _: Number, new: Number ->
			val interpolatedTransform = Transforms.toTransformFX(interpolator[new.toDouble()])
			setAffine(interpolatedTransform)
		}
		val kv = KeyValue(progressProperty, 1.0, Interpolator.EASE_BOTH)
		timeline.keyFrames.add(KeyFrame(duration, kv))
		timeline.play()
	}

	fun getAffine(target: Affine?) {
		handler.getAffine(target)
	}

	fun setAffine(affine: Affine?) {
		handler.setAffine(affine)
	}

	fun addAffineListener(listener: TransformListener<Affine?>?) {
		handler.addAffineListener(listener)
	}

	fun backgroundFillProperty(): ObjectProperty<Color> {
		return backgroundFill
	}

	fun reset3DAffine() {
		handler.resetAffine()
	}

	fun center3DAffine() {
		handler.centerAffine()
	}

	companion object {
		val LOG = KotlinLogging.logger { }
	}
}
