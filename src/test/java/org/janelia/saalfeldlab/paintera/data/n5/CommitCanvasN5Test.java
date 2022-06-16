package org.janelia.saalfeldlab.paintera.data.n5;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingConsumer;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.application.Platform;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupFromFile;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToPersistCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.persist.UnableToUpdateLabelBlockLookup;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.janelia.saalfeldlab.util.n5.N5TestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommitCanvasN5Test {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final UnsignedLongType INVALID = new UnsignedLongType(Label.INVALID);

  private static final Map<String, Object> MULTISET_ATTRIBUTE = Map.of(N5Helpers.LABEL_MULTISETTYPE_KEY, true);

  private static final Map<String, Object> PAINTERA_DATA_ATTRIBUTE = Map.of("type", "label");

  private static boolean isInvalid(final UnsignedLongType pixel) {

	final boolean isInvalid = INVALID.valueEquals(pixel);
	LOG.trace("{} is invalid? {}", pixel, isInvalid);
	return isInvalid;
  }

  @BeforeClass
  public static void startJavaFx() {

	try {
	  Platform.startup(() -> {
	  });
	  WaitForAsyncUtils.waitForFxEvents();
	} catch (IllegalStateException e) {
	  /* This happens if the JavaFx thread is already running, which is what we want.*/
	}
  }

  @Test
  public void testSingleScaleLabelMultisetCommit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	final int[][] scales = {{2, 2, 3}};
	testLabelMultisetSingleScale(container, "single-scale-label-multisets", canvas);
  }

  @Test
  public void testMultiScaleScaleLabelMultisetCommit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	final int[][] scales = {{2, 2, 3}};
	testLabelMultisetMultiScale(container, "multi-scale-label-multisets", canvas);

  }

  @Test
  public void testPainteraLabelMultisetCommit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	final int[][] scales = {{2, 2, 3}};
	testLabelMultisetPaintera(container, "paintera-label-multisets", canvas, scales);
  }

  @Test
  public void testSingleScaleUint64Commit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	testUnsignedLongTypeSingleScale(container, "single-scale-uint64", canvas);
  }

  @Test
  public void testMultiScaleUint64Commit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	testUnsignedLongTypeMultiScale(container, "multi-scale-uint64", canvas);
  }

  @Test
  public void testPainteraUint64Commit() throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final CachedCellImg<UnsignedLongType, ?> canvas = getNewCanvas();

	final N5FSWriter writer = N5TestUtil.fileSystemWriterAtTmpDir(!LOG.isDebugEnabled());
	final var container = new N5ContainerState(writer.getBasePath(), writer, writer);
	LOG.debug("Created temporary N5 container {}", writer);
	/* persistCanvas now has a call to update it's progress, which is on the UI thread. This means we need the UI thread to exist first. */
	final int[][] scales = {{2, 2, 3}};
	testUnsignedLongTypePaintera(container, "paintera-uint64", canvas, scales);
  }

  private CachedCellImg<UnsignedLongType, ?> getNewCanvas() {

	final long[] dims = new long[]{10, 20, 30};
	final int[] blockSize = new int[]{5, 7, 9};
	final CellLoader<UnsignedLongType> loader = img -> img.forEach(UnsignedLongType::setOne);
	final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options().cellDimensions(blockSize));
	final CachedCellImg<UnsignedLongType, ?> canvas = factory.create(dims, new UnsignedLongType(), loader);
	final Random rng = new Random(100);
	canvas.forEach(px -> px.setInteger(rng.nextDouble() > 0.5 ? rng.nextInt(50) : Label.INVALID));
	return canvas;
  }

  private static void assertMultisetType(final UnsignedLongType c, final LabelMultisetType l) {

	Assert.assertEquals(1, l.entrySet().size());
	final LabelMultisetType.Entry<Label> entry = l.entrySet().iterator().next();
	Assert.assertEquals(1, entry.getCount());
	final boolean isInvalid = isInvalid(c);
	if (isInvalid) {
	  Assert.assertEquals(0, l.getIntegerLong());
	} else {
	  Assert.assertEquals(c.getIntegerLong(), entry.getElement().id());
	}
  }

  private static void testUnsignedLongTypeSingleScale(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	CommitCanvasN5Test.<UnsignedLongType>testSingleScale(
			container,
			dataset,
			DataType.UINT64,
			canvas,
			ThrowingBiFunction.unchecked(N5Utils::open),
			(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()));
  }

  private static void testLabelMultisetSingleScale(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	CommitCanvasN5Test.testSingleScale(
			container,
			dataset,
			DataType.UINT8,
			canvas,
			ThrowingBiFunction.unchecked(N5LabelMultisets::openLabelMultiset),
			CommitCanvasN5Test::assertMultisetType,
			MULTISET_ATTRIBUTE);
  }

  private static void testUnsignedLongTypeMultiScale(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	CommitCanvasN5Test.<UnsignedLongType>testMultiScale(
			container,
			dataset,
			DataType.UINT64,
			canvas,
			ThrowingBiFunction.unchecked(N5Utils::open),
			(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()));
  }

  private static void testLabelMultisetMultiScale(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	CommitCanvasN5Test.testMultiScale(
			container,
			dataset,
			DataType.UINT8,
			canvas,
			ThrowingBiFunction.unchecked(N5LabelMultisets::openLabelMultiset),
			CommitCanvasN5Test::assertMultisetType,
			MULTISET_ATTRIBUTE);
  }

  private static void testUnsignedLongTypePaintera(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final int[]... scales) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	CommitCanvasN5Test.<UnsignedLongType>testPainteraData(
			container,
			dataset,
			DataType.UINT64,
			canvas,
			ThrowingBiFunction.unchecked(N5Utils::open),
			(c, l) -> Assert.assertEquals(isInvalid(c) ? 0 : c.getIntegerLong(), l.getIntegerLong()),
			scales);
  }

  private static void testLabelMultisetPaintera(
		  final N5ContainerState container,
		  final String dataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final int[]... scales) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	CommitCanvasN5Test.testPainteraData(
			container,
			dataset,
			DataType.UINT8,
			canvas,
			ThrowingBiFunction.unchecked(N5LabelMultisets::openLabelMultiset),
			CommitCanvasN5Test::assertMultisetType,
			MULTISET_ATTRIBUTE,
			scales);
  }

  private static <T> void testPainteraData(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts,
		  final int[]... scaleFactors
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	testPainteraData(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>(), scaleFactors);
  }

  private static <T> void testPainteraData(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts,
		  final Map<String, Object> additionalAttributes,
		  final int[]... scaleFactors
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup, ReflectionException {

	final N5Writer writer = container.writer;
	final int[] blockSize = blockSize(canvas.getCellGrid());
	final long[] dims = canvas.getCellGrid().getImgDimensions();
	final DatasetAttributes attributes = new DatasetAttributes(dims, blockSize, dataType, new GzipCompression());
	final DatasetAttributes uniqueAttributes = new DatasetAttributes(dims, blockSize, DataType.UINT64, new GzipCompression());
	writer.createGroup(dataset);
	final String dataGroup = String.join("/", dataset, "data");
	final String uniqueLabelsGroup = String.join("/", dataset, "unique-labels");
	writer.createGroup(dataGroup);
	writer.createGroup(uniqueLabelsGroup);
	final String s0 = String.join("/", dataGroup, "s0");
	final String u0 = String.join("/", uniqueLabelsGroup, "s0");

	writer.createDataset(s0, attributes);
	writer.createDataset(u0, uniqueAttributes);
	additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(dataGroup, k, v)));
	additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(s0, k, v)));
	writer.setAttribute(dataset, "painteraData", PAINTERA_DATA_ATTRIBUTE);

	writer.setAttribute(dataGroup, N5Helpers.MULTI_SCALE_KEY, true);
	writer.setAttribute(uniqueLabelsGroup, N5Helpers.MULTI_SCALE_KEY, true);

	Grids.forEachOffset(
			new long[dims.length],
			canvas.getCellGrid().getGridDimensions(),
			ones(dims.length),
			ThrowingConsumer.unchecked(b -> writer.writeBlock(u0, uniqueAttributes, new LongArrayDataBlock(new int[]{1}, b, new long[]{}))));

	for (int scale = 0, scaleIndex = 1; scale < scaleFactors.length; ++scale) {
	  final int[] sf = scaleFactors[scale];
	  final long[] scaleDims = divideBy(dims, sf, 1);
	  final DatasetAttributes scaleAttributes = new DatasetAttributes(scaleDims, blockSize, dataType, new GzipCompression());
	  final DatasetAttributes uniqueScaleAttributes = new DatasetAttributes(scaleDims, blockSize, DataType.UINT64, new GzipCompression());
	  final String sN = String.join("/", dataGroup, "s" + scaleIndex);
	  final String uN = String.join("/", uniqueLabelsGroup, "s" + scaleIndex);
	  LOG.debug("Creating scale data set with scale factor {}: {}", sf, sN);
	  writer.createDataset(sN, scaleAttributes);
	  writer.createDataset(uN, uniqueScaleAttributes);
	  additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(sN, k, v)));
	  writer.setAttribute(sN, N5Helpers.DOWNSAMPLING_FACTORS_KEY, IntStream.of(sf).asDoubleStream().toArray());
	}

	testCanvasPersistance(container, dataset, s0, canvas, openLabels, asserts);

	// test highest level block lookups
	final String uniqueBlock0 = String.join("/", dataset, "unique-labels", "s0");
	final Path mappingPattern = Paths.get(container.getUrl(), dataset, "label-to-block-mapping", "s%d", "%d");
	final Path mapping0 = Paths.get(container.getUrl(), dataset, "label-to-block-mapping", "s0");
	final DatasetAttributes uniqueBlockAttributes = writer.getDatasetAttributes(uniqueBlock0);
	final List<Interval> blocks = Grids.collectAllContainedIntervals(dims, blockSize);
	final TLongObjectMap<TLongSet> labelToBLockMapping = new TLongObjectHashMap<>();
	for (final Interval block : blocks) {
	  final TLongSet labels = new TLongHashSet();
	  final long[] blockMin = Intervals.minAsLongArray(block);
	  final long[] blockPos = blockMin.clone();
	  Arrays.setAll(blockPos, d -> blockPos[d] / blockSize[d]);
	  final long blockIndex = IntervalIndexer.positionToIndex(blockPos, canvas.getCellGrid().getGridDimensions());
	  Views.interval(canvas, block).forEach(px -> {
		// blocks are loaded with default value 0 if not present, thus 0 will be in the updated data
		final long pxVal = isInvalid(px) ? 0 : px.getIntegerLong();
		labels.add(pxVal);
		if (pxVal != 0) {
		  if (!labelToBLockMapping.containsKey(pxVal))
			labelToBLockMapping.put(pxVal, new TLongHashSet());
		  labelToBLockMapping.get(pxVal).add(blockIndex);
		}
	  });

	  final DataBlock<?> uniqueBlock = writer.readBlock(uniqueBlock0, uniqueBlockAttributes, blockPos);
	  Assert.assertEquals(labels, new TLongHashSet((long[])uniqueBlock.getData()));
	}

	final long[] idsForMapping = Stream.of(mapping0.toFile().list((f, fn) -> Optional.ofNullable(f).map(File::isDirectory).orElse(false))).mapToLong(Long::parseLong).toArray();
	LOG.debug("Found ids for mapping: {}", idsForMapping);
	Assert.assertEquals(labelToBLockMapping.keySet(), new TLongHashSet(idsForMapping));
	final LabelBlockLookupFromFile lookup = new LabelBlockLookupFromFile(mappingPattern.toString());

	for (final long id : idsForMapping) {
	  final Interval[] lookupFor = lookup.read(new LabelBlockLookupKey(0, id));
	  LOG.trace("Found mapping {} for id {}", lookupFor, id);
	  Assert.assertEquals(labelToBLockMapping.get(id).size(), lookupFor.length);
	  final long[] blockIndices = Stream
			  .of(lookupFor)
			  .map(Intervals::minAsLongArray)
			  .mapToLong(m -> toBlockIndex(m, canvas.getCellGrid()))
			  .toArray();
	  LOG.trace("Block indices for id {}: {}", id, blockIndices);
	  Assert.assertEquals(labelToBLockMapping.get(id), new TLongHashSet(blockIndices));
	}

  }

  private static <T> void testMultiScale(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	testMultiScale(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>());
  }

  private static <T> void testMultiScale(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts,
		  final Map<String, Object> additionalAttributes
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	final DatasetAttributes attributes = new DatasetAttributes(canvas.getCellGrid().getImgDimensions(), blockSize(canvas.getCellGrid()), dataType, new GzipCompression());
	final var writer = container.writer;
	writer.createGroup(dataset);
	final String s0 = String.join("/", dataset, "s0");
	writer.createDataset(s0, attributes);
	additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(dataset, k, v)));
	additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(s0, k, v)));
	writer.setAttribute(s0, N5Helpers.MULTI_SCALE_KEY, true);
	testCanvasPersistance(container, dataset, s0, canvas, openLabels, asserts);
  }

  private static <T> void testSingleScale(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	testSingleScale(container, dataset, dataType, canvas, openLabels, asserts, new HashMap<>());
  }

  private static <T> void testSingleScale(
		  final N5ContainerState container,
		  final String dataset,
		  final DataType dataType,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts,
		  final Map<String, Object> additionalAttributes
  ) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	final DatasetAttributes attributes = new DatasetAttributes(canvas.getCellGrid().getImgDimensions(), blockSize(canvas.getCellGrid()), dataType, new GzipCompression());
	final N5Writer writer = container.writer;
	writer.createDataset(dataset, attributes);
	additionalAttributes.forEach(ThrowingBiConsumer.unchecked((k, v) -> writer.setAttribute(dataset, k, v)));
	testCanvasPersistance(container, dataset, canvas, openLabels, asserts);
  }

  private static <T> void testCanvasPersistance(
		  final N5ContainerState container,
		  final String group,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	testCanvasPersistance(container, group, group, canvas, openLabels, asserts);
  }

  private static <T> void testCanvasPersistance(
		  final N5ContainerState containerState,
		  final String dataset,
		  final String labelsDataset,
		  final CachedCellImg<UnsignedLongType, ?> canvas,
		  final BiFunction<N5Reader, String, RandomAccessibleInterval<T>> openLabels,
		  final BiConsumer<UnsignedLongType, T> asserts) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	final var container = containerState.writer;

	final var tmpMetadataState = getDummyMetadataState(dataset, container);

	writeAll(tmpMetadataState, canvas);

	final RandomAccessibleInterval<T> labels = openLabels.apply(container, labelsDataset);
	Assert.assertArrayEquals(Intervals.dimensionsAsLongArray(canvas), Intervals.dimensionsAsLongArray(labels));

	for (final Pair<UnsignedLongType, T> pair : Views.interval(Views.pair(canvas, labels), labels)) {
	  LOG.trace("Comparing canvas {} and background {}", pair.getA(), pair.getB());
	  asserts.accept(pair.getA(), pair.getB());
	}
  }

  private static void writeAll(
		  final MetadataState metadataState,
		  final CachedCellImg<UnsignedLongType, ?> canvas) throws IOException, UnableToPersistCanvas, UnableToUpdateLabelBlockLookup {

	final long numBlocks = Intervals.numElements(canvas.getCellGrid().getGridDimensions());
	final long[] blocks = new long[(int)numBlocks];
	Arrays.setAll(blocks, d -> d);

	final CommitCanvasN5 cc = new CommitCanvasN5(metadataState);
	final List<TLongObjectMap<PersistCanvas.BlockDiff>> blockDiffs = cc.persistCanvas(canvas, blocks);
	if (cc.supportsLabelBlockLookupUpdate())
	  cc.updateLabelBlockLookup(blockDiffs);
  }

  private static int[] blockSize(final CellGrid grid) {

	final int[] blockSize = new int[grid.numDimensions()];
	grid.cellDimensions(blockSize);
	return blockSize;
  }

  private static long[] divideBy(final long[] divident, final int[] divisor, final long minValue) {

	final long[] quotient = new long[divident.length];
	Arrays.setAll(quotient, d -> Math.max(divident[d] / divisor[d] + (divident[d] % divisor[d] == 0 ? 0 : 1), minValue));
	return quotient;
  }

  private static int[] ones(final int n) {

	final int[] ones = new int[n];
	Arrays.fill(ones, 1);
	return ones;
  }

  private static long toBlockIndex(final long[] intervalMin, final CellGrid grid) {

	grid.getCellPosition(intervalMin, intervalMin);
	return IntervalIndexer.positionToIndex(intervalMin, grid.getGridDimensions());
  }

  private static MetadataState getDummyMetadataState(String labelsDataset, N5Writer container) {

	return new MetadataState() {

	  @Override public void setDatasetAttributes(@NotNull DatasetAttributes datasetAttributes) {

	  }

	  @Override public void setTransform(@NotNull AffineTransform3D transform) {

	  }

	  @Override public void setLabel(boolean isLabel) {

	  }

	  @Override public void setLabelMultiset(boolean isLabelMultiset) {

	  }

	  @Override public void setMinIntensity(double minIntensity) {

	  }

	  @Override public void setMaxIntensity(double maxIntensity) {

	  }

	  @Override public void setPixelResolution(@NotNull double[] pixelResolution) {

	  }

	  @Override public void setOffset(@NotNull double[] offset) {

	  }

	  @Override public void setUnit(@NotNull String unit) {

	  }

	  @Override public void setReader(@NotNull N5Reader reader) {

	  }

	  @Override public void setWriter(@Nullable N5Writer writer) {

	  }

	  @Override public void setGroup(@NotNull String group) {

	  }

	  @NotNull @Override public String getUnit() {

		return "pixel";
	  }

	  @Override public String getDataset() {

		return labelsDataset;
	  }

	  @Override public void updateTransform(double[] resolution, double[] offset) {

	  }

	  @Override public void updateTransform(AffineTransform3D newTransform) {

	  }

	  @Override public String getGroup() {

		return labelsDataset;
	  }

	  @Override public N5Writer getWriter() {

		return container;
	  }

	  @Override public N5Reader getReader() {

		return container;
	  }

	  @Override public double[] getOffset() {

		return new double[0];
	  }

	  @Override public double[] getPixelResolution() {

		return new double[0];
	  }

	  @Override public double getMaxIntensity() {

		return 0;
	  }

	  @Override public double getMinIntensity() {

		return 0;
	  }

	  @Override public boolean isLabelMultiset() {

		return true;
	  }

	  @Override public boolean isLabel() {

		return true;
	  }

	  @Override public AffineTransform3D getTransform() {

		return null;
	  }

	  @Override public DatasetAttributes getDatasetAttributes() {

		return null;
	  }

	  @Override public N5Metadata getMetadata() {

		return () -> "TEST!";
	  }

	  @Override public N5ContainerState getN5ContainerState() {

		return null;
	  }
	};
  }

}
