package org.janelia.saalfeldlab.util.grids;

import bdv.viewer.Source;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.img.cell.AbstractCellImg;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@LabelBlockLookup.LookupType("ALL_BLOCKS")
public class LabelBlockLookupAllBlocks implements LabelBlockLookup {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @LabelBlockLookup.Parameter
  public long[][] dims;

  @LabelBlockLookup.Parameter
  private final int[][] blockSizes;

  private final transient Map<Integer, Interval[]> intervals;

  private LabelBlockLookupAllBlocks() {

	this(null, null);
  }

  public LabelBlockLookupAllBlocks(final long[][] dims, final int[][] blockSizes) {

	this.dims = dims;
	this.blockSizes = blockSizes;
	this.intervals = new ConcurrentHashMap<>();
  }

  public static LabelBlockLookupAllBlocks fromSource(final Source<?> source) {

	return fromSource(source, 64, 64, 64);
  }

  public static LabelBlockLookupAllBlocks fromSource(final Source<?> source, final int... fallbackBlockSize) {

	if (fallbackBlockSize.length < 3)
	  return fromSource(source);

	final long[][] dims = new long[source.getNumMipmapLevels()][3];
	final int[][] blockSizes = new int[dims.length][3];
	for (int level = 0; level < dims.length; ++level) {
	  final RandomAccessibleInterval<?> rai = source.getSource(0, level);
	  Arrays.setAll(dims[level], rai::dimension);
	  if (rai instanceof AbstractCellImg<?, ?, ?, ?>)
		((AbstractCellImg<?, ?, ?, ?>)rai).getCellGrid().cellDimensions(blockSizes[level]);
	  else
		Arrays.setAll(blockSizes[level], d -> fallbackBlockSize[d]);
	}
	return new LabelBlockLookupAllBlocks(dims, blockSizes);
  }

  @NotNull
  @Override
  public String getType() {

	return "ALL_BLOCKS";
  }

  @NotNull
  @Override
  public Interval[] read(final LabelBlockLookupKey key) {

	final int level = key.getLevel();
	final long id = key.getId();
	LOG.debug("level={} id={} -- reading intervals", level, id);
	final Interval[] intervals = this.intervals.computeIfAbsent(
			level,
			k -> Grids.collectAllContainedIntervals(dims[level], blockSizes[level]).stream().toArray(Interval[]::new));
	LOG.debug("level={} id={} -- intervals: {}", level, id, intervals);
	return intervals;
  }

  @Override
  public void write(final LabelBlockLookupKey key, Interval... intervals) {

	LOG.debug("Saving blocks not supported for non-paintera dataset");
  }
}
