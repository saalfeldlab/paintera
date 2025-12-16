package org.janelia.saalfeldlab.paintera

import kotlinx.coroutines.runBlocking
import org.janelia.saalfeldlab.fx.ortho.OrthoViewerOptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.config.SideBarConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WindowDimensionsTest {

	@Test
	fun `test default window dimensions on startup`(@PainteraTestApp app: PainteraTestApplication) {
		waitForFxThread()
		val orthoViewerOptions = OrthoViewerOptions.options().values
		val defaultWidth = orthoViewerOptions.width * 2 + SideBarConfig().width
		val statusBarHeight = 45.0
		val defaultHeight = orthoViewerOptions.height * 2 + statusBarHeight
		assertEquals(defaultWidth, app.stage.width, 0.1, "Stage width should match default")
		assertEquals(defaultHeight, app.stage.height, 0.1, "Stage height should match default")
		assertFalse(app.stage.isFullScreen, "Stage should not be fullscreen by default")
	}

	@Test
	fun `test window properties match stage dimensions`(@PainteraTestApp app: PainteraTestApplication) {
		val painteraMainWindow = Paintera.getPaintera()

		waitForFxThread()
		val windowProps = painteraMainWindow.properties.windowProperties

		assertEquals(app.stage.width.toInt(), windowProps.width, "WindowProperties width should match stage")
		assertEquals(app.stage.height.toInt(), windowProps.height, "WindowProperties height should match stage")
		assertEquals(app.stage.isFullScreen, windowProps.isFullScreen, "WindowProperties fullscreen should match stage")
	}

	@Test
	fun `test window properties update when stage resizes`(@PainteraTestApp app: PainteraTestApplication) {
		val paintera = Paintera.getPaintera()

		InvokeOnJavaFXApplicationThread {
			app.stage.width = 1024.0
			app.stage.height = 768.0
		}

		waitForFxThread()

		val windowProps = paintera.properties.windowProperties

		assertEquals(1024, windowProps.width, "WindowProperties width should update after stage resize")
		assertEquals(768, windowProps.height, "WindowProperties height should update after stage resize")
	}

	@Test
	fun `test window properties update when entering fullscreen`(@PainteraTestApp app: PainteraTestApplication) {
		val paintera = Paintera.getPaintera()

		InvokeOnJavaFXApplicationThread {
			app.stage.isFullScreen = true
		}
		waitForFxThread()


		val windowProps = paintera.properties.windowProperties

		assertEquals(true, windowProps.isFullScreen, "WindowProperties fullscreen should update")
	}

	@Test
	fun `test window properties bidirectional binding`(@PainteraTestApp app: PainteraTestApplication) {
		val paintera = Paintera.getPaintera()

		// Change stage dimensions
		InvokeOnJavaFXApplicationThread {
			// Change stage dimensions
			val initialWidth1 = app.stage.width
			val initialHeight1 = app.stage.height
			// Change stage dimensions
			app.stage.width = initialWidth1 + 100
			// Change stage dimensions
			app.stage.height = initialHeight1 + 50
		}

		waitForFxThread()
		
		val windowProps = paintera.properties.windowProperties

		// Verify WindowProperties updated
		assertEquals(
			app.stage.width.toInt(), windowProps.width,
			"WindowProperties should reflect stage width changes"
		)
		assertEquals(
			app.stage.height.toInt(), windowProps.height,
			"WindowProperties should reflect stage height changes"
		)
	}

	private fun waitForFxThread() {
		runBlocking { InvokeOnJavaFXApplicationThread { }.join() }
	}
}
