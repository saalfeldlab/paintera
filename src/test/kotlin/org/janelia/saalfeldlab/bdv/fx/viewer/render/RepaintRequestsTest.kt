package org.janelia.saalfeldlab.bdv.fx.viewer.render

import net.imglib2.FinalInterval
import net.imglib2.Interval
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException

class RepaintRequestsTest {

	private val failure = IllegalStateException("paint failed")

	private val screen = interval(0, 0, 99, 99)
	private val left = interval(0, 0, 9, 9)
	private val right = interval(50, 50, 59, 59)

	private fun interval(minX: Long, minY: Long, maxX: Long, maxY: Long): Interval = FinalInterval(longArrayOf(minX, minY), longArrayOf(maxX, maxY))

	@Test
	fun `a request is not completed before a paint has started on it`() {
		val repaints = RepaintRequests()
		val request = repaints.request(screen)

		/* a paint that started before the request */
		repaints.painted(screen)
		assertFalse(request.isDone)

		repaints.paintStarted(screen)
		assertFalse(request.isDone)
		repaints.painted(screen)
		assertTrue(request.isDone)
		assertFalse(request.isCompletedExceptionally)
	}

	@Test
	fun `requests made before a paint starts share it and need their union painted`() {
		val repaints = RepaintRequests()
		val request = repaints.request(left)
		assertSame(request, repaints.request(right))
		repaints.paintStarted(screen)
		assertNotSame(request, repaints.request(left))

		repaints.painted(left)
		assertFalse(request.isDone)
		repaints.painted(screen)
		assertTrue(request.isDone)
	}

	@Test
	fun `a later request does not delay an earlier one`() {
		val repaints = RepaintRequests()
		val first = repaints.request(screen)
		repaints.paintStarted(screen)
		val second = repaints.request(screen)

		repaints.painted(screen)
		assertTrue(first.isDone)
		assertFalse(second.isDone)

		repaints.paintStarted(screen)
		repaints.painted(screen)
		assertTrue(second.isDone)
	}

	@Test
	fun `a request stays open until its whole interval is painted`() {
		val repaints = RepaintRequests()
		val request = repaints.request(screen)
		repaints.paintStarted(screen)

		repaints.painted(left)
		assertFalse(request.isDone)
		repaints.painted(screen)
		assertTrue(request.isDone)
	}

	@Test
	fun `a failed paint fails the started requests it overlaps`() {
		val repaints = RepaintRequests()
		val overlapping = repaints.request(left)
		repaints.paintStarted(left)
		val elsewhere = repaints.request(right)
		repaints.paintStarted(right)
		val waiting = repaints.request(left)

		repaints.failed(left, failure)
		assertSame(failure, assertThrows(ExecutionException::class.java) { overlapping.get() }.cause)
		assertFalse(elsewhere.isDone)
		assertFalse(waiting.isDone)
	}

	@Test
	fun `a paint that does not cover a request does not start it`() {
		val repaints = RepaintRequests()
		val request = repaints.request(screen)
		repaints.paintStarted(left)

		/* not started, so an overlapping failure does not reach it */
		repaints.failed(left, failure)
		assertFalse(request.isDone)

		repaints.paintStarted(screen)
		repaints.painted(screen)
		assertTrue(request.isDone)
		assertFalse(request.isCompletedExceptionally)
	}

	@Test
	fun `a paint that fails before taking an interval fails every request`() {
		val repaints = RepaintRequests()
		val started = repaints.request(left)
		repaints.paintStarted(left)
		val waiting = repaints.request(right)

		repaints.failed(null, failure)
		assertSame(failure, assertThrows(ExecutionException::class.java) { started.get() }.cause)
		assertSame(failure, assertThrows(ExecutionException::class.java) { waiting.get() }.cause)
	}

	@Test
	fun `cancel cancels started and waiting requests`() {
		val repaints = RepaintRequests()
		val started = repaints.request(left)
		repaints.paintStarted(left)
		val waiting = repaints.request(right)

		repaints.cancel()
		assertTrue(started.isCancelled)
		assertTrue(waiting.isCancelled)
	}
}
