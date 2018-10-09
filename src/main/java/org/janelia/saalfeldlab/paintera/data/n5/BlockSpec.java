package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import org.janelia.saalfeldlab.util.grids.Grids;

public class BlockSpec {

	public final CellGrid grid;

	public final long[] pos;

	public final long[] min;

	public final long[] max;

	public BlockSpec(final CellGrid grid)
	{
		this.grid = grid;

		final int numDimensions = this.grid.numDimensions();

		this.pos = new long[numDimensions];

		this.min = new long[numDimensions];

		this.max = new long[numDimensions];
	}

	/**
	 * Set {@code pos}, {@code min}, {@code max} appropriately for {@code index}
	 * @param index linear index of block
	 */
	public void fromLinearIndex(final long index)
	{
		Grids.linearIndexToCellPositionMinMax(this.grid, index, pos, min, max);
	}

	public Interval asInterval()
	{
		return new FinalInterval(min, max);
	}

}
