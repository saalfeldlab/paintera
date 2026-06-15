package org.janelia.saalfeldlab.util.n5.universe

import org.janelia.saalfeldlab.n5.N5KeyValueReader
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueWriter
import org.janelia.saalfeldlab.n5.zarr.v3.ZarrV3KeyValueReader
import org.janelia.saalfeldlab.n5.zarr.v3.ZarrV3KeyValueWriter
import org.janelia.saalfeldlab.paintera.Paintera
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue

class N5FactoryWithCacheTest {

	@Test
	fun `no extension, no scheme, should be N5`(@TempDir tmpPath: Path ) {
		val path = tmpPath.toUri().toString()
		assertTrue { Paintera.n5Factory.newWriter(path) is N5KeyValueWriter }
		assertTrue { Paintera.n5Factory.openReader(path) is N5KeyValueReader }
	}

	@Test
	fun `no extension, zarr scheme, should be zarr`(@TempDir tmpPath: Path ) {
		val path = "zarr:${tmpPath.toUri()}"
		/* new zarr containers are created as zarr3 */
		assertTrue("no extension with zarr: should be zarr") { Paintera.n5Factory.newWriter(path) is ZarrV3KeyValueWriter }
		assertTrue("No extension with zarr.json and no scheme should be zarr") { Paintera.n5Factory.openReader(path) is ZarrV3KeyValueReader }
	}

	@Test
	fun `no extension, zarr2 scheme, should be zarr2`(@TempDir tmpPath: Path ) {
		val path = "zarr2:${tmpPath.toUri()}"
		assertTrue("no extension with zarr2: should be zarr2") { Paintera.n5Factory.newWriter(path) is ZarrKeyValueWriter }
		assertTrue("No extension with .zgroup and no scheme should be zarr2") { Paintera.n5Factory.openReader(tmpPath.toUri().toString()) is ZarrKeyValueReader }
	}

	@Test
	fun `no extension, no scheme, has zgroup, should be zarr`(@TempDir tmpPath: Path ) {
		val noExtensionOrScheme = tmpPath.toUri().toString()
		val withScheme = "zarr:$noExtensionOrScheme"
		assertTrue("no extension with zarr: should be zarr") { Paintera.n5Factory.newWriter(withScheme) is ZarrV3KeyValueWriter }
		assertTrue("No extension with zarr.json and no scheme should be zarr") { Paintera.n5Factory.openReader(noExtensionOrScheme) is ZarrV3KeyValueReader }
	}


	@Test
	fun `random extension, no scheme, should be N5`(@TempDir tmpfile: File ) {
		val path = tmpfile.resolve("${randString()}.asdf").toURI().toString()
		assertTrue("random extension should be n5") { Paintera.n5Factory.newWriter(path) is N5KeyValueWriter }
		assertTrue("random extension should be n5") { Paintera.n5Factory.openReader(path) is N5KeyValueReader }
	}

	@Test
	fun `random extension, zarr scheme, should be zarr`(@TempDir tmpfile: File ) {
		val path = tmpfile.resolve("${randString()}.asdf").toURI().toString()
		assertTrue("random extension with zarr: should be zarr") { Paintera.n5Factory.newWriter("zarr:$path") is ZarrV3KeyValueWriter }
		assertTrue("random extension with zarr.json: should be zarr") { Paintera.n5Factory.openReader(path) is ZarrV3KeyValueReader }
	}

	@Test
	fun `zarr extension, no scheme, should be zarr`(@TempDir tmpfile: File ) {
		val path = tmpfile.resolve("${randString()}.zarr").toURI().toString()
		assertTrue(".zarr should be zarr") { Paintera.n5Factory.newWriter(path) is ZarrV3KeyValueWriter }
		assertTrue(".zarr should be zarr") { Paintera.n5Factory.openReader(path) is ZarrV3KeyValueReader }
	}

	@Test
	fun `n5 extension, no scheme, should be N5`(@TempDir tmpfile: File ) {
		val path = tmpfile.resolve("${randString()}.n5").toURI().toString()
		assertTrue(".n5 should be n5") { Paintera.n5Factory.newWriter(path) is N5KeyValueWriter }
		assertTrue(".n5 should be n5 ") { Paintera.n5Factory.openReader(path) is N5KeyValueReader }
	}

	private fun randString() = List(10) {( ('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
}