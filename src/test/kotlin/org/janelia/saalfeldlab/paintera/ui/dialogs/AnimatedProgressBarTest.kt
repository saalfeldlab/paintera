package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.delay
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import kotlin.test.Test

class AnimatedProgressBarTest {

	@Test
	fun test() {
		val progressBinding = SimpleDoubleProperty(.1)
		InvokeOnJavaFXApplicationThread {
			val progressStringBinding = SimpleStringProperty("Another test " )
			val dialog = AnimatedProgressBarAlert("test", "this is a test", progressStringBinding, progressBinding)
			dialog.showAndWait()

		}
		InvokeOnJavaFXApplicationThread {
			progressBinding.value = .75
			progressBinding.value = 1.0
			progressBinding.value = 1.5
		}

		Thread.sleep(100000)
	}


}

fun main() {
	InvokeOnJavaFXApplicationThread {

	}
}