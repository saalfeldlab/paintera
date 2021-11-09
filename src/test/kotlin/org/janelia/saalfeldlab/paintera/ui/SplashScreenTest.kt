package org.janelia.saalfeldlab.paintera.ui

import javafx.application.Application
import javafx.application.Preloader
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.Tasks
import org.janelia.saalfeldlab.paintera.PainteraSplashScreen
import org.janelia.saalfeldlab.paintera.SplashScreenShowPreloader
import org.janelia.saalfeldlab.paintera.SplashScreenUpdateNotification
import org.janelia.saalfeldlab.paintera.SplashScreenUpdateNumItemsNotification
import org.junit.Test


class SplashScreenTest {

    @Test
    fun `Test Splash Screen`() {
        /* Simple test, just ensure the application doesn't crash. Should take a few seconds */
        System.setProperty("javafx.preloader", PainteraSplashScreen::class.java.canonicalName)
        Application.launch(SplashScreenApp::class.java, *arrayOf())
    }
}

class SplashScreenApp : Application() {

    override fun init() {
        notifyPreloader(SplashScreenShowPreloader())
        Tasks.createTask<String> {
            notifyPreloader(SplashScreenUpdateNumItemsNotification(10))
            for (i in 0..10) {
                Thread.sleep(250)
                notifyPreloader(SplashScreenUpdateNotification("$i / 10"))
            }
            "Done!"
        }.onEnd {
            notifyPreloader(Preloader.StateChangeNotification(Preloader.StateChangeNotification.Type.BEFORE_START))
        }.submitAndWait()
    }

    override fun start(primaryStage: Stage) {
    }
}
