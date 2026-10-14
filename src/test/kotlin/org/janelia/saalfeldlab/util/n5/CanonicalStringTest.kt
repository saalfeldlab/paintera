package org.janelia.saalfeldlab.util.n5

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalStringTest {

	/** the canonical string is a `file://` URI of the canonical path of [file] */
	private fun assertCanonicalFileUri(file: File, canonical: String) {
		assertTrue(canonical.startsWith("file://"), canonical)
		val uri = URI(canonical)
		assertNull(uri.fragment)
		assertNull(uri.query)
		assertEquals(file.canonicalPath, File(uri).canonicalPath)
	}

	@Test
	fun `remote locations are unchanged`() {
		listOf(
			"s3://bucket/path/to/container.n5",
			"gs://bucket/path/container.zarr",
			"https://example.org/data/container.n5",
			"http://example.org:8080/a%20b/container.n5",
			"s3://bucket/with%20escape/x",
		).forEach { remote ->
			assertEquals(remote, N5Helpers.canonicalString(URI.create(remote)))
			assertEquals(remote, N5Helpers.canonicalString(remote))
			assertEquals(remote, N5Helpers.displayString(URI.create(remote)))
			assertEquals(remote, N5Helpers.displayString(remote))
		}
	}

	@Test
	fun `file locations are canonical file uris`(@TempDir tmp: Path) {
		val container = tmp.resolve("container.n5").toFile().apply { mkdirs() }
		assertCanonicalFileUri(container, N5Helpers.canonicalString(container.toURI()))
		assertCanonicalFileUri(container, N5Helpers.canonicalString(container.absolutePath))
		assertCanonicalFileUri(container, N5Helpers.canonicalString(container.toURI().toString()))
	}

	@Test
	fun `display string of a file location is its path without the scheme`(@TempDir tmp: Path) {
		val container = tmp.resolve("container.n5").toFile().apply { mkdirs() }
		assertEquals(container.canonicalPath, N5Helpers.displayString(container.toURI()))
		assertEquals(container.canonicalPath, N5Helpers.displayString(container.absolutePath))
		assertEquals(container.canonicalPath, N5Helpers.displayString(container.toURI().toString()))
	}

	@Test
	fun `dot segments are normalized`(@TempDir tmp: Path) {
		val container = tmp.resolve("a/container.n5").toFile().apply { mkdirs() }
		val withDots = File(tmp.toFile(), "a/../a/./container.n5")
		assertCanonicalFileUri(container, N5Helpers.canonicalString(withDots.toURI()))
		assertEquals(container.canonicalPath, N5Helpers.displayString(withDots.absolutePath))
	}

	@Test
	fun `special characters in a path are part of the path`(@TempDir tmp: Path) {
		listOf("has space", "has#hash", "has?question", "has%percent", "has%20literal", "a#b?c").forEach { name ->
			val container = File(tmp.toFile(), "$name/container.n5").apply { mkdirs() }
			assertCanonicalFileUri(container, N5Helpers.canonicalString(container.absolutePath))
			assertCanonicalFileUri(container, N5Helpers.canonicalString(container.toURI()))
			assertEquals(container.canonicalPath, N5Helpers.displayString(container.absolutePath))
		}
	}

	@Test
	fun `a path with nothing at it still resolves`(@TempDir tmp: Path) {
		val missing = File(tmp.toFile(), "does/not/exist/container.n5")
		assertTrue(!missing.exists())
		assertCanonicalFileUri(missing, N5Helpers.canonicalString(missing.toURI()))
		assertEquals(missing.canonicalPath, N5Helpers.displayString(missing.absolutePath))
	}

	@Test
	fun `a path that exists later has the same canonical string`(@TempDir tmp: Path) {
		val container = File(tmp.toFile(), "container.n5")
		val before = N5Helpers.canonicalString(container.absolutePath)
		container.mkdirs()
		assertEquals(before, N5Helpers.canonicalString(container.absolutePath))
	}

	@Test
	fun `resolving twice gives the same location`(@TempDir tmp: Path) {
		val once = N5Helpers.canonicalString(File(tmp.toFile(), "with #hash/container.n5").absolutePath)
		assertEquals(once, N5Helpers.canonicalString(once))
		assertEquals(once, N5Helpers.canonicalString(URI(once)))
		val display = N5Helpers.displayString(once)
		assertEquals(display, N5Helpers.displayString(display))
		assertEquals(once, N5Helpers.canonicalString(display))
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	fun `unix absolute path without a scheme resolves`(@TempDir tmp: Path) {
		val container = File(tmp.toFile(), "container.n5").apply { mkdirs() }
		assertCanonicalFileUri(container, N5Helpers.canonicalString(URI(null, null, container.absolutePath, null)))
	}

	@Test
	fun `relative uri is unchanged`() {
		val relative = "relative/path/container.n5"
		assertEquals(relative, N5Helpers.canonicalString(URI.create(relative)))
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	fun `windows drive path parsed as a one letter scheme resolves to a file uri`() {
		val drivePath = "C:/paintera-test-missing/container.n5"
		val drive = URI.create(drivePath)
		assertEquals("c", drive.scheme.lowercase())
		assertCanonicalFileUri(File(drivePath), N5Helpers.canonicalString(drive))
		assertCanonicalFileUri(File(drivePath), N5Helpers.canonicalString(drivePath))
		assertCanonicalFileUri(File(drivePath), N5Helpers.canonicalString(drivePath.replace('/', '\\')))
		assertEquals(File(drivePath).canonicalPath, N5Helpers.displayString(drivePath))
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	fun `windows file uri with a drive resolves`(@TempDir tmp: Path) {
		val container = File(tmp.toFile(), "container.n5").apply { mkdirs() }
		assertCanonicalFileUri(container, N5Helpers.canonicalString(container.toURI()))
		assertEquals(container.canonicalPath, N5Helpers.displayString(container.toURI()))
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	fun `windows drive path and its file uri resolve to the same location`(@TempDir tmp: Path) {
		val container = File(tmp.toFile(), "container.n5").apply { mkdirs() }
		val asDrivePath = URI.create(container.absolutePath.replace('\\', '/'))
		assertEquals(N5Helpers.canonicalString(container.toURI()), N5Helpers.canonicalString(asDrivePath))
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	fun `windows remote locations stay unchanged`() {
		val remote = "s3://bucket/key/container.n5"
		assertEquals(remote, N5Helpers.canonicalString(URI.create(remote)))
		assertEquals(remote, N5Helpers.displayString(remote))
	}
}
