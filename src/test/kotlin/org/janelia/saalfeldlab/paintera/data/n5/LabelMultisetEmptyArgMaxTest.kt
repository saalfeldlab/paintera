package org.janelia.saalfeldlab.paintera.data.n5

import net.imglib2.Localizable
import net.imglib2.img.cell.CellGrid
import net.imglib2.type.label.*
import net.imglib2.util.Intervals
import net.imglib2.util.Intervals.positions
import net.imglib2.view.Views.flatIterable
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelMultisetEmptyArgMaxTest {

	companion object {
		private const val ID = 7L

		private class Pixel(entries: LabelMultisetEntryList, val expectedArgMax: Long) : LabelMultisetType(entries) {

			class Builder(build: Builder.() -> Unit) {

				val pixels = mutableListOf<Pixel>()

				init {
					build()
				}

				fun pixel(expectedArgMax: Long, entries: LabelMultisetEntryList.() -> Unit = {}) {
					pixels += Pixel(labelMultisetEntryList(entries), expectedArgMax)
				}
			}
		}

		/* `pixels` is `null` for a block that is never written */
		private class Block(val pixels: List<Pixel>?) {

			class Builder(build: Builder.() -> Unit) {

				val blocks = mutableListOf<Block>()

				init {
					build()
				}

				fun block(pixels: (Pixel.Builder.() -> Unit)?) {
					blocks += Block(pixels?.let { Pixel.Builder(it).pixels })
				}
			}

			fun expectedArgMaxes(elementsPerBlock: Int) =
				pixels?.map { it.expectedArgMax } ?: List(elementsPerBlock) { Label.BACKGROUND }
		}

		private fun multisetBlocks(blocks: Block.Builder.() -> Unit) = Block.Builder(blocks).blocks.toList()

		private fun labelMultisetEntryList(entries: LabelMultisetEntryList.() -> Unit) = LabelMultisetEntryList().apply(entries)

		private fun LabelMultisetEntryList.entry(id: Long, count: Int) {
			add(LabelMultisetEntry(id, count))
		}

		private fun CellGrid.blockGridPosition(blockIndex: Long) = LongArray(numDimensions()).also { getCellGridPositionFlat(blockIndex, it) }

		private fun CellGrid.blockInterval(blockIndex: Long) = cellIntervals().getAt(*blockGridPosition(blockIndex))
	}

	private val grid = CellGrid(
		longArrayOf(4, 2, 1),
		intArrayOf(2, 2, 1)
	)

	private val elementPerBlock = Intervals.numElements(*grid.cellDimensions).toInt()

	private val blocks: List<Block> = multisetBlocks {

		/*block with some elements, and expected ArgMax */
		block {
			pixel(Label.BACKGROUND)
			pixel(ID) { entry(ID, 3) }
			pixel(Label.BACKGROUND) { entry(Label.BACKGROUND, 0) }
			pixel(ID) { entry(ID, 3) }
		}

		/* non-existent block */
		block(null)
	}

	private val testAttributes = DatasetAttributes(grid.imgDimensions, grid.cellDimensions, DataType.UINT8)

	private fun writeTestDataset(tmp: Path): Pair<N5Writer, String> {
		val dataset = "multiset"
		val writer = Paintera.n5Factory.newWriter(tmp.toAbsolutePath().toString())
		writer.createDataset(dataset, testAttributes)
		writer.setAttribute(dataset, N5Helpers.IS_LABEL_MULTISET_KEY, true)

		blocks.forEachIndexed { blockIndex, block ->
			val pixels = block.pixels ?: return@forEachIndexed
			val bytes = LabelUtils.serializeLabelMultisetTypes(pixels, elementPerBlock)
			val dataBlock = ByteArrayDataBlock(grid.cellDimensions, grid.blockGridPosition(blockIndex.toLong()), bytes)
			writer.writeBlock(dataset, testAttributes, dataBlock)
		}
		return writer to dataset
	}

	@Test
	fun `empty cells and missing blocks both read as background`(@TempDir tmp: Path) {
		val (writer, dataset) = writeTestDataset(tmp)
		val access = openLabelMultiset(writer, dataset).randomAccess()

		fun argMaxAndTypeAt(position: Localizable): Pair<Long, LabelMultisetType> {
			val type = access.setPositionAndGet(position)
			return type.argMax() to type.copy()
		}

		for (blockIndex in blocks.indices) {
			val actual = flatIterable(positions(grid.blockInterval(blockIndex.toLong()))).map(::argMaxAndTypeAt)
			assertEquals(blocks[blockIndex].expectedArgMaxes(elementPerBlock), actual.map { (argMax, _) -> argMax }, "block $blockIndex")
			actual.forEachIndexed { pixel, (argMax, type) ->
				if (argMax == Label.BACKGROUND)
					assertEmptyEntries(type, "block $blockIndex pixel $pixel")
			}
		}
	}

	private fun assertEmptyEntries(type: LabelMultisetType, message: String) {
		val entries = type.entrySet()
		assertTrue(entries.size <= 1, "$message has ${entries.size} entries")
		entries.forEach {
			val id = it.element.id()
			assertEquals(0, it.count, "$message has a count in entry $id")
			assertTrue(id == Label.BACKGROUND || id == Label.INVALID, "$message has entry id $id")
		}
	}

	@Test
	fun `the loader gives background for empty cells, not invalid`(@TempDir tmp: Path) {
		val (writer, dataset) = writeTestDataset(tmp)
		val loader = LabelMultisetCacheLoader(writer, dataset)

		for (blockIndex in blocks.indices) {
			val access =loader.get(blockIndex.toLong()).data
			assertEquals(blocks[blockIndex].expectedArgMaxes(elementPerBlock), List(elementPerBlock) { access.argMax(it) }, "block $blockIndex")
		}
	}
}
