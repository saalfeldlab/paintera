package org.janelia.saalfeldlab.util.n5.universe

import org.assertj.core.util.Files
import org.janelia.saalfeldlab.n5.N5KeyValueReader
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueWriter
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.util.n5.universe.N5FactoryWithCache.Companion.n5OrZarrURI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PainteraMainWindowTest {

	@Test
	fun `project directory as n5 or zarr`() {
		val chars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
		val randString = { List(10) { chars.random() }.joinToString("") }

		val noExtension = Files.temporaryFolder().resolve(randString())
		assertTrue { noExtension.n5OrZarrURI().startsWith("n5:") }
		assertTrue { Paintera.n5Factory.newWriter(noExtension.n5OrZarrURI()) is N5KeyValueWriter }
		assertTrue { Paintera.n5Factory.openReader(noExtension.n5OrZarrURI()) is N5KeyValueReader }

		val nonsenseExtension = Files.temporaryFolder().resolve("${randString()}.asdf")
		assertTrue("Nonsense extension should have n5 scheme") { nonsenseExtension.n5OrZarrURI().startsWith("n5:") }
		assertTrue { Paintera.n5Factory.newWriter(nonsenseExtension.n5OrZarrURI()) is N5KeyValueWriter }
		assertTrue { Paintera.n5Factory.openReader(nonsenseExtension.n5OrZarrURI()) is N5KeyValueReader }

		val zarrFile = Files.temporaryFolder().resolve("${randString()}.zarr")
		assertFalse("No Scheme added with .zarr present") { zarrFile.n5OrZarrURI().matches("^(?i)\\S+:.*$".toRegex()) }
		assertTrue(".zarr with n5: should be N5") { Paintera.n5Factory.newWriter("n5:${zarrFile.n5OrZarrURI()}") is N5KeyValueWriter }
		assertTrue(".zarr with attributes.json and no scheme should have n5:") { zarrFile.n5OrZarrURI().startsWith("n5:") }
		assertTrue(".zarr with attributes.json and no scheme should be N5") { Paintera.n5Factory.openReader(zarrFile.n5OrZarrURI()) is N5KeyValueReader }

		val noExtension2 = Files.temporaryFolder().resolve(randString())
		assertTrue("no extension with zarr: should be zarr") { Paintera.n5Factory.newWriter("zarr:${noExtension2.absolutePath}") is ZarrKeyValueWriter }
		assertTrue("No extension with .zgroup and no scheme should have zarr:") { noExtension2.n5OrZarrURI().startsWith("zarr:") }
		assertTrue("No extension with .zgroup and no scheme should be zarr") { Paintera.n5Factory.openReader(noExtension2.n5OrZarrURI()) is ZarrKeyValueReader }

		val n5File = Files.temporaryFolder().resolve("${randString()}.n5")
		assertFalse("No Scheme added when .n5 extension present") { n5File.n5OrZarrURI().matches("^(?i)\\S+:.*$".toRegex()) }
		assertTrue(".n5 with zarr: should be zarr") { Paintera.n5Factory.newWriter("zarr:${n5File.n5OrZarrURI()}") is ZarrKeyValueWriter }
		assertTrue(".n5 with .zgroup and no scheme should have zarr: ") { n5File.n5OrZarrURI().startsWith("zarr:") }
		assertTrue(".n5 with .zgroup and no scheme should be zarr ") { Paintera.n5Factory.openReader(n5File.n5OrZarrURI()) is ZarrKeyValueReader }
	}


}