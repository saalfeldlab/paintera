package org.janelia.saalfeldlab.util.n5.universe

import org.assertj.core.util.Files
import org.janelia.saalfeldlab.n5.N5KeyValueReader
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueWriter
import org.janelia.saalfeldlab.paintera.Paintera
import kotlin.test.Test
import kotlin.test.assertTrue

class PainteraMainWindowTest {

	@Test
	fun `project directory as n5 or zarr`() {
		val chars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
		val randString = { List(10) { chars.random() }.joinToString("") }

		val noExtension = Files.temporaryFolder().resolve(randString()).absolutePath
		assertTrue { Paintera.n5Factory.newWriter(noExtension) is N5KeyValueWriter }
		assertTrue { Paintera.n5Factory.openReader(noExtension) is N5KeyValueReader }

		val noExtension2 = Files.temporaryFolder().resolve(randString()).absolutePath
		assertTrue("no extension with zarr: should be zarr") { Paintera.n5Factory.newWriter("zarr:${noExtension2}") is ZarrKeyValueWriter }
		assertTrue("No extension with .zgroup and no scheme should be zarr") { Paintera.n5Factory.openReader(noExtension2) is ZarrKeyValueReader }

		val nonsenseExtension = Files.temporaryFolder().resolve("${randString()}.asdf").absolutePath
		assertTrue("random extension should be n5") { Paintera.n5Factory.newWriter(nonsenseExtension) is N5KeyValueWriter }
		assertTrue("random extension should be n5") { Paintera.n5Factory.openReader(nonsenseExtension) is N5KeyValueReader }

		val nonsenseExtension2 = Files.temporaryFolder().resolve("${randString()}.asdf").absolutePath
		assertTrue("random extension with zarr: should be zarr") { Paintera.n5Factory.newWriter("zarr:$nonsenseExtension2") is ZarrKeyValueWriter }
		assertTrue("random extension with zarr: should be zarr") { Paintera.n5Factory.openReader(nonsenseExtension2) is ZarrKeyValueReader }

		val zarrFile = Files.temporaryFolder().resolve("${randString()}.zarr").absolutePath
		assertTrue(".zarr should be zarr") { Paintera.n5Factory.newWriter(zarrFile) is ZarrKeyValueWriter }
		assertTrue(".zarr should be zarr") { Paintera.n5Factory.openReader(zarrFile) is ZarrKeyValueReader }

		val n5File = Files.temporaryFolder().resolve("${randString()}.n5").absolutePath
		assertTrue(".n5 should be n5") { Paintera.n5Factory.newWriter(n5File) is N5KeyValueWriter }
		assertTrue(".n5 should be n5 ") { Paintera.n5Factory.openReader(n5File) is N5KeyValueReader }
	}
}