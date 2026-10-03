package org.janelia.saalfeldlab.bdv.fx.viewer.render

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PainterThreadFxTest {

	@Test
	fun `a failed paint does not end the painter thread`() {
		val paints = AtomicInteger()
		val failedPaint = CountDownLatch(1)
		val nextPaint = CountDownLatch(1)
		val painterThread = PainterThreadFx {
			if (paints.incrementAndGet() == 1) {
				failedPaint.countDown()
				throw IllegalStateException("paint failed")
			}
			nextPaint.countDown()
		}
		painterThread.start()
		try {
			painterThread.requestRepaint()
			assertTrue(failedPaint.await(1, TimeUnit.SECONDS))
			painterThread.requestRepaint()
			assertTrue(nextPaint.await(1, TimeUnit.SECONDS)) { "the painter thread did not paint again after a failed paint" }
		} finally {
			painterThread.stopRendering()
		}
	}
}
