package org.janelia.saalfeldlab.util.grids;


import com.google.gson.annotations.Expose;
import net.imglib2.Interval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@LabelBlockLookup.LookupType("ALL_BLOCKS")
public class LabelBlockLookupAllBlocks implements LabelBlockLookup{

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

	@NotNull
	@Override
	public String getType() {
		return "ALL_BLOCKS";
	}

	@NotNull
	@Override
	public Interval[] read(int level, long id) {
		LOG.debug("level={} id={} -- reading intervals", level, id);
		final Interval[] intervals = this.intervals.computeIfAbsent(
				level,
				k -> Grids.collectAllContainedIntervals(dims[level], blockSizes[level]).stream().toArray(Interval[]::new));
		LOG.debug("level={} id={} -- intervals: {}", level, id, intervals);
		return intervals;
	}

	@Override
	public void write(int i, long l, Interval... intervals) {
		LOG.debug("Saving blocks not supported for non-paintera dataset");
	}
}
