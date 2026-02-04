package org.janelia.saalfeldlab.paintera.ui

import javafx.animation.*
import javafx.application.Preloader
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Duration
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import kotlin.math.roundToInt

open class SplashScreenNotification : Preloader.PreloaderNotification
class SplashScreenFinishPreloader : SplashScreenNotification()
class SplashScreenUpdateNumItemsNotification @JvmOverloads constructor(val numItems: Int, val increment: Boolean = false) : SplashScreenNotification()
class SplashScreenUpdateNotification @JvmOverloads constructor(val text: String, val updateProgress: Boolean = true) : SplashScreenNotification()

class PainteraSplashScreen : Preloader() {
	companion object {

		private val SPLASH_SCREEN_RES = "/icon-256.png"

		private fun wrapHBoxAndCenter(node: Node, width: Double = 512.0): HBox {
			node.also { HBox.setHgrow(it, Priority.ALWAYS) }
			return HBox().apply {
				VBox.setVgrow(this, Priority.ALWAYS)
				alignment = Pos.CENTER
				prefWidth = width
				children += node
			}
		}

		// Helper method
		private fun doubleToHex(rgbComponent: Double): String {
			val asHex = Integer.toHexString((rgbComponent * 255).roundToInt())
			return if (asHex.length == 1) "0${asHex}" else asHex
		}

		fun colorToHex(color: Color): String {
			with(color) {
				return "#${doubleToHex(red)}${doubleToHex(green)}${doubleToHex(blue)}${doubleToHex(opacity)}"
			}
		}
	}

	private var finished: Boolean = false

	private lateinit var stage: Stage

	/* We need this to ensure that the progress bar only ever increases.
	 *  Otherwise, we end up in a situation where a previous Timeline may not be
	 *  stopped yet, but the next one begins, and the animation jumps around a bit.
	 *  This way is more robust than try to synchronize the Timelines, but doesn't allow your
	 *  progress to revert. May need to reconsider if that becomes desirable. */
	private val unidirectionalProgressProperty = object : SimpleDoubleProperty() {

		override fun set(newValue: Double) {
			if (newValue >= this.get()) {
				super.set(newValue)
			}
		}
	}

	private val progressBar = ProgressBar(0.0).apply {
		progressProperty().bind(unidirectionalProgressProperty)
	}

	private val progressLabel = Label().apply { textFill = Color.WHITESMOKE }
	var progressText: String by progressLabel.textProperty().nonnull()

	private val numItemsProperty = SimpleIntegerProperty(0)
	var numItems by numItemsProperty.nonnull()

	private val curItemNumProperty = SimpleIntegerProperty()
	var curItemNum by curItemNumProperty.nonnull()


	override fun handleApplicationNotification(info: PreloaderNotification) {
		if (finished)
			return
		when (info) {
			is SplashScreenUpdateNumItemsNotification -> {
				if (info.increment) {
					numItems += info.numItems
				} else {
					numItems = info.numItems
				}
			}

			is SplashScreenUpdateNotification -> {
				if (info.updateProgress) {
					updateText(info.text)
				} else {
					setText(info.text)
				}
			}

			is SplashScreenFinishPreloader -> {
				finish()
			}

			else -> super.handleApplicationNotification(info)
		}
	}

	private val currentAnimationProperty = SimpleObjectProperty<Timeline>().apply {
		addListener { _, prev, next ->
			if (!finished) {
				prev?.let {
					if (prev.statusProperty().get() != Animation.Status.STOPPED) {
						prev.setOnFinished { next.play() }
					} else {
						prev.stop()
						next.play()
					}
				} ?: run {
					next?.play()
				}
			}
		}
	}
	private var currentAnimation by currentAnimationProperty.nullable()


	private fun updateProgress(progress: Double) {
		if (progressBar.progressProperty().get() < 0.0) {
			progressBar.progressProperty().set(0.0)
		}
		currentAnimation = Timeline().apply {
			val keyValue = KeyValue(unidirectionalProgressProperty, progress, Interpolator.EASE_OUT)
			val keyFrame = KeyFrame(Duration(500.0), keyValue)
			keyFrames += keyFrame
		}
	}

	private val splashImg = ImageView(SPLASH_SCREEN_RES).also { HBox.setHgrow(it, Priority.ALWAYS) }
	private val root = VBox()

	var backgroundColor: Color = Color.valueOf("#444444")

	private fun finalizeView() {
		root.apply {
			alignment = Pos.CENTER
			roundClip(this)
			style = """
                -fx-background-color: ${colorToHex(backgroundColor)};
                -fx-background-radius: 30;
            """.trimIndent()

			children += wrapHBoxAndCenter(splashImg).also { it.style = "-fx-background-color: ${colorToHex(backgroundColor)};" }
			children += wrapHBoxAndCenter(progressBar)
			children += wrapHBoxAndCenter(progressLabel).also {
				it.prefHeight = 25.0
				roundClip(it)
			}

			minHeightProperty().bind(splashImg.fitHeightProperty())
			minWidthProperty().bind(splashImg.fitWidthProperty().createNonNullValueBinding { it.toDouble() * 2 })
		}
		progressBar.prefWidth = 512.0
	}

	private fun roundClip(it: Region, clipArc: Double = 30.0) {
		it.clip = Rectangle().apply {
			widthProperty().bind(it.widthProperty())
			heightProperty().bind(it.heightProperty())
			arcWidth = clipArc
			arcHeight = clipArc
		}
	}

	private fun finish() {
		/* If done, or not started, close; Otherwise, update to done, and then close */
		if (progressBar.progressProperty().get() >= 1.0 || currentAnimation == null || currentAnimation?.status == Animation.Status.STOPPED) {
			fadeAndClose()
		} else {
			progressBar.progressProperty().addListener { _, _, newv ->
				if (newv.toDouble() >= 1.0) {
					fadeAndClose()
				}
				updateProgress(1.0)
			}
		}
		finished = true
	}

	private fun fadeAndClose() {
		FadeTransition(Duration(750.0)).apply {
			fromValue = 1.0
			toValue = 0.0
			node = root
		}.play()
		root.opacityProperty().addListener { _, _, new ->
			if (new.toDouble() <= 0.0) {
				stage.close()
			}
		}
	}

	override fun handleStateChangeNotification(info: StateChangeNotification) {
		if (info.type == StateChangeNotification.Type.BEFORE_START) {
			finish()
		}
	}

	private fun setText(message: String) {
		progressText = message
	}

	private fun updateText(text: String) {
		InvokeOnJavaFXApplicationThread {
			setText(text)
			if (curItemNum != numItems) {
				curItemNum++
				updateProgress(curItemNum.toDouble() / numItems)
			}
		}
	}

	override fun start(primaryStage: Stage) {
		stage = primaryStage
		stage.initStyle(StageStyle.TRANSPARENT)
		stage.isResizable = false
		val scene = Scene(root, Color.TRANSPARENT)
		stage.scene = scene
		finalizeView()
		stage.show()
	}
}


