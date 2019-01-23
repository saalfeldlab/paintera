package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelUtils;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5TestUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.touk.throwing.ThrowingBiConsumer;
import pl.touk.throwing.ThrowingBiFunction;
import pl.touk.throwing.ThrowingFunction;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommitCanvasN5Test {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final UnsignedLongType INVALID = new UnsignedLongType(Label.INVALID);

	private static final Map<String, Object> MULTISET_ATTRIBUTE = Collections.unmodifiableMap(Stream.of(1).collect(Collectors.toMap(o -> N5Helpers.LABEL_MULTISETTYPE_KEY, o -> true)));

	private static final Map<String, Object> PAINTERA_DATA_ATTRIBUTE = Collections.unmodifiableMap(Stream.of(1).collect(Collectors.toMap(o -> "type", o -> "label")));

	private static boolean isInvalid(final UnsignedLongType pixel) {
		return INVALID.valueEquals(pixel);
	}

	@Test
	public void testCommit() throws IOException, UnableToPersistCanvas {

		final long[] dims = new long[] {2,3,4};
		final int[] blockSize = new int[] {1, 2, 2};
		final CellGrid grid = new CellGrid(dims, blockSize);
		final CellLoader<UnsignedLongType> loader = img -> img.forEach(UnsignedLongType::setOne);
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(blockSize));
		final CachedCellImg<UnsignedLongType, ?> canvas = factory.create(dims, new UnsignedLongType(), loader);
		final Random rng = new Random(100);
		canvas.forEach(px -> px.setInteger(rng.nextDouble() > 0.5 ? rng.nextInt(50) : Label.INVALID));

		final N5Writer container = N5TestUtil.fileSystemWriterAtTmpDir();
		testLabelMultisetSingleScale(container, "single-scale-label-multisets", canvas);
		testLabelMultisetMultiScale(container, "multi-scale-label-multisets", canvas);
		testLabelMultisetPaintera(container, "paintera-label-multisets", canvas);
		testUnsignedLongTypeSingleScale(container, "single-scale-uint64", canvas);
		testUnsignedLongTypeMultiScale(container, "multi-scale-uint64", canvas);
		testUnsignedLongTypePaintera(container, "paintera-uint64", canvas);
	}

	private static void testUnsignedLongTypeSingleScale(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<UnsignedLongType>testSingleScale(
				container,
				dataset,
				DataType.UINT64,
				canvas,
				ThrowingBiFunction.unchecked(N5Utils::open),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()));
	}

	private static void testLabelMultisetSingleScale(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<LabelMultisetType>testSingleScale(
				container,
				dataset,
				DataType.UINT8,
				canvas,
				ThrowingBiFunction.unchecked(LabelUtils::openVolatile),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()),
				MULTISET_ATTRIBUTE);
	}

	private static void testUnsignedLongTypeMultiScale(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<UnsignedLongType>testMultiScale(
				container,
				dataset,
				DataType.UINT64,
				canvas,
				ThrowingBiFunction.unchecked(N5Utils::open),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()));
	}

	private static void testLabelMultisetMultiScale(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<LabelMultisetType>testMultiScale(
				container,
				dataset,
				DataType.UINT8,
				canvas,
				ThrowingBiFunction.unchecked(LabelUtils::openVolatile),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()),
				MULTISET_ATTRIBUTE);
	}

	private static void testUnsignedLongTypePaintera(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<UnsignedLongType>testPainteraData(
				container,
				dataset,
				DataType.UINT64,
				canvas,
				ThrowingBiFunction.unchecked(N5Utils::open),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()));
	}

	private static void testLabelMultisetPaintera(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		CommitCanvasN5Test.<LabelMultisetType>testPainteraData(
				container,
				dataset,
				DataType.UINT8,
				canvas,
				ThrowingBiFunction.unchecked(LabelUtils::openVolatile),
				(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()),
				MULTISET_ATTRIBUTE);
	}

	private static <T> void testPainteraData(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts,
			final int[]... scaleFactors
	) throws IOException, UnableToPersistCanvas {
		testPainteraData(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>());
	}

	private static <T> void testPainteraData(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts,
			final Map<String, Object> additionalAttributes,
			final int[]... sclaeFactors
	) throws IOException, UnableToPersistCanvas {
		final DatasetAttributes attributes = new DatasetAttributes(canvas.getCellGrid().getImgDimensions(), blockSize(canvas.getCellGrid()), dataType, new GzipCompression());
		container.createGroup(dataset);
		final String dataGroup = String.join("/", dataset, "data");
		container.createGroup(dataGroup);
		final String s0 = String.join("/", dataGroup, "s0");
		container.createDataset(s0, attributes);
		additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> container.setAttribute(dataGroup, k, v)));
		additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> container.setAttribute(s0, k, v)));
		container.setAttribute(dataset, "painteraData", PAINTERA_DATA_ATTRIBUTE);
		container.setAttribute(s0, N5Helpers.MULTI_SCALE_KEY, true);
		testCanvasPersistance(container, dataset, s0, canvas, openLabels, asserts);
	}

	private static <T> void testMultiScale(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts,
			final int[]... scaleFactors
	) throws IOException, UnableToPersistCanvas {
		testMultiScale(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>());
	}

	private static <T> void testMultiScale(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts,
			final Map<String, Object> additionalAttributes,
			final int[]... sclaeFactors
	) throws IOException, UnableToPersistCanvas {
		final DatasetAttributes attributes = new DatasetAttributes(canvas.getCellGrid().getImgDimensions(), blockSize(canvas.getCellGrid()), dataType, new GzipCompression());
		container.createGroup(dataset);
		final String s0 = String.join("/", dataset, "s0");
		container.createDataset(s0, attributes);
		additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> container.setAttribute(dataset, k, v)));
		additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> container.setAttribute(s0, k, v)));
		container.setAttribute(s0, N5Helpers.MULTI_SCALE_KEY, true);
		testCanvasPersistance(container, dataset, s0, canvas, openLabels, asserts);
	}

	private static <T> void testSingleScale(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts
	) throws IOException, UnableToPersistCanvas {
		testSingleScale(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>());
	}

	private static <T> void testSingleScale(
			final N5Writer container,
			final String dataset,
			final DataType dataType,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts,
			final Map<String, Object> additionalAttributes
	) throws IOException, UnableToPersistCanvas {
		final DatasetAttributes attributes = new DatasetAttributes(canvas.getCellGrid().getImgDimensions(), blockSize(canvas.getCellGrid()), dataType, new GzipCompression());
		container.createDataset(dataset, attributes);
		additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> container.setAttribute(dataset, k, v)));
		testCanvasPersistance(container, dataset, canvas, openLabels, asserts);
	}

	private static <T> void testCanvasPersistance(
			final N5Writer container,
			final String group,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts) throws IOException, UnableToPersistCanvas {

		testCanvasPersistance(container, group, group, canvas, openLabels, asserts);
	}

	private static <T> void testCanvasPersistance(
			final N5Writer container,
			final String group,
			final String labelsDataset,
			final CachedCellImg<UnsignedLongType, ?> canvas,
			final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
			final BiConsumer<UnsignedLongType, T> asserts) throws IOException, UnableToPersistCanvas {

		writeAll(container, group, canvas);

		final RandomAccessibleInterval<T> labels = openLabels.apply(container, labelsDataset);
		Assert.assertArrayEquals(Intervals.dimensionsAsLongArray(canvas), Intervals.dimensionsAsLongArray(labels));

		for (final Pair<UnsignedLongType, T> pair : Views.interval(Views.pair(canvas, labels), labels)) {
			LOG.debug("Comparing canvas {} and background {}", pair.getA(), pair.getB());
			asserts.accept(pair.getA(), pair.getB());
		}
	}


	private static void writeAll(
			final N5Writer container,
			final String dataset,
			final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas {
		final long numBlocks = Intervals.numElements(canvas.getCellGrid().getGridDimensions());
		final long[] blocks = new long[(int) numBlocks];
		Arrays.setAll(blocks, d -> d);

		final CommitCanvasN5 cc = new CommitCanvasN5(container, dataset);
		cc.persistCanvas(canvas, blocks);
	}

	private static int[] blockSize(final CellGrid grid) {
		int[] blockSize = new int[grid.numDimensions()];
		grid.cellDimensions(blockSize);
		return blockSize;
	}

}
