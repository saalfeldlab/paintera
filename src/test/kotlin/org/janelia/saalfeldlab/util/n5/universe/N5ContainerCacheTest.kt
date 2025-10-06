package org.janelia.saalfeldlab.util.n5.universe

import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueWriter
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class N5ContainerCacheTest {

	private lateinit var n5Factory: PainteraN5Factory
	
	@BeforeEach
	fun setUp() {
		n5Factory = PainteraN5Factory()
	}
	
	@AfterEach
	fun tearDown() {
		n5Factory.clear()
	}
	
	@Test
	fun `test cache returns same reader instance`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		n5Factory.newWriter(uri) // Create the container
		val reader2 = n5Factory.openReader(uri)
		val reader3 = n5Factory.openReader(uri)
		
		assertSame(reader2, reader3, "Cache should return the same reader instance")
	}
	
	@Test
	fun `test cache returns same writer instance`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer1 = n5Factory.newWriter(uri)
		val writer2 = n5Factory.openWriter(uri)
		val writer3 = n5Factory.openWriter(uri)
		
		assertSame(writer1, writer2, "Cache should return the same writer instance")
		assertSame(writer2, writer3, "Cache should return the same writer instance")
	}
	
	@Test
	fun `test writer is also returned as reader when allowWriter is true`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer = n5Factory.newWriter(uri)
		val reader = n5Factory.openReader(uri, allowWriter = true)
		
		assertSame(writer, reader, "Writer should be returned as reader when allowWriter=true")
	}
	
	@Test
	fun `test writer is not returned as reader when allowWriter is false`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer = n5Factory.newWriter(uri)
		val reader = n5Factory.openReader(uri, allowWriter = false)
		
		assertNotSame(writer, reader, "Writer should not be returned as reader when allowWriter=false")
		assertTrue(reader !is N5Writer, "Reader should not be a writer when allowWriter=false")
	}
	
	@Test
	fun `test clearKey removes from both caches`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer = n5Factory.newWriter(uri)
		val reader = n5Factory.openReader(uri)
		
		n5Factory.remove(uri)
		
		// New instances should be created
		val newWriter = n5Factory.openWriter(uri)
		val newReader = n5Factory.openReader(uri)
		
		assertNotSame(writer, newWriter, "Writer should be removed from cache")
		assertNotSame(reader, newReader, "Reader should be removed from cache")
	}
	
	@Test
	fun `test clearCache removes all entries`(@TempDir tempDir: Path) {
		val uri1 = tempDir.resolve("test1.n5").toUri().toString()
		val uri2 = tempDir.resolve("test2.n5").toUri().toString()
		
		val writer1 = n5Factory.newWriter(uri1)
		val writer2 = n5Factory.newWriter(uri2)
		
		n5Factory.clear()
		
		val newWriter1 = n5Factory.openWriter(uri1)
		val newWriter2 = n5Factory.openWriter(uri2)
		
		assertNotSame(writer1, newWriter1, "All writers should be cleared")
		assertNotSame(writer2, newWriter2, "All writers should be cleared")
	}
	
	@Test
	fun `test concurrent access to cache`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		n5Factory.newWriter(uri) // Create the container
		
		val numThreads = 10
		val latch = CountDownLatch(numThreads)
		val readers = mutableListOf<N5Reader?>()
		val writers = mutableListOf<N5Writer?>()
		
		// Launch multiple threads to access the cache concurrently
		repeat(numThreads) { i ->
			thread {
				try {
					if (i % 2 == 0) {
						readers.add(n5Factory.openReader(uri))
					} else {
						writers.add(n5Factory.openWriter(uri))
					}
				} finally {
					latch.countDown()
				}
			}
		}
		
		assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete")
		
		// All readers should be the same instance
		val distinctReaders = readers.filterNotNull().distinct()
		assertEquals(1, distinctReaders.size, "All readers should be the same instance")
		
		// All writers should be the same instance
		val distinctWriters = writers.filterNotNull().distinct()
		assertEquals(1, distinctWriters.size, "All writers should be the same instance")
	}
	
	@Test
	fun `test N5ContainerDoesntExist exception thrown for non-existent container`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("nonexistent.n5").toUri().toString()
		
		assertThrows<N5ContainerDoesntExist> {
			n5Factory.openReader(uri)
		}
		
		assertThrows<N5ContainerDoesntExist> {
			n5Factory.openWriter(uri)
		}
	}
	
	@Test
	fun `test openWriterOrNull returns null for non-existent container`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("nonexistent.n5").toUri().toString()
		
		assertNull(n5Factory.openWriterOrNull(uri))
	}
	
	@Test
	fun `test openReaderOrNull returns null for non-existent container`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("nonexistent.n5").toUri().toString()
		
		assertNull(n5Factory.openReaderOrNull(uri))
	}
	
	@Test
	fun `test openWriterElseOpenReader falls back to reader`(@TempDir tempDir: Path) {
		// Create a container first
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val initialWriter = n5Factory.newWriter(uri)
		initialWriter.setAttribute("/", "test", "value")
		
		// Clear cache to simulate fresh start
		n5Factory.clear()
		
		// Now test the method - it should try writer first, then fall back to reader
		val result = n5Factory.openWriterElseOpenReader(uri)
		assertNotNull(result, "Should return a non-null N5 container")
		
		// For a non-existent container, it should throw N5ContainerDoesntExist
		val nonExistentUri = tempDir.resolve("nonexistent.n5").toUri().toString()
		assertThrows<N5ContainerDoesntExist> {
			n5Factory.openWriterElseOpenReader(nonExistentUri)
		}
	}
	
	@Test
	fun `test N5ContainerState with cache`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer = n5Factory.newWriter(uri)
		
		val state1 = N5ContainerState(writer, readOnly = false)
		val state2 = N5ContainerState(n5Factory.openReader(uri), readOnly = false)
		
		// Both states should have the same URI
		assertEquals(state1.uri, state2.uri)
		
		// Writers should be the same cached instance
		assertSame(state1.writer, state2.writer, "N5ContainerState should use cached writers")
	}
	
	@Test
	fun `test N5ContainerState readOnly copy`(@TempDir tempDir: Path) {
		val uri = tempDir.resolve("test.n5").toUri().toString()
		val writer = n5Factory.newWriter(uri)
		
		val writableState = N5ContainerState(writer, readOnly = false)
		val readOnlyState = writableState.readOnlyCopy()
		
		assertTrue(readOnlyState.readOnly, "Copy should be read-only")
		assertNull(readOnlyState.writer, "Read-only state should have null writer")
		assertNotSame(writableState, readOnlyState, "Copy should create new instance")
		
		// Test that copying a read-only state returns itself
		val anotherCopy = readOnlyState.readOnlyCopy()
		assertSame(readOnlyState, anotherCopy, "Copying read-only state should return same instance")
	}
	
	@Test
	fun `test cache handles different N5 formats`(@TempDir tempDir: Path) {
		// Test N5 format
		val n5Uri = tempDir.resolve("test.n5").toUri().toString()
		val n5Writer = n5Factory.newWriter(n5Uri)
		assertTrue(n5Writer is N5KeyValueWriter, "Should create N5 writer")
		
		// Test Zarr format
		val zarrUri = tempDir.resolve("test.zarr").toUri().toString()
		val zarrWriter = n5Factory.newWriter(zarrUri)
		assertTrue(zarrWriter is ZarrKeyValueWriter, "Should create Zarr writer")
		
		// Both should be cached
		assertSame(n5Writer, n5Factory.openWriter(n5Uri))
		assertSame(zarrWriter, n5Factory.openWriter(zarrUri))
	}

	@Test
	fun `test guessStorageFromFormatSpecificFiles`(@TempDir tempDir: Path) {
		// Test N5 format detection
		val n5Dir = tempDir.resolve("n5test").toFile()
		n5Dir.mkdirs()
		File(n5Dir, "attributes.json").createNewFile()
		
		// Test Zarr format detection
		val zarrDir = tempDir.resolve("zarrtest").toFile()
		zarrDir.mkdirs()
		File(zarrDir, ".zgroup").createNewFile()
		
		// The actual test would require access to the companion object method
		// which is internal. In a real test scenario, you might make it testable
		// or test it indirectly through the public API
	}
	
	@Test
	fun `test thread safety of cache operations`(@TempDir tempDir: Path) {
		// Create multiple containers to avoid N5 concurrent access issues
		val uris = (0..4).map { tempDir.resolve("test$it.n5").toUri().toString() }
		uris.forEach { n5Factory.newWriter(it) }
		
		val executor = Executors.newFixedThreadPool(20)
		val operations = 1000
		val latch = CountDownLatch(operations)
		val errors = mutableListOf<Exception>()
		val instances = ConcurrentHashMap<String, MutableSet<N5Reader>>()
		
		repeat(operations) { i ->
			executor.submit {
				try {
					val uri = uris[i % uris.size]
					when (i % 4) {
						0 -> {
							// Test getting readers from cache
							val reader = n5Factory.openReader(uri)
							assertNotNull(reader, "Reader should not be null")
							instances.computeIfAbsent(uri) { mutableSetOf() }.add(reader)
						}
						1 -> {
							// Test getting writers from cache
							val writer = n5Factory.openWriter(uri)
							assertNotNull(writer, "Writer should not be null")
							instances.computeIfAbsent(uri) { mutableSetOf() }.add(writer)
						}
						2 -> {
							// Test cache clearing - only for one specific URI to avoid affecting other threads
							if (i % 20 == 0) { // Less frequent to avoid too much disruption
								val clearUri = uris.last()
								n5Factory.remove(clearUri)
								// Verify we can still get a new instance
								val newReader = n5Factory.openReader(clearUri)
								assertNotNull(newReader, "Should get new instance after clear")
							} else {
								// Regular cache access
								val reader = n5Factory.openReader(uri, allowWriter = false)
								assertNotNull(reader, "Read-only reader should not be null")
							}
						}
						3 -> {
							// Test concurrent access with different parameters
							val reader1 = n5Factory.openReader(uri, allowWriter = true)
							val reader2 = n5Factory.openReader(uri, allowWriter = false)
							assertNotNull(reader1, "Reader with allowWriter=true should not be null")
							assertNotNull(reader2, "Reader with allowWriter=false should not be null")
						}
					}
				} catch (e: Exception) {
					synchronized(errors) {
						errors.add(e)
					}
				} finally {
					latch.countDown()
				}
			}
		}
		
		assertTrue(latch.await(10, TimeUnit.SECONDS), "All operations should complete")
		executor.shutdown()
		
		// Check results
		assertTrue(errors.isEmpty(), "No exceptions should occur during cache operations. Errors: ${errors.take(5)}")
		
		// Verify cache consistency - each URI should have returned mostly the same instances
		// (allowing for some variation due to cache clearing)
		instances.forEach { (uri, readers) ->
			val uniqueInstances = readers.distinct().size
			assertTrue(uniqueInstances <= 3, 
				"URI $uri should have returned mostly the same instances, but got $uniqueInstances unique instances")
		}
	}
}