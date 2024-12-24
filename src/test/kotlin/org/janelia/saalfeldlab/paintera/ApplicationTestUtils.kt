package org.janelia.saalfeldlab.paintera

import com.sun.javafx.application.ParametersImpl
import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.stage.StageHelper
import javafx.application.Application
import javafx.application.Platform
import javafx.application.Preloader
import javafx.application.Preloader.ProgressNotification
import javafx.application.Preloader.StateChangeNotification.Type.*
import javafx.stage.Stage
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.Companion.invokeAndWait
import java.util.stream.Stream
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

object ApplicationTestUtils {


	lateinit var spinIdle : Runnable
	val initSpinIdle by lazy {
		/* Use this to stop the Toolkit from shutting down when no windows are open
		* between tests. It will stay alive so long as there are pendingRunnables */
		spinIdle = Runnable {
			Platform.runLater(spinIdle)
		}
		spinIdle.run()
		true
	}

	inline fun <reified P : Preloader> launchPreloader(args: Array<String> = emptyArray()): TestApplication<P> {

		return launchApplication<P, TestApplication<P>>(null, args = args).also {
			it.preloader.notifyProgress(0.0)
		}
	}

	fun Preloader?.notifyProgress(progress: Double) {
		this?.apply {
			invokeAndWait {
				handleProgressNotification(ProgressNotification(progress))
			}
		}
	}

	fun Preloader?.notifyStateChange(state: Preloader.StateChangeNotification.Type, app: Application? = null) {
		this?.apply {
			invokeAndWait {
				handleStateChangeNotification(Preloader.StateChangeNotification(state, app))
			}
		}
	}


	inline fun <reified T : Application, reified A : TestApplication<T>> launchApplication(
		preloader: Preloader? = null,
		stage: Stage? = null,
		args: Array<String> = emptyArray()
	): A {
		val appClass = T::class.java

		preloader.notifyProgress(1.0)
		preloader.notifyStateChange(BEFORE_LOAD)

		lateinit var app: T
		invokeAndWait {
			app = appClass.getConstructor().newInstance()
			ParametersImpl.registerParameters(app, ParametersImpl(args))
			PlatformImpl.setApplicationName(appClass)
		}
		preloader.notifyStateChange(BEFORE_INIT, app)
		(app as? LocalPreloader)?.preloader = preloader
		app.init()
		preloader.notifyStateChange(BEFORE_START, app)
		lateinit var primaryStage: Stage
		invokeAndWait {
			primaryStage = (stage ?: Stage())
			StageHelper.setPrimary(primaryStage, true)
			app.start(primaryStage)
		}
		initSpinIdle
		return A::class.constructors.first().call(app, primaryStage)
	}

	inline fun <reified T : Application, reified P : Preloader, reified A : TestApplication<T>> launchApplicationWithPreloader(
		args: Array<String> = emptyArray()
	): A {
		val (preloader, preloaderStage) = launchPreloader<P>(args)
		return launchApplication<T, A>(preloader, preloaderStage, args)
	}


	inline fun <reified T : Application, reified A : TestApplication<T>> launchApplication(args: Array<String> = emptyArray()) = launchApplication<T, A>(null, null, args)

	@JvmStatic
	fun painteraTestApp() : PainteraTestApplication = launchApplication<Paintera, PainteraTestApplication>()

	@JvmStatic
	fun painteraTestAppParameter(): Stream<PainteraTestApplication> = Stream.of(painteraTestApp())
}

open class TestApplication<T : Application>(val app : T, val stage : Stage) : AutoCloseable{
	val preloader = (app as? LocalPreloader)?.preloader

	operator fun component1() = app
	operator fun component2() = stage
	operator fun component3() = preloader

	override fun close() = invokeAndWait {
		app.stop()
		stage.close()
	}
}

class PainteraTestApplication(paintera : Paintera, stage: Stage) : TestApplication<Paintera>(paintera, stage) {
	init {
		paintera.mainWindow.wasQuit = true
	}
}


interface LocalPreloader {
	var preloader: Preloader?
}