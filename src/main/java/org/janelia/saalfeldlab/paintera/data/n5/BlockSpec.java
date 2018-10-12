package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.IntervalIndexer;
import org.janelia.saalfeldlab.util.grids.Grids;

import java.util.Arrays;

public class BlockSpec {

	public final CellGrid grid;

	public final long[] pos;

	public final long[] min;

	public final long[] max;

	private final long[] gridDimensions;

	public BlockSpec(final CellGrid grid)
	{
		this.grid = grid;

		final int numDimensions = this.grid.numDimensions();

		this.pos = new long[numDimensions];

		this.min = new long[numDimensions];

		this.max = new long[numDimensions];

		this.gridDimensions = grid.getGridDimensions();
	}

	/**
	 * Set {@code pos}, {@code min}, {@code max} appropriately for {@code index}
	 * @param index linear index of block
	 */
	public void fromLinearIndex(final long index)
	{
		Grids.linearIndexToCellPositionMinMax(this.grid, index, pos, min, max);
	}

	public void fromInterval(final Interval interval)
	{
		interval.min(min);
		interval.max(max);
		grid.getCellPosition(min, pos);
	}

	public Interval asInterval()
	{
		return new FinalInterval(min, max);
	}

	public long asLinearIndex()
	{
		return IntervalIndexer.positionToIndex(pos, gridDimensions);
	}

	@Override
	public String toString() {
		return String.format("{BlockSpec: min=%s max=%s pos=%s grid=%s}", Arrays.toString(min), Arrays.toString(max), Arrays.toString(pos), grid);
	}

}
