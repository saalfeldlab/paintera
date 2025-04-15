package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.javafx.awaitPulse
import kotlinx.coroutines.runBlocking
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class AnimatedProgressBarTest {

	@Test
	fun test() {
		val progressBinding = SimpleDoubleProperty(.1)
		runBlocking {
			lateinit var dialog: AnimatedProgressBarAlert
			InvokeOnJavaFXApplicationThread {
				val progressBinding = SimpleDoubleProperty(.1)
				val progressStringBinding = SimpleStringProperty("Another test ")
				dialog = AnimatedProgressBarAlert("test", "this is a test", progressStringBinding, progressBinding)
				dialog.showAndWait()
			}

			/* Dialog should not be able to close until progress bar is done (Or stop() is called)*/
			awaitPulse()
			InvokeOnJavaFXApplicationThread {
				dialog.close()
			}
			awaitPulse()
			assertTrue(dialog.isShowing)

			progressBinding.value = .75
			while (dialog.progressBar.progress < .75)
				awaitPulse()

			progressBinding.value = 1.0
			while (dialog.progressBar.progress < 1.0)
				awaitPulse()

			progressBinding.value = 1.5
			for (i in 0..100) {
				awaitPulse()
				assertTrue(dialog.progressBar.progress == 1.0)
			}

			InvokeOnJavaFXApplicationThread {
				dialog.close()
			}
			awaitPulse()

			assertTrue(dialog.isShowing)
		}
	}


}

fun main() {
	InvokeOnJavaFXApplicationThread {

	}
}