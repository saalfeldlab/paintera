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
import kotlinx.coroutines.runBlocking
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.ApplicationTestUtils.launchApplication
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.AnnotationConsumer
import java.util.stream.Stream

object ApplicationTestUtils {


	lateinit var spinIdle: Runnable
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
			runBlocking {
				InvokeOnJavaFXApplicationThread {
					handleProgressNotification(ProgressNotification(progress))
				}.join()
			}
		}
	}

	fun Preloader?.notifyStateChange(state: Preloader.StateChangeNotification.Type, app: Application? = null) {
		this?.apply {
			runBlocking {
				InvokeOnJavaFXApplicationThread {
					handleStateChangeNotification(Preloader.StateChangeNotification(state, app))
				}.join()
			}
		}
	}


	inline fun <reified T : Application, reified A : TestApplication<T>> launchApplication(
		preloader: Preloader? = null,
		stage: Stage? = null,
		args: Array<String> = emptyArray(),
	): A {
		val appClass = T::class.java

		preloader.notifyProgress(1.0)
		preloader.notifyStateChange(BEFORE_LOAD)

		lateinit var app: T
		runBlocking {
			InvokeOnJavaFXApplicationThread {
				app = appClass.getConstructor().newInstance()
				ParametersImpl.registerParameters(app, ParametersImpl(args))
				PlatformImpl.setApplicationName(appClass)
			}.join()
		}
		preloader.notifyStateChange(BEFORE_INIT, app)
		(app as? LocalPreloader)?.preloader = preloader
		app.init()
		preloader.notifyStateChange(BEFORE_START, app)
		lateinit var primaryStage: Stage
		runBlocking {
			InvokeOnJavaFXApplicationThread {
				primaryStage = (stage ?: Stage())
				StageHelper.setPrimary(primaryStage, true)
				app.start(primaryStage)
			}.join()
		}
		initSpinIdle
		return A::class.constructors.first().call(app, primaryStage)
	}

	inline fun <reified T : Application, reified P : Preloader, reified A : TestApplication<T>> launchApplicationWithPreloader(
		args: Array<String> = emptyArray(),
	): A {
		val (preloader, preloaderStage) = launchPreloader<P>(args)
		return launchApplication<T, A>(preloader, preloaderStage, args)
	}


	inline fun <reified T : Application, reified A : TestApplication<T>> launchApplication(args: Array<String> = emptyArray()) = launchApplication<T, A>(null, null, args)
}

open class TestApplication<T : Application>(val app: T, val stage: Stage) : AutoCloseable {
	val preloader = (app as? LocalPreloader)?.preloader

	operator fun component1() = app
	operator fun component2() = stage
	operator fun component3() = preloader

	override fun close() = runBlocking {
		InvokeOnJavaFXApplicationThread {

			app.stop()
			stage.close()
		}.join()
	}
}

class PainteraTestApplication(paintera: Paintera, stage: Stage) : TestApplication<Paintera>(paintera, stage) {
	internal var dontClose: Boolean = false

	init {
		paintera.mainWindow.wasQuit = true
	}

	override fun close() {
		if (dontClose) return
		super.close()
	}
}


interface LocalPreloader {
	var preloader: Preloader?
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest
@ArgumentsSource(PainteraTestAppProvider::class)
annotation class PainteraApplicationTest(val reuseInstance: Boolean = false)

class PainteraTestAppProvider : ArgumentsProvider, AnnotationConsumer<PainteraApplicationTest> {
	var reuseInstance = false

	override fun provideArguments(context: ExtensionContext?): Stream<out Arguments?>? {
		val app = when {
			reuseInstance && previousInstance != null -> previousInstance
			reuseInstance -> launchPaintera().also {
				it.dontClose = true
				previousInstance = it
			}

			else -> let {
				launchPaintera()
				previousInstance?.dontClose = false
				previousInstance?.close()
				previousInstance = null
			}
		}
		return Stream.of(Arguments.of(app))
	}

	override fun accept(t: PainteraApplicationTest) {
		reuseInstance = t.reuseInstance
	}

	companion object {
		private var previousInstance: PainteraTestApplication? = null

		@JvmStatic
		fun launchPaintera() = launchApplication<Paintera, PainteraTestApplication>()
	}
}