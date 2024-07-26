package org.janelia.saalfeldlab.paintera.ui

import javafx.application.Application
import javafx.application.Preloader
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.paintera.PainteraSplashScreen
import org.janelia.saalfeldlab.paintera.SplashScreenUpdateNotification
import org.janelia.saalfeldlab.paintera.SplashScreenUpdateNumItemsNotification
import org.junit.Test
import org.testfx.api.FxRobot
import org.testfx.api.FxToolkit


class SplashScreenTest : FxRobot() {

	@Test
	fun `Test Splash Screen`() {
		/* Simple test, just ensure the application doesn't crash. Should take a few seconds */
		System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)

		FxToolkit.registerPrimaryStage()
		val app = FxToolkit.setupApplication(SplashScreenApp::class.java, *arrayOf())

		FxToolkit.cleanupApplication(app)
	}
}

fun main(args: Array<String>) {
	System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)
	Application.launch(SplashScreenApp::class.java, *args)
}

class SplashScreenApp : Application() {

	override fun init() {
		Tasks.createTask {
			notifyPreloader(SplashScreenUpdateNumItemsNotification(10))
			for (i in 0..10) {
				Thread.sleep(250)
				notifyPreloader(SplashScreenUpdateNotification("$i / 10"))
			}
			"Done!"
		}.onEnd { _, _ ->
			notifyPreloader(Preloader.StateChangeNotification(Preloader.StateChangeNotification.Type.BEFORE_START))
		}.get()
	}

	override fun start(primaryStage: Stage) {
	}
}
