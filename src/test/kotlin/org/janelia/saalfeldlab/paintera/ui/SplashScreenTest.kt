package org.janelia.saalfeldlab.paintera.ui

import javafx.application.Application
import javafx.application.Preloader
import javafx.application.Preloader.StateChangeNotification.Type.BEFORE_START
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.paintera.*
import org.janelia.saalfeldlab.paintera.ApplicationTestUtils.launchApplicationWithPreloader
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SplashScreenTest {

	class SplashScreenApp : Application(),  LocalPreloader {

		override var preloader: Preloader? = null

		override fun init() {
			ApplicationTestUtils.run {
				Tasks.createTask {
					preloader!!.handleApplicationNotification(SplashScreenUpdateNumItemsNotification(10))
					for (i in 0..10) {
						Thread.sleep(100)
						preloader!!.handleApplicationNotification(SplashScreenUpdateNotification("$i / 10"))
					}
					"Done!"
				}.onEnd { _, _ ->
					preloader!!.notifyStateChange(BEFORE_START)
				}.get()
			}
		}

		override fun start(primaryStage: Stage) {}
	}

	@ParameterizedTest
	@MethodSource
	fun `Test Splash Screen`(app : SplashScreenTestApp) {
		assertEquals(app.preloader.curItemNum, 10, "process 10 progress notifications")
		assertEquals(app.preloader.numItems, 10, "num items 10")
	}


	fun `Test Splash Screen`() : Stream<SplashScreenTestApp> {
		return Stream.of( SplashScreenTestApp() )
	}

	class SplashScreenTestApp(val testApp : TestApplication<SplashScreenApp> = launchApplicationWithPreloader<SplashScreenApp, PainteraSplashScreen, TestApplication<SplashScreenApp>>()) {
		val preloader = testApp.preloader as PainteraSplashScreen
	}
}
